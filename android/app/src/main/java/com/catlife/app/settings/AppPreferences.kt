package com.catlife.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.korokkeLifeDataStore by preferencesDataStore("korokke_life_settings")
val CatVoiceEnabledKey = booleanPreferencesKey("cat_voice_enabled")
