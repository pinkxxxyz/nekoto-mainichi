package com.catlife.app.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.datastore.preferences.core.*
import androidx.room.withTransaction
import com.catlife.app.data.AppDatabase
import com.catlife.app.reminder.TodoReminderScheduler
import com.catlife.app.reminder.deleteReminderCommands
import com.catlife.app.reminder.updateReminderCommands
import com.catlife.app.settings.korokkeLifeDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AndroidBackupService(context: Context, db: AppDatabase) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private val pendingRecovery = MutableStateFlow(false)
    val recoveryRequired = pendingRecovery.asStateFlow()
    private val working = MutableStateFlow(false)
    val isWorking = working.asStateFlow()
    private val store = object : BackupStore {
        override suspend fun read(): UserBackup = db.withTransaction {
            UserBackup(
                todos = db.todoDao().getAllOnce(),
                shopping = db.shoppingDao().getAllOnce(),
                settings = app.korokkeLifeDataStore.data.first().asMap().map { (key, value) ->
                    key.name to when (value) {
                        is Boolean -> BackupPreference.Bool(value)
                        is String -> BackupPreference.Text(value)
                        is Int -> BackupPreference.Integer(value)
                        is Long -> BackupPreference.LongNumber(value)
                        is Float -> BackupPreference.FloatNumber(value)
                        is Double -> BackupPreference.DoubleNumber(value)
                        is Set<*> -> BackupPreference.TextSet(value.map { it as String }.toSet())
                        else -> error("バックアップに対応していない設定です: ${key.name}")
                    }
                }.toMap(),
            )
        }

        override suspend fun replace(snapshot: UserBackup) {
            val scheduler = TodoReminderScheduler(app)
            ReminderSafeRestore(
                replaceRows = { restored, beforeReplace ->
                    db.withTransaction {
                        val previous = db.todoDao().getAllOnce()
                        beforeReplace(previous)
                        db.todoDao().deleteAllForRestore()
                        db.shoppingDao().deleteAllForRestore()
                        db.todoDao().insertRestored(restored.todos)
                        db.shoppingDao().insertRestored(restored.shopping)
                        app.korokkeLifeDataStore.edit { settings ->
                            settings.clear()
                            restored.settings.forEach { (key, value) ->
                                when (value) {
                                    is BackupPreference.Bool -> settings[booleanPreferencesKey(key)] = value.value
                                    is BackupPreference.Text -> settings[stringPreferencesKey(key)] = value.value
                                    is BackupPreference.Integer -> settings[intPreferencesKey(key)] = value.value
                                    is BackupPreference.LongNumber -> settings[longPreferencesKey(key)] = value.value
                                    is BackupPreference.FloatNumber -> settings[floatPreferencesKey(key)] = value.value
                                    is BackupPreference.DoubleNumber -> settings[doublePreferencesKey(key)] = value.value
                                    is BackupPreference.TextSet -> settings[stringSetPreferencesKey(key)] = value.value
                                }
                            }
                        }
                    }
                },
                cancel = { scheduler.apply(deleteReminderCommands(it)) },
                schedule = { scheduler.apply(updateReminderCommands(null, it)) },
            ).replace(snapshot)
        }
    }
    private val journalFile = AtomicFile(File(app.filesDir, "pending-backup-restore.bin"))
    private val journal = object : RestoreJournal {
        override suspend fun read(): UserBackup? {
            if (!journalFile.baseFile.exists() && !File(journalFile.baseFile.path + ".bak").exists()) return null
            return journalFile.openRead().use { BackupCodec.decode(readLimited(it)) }
        }
        override suspend fun write(snapshot: UserBackup) {
            val bytes = BackupCodec.encode(snapshot)
            val output = journalFile.startWrite()
            try {
                output.write(bytes)
                output.fd.sync()
                journalFile.finishWrite(output)
                check(journalFile.openRead().use { readLimited(it).contentEquals(bytes) }) { "回復用ファイルを保存できません" }
            } catch (error: Exception) {
                journalFile.failWrite(output)
                throw error
            }
        }
        override suspend fun clear() {
            journalFile.delete()
            check(!journalFile.baseFile.exists() && !File(journalFile.baseFile.path + ".bak").exists()) { "回復用ファイルを削除できません" }
        }
    }
    private val coordinator = RestoreCoordinator(store, journal)

    suspend fun recover() = withLockedOperation { coordinator.recover() }

    suspend fun export(uri: Uri) = withLockedOperation {
        coordinator.recover()
        val bytes = BackupCodec.encode(store.read())
        val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("保存先を開けません")
        output.use { it.write(bytes); it.flush() }
    }

    suspend fun readImport(uri: Uri): UserBackup = withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri) ?: error("ファイルを開けません")
        input.use { BackupCodec.decode(readLimited(it)) }
    }

    suspend fun restore(snapshot: UserBackup) = withLockedOperation { coordinator.restore(snapshot) }

    private suspend fun withLockedOperation(block: suspend () -> Unit) = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            working.value = true
            try { block() }
            finally {
                pendingRecovery.value = journalExists()
                working.value = false
            }
        }
    }

    private fun journalExists(): Boolean = hasPendingRestore(app)

    companion object {
        fun hasPendingRestore(context: Context): Boolean {
            val file = File(context.filesDir, "pending-backup-restore.bin")
            return file.exists() || File(file.path + ".bak").exists()
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= BackupCodec.MAX_BYTES) { "バックアップが大きすぎます" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
