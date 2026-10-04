package com.catlife.app

import com.catlife.app.cat.CatActionTransitions
import com.catlife.app.cat.CatMode
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class CatActionTransitionsTest {
    private val transitions = CatActionTransitions(Random(42))

    @Test fun groomingRouteReturnsThroughIdleToWalking() {
        val grooming = transitions.startRest(CatMode.WALKING, true, CatMode.GROOMING)
        assertEquals(CatMode.GROOMING, grooming)
        val idle = transitions.finished(grooming, true)
        assertEquals(CatMode.IDLE, idle)
        assertEquals(CatMode.WALKING, transitions.finished(idle, true))
    }

    @Test fun directIdleRouteReturnsToWalking() {
        val idle = transitions.startRest(CatMode.WALKING, true, CatMode.IDLE)
        assertEquals(CatMode.IDLE, idle)
        assertEquals(CatMode.WALKING, transitions.finished(idle, true))
    }

    @Test fun meowReturnsToWalkingWithoutGrooming() {
        assertEquals(CatMode.WALKING, transitions.finished(CatMode.MEOWING, true))
    }

    @Test fun automaticRestNeverInterruptsMeowOrAnotherRest() {
        for (mode in listOf(CatMode.MEOWING, CatMode.GROOMING, CatMode.IDLE)) {
            for (rest in listOf(CatMode.GROOMING, CatMode.IDLE)) {
                assertEquals(mode, transitions.startRest(mode, true, rest))
            }
        }
    }

    @Test fun inactiveWalkingCannotStartRest() {
        assertEquals(CatMode.WALKING, transitions.startRest(CatMode.WALKING, false, CatMode.GROOMING))
        assertEquals(CatMode.WALKING, transitions.startRest(CatMode.WALKING, false, CatMode.IDLE))
    }

    @Test fun interruptedRestCannotContinueToIdle() {
        assertEquals(CatMode.WALKING, transitions.finished(CatMode.GROOMING, false))
        assertEquals(CatMode.WALKING, transitions.finished(CatMode.IDLE, false))
    }

    @Test fun deactivationResetsEveryModeToWalking() {
        for (mode in CatMode.entries) {
            assertEquals(CatMode.WALKING, transitions.onInactive(mode))
        }
    }

    @Test fun walkingDelayStaysWithinFifteenToThirtyFiveSecondsAndVaries() {
        val delays = List(500) { transitions.nextWalkingDelayMillis() }
        assertTrue(delays.all { it in 15_000L..35_000L })
        assertTrue(delays.toSet().size > 1)
    }

    @Test fun randomRestSelectsOnlyGroomingAndIdleAndReachesBoth() {
        val rests = List(100) { transitions.nextRest() }.toSet()
        assertEquals(setOf(CatMode.GROOMING, CatMode.IDLE), rests)
    }
}
