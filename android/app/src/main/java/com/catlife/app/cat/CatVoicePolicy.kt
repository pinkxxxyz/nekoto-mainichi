package com.catlife.app.cat

enum class CatVoice { MYAAON, NYAA, CAT20, CAT31, UNSATISFIED, GROOMING_ANGRY }

class CatVoicePolicy(private val cooldownMillis: Long = 900L) {
    private var lastPlayedAt = Long.MIN_VALUE

    private fun isCoolingDown(nowMillis: Long): Boolean =
        lastPlayedAt != Long.MIN_VALUE && nowMillis - lastPlayedAt < cooldownMillis

    fun choose(nowMillis: Long, roll: Int): CatVoice? {
        require(roll in 0..99)
        if (isCoolingDown(nowMillis)) return null

        val voice = when (roll) {
            in 0..29 -> null
            in 30..46 -> CatVoice.MYAAON
            in 47..63 -> CatVoice.NYAA
            in 64..80 -> CatVoice.CAT20
            in 81..96 -> CatVoice.CAT31
            else -> CatVoice.UNSATISFIED
        }
        if (voice != null) lastPlayedAt = nowMillis
        return voice
    }

    /** A dedicated reaction sharing the normal voice cooldown, without a random draw. */
    fun chooseGrooming(nowMillis: Long): CatVoice? {
        if (isCoolingDown(nowMillis)) return null
        lastPlayedAt = nowMillis
        return CatVoice.GROOMING_ANGRY
    }
}
