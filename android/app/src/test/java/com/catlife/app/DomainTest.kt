package com.catlife.app

import com.catlife.app.cat.CatAction
import com.catlife.app.cat.CatBrain
import com.catlife.app.cat.CatVoice
import com.catlife.app.cat.CatVoicePolicy
import com.catlife.app.data.ShoppingItem
import com.catlife.app.data.TodoItem
import com.catlife.app.data.isOverdue
import com.catlife.app.data.sortShopping
import com.catlife.app.data.sortTodos
import com.catlife.app.ui.DecisionSoundController
import com.catlife.app.ui.CatVoiceController
import com.catlife.app.ui.CatAnimationPlayback
import com.catlife.app.ui.formatTodoDueSummary
import com.catlife.app.ui.formatTodoPickerTime
import com.catlife.app.ui.TodoDueDraft
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DomainTest {
    @Test fun timePickerFormatsSelectedCenterValuesAsSavedTime() {
        assertEquals("16:40", formatTodoPickerTime(16, 40))
        assertEquals("00:05", formatTodoPickerTime(0, 5))
    }

    @Test fun todoDueSummarySupportsNoDate() {
        assertNull(formatTodoDueSummary(null, null))
    }

    @Test fun todoDueSummarySupportsDateWithoutTime() {
        assertEquals("2026年10月5日\n時間指定なし", formatTodoDueSummary("2026-10-05", null))
    }

    @Test fun todoDueSummarySupportsDateAndTime() {
        assertEquals("2026年10月5日 17:30", formatTodoDueSummary("2026-10-05", "17:30"))
    }

    @Test fun cancellingDateReselectionKeepsExistingDeadline() {
        val original = TodoDueDraft("2026-10-05", "17:30")

        val cancelled = original.beginDateSelection(LocalDate.of(2026, 10, 6)).cancelDateSelection()

        assertEquals(original, cancelled)
    }

    @Test fun dateSelectionCanCommitWithOrWithoutTime() {
        val draft = TodoDueDraft(null, null).beginDateSelection(LocalDate.of(2026, 10, 5))

        assertEquals(TodoDueDraft("2026-10-05", null), draft.commitDateSelection(null))
        assertEquals(TodoDueDraft("2026-10-05", "17:30"), draft.commitDateSelection("17:30"))
    }

    @Test fun meowUsesManualLifetimeSoSceneViewCannotRemoveItBeforeResume() {
        val events = mutableListOf<String>()
        val playback = CatAnimationPlayback(
            stopAnimation = { events += "stop:$it" },
            playAnimation = { index, loop -> events += "play:$index:$loop" }
        )

        playback.startMeow()

        assertEquals(
            listOf("stop:0", "stop:3", "play:3:true"),
            events
        )
    }

    @Test fun meowCompletionRestartsWalkLoopAfterStoppingMeowAndStaleWalk() {
        val events = mutableListOf<String>()
        val playback = CatAnimationPlayback(
            stopAnimation = { events += "stop:$it" },
            playAnimation = { index, loop -> events += "play:$index:$loop" }
        )

        playback.resumeWalking()

        assertEquals(
            listOf("stop:3", "stop:0", "play:0:true"),
            events
        )
    }

    @Test fun catVoicePolicyUsesExpectedProbabilityBoundaries() {
        val expectedAtBoundary = listOf(
            0 to null,
            29 to null,
            30 to CatVoice.AA,
            49 to CatVoice.AA,
            50 to CatVoice.NN,
            69 to CatVoice.NN,
            70 to CatVoice.NYAA,
            84 to CatVoice.NYAA,
            85 to CatVoice.MYAAON,
            96 to CatVoice.MYAAON,
            97 to CatVoice.UNSATISFIED,
            99 to CatVoice.UNSATISFIED
        )

        expectedAtBoundary.forEach { (roll, expected) ->
            assertEquals(
                "roll=$roll",
                expected,
                CatVoicePolicy(cooldownMillis = 900).choose(nowMillis = 1_000, roll = roll)
            )
        }
    }

    @Test fun catVoiceControllerDoesNotChooseOrPlayWhenDisabled() {
        var chooseCount = 0
        val playedVoices = mutableListOf<CatVoice>()
        val controller = CatVoiceController(
            chooseVoice = { _, _ -> chooseCount++; CatVoice.AA },
            playVoice = playedVoices::add,
            nowMillis = { 1_000L },
            nextRoll = { 30 }
        )

        val selectedVoice = controller.choose(enabled = false)

        assertNull(selectedVoice)
        assertEquals(0, chooseCount)
        assertTrue(playedVoices.isEmpty())
    }

    @Test fun catVoiceControllerReturnsNullAndDoesNotPlayForSilentRoll() {
        val playedVoices = mutableListOf<CatVoice>()
        val controller = CatVoiceController(
            chooseVoice = { _, _ -> null },
            playVoice = playedVoices::add,
            nowMillis = { 1_000L },
            nextRoll = { 0 }
        )

        val selectedVoice = controller.choose(enabled = true)

        assertNull(selectedVoice)
        assertTrue(playedVoices.isEmpty())
    }

    @Test fun catVoiceControllerReturnsSelectedVoiceToTapCaller() {
        val playedVoices = mutableListOf<CatVoice>()
        val controller = CatVoiceController(
            chooseVoice = { _, _ -> CatVoice.NYAA },
            playVoice = playedVoices::add,
            nowMillis = { 1_000L },
            nextRoll = { 70 }
        )

        val selectedVoice = controller.choose(enabled = true)

        assertEquals(CatVoice.NYAA, selectedVoice)
        assertTrue(playedVoices.isEmpty())

        controller.play(selectedVoice!!)

        assertEquals(listOf(CatVoice.NYAA), playedVoices)
    }

    @Test fun catVoicePolicyBlocksOverlappingTapSoundsDuringCooldown() {
        val policy = CatVoicePolicy(cooldownMillis = 900)

        assertEquals(CatVoice.NYAA, policy.choose(nowMillis = 1_000, roll = 70))
        assertNull(policy.choose(nowMillis = 1_899, roll = 70))
        assertEquals(CatVoice.NYAA, policy.choose(nowMillis = 1_900, roll = 70))
    }

    @Test fun decisionSoundPlaysOnceWhenEnabled() {
        var playCount = 0
        val controller = DecisionSoundController { playCount++ }

        controller.play(enabled = true)

        assertEquals(1, playCount)
    }

    @Test fun decisionSoundDoesNotPlayWhenDisabled() {
        var playCount = 0
        val controller = DecisionSoundController { playCount++ }

        controller.play(enabled = false)

        assertEquals(0, playCount)
    }

    @Test fun todoSortsIncompleteBeforeCompleteThenByCreation() {
        val items = listOf(TodoItem(1,"done",true,createdAt=1), TodoItem(2,"later",false,createdAt=3), TodoItem(3,"first",false,createdAt=2))
        assertEquals(listOf(3L,2L,1L), sortTodos(items).map { it.id })
    }
    @Test fun shoppingSortsUnpurchasedBeforePurchasedThenByCreation() {
        val items = listOf(ShoppingItem(1,"b",true,1), ShoppingItem(2,"c",false,3), ShoppingItem(3,"a",false,2))
        assertEquals(listOf(3L,2L,1L), sortShopping(items).map { it.id })
    }
    @Test fun dateOnlyDeadlineExpiresAfterEndOfDay() {
        val item = TodoItem(1,"x",deadlineDate="2026-09-20")
        assertFalse(item.isOverdue(LocalDate.of(2026,9,20), LocalTime.of(23,59)))
        assertTrue(item.isOverdue(LocalDate.of(2026,9,21), LocalTime.MIDNIGHT))
    }
    @Test fun tapInterruptsCatWithPurringThenReturnsToWalking() {
        val brain = CatBrain()
        brain.tap(1000)
        assertEquals(CatAction.PURRING, brain.actionAt(5999))
        assertEquals(CatAction.WALKING, brain.actionAt(6000))
    }
}
