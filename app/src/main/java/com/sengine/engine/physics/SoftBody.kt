package com.sengine.engine.physics

import kotlin.math.sqrt

/**
 * Position-Based Dynamics / Verlet soft-body core: ropes, chains, cloth strips
 * and hanging lanterns. Deterministic (no RNG), pure Kotlin, no Android deps.
 *
 * A soft body is a list of points connected by distance constraints; points can
 * be pinned (infinite mass) and the whole body is affected by gravity, wind and
 * user forces. `step` uses Verlet integration followed by iterative constraint
 * projection — the same family of solvers used by modern physics engines.
 */
class VerletPoint(
    var x: Float, var y: Float, var z: Float = 0f,
    var pinned: Boolean = false
) {
    var oldX = x
    var oldY = y
    var oldZ = z
    var fx = 0f
    var fy = 0f
    var fz = 0f

    fun teleport(x: Float, y: Float, z: Float = this.z) {
        this.x = x; this.y = y; this.z = z
        oldX = x; oldY = y; oldZ = z
        fx = 0f; fy = 0f; fz = 0f
    }
}

class VerletConstraint(val a: Int, val b: Int, var rest: Float, var stiffness: Float = 1f)

class SoftBody(val name: String, val points: MutableList<VerletPoint>, val constraints: MutableList<VerletConstraint>) {

    var gravityX = 0f
    var gravityY = -9.81f
    var gravityZ = 0f
    var windX = 0f
    var windY = 0f
    var windZ = 0f
    var damping = 0.995f
    var iterations = 6
    /** Optional ground plane: points are clamped to y >= [groundY]. */
    var groundY: Float? = null
    var broken = false

    fun pin(i: Int, value: Boolean = true) {
        points.getOrNull(i)?.pinned = value
    }

    fun addForce(fx: Float, fy: Float, fz: Float = 0f, index: Int = -1) {
        if (index >= 0) {
            points.getOrNull(index)?.let { it.fx += fx; it.fy += fy; it.fz += fz }
        } else {
            for (p in points) { p.fx += fx; p.fy += fy; p.fz += fz }
        }
    }

    /** Radial impulse (explosions, shockwaves) with linear falloff. */
    fun radialImpulse(cx: Float, cy: Float, cz: Float, radius: Float, force: Float) {
        for (p in points) {
            val dx = p.x - cx; val dy = p.y - cy; val dz = p.z - cz
            val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
            if (d > radius) continue
            val k = (1f - d / radius) * force / d
            p.fx += dx * k; p.fy += dy * k; p.fz += dz * k
        }
    }

    fun step(dt: Float) {
        val h = dt.coerceIn(1f / 240f, 1f / 20f)
        for (p in points) {
            if (p.pinned) { p.fx = 0f; p.fy = 0f; p.fz = 0f; continue }
            val vx = (p.x - p.oldX) * damping
            val vy = (p.y - p.oldY) * damping
            val vz = (p.z - p.oldZ) * damping
            p.oldX = p.x; p.oldY = p.y; p.oldZ = p.z
            p.x += vx + (gravityX + windX + p.fx) * h * h
            p.y += vy + (gravityY + windY + p.fy) * h * h
            p.z += vz + (gravityZ + windZ + p.fz) * h * h
            p.fx = 0f; p.fy = 0f; p.fz = 0f
        }
        repeat(iterations.coerceIn(1, 32)) {
            for (c in constraints) {
                val a = points.getOrNull(c.a) ?: continue
                val b = points.getOrNull(c.b) ?: continue
                val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
                val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
                val diff = (d - c.rest) / d * c.stiffness.coerceIn(0f, 1f) * 0.5f
                val wa = if (a.pinned) 0f else 1f
                val wb = if (b.pinned) 0f else 1f
                val w = wa + wb
                if (w <= 0f) continue
                val sa = 2f * wa / w * diff
                val sb = 2f * wb / w * diff
                a.x += dx * sa; a.y += dy * sa; a.z += dz * sa
                b.x -= dx * sb; b.y -= dy * sb; b.z -= dz * sb
            }
            val gy = groundY
            if (gy != null) for (p in points) {
                if (!p.pinned && p.y < gy) { p.y = gy; p.oldY = gy }
            }
        }
    }

    companion object {
        /** Hanging rope: point 0 pinned, all segments have equal rest length. */
        fun rope(name: String, x: Float, y: Float, z: Float, segments: Int, spacing: Float, dirX: Float = 1f, dirY: Float = 0f): SoftBody {
            val n = segments.coerceIn(2, 256)
            val len = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(1e-6f)
            val ux = dirX / len; val uy = dirY / len
            val pts = MutableList(n) { i -> VerletPoint(x + ux * spacing * i, y + uy * spacing * i, z) }
            pts[0].pinned = true
            val cs = MutableList(n - 1) { i -> VerletConstraint(i, i + 1, spacing) }
            return SoftBody(name, pts, cs)
        }

        /** Cloth strip: a grid of `cols x rows` points pinned along the top row. */
        fun cloth(name: String, x: Float, y: Float, z: Float, cols: Int, rows: Int, spacing: Float): SoftBody {
            val c = cols.coerceIn(2, 64); val r = rows.coerceIn(2, 64)
            val pts = ArrayList<VerletPoint>()
            for (j in 0 until r) for (i in 0 until c) {
                val p = VerletPoint(x + i * spacing, y - j * spacing, z)
                if (j == 0) p.pinned = true
                pts.add(p)
            }
            val cs = ArrayList<VerletConstraint>()
            for (j in 0 until r) for (i in 0 until c) {
                val id = j * c + i
                if (i + 1 < c) cs.add(VerletConstraint(id, id + 1, spacing))
                if (j + 1 < r) cs.add(VerletConstraint(id, id + c, spacing))
            }
            return SoftBody(name, pts.toMutableList(), cs)
        }
    }
}

/** Registry used by the engine and by the `soft.*` script API. */
class SoftBodyWorld {
    private val bodies = LinkedHashMap<String, SoftBody>()
    var autoStep = true

    fun createRope(name: String, x: Float, y: Float, z: Float, segments: Int, spacing: Float, dirX: Float, dirY: Float): SoftBody {
        val b = SoftBody.rope(name, x, y, z, segments, spacing, dirX, dirY)
        bodies[name] = b
        return b
    }

    fun createCloth(name: String, x: Float, y: Float, z: Float, cols: Int, rows: Int, spacing: Float): SoftBody {
        val b = SoftBody.cloth(name, x, y, z, cols, rows, spacing)
        bodies[name] = b
        return b
    }

    fun get(name: String): SoftBody? = bodies[name]
    fun remove(name: String) { bodies.remove(name) }
    fun clear() { bodies.clear() }
    fun names(): List<String> = bodies.keys.toList()
    val size: Int get() = bodies.size

    fun step(dt: Float) {
        if (!autoStep || dt <= 0f) return
        for (b in bodies.values) b.step(dt)
    }
}
