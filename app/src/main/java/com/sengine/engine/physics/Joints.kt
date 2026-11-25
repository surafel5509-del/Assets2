package com.sengine.engine.physics

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import kotlin.math.sqrt

/** Constraint flavours understood by [JointSolver]. */
enum class JointMode { DISTANCE, SPRING, ROPE, WELD }

/** A point mass seen by the constraint solver. Positions are world space. */
interface JointParticle {
    var px: Float
    var py: Float
    var vx: Float
    var vy: Float
    val invMass: Float
    fun commit()
}

interface JointParticle3 {
    var px: Float
    var py: Float
    var pz: Float
    var vx: Float
    var vy: Float
    var vz: Float
    val invMass: Float
    fun commit()
}

/** Simple in-memory particle used by tests, procedural animation and tools. */
class JointPoint(
    px: Float, py: Float, override var vx: Float = 0f, override var vy: Float = 0f,
    override val invMass: Float = 1f
) : JointParticle {
    override var px: Float = px
    override var py: Float = py
    override fun commit() {}
}

class JointPoint3(
    px: Float, py: Float, pz: Float,
    override var vx: Float = 0f, override var vy: Float = 0f, override var vz: Float = 0f,
    override val invMass: Float = 1f
) : JointParticle3 {
    override var px: Float = px
    override var py: Float = py
    override var pz: Float = pz
    override fun commit() {}
}

class JointLink(
    val a: JointParticle,
    val b: JointParticle,
    val mode: JointMode = JointMode.DISTANCE,
    var restLength: Float = 1f,
    var stiffness: Float = 0.85f,
    var damping: Float = 0.08f,
    var breakForce: Float = 0f
) {
    var broken = false
    var lastForce = 0f
        private set

    internal fun setForce(v: Float) { lastForce = v }
}

class JointLink3(
    val a: JointParticle3,
    val b: JointParticle3,
    val mode: JointMode = JointMode.DISTANCE,
    var restLength: Float = 1f,
    var stiffness: Float = 0.85f,
    var damping: Float = 0.08f,
    var breakForce: Float = 0f
) {
    var broken = false
}

/**
 * Position-based constraint solver (Gauss-Seidel). Distance / weld / rope use
 * projection; springs integrate a damped Hooke impulse. Both paths damp the
 * relative velocity along the constraint axis so chains settle instead of
 * oscillating forever. No Android dependencies: fully unit-testable.
 */
object JointSolver {

    fun solve(links: List<JointLink>, dt: Float, iterations: Int = 4): Int {
        val iters = iterations.coerceIn(1, 16)
        var broken = 0
        repeat(iters) {
            for (j in links) {
                if (j.broken) continue
                val dx = j.b.px - j.a.px
                val dy = j.b.py - j.a.py
                val d2 = dx * dx + dy * dy
                val dist = sqrt(d2).coerceAtLeast(1e-6f)
                val nx = dx / dist
                val ny = dy / dist
                val w = j.a.invMass + j.b.invMass
                if (w <= 0f) continue
                var err = dist - (if (j.mode == JointMode.WELD) 0f else j.restLength)
                if (j.mode == JointMode.ROPE && err < 0f) err = 0f // ropes only pull
                val rel = (j.b.vx - j.a.vx) * nx + (j.b.vy - j.a.vy) * ny
                val k = j.stiffness.coerceAtLeast(0f)
                val force = kotlin.math.abs(k * err + j.damping * rel)
                j.setForce(force)
                if (j.breakForce > 0f && force > j.breakForce) {
                    j.broken = true
                    broken++
                    continue
                }
                if (j.mode == JointMode.SPRING) {
                    val impulse = -(k * err + j.damping * rel) * dt.coerceAtMost(0.05f)
                    j.a.vx -= impulse * nx * j.a.invMass
                    j.a.vy -= impulse * ny * j.a.invMass
                    j.b.vx += impulse * nx * j.b.invMass
                    j.b.vy += impulse * ny * j.b.invMass
                } else {
                    val gain = (k / iters.toFloat()).coerceIn(0f, 1f)
                    val corr = err / dist * gain / w
                    j.a.px += dx * corr * j.a.invMass
                    j.a.py += dy * corr * j.a.invMass
                    j.b.px -= dx * corr * j.b.invMass
                    j.b.py -= dy * corr * j.b.invMass
                    val jImpulse = -rel * j.damping.coerceIn(0f, 1f) / w
                    j.a.vx -= jImpulse * nx * j.a.invMass
                    j.a.vy -= jImpulse * ny * j.a.invMass
                    j.b.vx += jImpulse * nx * j.b.invMass
                    j.b.vy += jImpulse * ny * j.b.invMass
                }
            }
        }
        for (j in links) { j.a.commit(); j.b.commit() }
        return broken
    }

    fun solve3(links: List<JointLink3>, dt: Float, iterations: Int = 4): Int {
        val iters = iterations.coerceIn(1, 16)
        var broken = 0
        repeat(iters) {
            for (j in links) {
                if (j.broken) continue
                val dx = j.b.px - j.a.px
                val dy = j.b.py - j.a.py
                val dz = j.b.pz - j.a.pz
                val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
                val nx = dx / dist; val ny = dy / dist; val nz = dz / dist
                val w = j.a.invMass + j.b.invMass
                if (w <= 0f) continue
                var err = dist - (if (j.mode == JointMode.WELD) 0f else j.restLength)
                if (j.mode == JointMode.ROPE && err < 0f) err = 0f
                val rel = (j.b.vx - j.a.vx) * nx + (j.b.vy - j.a.vy) * ny + (j.b.vz - j.a.vz) * nz
                val k = j.stiffness.coerceAtLeast(0f)
                if (j.breakForce > 0f && kotlin.math.abs(k * err) > j.breakForce) {
                    j.broken = true; broken++; continue
                }
                if (j.mode == JointMode.SPRING) {
                    val impulse = -(k * err + j.damping * rel) * dt.coerceAtMost(0.05f)
                    j.a.vx -= impulse * nx * j.a.invMass; j.b.vx += impulse * nx * j.b.invMass
                    j.a.vy -= impulse * ny * j.a.invMass; j.b.vy += impulse * ny * j.b.invMass
                    j.a.vz -= impulse * nz * j.a.invMass; j.b.vz += impulse * nz * j.b.invMass
                } else {
                    val corr = err / dist * (k / iters).coerceIn(0f, 1f) / w
                    j.a.px += dx * corr * j.a.invMass; j.b.px -= dx * corr * j.b.invMass
                    j.a.py += dy * corr * j.a.invMass; j.b.py -= dy * corr * j.b.invMass
                    j.a.pz += dz * corr * j.a.invMass; j.b.pz -= dz * corr * j.b.invMass
                }
            }
        }
        for (j in links) { j.a.commit(); j.b.commit() }
        return broken
    }
}

/** Adapts a scene object to the solver. Static/kinematic bodies have zero inverse mass. */
private class BodyParticle(val go: GameObject, val rb: Rigidbody2D?) : JointParticle {
    override var px = go.computeWorld().tx
    override var py = go.computeWorld().ty
    override var vx = rb?.vx ?: 0f
    override var vy = rb?.vy ?: 0f
    override val invMass: Float = if (rb == null || rb.bodyType != 0) 0f else 1f / rb.mass.coerceAtLeast(0.001f)
    override fun commit() {
        go.setWorldPosition(px, py)
        rb?.let { it.vx = vx; it.vy = vy }
    }
}

private class BodyParticle3(val go: GameObject, val rb: Rigidbody3D?) : JointParticle3 {
    private val w = go.computeWorld3()
    override var px = w[12]
    override var py = w[13]
    override var pz = w[14]
    override var vx = rb?.vx ?: 0f
    override var vy = rb?.vy ?: 0f
    override var vz = rb?.vz ?: 0f
    override val invMass: Float = if (rb == null || rb.bodyType != 0) 0f else 1f / rb.mass.coerceAtLeast(0.001f)
    override fun commit() {
        go.setWorldPosition3(px, py, pz)
        rb?.let { it.vx = vx; it.vy = vy; it.vz = vz }
    }
}

/**
 * Collects joints declared with the `Joint2D` / `Joint3D` components plus the
 * ones created at runtime from scripts, then solves them once per physics step.
 */
class JointSystem {
    private val runtime2 = ArrayList<JointLink>()
    private val runtime3 = ArrayList<JointLink3>()
    var brokenCount = 0
        private set
    var lastActive = 0
        private set

    fun clear() {
        runtime2.clear(); runtime3.clear(); brokenCount = 0; lastActive = 0
    }

    fun add2(a: GameObject, b: GameObject, mode: Int, rest: Float, stiffness: Float, damping: Float, breakForce: Float): Boolean {
        if (a === b) return false
        val m = JointMode.values().getOrElse(mode) { JointMode.DISTANCE }
        runtime2.add(JointLink(BodyParticle(a, a.getAny<Rigidbody2D>()), BodyParticle(b, b.getAny<Rigidbody2D>()), m, rest, stiffness, damping, breakForce))
        return true
    }

    fun add3(a: GameObject, b: GameObject, mode: Int, rest: Float, stiffness: Float, damping: Float, breakForce: Float): Boolean {
        if (a === b) return false
        val m = JointMode.values().getOrElse(mode) { JointMode.DISTANCE }
        runtime3.add(JointLink3(BodyParticle3(a, a.getAny<Rigidbody3D>()), BodyParticle3(b, b.getAny<Rigidbody3D>()), m, rest, stiffness, damping, breakForce))
        return true
    }

    fun step(scene: Scene, dt: Float, iterations: Int = 2) {
        val links = ArrayList<JointLink>(runtime2)
        val links3 = ArrayList<JointLink3>(runtime3)
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val j = go.getAny<com.sengine.engine.core.Joint2D>() ?: continue
            if (!j.enabled || j.target.isBlank()) continue
            val other = scene.find(j.target) ?: continue
            val mode = JointMode.values().getOrElse(j.mode) { JointMode.DISTANCE }
            val rest = if (j.restLength <= 0f) {
                val w = go.computeWorld(); val o = other.computeWorld()
                sqrt((w.tx - o.tx) * (w.tx - o.tx) + (w.ty - o.ty) * (w.ty - o.ty))
            } else j.restLength
            links.add(JointLink(BodyParticle(go, go.getAny<Rigidbody2D>()), BodyParticle(other, other.getAny<Rigidbody2D>()),
                mode, rest, j.stiffness, j.damping, j.breakForce))
        }
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val j = go.getAny<com.sengine.engine.core.Joint3D>() ?: continue
            if (!j.enabled || j.target.isBlank()) continue
            val other = scene.find(j.target) ?: continue
            val mode = JointMode.values().getOrElse(j.mode) { JointMode.DISTANCE }
            val w = go.computeWorld3(); val o = other.computeWorld3()
            val rest = if (j.restLength <= 0f) sqrt(
                (w[12] - o[12]) * (w[12] - o[12]) + (w[13] - o[13]) * (w[13] - o[13]) +
                    (w[14] - o[14]) * (w[14] - o[14])) else j.restLength
            links3.add(JointLink3(BodyParticle3(go, go.getAny<Rigidbody3D>()), BodyParticle3(other, other.getAny<Rigidbody3D>()),
                mode, rest, j.stiffness, j.damping, j.breakForce))
        }
        runtime2.removeAll { it.broken }
        runtime3.removeAll { it.broken }
        brokenCount += JointSolver.solve(links, dt, iterations)
        if (links3.isNotEmpty()) brokenCount += JointSolver.solve3(links3, dt, iterations)
        lastActive = links.size + links3.size
    }

    val runtimeCount: Int get() = runtime2.size + runtime3.size
}
