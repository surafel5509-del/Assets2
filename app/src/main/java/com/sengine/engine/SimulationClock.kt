package com.sengine.engine

import kotlin.math.min

/**
 * Deterministic fixed-timestep scheduler used by the physics worlds.
 *
 * A variable render delta is accumulated and drained in whole [fixedDt]
 * steps. [alpha] is the leftover fraction (0..1) that renderers can use to
 * interpolate between the previous and the current simulation state. A frame
 * spike can never cause more than [maxSubSteps] steps, which prevents the
 * classic "spiral of death"; the leftover is dropped in that case.
 *
 * The class is deliberately free of Android/GL dependencies so it can be
 * unit-tested headlessly and reused by replay / networking code.
 */
class SimulationClock(
    fixedDt: Float = 1f / 60f,
    var maxSubSteps: Int = 5,
    var maxFrameDt: Float = 0.25f
) {
    var fixedDt: Float = fixedDt
        set(v) {
            field = v.coerceIn(1f / 240f, 1f / 10f)
            accumulator = 0f
        }

    var accumulator = 0f
        private set

    /** Total number of fixed steps since [reset] — the simulation "tick". */
    var totalSteps = 0L
        private set

    /** Steps produced by the last [advance] call. */
    var lastSteps = 0
        private set

    /** Interpolation factor for rendering between two simulation states. */
    val alpha: Float get() = (accumulator / fixedDt).coerceIn(0f, 1f)

    val hz: Float get() = 1f / fixedDt

    fun reset() {
        accumulator = 0f
        totalSteps = 0L
        lastSteps = 0
    }

    /** Consumes a render frame delta and returns how many fixed steps to run. */
    fun advance(frameDt: Float): Int {
        val dt = min(frameDt.coerceAtLeast(0f), maxFrameDt.coerceAtLeast(0f))
        accumulator += dt
        var steps = 0
        while (accumulator >= fixedDt && steps < maxSubSteps.coerceAtLeast(1)) {
            accumulator -= fixedDt
            steps++
        }
        // Dropping the backlog keeps the simulation deterministic after a stall.
        if (steps >= maxSubSteps.coerceAtLeast(1) && accumulator >= fixedDt) accumulator = 0f
        lastSteps = steps
        totalSteps += steps
        return steps
    }

    /** Replays a fixed number of steps without touching the accumulator. */
    fun forceSteps(n: Int) {
        totalSteps += n
        lastSteps = n
    }
}
