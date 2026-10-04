package com.catlife.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.catlife.app.R

class DecisionSoundController(private val playSound: () -> Unit) {
    fun play(enabled: Boolean) {
        if (enabled) playSound()
    }
}

private class DecisionSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var loadedSoundId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSoundId = sampleId
        }
        soundPool.load(context, R.raw.decision_button, 1)
    }

    fun play() {
        val soundId = loadedSoundId
        if (soundId != 0) soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() = soundPool.release()
}

@Composable
fun rememberDecisionSoundController(): DecisionSoundController {
    val context = LocalContext.current
    val player = remember(context) { DecisionSoundPlayer(context.applicationContext) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return remember(player) { DecisionSoundController(player::play) }
}
