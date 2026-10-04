package com.catlife.app.cat

import kotlin.random.Random

enum class CatMode { WALKING, MEOWING, GROOMING, IDLE }

/** Pure transitions shared by the Compose timers and unit tests. */
class CatActionTransitions(private val random: Random = Random.Default) {
    fun nextWalkingDelayMillis(): Long = random.nextLong(15_000L, 35_001L)

    fun nextRest(): CatMode = if (random.nextBoolean()) CatMode.GROOMING else CatMode.IDLE

    fun startRest(mode: CatMode, active: Boolean, rest: CatMode): CatMode =
        if (active && mode == CatMode.WALKING && rest in setOf(CatMode.GROOMING, CatMode.IDLE)) {
            rest
        } else {
            mode
        }

    fun finished(mode: CatMode, active: Boolean): CatMode = when {
        !active -> CatMode.WALKING
        mode == CatMode.GROOMING -> CatMode.IDLE
        else -> CatMode.WALKING
    }

    fun onInactive(mode: CatMode): CatMode = finished(mode, active = false)
}
