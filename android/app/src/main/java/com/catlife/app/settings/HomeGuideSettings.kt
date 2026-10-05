package com.catlife.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map

val HomeGuideCompletedKey = booleanPreferencesKey("home_guide_completed")

/** Device-local guide state, separate from tutorial completion and user backups. */
class HomeGuideSettings(private val store: DataStore<Preferences>) {
    val completed = store.data.map { it[HomeGuideCompletedKey] }

    suspend fun initialize() {
        store.edit { preferences ->
            if (preferences[HomeGuideCompletedKey] == null) {
                // Existing users have already launched the app. Only new installs need this guide.
                preferences[HomeGuideCompletedKey] = preferences[TutorialCompletedKey] == true
            }
        }
    }

    suspend fun complete() {
        store.edit { it[HomeGuideCompletedKey] = true }
    }
}
