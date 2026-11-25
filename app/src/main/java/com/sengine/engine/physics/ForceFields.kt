package com.sengine.engine.physics

import kotlin.math.sqrt

/** Anything a force field can push (tests use [ForcePoint], the engine wraps rigidbodies). */
interface ForcePoint {
    val x: Float
    val y: Float
    val z: Float
    val mass: Float
    val active: Boolean
    var vx: Float
    var vy: Float
    var vz: Float
}

class ForcePointImpl(
    override val x: Float, override val y: Float, override val z: Float = 0f,
    override val mass: Float = 1f, override val active: Boolean = true,
    override var vx: Float = 0f, override var vy: Float = 0f, override var vz: Float = 0f
) : ForcePoint

/** Radial falloff shapes for explosions and attractors. */
enum class Falloff { LINEAR, INVERSE_SQUARE, CONSTANT }

/**
 * Area forces: explosions, shockwaves, wind zones, gravity wells. Pure math so
 * gameplay code, the editor tools and unit tests all share one implementation.
 */
object ForceFields {

    /** Pushes bodies away from (cx,cy,cz); returns the number of affected bodies. */
    fun explode(
        bodies: List<ForcePoint>, cx: Float, cy: Float, cz: Float,
        radius: Float, force: Float, falloff: Falloff = Falloff.LINEAR
    ): Int {
        if (radius <= 0f) return 0
        var n = 0
        for (b in bodies) {
            if (!b.active) continue
            val dx = b.x - cx; val dy = b.y - cy; val dz = b.z - cz
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            if (d > radius) continue
            val k = factor(d, radius, falloff) * force / d.coerceAtLeast(0.05f) / b.mass.coerceAtLeast(0.001f)
            b.vx += dx * k; b.vy += dy * k; b.vz += dz * k
            n++
        }
        return n
    }

    /** Pulls bodies toward (cx,cy,cz) — use negative force for repulsion. */
    fun attract(
        bodies: List<ForcePoint>, cx: Float, cy: Float, cz: Float,
        radius: Float, force: Float, falloff: Falloff = Falloff.INVERSE_SQUARE
    ): Int {
        if (radius <= 0f) return 0
        var n = 0
        for (b in bodies) {
            if (!b.active) continue
            val dx = cx - b.x; val dy = cy - b.y; val dz = cz - b.z
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            if (d > radius) continue
            val k = factor(d, radius, falloff) * force / d.coerceAtLeast(0.05f) / b.mass.coerceAtLeast(0.001f)
            b.vx += dx * k; b.vy += dy * k; b.vz += dz * k
            n++
        }
        return n
    }

    /** Constant acceleration applied over dt (wind / conveyor / current). */
    fun wind(bodies: List<ForcePoint>, fx: Float, fy: Float, fz: Float, dt: Float): Int {
        var n = 0
        for (b in bodies) {
            if (!b.active) continue
            b.vx += fx * dt / b.mass.coerceAtLeast(0.001f)
            b.vy += fy * dt / b.mass.coerceAtLeast(0.001f)
            b.vz += fz * dt / b.mass.coerceAtLeast(0.001f)
            n++
        }
        return n
    }

    private fun factor(d: Float, radius: Float, falloff: Falloff): Float = when (falloff) {
        Falloff.CONSTANT -> 1f
        Falloff.LINEAR -> (1f - d / radius).coerceIn(0f, 1f)
        Falloff.INVERSE_SQUARE -> {
            val q = (d / radius).coerceIn(0.05f, 1f)
            (1f / (1f + 8f * q * q) - 1f / 9f).coerceIn(0f, 1f)
        }
    }
}
