package com.catlife.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.catlife.app.backup.*
import com.catlife.app.data.ShoppingItem
import com.catlife.app.data.TodoItem
import com.catlife.app.settings.LocationSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class SettingsBackupTest {
    private val old = UserBackup(todos = listOf(TodoItem(id = 1, title = "前のTODO", createdAt = 1)))
    private val incoming = UserBackup(
        todos = listOf(TodoItem(2, "猫ごはん", true, "2026-10-05", "21:55", null, true, "21:55", 123)),
        shopping = listOf(ShoppingItem(3, "牛乳", true, 456)),
        settings = mapOf(
            "cat_voice_enabled" to BackupPreference.Bool(false),
            "sound_enabled" to BackupPreference.Bool(true),
            "tutorial_done" to BackupPreference.Bool(true),
            "weather_location" to BackupPreference.Text("東京都"),
            "future_count" to BackupPreference.Integer(7),
            "future_long" to BackupPreference.LongNumber(8),
            "future_float" to BackupPreference.FloatNumber(1.25f),
            "future_double" to BackupPreference.DoubleNumber(2.5),
            "future_set" to BackupPreference.TextSet(setOf("あ", "い")),
        ),
    )

    @Test fun locationSurvivesStoreRecreationAndRejectsBlankWithoutChangingValue() = runBlocking {
        val dir = Files.createTempDirectory("location-test").toFile()
        val file = dir.resolve("settings.preferences_pb")
        val firstJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file })
        val repository = LocationSettings(store)
        assertNull(repository.location.first())
        repository.save("  東京都  ")
        assertEquals("東京都", repository.location.first())
        try { repository.save("　 \n"); fail("blank accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("東京都", repository.location.first())
        firstJob.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val recreated = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file })
            assertEquals("東京都", LocationSettings(recreated).location.first())
        } finally { secondJob.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun generatedBackupContainsVersionAndRoundTripsAllUserData() {
        assertEquals(1, incoming.version)
        assertEquals(incoming, BackupCodec.decode(BackupCodec.encode(incoming)))
    }

    @Test fun randomTruncatedCorruptedAndTrailingDataAreRejected() {
        val good = BackupCodec.encode(incoming)
        listOf(byteArrayOf(1, 2, 3), good.copyOf(25), good + byteArrayOf(0), good.clone().also { it[20] = (it[20].toInt() xor 1).toByte() }).forEach {
            try { BackupCodec.decode(it); fail("invalid accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun unsupportedVersionWithValidChecksumIsRejected() {
        val bytes = BackupCodec.encode(incoming)
        // Header is 13 ASCII bytes 'KorokkeLifeBK', followed by a big-endian version.
        bytes[16] = 2
        val payload = bytes.copyOf(bytes.size - 32)
        val checksum = MessageDigest.getInstance("SHA-256").digest(payload)
        checksum.copyInto(bytes, payload.size)
        try { BackupCodec.decode(bytes); fail("version accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun invalidRecordsAndBlankLocationAreRejectedBeforeWriting() {
        listOf(
            incoming.copy(todos = listOf(TodoItem(id = 1, title = " "))),
            incoming.copy(todos = listOf(TodoItem(id = 0, title = "bad"))),
            incoming.copy(todos = listOf(TodoItem(id = 1, title = "date", deadlineDate = "bad"))),
            incoming.copy(todos = listOf(TodoItem(id = 1, title = "time", deadlineTime = "25:00"))),
            incoming.copy(todos = listOf(incoming.todos[0], incoming.todos[0])),
            incoming.copy(settings = mapOf("weather_location" to BackupPreference.Text(" "))),
            incoming.copy(settings = mapOf("cat_voice_enabled" to BackupPreference.Text("true"))),
        ).forEach {
            try { BackupCodec.encode(it); fail("invalid accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    private class MemoryStore(var value: UserBackup) : BackupStore {
        var failAfterPartialWrite = false
        var failAlways = false
        override suspend fun read() = value
        override suspend fun replace(snapshot: UserBackup) {
            if (failAlways) error("disk unavailable")
            value = snapshot
            if (failAfterPartialWrite) { failAfterPartialWrite = false; error("settings write failed") }
        }
    }
    private class MemoryJournal : RestoreJournal {
        var pending: UserBackup? = null
        var failWrite = false
        override suspend fun read() = pending
        override suspend fun write(snapshot: UserBackup) { if (failWrite) error("full disk"); pending = snapshot }
        override suspend fun clear() { pending = null }
    }

    @Test fun validImportReplacesDataAndClearsUndoJournal() = runBlocking {
        val store = MemoryStore(old); val journal = MemoryJournal()
        RestoreCoordinator(store, journal).restore(incoming)
        assertEquals(incoming, store.value); assertNull(journal.pending)
    }

    @Test fun partialFailureRestoresPreviousDataAndSettings() = runBlocking {
        val store = MemoryStore(old).apply { failAfterPartialWrite = true }; val journal = MemoryJournal()
        try { RestoreCoordinator(store, journal).restore(incoming); fail("failure hidden") } catch (_: IllegalStateException) { }
        assertEquals(old, store.value); assertNull(journal.pending)
    }

    @Test fun inabilityToWriteUndoJournalDoesNotTouchExistingData() = runBlocking {
        val store = MemoryStore(old); val journal = MemoryJournal().apply { failWrite = true }
        try { RestoreCoordinator(store, journal).restore(incoming); fail("failure hidden") } catch (_: IllegalStateException) { }
        assertEquals(old, store.value)
    }

    @Test fun interruptedRestoreRecoversBeforeNextOperation() = runBlocking {
        val store = MemoryStore(incoming); val journal = MemoryJournal().apply { pending = old }
        RestoreCoordinator(store, journal).recover()
        assertEquals(old, store.value); assertNull(journal.pending)
    }

    @Test fun failedRecoveryRetainsJournalForRetry() = runBlocking {
        val store = MemoryStore(incoming).apply { failAlways = true }; val journal = MemoryJournal().apply { pending = old }
        try { RestoreCoordinator(store, journal).recover(); fail("failure hidden") } catch (_: IllegalStateException) { }
        assertEquals(old, journal.pending)
        store.failAlways = false
        RestoreCoordinator(store, journal).recover()
        assertEquals(old, store.value); assertNull(journal.pending)
    }

    @Test fun invalidImportDoesNotTouchStoreOrJournal() = runBlocking {
        val store = MemoryStore(old); val journal = MemoryJournal()
        try { RestoreCoordinator(store, journal).restore(incoming.copy(version = 99)); fail("version accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(old, store.value); assertNull(journal.pending)
    }
    @Test fun interruptedRollbackCannotLeaveImportedAlarmWithoutMatchingRows() = runBlocking {
        var rows = incoming
        val activeAlarms = mutableSetOf(2L)
        var crashAfterCommit = true
        val writer = ReminderSafeRestore(
            replaceRows = { snapshot, beforeCommit ->
                beforeCommit(rows.todos)
                rows = snapshot
                if (crashAfterCommit) { crashAfterCommit = false; throw AssertionError("simulated process death") }
            },
            cancel = { activeAlarms.remove(it.id) },
            schedule = { activeAlarms.add(it.id) },
        )
        val store = object : BackupStore {
            override suspend fun read() = rows
            override suspend fun replace(snapshot: UserBackup) = writer.replace(snapshot)
        }
        val journal = MemoryJournal().apply { pending = old }
        try { RestoreCoordinator(store, journal).recover(); fail("crash hidden") } catch (_: AssertionError) { }
        assertFalse(activeAlarms.contains(2L))
        assertEquals(old, journal.pending)
        RestoreCoordinator(store, journal).recover()
        assertEquals(old, rows)
        assertEquals(setOf(1L), activeAlarms)
        assertNull(journal.pending)
    }

    @Test fun failedRowTransactionRestoresCancelledAlarmsOnRecovery() = runBlocking {
        var rows = old
        val activeAlarms = mutableSetOf(1L)
        var failCommit = true
        val writer = ReminderSafeRestore(
            replaceRows = { snapshot, beforeCommit ->
                beforeCommit(rows.todos)
                if (failCommit) { failCommit = false; error("Room commit failed") }
                rows = snapshot
            },
            cancel = { activeAlarms.remove(it.id) },
            schedule = { activeAlarms.add(it.id) },
        )
        val store = object : BackupStore {
            override suspend fun read() = rows
            override suspend fun replace(snapshot: UserBackup) = writer.replace(snapshot)
        }
        try { RestoreCoordinator(store, MemoryJournal()).restore(incoming); fail("failure hidden") } catch (_: IllegalStateException) { }
        assertEquals(old, rows)
        assertEquals(setOf(1L), activeAlarms)
    }

}
