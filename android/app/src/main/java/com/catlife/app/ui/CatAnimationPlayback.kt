package com.catlife.app.ui

class CatAnimationPlayback(
    private val stopAnimation: (Int) -> Unit,
    private val playAnimation: (index: Int, loop: Boolean) -> Unit
) {
    fun startMeow() {
        stopAnimation(WALK_ANIMATION_INDEX)
        stopAnimation(MEOW_ANIMATION_INDEX)
        // SceneView removes non-looping entries from its animation map on-frame.
        // Keep ownership here and stop it explicitly at the known GLB duration.
        playAnimation(MEOW_ANIMATION_INDEX, true)
    }

    fun resumeWalking() {
        stopAnimation(MEOW_ANIMATION_INDEX)
        // Remove any stale Walk registration before starting a fresh loop.
        stopAnimation(WALK_ANIMATION_INDEX)
        playAnimation(WALK_ANIMATION_INDEX, true)
    }

    private companion object {
        const val WALK_ANIMATION_INDEX = 0
        const val MEOW_ANIMATION_INDEX = 3
    }
}
