package com.catlife.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.catlife.app.backup.*
import com.catlife.app.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RegionSelectionTest {
    private fun catalog(): LocationCatalog = File("src/main/assets/locations/regions.tsv").reader().use(LocationCatalog::read)

    @Test fun bundledDataCoversEveryPrefectureAndDisambiguatesMunicipalities() {
        val data = catalog()
        assertEquals(47, data.prefectures.size)
        assertEquals("福島県 福島市", data.find("070009", "072010")!!.displayName)
        assertEquals("大阪府 大阪市 福島区", data.find("270008", "271039")!!.displayName)
        assertNull(data.find("270008", "072010"))
        val fukushima = data.find("070009", "072010")!!
        assertEquals(37.760759, fukushima.latitude, 0.000001)
        assertEquals(140.473269, fukushima.longitude, 0.000001)
    }

    @Test fun sameNamedVillagesWithinPrefectureAreDistinguishedAndLegacyNameIsNotGuessed() {
        val data = catalog()
        assertEquals("北海道 古宇郡 泊村", data.find("010006", "014036")!!.displayName)
        assertEquals("北海道 国後郡 泊村", data.find("010006", "016969")!!.displayName)
        assertNull(data.findDisplayName("北海道 泊村"))
        assertEquals("014036", data.findDisplayName("北海道 古宇郡 泊村")!!.municipalityCode)
    }

    @Test fun prefectureChangeClearsMunicipalityAndCannotConfirmIncompleteDraft() {
        val data = catalog()
        val selected = RegionSelectionDraft("070009", "072010")
        assertEquals("福島県 福島市", selected.selection(data)!!.displayName)
        val changed = selected.selectPrefecture("270008")
        assertNull(changed.municipalityCode)
        assertNull(changed.selection(data))
    }

    @Test fun municipalitySearchIsScopedToSelectedPrefectureAndDoesNotPickAutomatically() {
        val data = catalog()
        val results = data.searchMunicipalities("070009", "福島")
        assertEquals(listOf("072010"), results.map { it.municipalityCode })
        assertTrue(data.searchMunicipalities("070009", "大阪").isEmpty())
        assertNull(RegionSelectionDraft("070009").selection(data))
    }

    @Test fun structuredSelectionPersistsAndLegacyStringRemainsCompatible() = runBlocking {
        val dir = Files.createTempDirectory("region-test").toFile()
        val file = dir.resolve("settings.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        try {
            val settings = LocationSettings(store, catalog())
            settings.save("福島市")
            assertEquals("福島市", settings.location.first())
            assertNull(settings.selectedLocation.first())
            // Changing draft or cancelling never writes to the repository.
            val draft = RegionSelectionDraft().selectPrefecture("070009").copy(municipalityCode = "072010")
            assertEquals("福島市", settings.location.first())
            settings.save(draft.selection(catalog())!!)
            assertEquals("福島県 福島市", settings.location.first())
            assertEquals("072010", settings.selectedLocation.first()!!.municipalityCode)
            val prefs = store.data.first()
            assertEquals("070009", prefs[WeatherPrefectureCodeKey])
            assertEquals("福島県", prefs[WeatherPrefectureNameKey])
            assertEquals("072010", prefs[WeatherMunicipalityCodeKey])
            assertEquals("福島市", prefs[WeatherMunicipalityNameKey])
        } finally { job.cancelAndJoin() }
        val recreatedJob = SupervisorJob()
        try {
            val recreated = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + recreatedJob), produceFile = { file })
            val settings = LocationSettings(recreated, catalog())
            assertEquals("福島県 福島市", settings.location.first())
            assertEquals("072010", settings.selectedLocation.first()!!.municipalityCode)
            settings.save("旧設定")
            assertNull(settings.selectedLocation.first())
            assertEquals("旧設定", settings.location.first())
        } finally { recreatedJob.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun forgedCrossPrefectureSelectionIsRejectedWithoutOverwriting() = runBlocking {
        val dir = Files.createTempDirectory("region-invalid").toFile(); val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("s.preferences_pb") })
            val settings = LocationSettings(store, catalog())
            settings.save("保存済み")
            val invalid = catalog().find("070009", "072010")!!.copy(prefectureCode = "270008")
            try { settings.save(invalid); fail("invalid accepted") } catch (_: IllegalArgumentException) { }
            assertEquals("保存済み", settings.location.first())
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun incorrectlyTypedOptionalRegionFieldsFallBackToLegacyDisplay() = runBlocking {
        val dir = Files.createTempDirectory("region-types").toFile(); val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("s.preferences_pb") })
            val settings = LocationSettings(store, catalog())
            settings.save("福島市")
            store.edit { it[intPreferencesKey("weather_prefecture_code")] = 7 }
            assertNull(settings.selectedLocation.first())
            assertEquals("福島市", settings.location.first())
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun frozenLegacyVersionOneBackupIsStillDecodedAndEncodedUnchanged() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("location-legacy-v1.klbackup")!!.use { it.readBytes() }
        val snapshot = BackupCodec.decode(bytes)
        assertEquals(1, snapshot.version)
        assertEquals(BackupPreference.Text("Fukushima"), snapshot.settings["weather_location"])
        assertEquals("21:55", snapshot.todos.single().reminderTime)
        assertArrayEquals(bytes, BackupCodec.encode(snapshot))
    }

    @Test fun structuredRegionUsesExistingVersionOnePreferenceFields() {
        val snapshot = UserBackup(settings = mapOf(
            "weather_location" to BackupPreference.Text("福島県 福島市"),
            "weather_prefecture_code" to BackupPreference.Text("070009"),
            "weather_prefecture_name" to BackupPreference.Text("福島県"),
            "weather_municipality_code" to BackupPreference.Text("072010"),
            "weather_municipality_name" to BackupPreference.Text("福島市"),
        ))
        val decoded = BackupCodec.decode(BackupCodec.encode(snapshot))
        assertEquals(1, decoded.version)
        assertEquals(snapshot, decoded)
    }
}
