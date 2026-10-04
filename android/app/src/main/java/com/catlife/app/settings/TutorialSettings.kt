package com.catlife.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

// Per-device onboarding is intentionally outside the user's backup preferences.
val Context.korokkeLifeTutorialStore by preferencesDataStore("korokke_life_tutorial")
val TutorialCompletedKey = booleanPreferencesKey("tutorial_completed")
const val TUTORIAL_PAGE_COUNT = 4

fun shouldShowTutorial(completed: Boolean, manuallyRequested: Boolean): Boolean = !completed || manuallyRequested

class TutorialSettings(private val store: DataStore<Preferences>) {
    val completed = store.data.map { it[TutorialCompletedKey] ?: false }

    suspend fun complete(pageIndex: Int) {
        require(pageIndex == TUTORIAL_PAGE_COUNT - 1) { "最後のページでのみ完了できます" }
        store.edit { it[TutorialCompletedKey] = true }
    }
}
