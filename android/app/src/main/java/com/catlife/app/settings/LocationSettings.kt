package com.catlife.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

val WeatherLocationKey = stringPreferencesKey("weather_location")
val WeatherPrefectureCodeKey = stringPreferencesKey("weather_prefecture_code")
val WeatherPrefectureNameKey = stringPreferencesKey("weather_prefecture_name")
val WeatherMunicipalityCodeKey = stringPreferencesKey("weather_municipality_code")
val WeatherMunicipalityNameKey = stringPreferencesKey("weather_municipality_name")

class LocationSettings(private val store: DataStore<Preferences>, val catalog: LocationCatalog? = null) {
    val selectedLocation = store.data.map { settings ->
        val values = settings.asMap()
        val prefecture = values[WeatherPrefectureCodeKey] as? String
        val municipality = values[WeatherMunicipalityCodeKey] as? String
        if (prefecture == null || municipality == null) catalog?.findDisplayName(settings[WeatherLocationKey])
        else catalog?.find(prefecture, municipality)?.takeIf {
            it.prefectureName == values[WeatherPrefectureNameKey] &&
                it.municipalityName == values[WeatherMunicipalityNameKey] &&
                it.displayName == settings[WeatherLocationKey]
        }
    }
    // Keep the original display string for legacy preferences and version-1 backups.
    val location = store.data.map { it[WeatherLocationKey] }

    suspend fun save(location: WeatherLocation) {
        require(catalog?.find(location.prefectureCode, location.municipalityCode) == location) { "一覧から地域を選択してください" }
        store.edit {
            it[WeatherLocationKey] = location.displayName
            it[WeatherPrefectureCodeKey] = location.prefectureCode
            it[WeatherPrefectureNameKey] = location.prefectureName
            it[WeatherMunicipalityCodeKey] = location.municipalityCode
            it[WeatherMunicipalityNameKey] = location.municipalityName
        }
    }

    // Legacy callers remain compatible; the region selection UI never uses free text to save.
    suspend fun save(name: String) {
        val normalized = name.trim()
        require(normalized.isNotBlank() && normalized.length <= 100) { "地域名を1〜100文字で入力してください" }
        store.edit {
            it[WeatherLocationKey] = normalized
            it.remove(WeatherPrefectureCodeKey)
            it.remove(WeatherPrefectureNameKey)
            it.remove(WeatherMunicipalityCodeKey)
            it.remove(WeatherMunicipalityNameKey)
        }
    }
}
