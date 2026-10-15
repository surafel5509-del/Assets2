package com.sengine.engine.core

/** Bounded accumulator shared by the 2D and 3D solvers. Not a determinism guarantee. */
class FixedStepClock(stepSeconds: Float = 1f / 60f, val maxSteps: Int = 5) {
    var stepSeconds = stepSeconds
        set(value) {
            require(value.isFinite() && value > 0f)
            if (field != value) { field = value; accumulator = 0.0 }
        }
    init {
        require(stepSeconds.isFinite() && stepSeconds > 0f)
        require(maxSteps > 0)
    }
    private var accumulator = 0.0
    var droppedSeconds = 0.0
        private set
    var stepsLastFrame = 0
        private set
    val interpolationAlpha: Float get() = (accumulator / stepSeconds).toFloat().coerceIn(0f, 1f)

    fun advance(deltaSeconds: Float, simulate: (Float) -> Unit): Int {
        stepsLastFrame = 0
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f) return 0
        accumulator += deltaSeconds.toDouble()
        val step = stepSeconds.toDouble()
        while (accumulator + step * 1e-6 >= step && stepsLastFrame < maxSteps) {
            // Consume before callback so callbacks cannot observe a stale accumulator.
            accumulator = (accumulator - step).coerceAtLeast(0.0)
            stepsLastFrame++
            simulate(stepSeconds)
        }
        if (accumulator >= step) {
            val dropped = kotlin.math.floor(accumulator / step) * step
            droppedSeconds += dropped
            accumulator -= dropped
        }
        return stepsLastFrame
    }

    fun reset() { accumulator = 0.0; droppedSeconds = 0.0; stepsLastFrame = 0 }
}
