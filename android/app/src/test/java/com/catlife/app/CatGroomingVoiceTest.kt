package com.catlife.app

import com.catlife.app.cat.CatVoice
import com.catlife.app.cat.CatVoicePolicy
import com.catlife.app.ui.CatVoiceController
import org.junit.Assert.*
import org.junit.Test

class CatGroomingVoiceTest {
    @Test fun exhaustiveNormalRollsKeepSilenceRareVoiceAndNearEqualFourVoices() {
        val counts = (0..99).map { CatVoicePolicy().choose(1000, it) }.groupingBy { it }.eachCount()
        assertEquals(30, counts[null])
        assertEquals(17, counts[CatVoice.MYAAON])
        assertEquals(17, counts[CatVoice.NYAA])
        assertEquals(17, counts[CatVoice.CAT20])
        assertEquals(16, counts[CatVoice.CAT31])
        assertEquals(3, counts[CatVoice.UNSATISFIED])
        assertNull(counts[CatVoice.GROOMING_ANGRY])
    }

    @Test fun groomingUsesDedicatedVoiceWithoutAnyNormalDraw() {
        val policy = CatVoicePolicy()
        val played = mutableListOf<CatVoice>()
        val controller = CatVoiceController(
            chooseVoice = { _, _ -> error("Normal draw during grooming") },
            playVoice = played::add,
            nowMillis = { 1000L },
            nextRoll = { error("Random draw during grooming") },
            chooseGroomingVoice = policy::chooseGrooming,
        )
        controller.playGrooming(enabled = true)
        assertEquals(listOf(CatVoice.GROOMING_ANGRY), played)
        controller.playGrooming(enabled = true)
        assertEquals(1, played.size)
    }

    @Test fun disabledGroomingDoesNotChooseReadClockOrPlay() {
        val controller = CatVoiceController(
            chooseVoice = { _, _ -> error("Normal draw") },
            playVoice = { error("Playback while disabled") },
            nowMillis = { error("Clock read while disabled") },
            nextRoll = { error("Random draw") },
            chooseGroomingVoice = { error("Grooming selection while disabled") },
        )
        controller.playGrooming(enabled = false)
    }

    @Test fun groomingAndNormalShareExact900msCooldownInBothDirections() {
        val policy = CatVoicePolicy()
        assertEquals(CatVoice.GROOMING_ANGRY, policy.chooseGrooming(1000))
        assertNull(policy.chooseGrooming(1899))
        assertNull(policy.choose(1899, 30))
        assertEquals(CatVoice.MYAAON, policy.choose(1900, 30))
        assertNull(policy.chooseGrooming(2799))
        assertEquals(CatVoice.GROOMING_ANGRY, policy.chooseGrooming(2800))
    }

    @Test fun silentNormalDrawDoesNotConsumeGroomingCooldown() {
        val policy = CatVoicePolicy()
        assertNull(policy.choose(1000, 0))
        assertEquals(CatVoice.GROOMING_ANGRY, policy.chooseGrooming(1001))
    }
}
