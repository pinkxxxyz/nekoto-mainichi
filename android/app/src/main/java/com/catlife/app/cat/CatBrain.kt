package com.catlife.app.cat

import kotlin.random.Random

enum class CatAction { WALKING, SITTING, SLEEPING, SCRATCHING, PURRING }
data class CatState(val action: CatAction = CatAction.WALKING, val x: Float = .25f, val facingRight: Boolean = true)

class CatBrain(private val random: Random = Random.Default) {
    private var purringUntil = 0L
    fun tap(now: Long) { purringUntil = now + 5_000 }
    fun actionAt(now: Long): CatAction = if (now < purringUntil) CatAction.PURRING else CatAction.WALKING
    fun next(current: CatState): Pair<CatState, Long> {
        val roll = random.nextInt(10)
        return when {
            roll == 0 -> CatState(CatAction.SCRATCHING, .82f, current.x <= .82f) to random.nextLong(5_000, 10_001)
            roll <= 2 -> CatState(CatAction.SLEEPING, .18f, current.x <= .18f) to random.nextLong(30_000, 90_001)
            roll <= 5 -> current.copy(action = CatAction.SITTING) to random.nextLong(5_000, 15_001)
            else -> CatState(CatAction.WALKING, random.nextDouble(.15, .85).toFloat(), random.nextBoolean()) to random.nextLong(8_000, 16_001)
        }
    }
}
