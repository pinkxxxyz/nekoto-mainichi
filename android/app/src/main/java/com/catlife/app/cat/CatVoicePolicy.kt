package com.catlife.app.cat

enum class CatVoice { AA, NN, NYAA, MYAAON, UNSATISFIED }

class CatVoicePolicy(private val cooldownMillis: Long = 900L) {
    private var lastPlayedAt = Long.MIN_VALUE

    fun choose(nowMillis: Long, roll: Int): CatVoice? {
        require(roll in 0..99)
        if (lastPlayedAt != Long.MIN_VALUE && nowMillis - lastPlayedAt < cooldownMillis) return null

        val voice = when (roll) {
            in 0..29 -> null
            in 30..49 -> CatVoice.AA
            in 50..69 -> CatVoice.NN
            in 70..84 -> CatVoice.NYAA
            in 85..96 -> CatVoice.MYAAON
            else -> CatVoice.UNSATISFIED
        }
        if (voice != null) lastPlayedAt = nowMillis
        return voice
    }
}
