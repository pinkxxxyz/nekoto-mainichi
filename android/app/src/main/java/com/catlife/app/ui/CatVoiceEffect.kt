package com.catlife.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.catlife.app.R
import com.catlife.app.cat.CatVoice
import com.catlife.app.cat.CatVoicePolicy
import kotlin.random.Random

class CatVoiceController(
    private val chooseVoice: (Long, Int) -> CatVoice?,
    private val playVoice: (CatVoice) -> Unit,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val nextRoll: () -> Int = { Random.nextInt(100) }
) {
    fun choose(enabled: Boolean): CatVoice? {
        if (!enabled) return null
        return chooseVoice(nowMillis(), nextRoll())
    }

    fun play(voice: CatVoice) = playVoice(voice)
}

private class CatVoicePlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()
    private val loadedSounds = mutableMapOf<CatVoice, Int>()
    private val pendingSounds = mutableMapOf<Int, CatVoice>()
    private var activeStreamId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val voice = pendingSounds.remove(sampleId)
            android.util.Log.d("CatVoiceAudio", "LOAD sample=$sampleId status=$status voice=$voice")
            if (status == 0 && voice != null) loadedSounds[voice] = sampleId
        }
        load(context, CatVoice.AA, R.raw.aa)
        load(context, CatVoice.MYAAON, R.raw.myaaon)
        load(context, CatVoice.NN, R.raw.nn)
        load(context, CatVoice.NYAA, R.raw.nyaa)
        load(context, CatVoice.UNSATISFIED, R.raw.unsatisfied_cat)
    }

    private fun load(context: Context, voice: CatVoice, resourceId: Int) {
        pendingSounds[soundPool.load(context, resourceId, 1)] = voice
    }

    fun play(voice: CatVoice) {
        val soundId = loadedSounds[voice]
        android.util.Log.d("CatVoiceAudio", "PLAY request voice=$voice soundId=$soundId loaded=${loadedSounds.keys}")
        if (soundId == null) return
        if (activeStreamId != 0) soundPool.stop(activeStreamId)
        activeStreamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        android.util.Log.d("CatVoiceAudio", "PLAY result streamId=$activeStreamId")
    }

    fun release() = soundPool.release()
}

@Composable
fun rememberCatVoiceController(): CatVoiceController {
    val context = LocalContext.current
    val player = remember(context) { CatVoicePlayer(context.applicationContext) }
    val policy = remember { CatVoicePolicy() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return remember(player, policy) { CatVoiceController(policy::choose, player::play) }
}
