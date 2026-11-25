package com.sengine.engine.physics

import com.sengine.engine.SimulationClock
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lightweight impulse-based 2D physics: axis-aligned boxes and circles,
 * gravity, restitution, friction, triggers and collision callbacks.
 */
class PhysicsWorld {

    interface Listener {
        fun onCollisionEnter(a: GameObject, b: GameObject)
        fun onTriggerEnter(a: GameObject, b: GameObject)
        fun onTriggerExit(a: GameObject, b: GameObject)
    }

    var listener: Listener? = null

    /** Deterministic fixed-step scheduler shared with the diagnostics HUD. */
    val clock = SimulationClock()
    /** Number of contact solver passes per physics step (1 = legacy behaviour). */
    var solverIterations = 1
    /** Spatial-hash broad phase (false = brute force, useful for comparisons). */
    var broadphase = true
    var cellSize = 4f
    var lastBroadphasePairs = 0
        private set
    var lastBruteForcePairs = 0
        private set
    /** Called at the end of every fixed step (joints / soft bodies / gameplay hooks). */
    var onPostStep: ((Scene, Float) -> Unit)? = null

    private class Body(
        val go: GameObject,
        val rb: Rigidbody2D?,
        val col: Collider2D,
        var cx: Float = 0f, var cy: Float = 0f,
        var hw: Float = 0f, var hh: Float = 0f, var r: Float = 0f
    ) {
        val isCircle get() = col.shape == 1
        val invMass: Float
            get() = if (rb == null || rb.bodyType != 0) 0f else 1f / rb.mass
    }

    /** Layer filter: both colliders must accept the other's layer bit. */
    private fun layersMatch(a: Collider2D, b: Collider2D): Boolean =
        (a.mask and (1 shl b.layer)) != 0 && (b.mask and (1 shl a.layer)) != 0

    private var prevContacts = HashSet<Long>()
    private var prevTriggers = HashSet<Long>()

    fun reset() {
        clock.reset()
        prevContacts = HashSet(); prevTriggers = HashSet()
    }

    fun step(scene: Scene, dt: Float) {
        val steps = clock.advance(dt)
        repeat(steps) { stepFixed(scene, clock.fixedDt) }
    }

    /** One deterministic physics tick. Exposed for replays and headless tests. */
    fun stepFixed(scene: Scene, dt: Float) {
        // kinematic bodies record their per-step delta so riders can be carried
        val carry = HashMap<Long, FloatArray>()
        // integrate
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val rb = go.get<Rigidbody2D>() ?: continue
            rb.grounded = false
            when (rb.bodyType) {
                0 -> {
                    rb.vx += scene.gravityX * rb.gravityScale * dt
                    rb.vy += scene.gravityY * rb.gravityScale * dt
                    if (rb.drag > 0f) {
                        val k = max(0f, 1f - rb.drag * dt)
                        rb.vx *= k; rb.vy *= k
                    }
                    clampSpeed(rb)
                    var dx = rb.vx * dt
                    var dy = rb.vy * dt
                    if (rb.ccd) ccdAdjust(scene, go, rb, dt)?.let { dx = it[0]; dy = it[1] }
                    moveWorld(go, dx, dy)
                }
                1 -> {
                    val w = go.computeWorld()
                    val px = w.tx; val py = w.ty
                    moveWorld(go, rb.vx * dt, rb.vy * dt)
                    val w2 = go.computeWorld()
                    carry[go.id] = floatArrayOf(w2.tx - px, w2.ty - py)
                }
                else -> {}
            }
        }

        // gather bodies
        val bodies = ArrayList<Body>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider2D>() ?: continue
            val b = Body(go, go.get(), col)
            refresh(b)
            bodies.add(b)
        }

        val contacts = HashSet<Long>()
        val triggers = HashSet<Long>()
        val carried = HashSet<Long>()
        val pairs = candidatePairs(bodies)
        repeat(solverIterations.coerceIn(1, 8)) { iter ->
            if (iter > 0) for (b in bodies) refresh(b)
            for ((i, j) in pairs) {
                val a = bodies[i]
                val b = bodies[j]
                if (!layersMatch(a.col, b.col)) continue
                if (a.invMass == 0f && b.invMass == 0f && !a.col.isTrigger && !b.col.isTrigger) continue
                val m = collide(a, b) ?: continue
                val key = pairKey(a.go.id, b.go.id)
                if (a.col.isTrigger || b.col.isTrigger) {
                    triggers.add(key)
                    if (iter == 0 && key !in prevTriggers) listener?.onTriggerEnter(a.go, b.go)
                    continue
                }
                contacts.add(key)
                resolve(a, b, m)
                // ride kinematic movers (platforms): apply their step delta to resting dynamics
                val kin = if (a.rb?.bodyType == 1) a else if (b.rb?.bodyType == 1) b else null
                val dyn = if (kin === a) b else a
                val delta = kin?.let { carry[it.go.id] }
                if (iter == 0 && delta != null && dyn.rb?.bodyType == 0 && m.ny >= -0.55f && dyn.go.id !in carried) {
                    // only when the dynamic body rests on top of (or is pushed by) the mover
                    // normal points a->b: rider above mover => ny>0.55 when mover is a, ny<-0.55 when mover is b
                    val onTop = if (kin === a) m.ny > 0.55f else m.ny < -0.55f
                    val sidePush = abs(m.nx) > 0.55f
                    if (onTop || sidePush) {
                        moveWorld(dyn.go, delta[0], delta[1])
                        carried.add(dyn.go.id)
                    }
                }
                if (iter == 0 && key !in prevContacts) listener?.onCollisionEnter(a.go, b.go)
            }
        }
        for (k in prevTriggers) if (k !in triggers) {
            val a = scene.findById(k shr 32)
            val b = scene.findById(k and 0xFFFFFFFFL)
            if (a != null && b != null) listener?.onTriggerExit(a, b)
        }
        prevContacts = contacts
        prevTriggers = triggers
        onPostStep?.invoke(scene, dt)
    }

    /** Broad-phase candidate pairs: spatial hash grid, or brute force for small scenes. */
    private fun candidatePairs(bodies: List<Body>): List<Pair<Int, Int>> {
        if (bodies.size < 12 || !broadphase) {
            val all = ArrayList<Pair<Int, Int>>()
            for (i in bodies.indices) for (j in i + 1 until bodies.size) all.add(i to j)
            lastBroadphasePairs = all.size
            lastBruteForcePairs = all.size
            return all
        }
        val boxes = bodies.mapIndexed { i, b ->
            val hw = if (b.isCircle) b.r else b.hw
            val hh = if (b.isCircle) b.r else b.hh
            Aabb2(b.cx - hw, b.cy - hh, b.cx + hw, b.cy + hh, i)
        }
        val hash = SpatialHash2D(cellSize)
        val pairs = hash.candidatePairs(boxes)
        lastBroadphasePairs = hash.lastPairCount
        lastBruteForcePairs = hash.lastBruteForcePairs
        return pairs
    }

    // ---------------------------------------------------------------- shape casts
    /** Result of a swept-shape query. */
    data class CastResult(val go: GameObject, val distance: Float, val nx: Float, val ny: Float)

    private fun castShapes(scene: Scene, tag: String): Pair<List<CastShape2>, List<GameObject>> {
        val shapes = ArrayList<CastShape2>()
        val owners = ArrayList<GameObject>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            if (tag.isNotEmpty() && go.tag != tag) continue
            val col = go.get<Collider2D>() ?: continue
            val b = Body(go, null, col)
            refresh(b)
            shapes.add(CastShape2(b.cx, b.cy, b.hw, b.hh, b.isCircle, b.r, shapes.size))
            owners.add(go)
        }
        return shapes to owners
    }

    /** Sweeps a circle; returns the first hit (nearest) or null. */
    fun circleCast(scene: Scene, ox: Float, oy: Float, dx: Float, dy: Float,
                   radius: Float, maxDist: Float, tag: String = "", ignore: GameObject? = null): CastResult? {
        val (shapes, owners) = castShapes(scene, tag)
        val hit = ShapeCasts.circleCast(shapes, ox, oy, dx, dy, radius, maxDist) { owners[it] !== ignore } ?: return null
        return CastResult(owners[hit.index], hit.distance, hit.nx, hit.ny)
    }

    /** Sweeps an axis-aligned box of half-size (hw,hh). */
    fun boxCast(scene: Scene, ox: Float, oy: Float, dx: Float, dy: Float,
                hw: Float, hh: Float, maxDist: Float, tag: String = "", ignore: GameObject? = null): CastResult? {
        val (shapes, owners) = castShapes(scene, tag)
        val hit = ShapeCasts.boxCast(shapes, ox, oy, dx, dy, hw, hh, maxDist) { owners[it] !== ignore } ?: return null
        return CastResult(owners[hit.index], hit.distance, hit.nx, hit.ny)
    }

    // ---------------------------------------------------------------- area forces
    private class RigidForceAdapter(val go: GameObject, val rb: Rigidbody2D) : ForcePoint {
        private val w = go.computeWorld()
        override val x: Float = w.tx
        override val y: Float = w.ty
        override val z: Float = 0f
        override val mass: Float = rb.mass.coerceAtLeast(0.001f)
        override val active: Boolean = go.isActiveInHierarchy() && rb.bodyType == 0
        override var vx: Float get() = rb.vx; set(v) { rb.vx = v }
        override var vy: Float get() = rb.vy; set(v) { rb.vy = v }
        override var vz: Float = 0f
    }

    private fun forceBodies(scene: Scene, tag: String): List<ForcePoint> {
        val out = ArrayList<ForcePoint>()
        for (go in scene.objects) {
            if (tag.isNotEmpty() && go.tag != tag) continue
            val rb = go.get<Rigidbody2D>() ?: continue
            out.add(RigidForceAdapter(go, rb))
        }
        return out
    }

    fun explode(scene: Scene, x: Float, y: Float, radius: Float, force: Float, tag: String = "", falloff: Int = 0): Int =
        ForceFields.explode(forceBodies(scene, tag), x, y, 0f, radius, force, Falloff.values().getOrElse(falloff) { Falloff.LINEAR })

    fun attract(scene: Scene, x: Float, y: Float, radius: Float, force: Float, tag: String = "", falloff: Int = 1): Int =
        ForceFields.attract(forceBodies(scene, tag), x, y, 0f, radius, force, Falloff.values().getOrElse(falloff) { Falloff.INVERSE_SQUARE })

    fun wind(scene: Scene, fx: Float, fy: Float, dt: Float, tag: String = ""): Int =
        ForceFields.wind(forceBodies(scene, tag), fx, fy, 0f, dt)

    private fun clampSpeed(rb: Rigidbody2D) {
        if (rb.maxSpeed <= 0f) return
        val s = sqrt(rb.vx * rb.vx + rb.vy * rb.vy)
        if (s > rb.maxSpeed) {
            val k = rb.maxSpeed / s
            rb.vx *= k; rb.vy *= k
        }
    }

    /**
     * Swept collision for fast bodies: if the straight-line move would cross a solid
     * collider, stop just before the impact instead of tunneling through it.
     */
    private fun ccdAdjust(scene: Scene, go: GameObject, rb: Rigidbody2D, dt: Float): FloatArray? {
        val dx = rb.vx * dt
        val dy = rb.vy * dt
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.01f) return null
        val ux = dx / len
        val uy = dy / len
        val w = go.computeWorld()
        val col = go.get<Collider2D>() ?: return null
        val hw = col.width * w.scaleX * 0.5f
        val hh = col.height * w.scaleY * 0.5f
        val r = if (col.shape == 1) col.radius * max(w.scaleX, w.scaleY) else max(hw, hh)
        val ox = w.tx
        val oy = w.ty
        var best = len
        var hit = false
        for (other in scene.objects) {
            if (other === go || !other.isActiveInHierarchy()) continue
            val oc = other.get<Collider2D>() ?: continue
            if (oc.isTrigger || !layersMatch(col, oc)) continue
            val orb = other.get<Rigidbody2D>()
            if (orb != null && orb.bodyType == 0) continue // only vs kinematic/static solids
            val ob = Body(other, orb, oc)
            refresh(ob)
            val t = if (ob.isCircle) rayCircle(ox, oy, ux, uy, ob) else rayBox(ox, oy, ux, uy, ob)
            if (t != null && t >= 0f && t < best) { best = t; hit = true }
        }
        val stop = if (hit) max(best - r, 0f) else len
        if (stop < len - 1e-4f) {
            // kill velocity into the surface so the body rests instead of vibrating
            rb.vx = if (abs(ux) > 0.5f) 0f else rb.vx
            rb.vy = if (abs(uy) > 0.5f) 0f else rb.vy
            return floatArrayOf(ux * stop, uy * stop)
        }
        return null
    }

    private fun pairKey(a: Long, b: Long): Long {
        val lo = min(a, b); val hi = max(a, b)
        return (lo shl 32) or (hi and 0xFFFFFFFFL)
    }

    private fun moveWorld(go: GameObject, dx: Float, dy: Float) {
        if (go.parent == null) {
            go.x += dx; go.y += dy
        } else {
            val w = go.computeWorld()
            go.setWorldPosition(w.tx + dx, w.ty + dy)
        }
    }

    private fun refresh(b: Body) {
        val w = b.go.computeWorld()
        val sx = w.scaleX
        val sy = w.scaleY
        b.cx = w.mapX(b.col.offsetX, b.col.offsetY)
        b.cy = w.mapY(b.col.offsetX, b.col.offsetY)
        b.hw = b.col.width * sx * 0.5f
        b.hh = b.col.height * sy * 0.5f
        b.r = b.col.radius * max(sx, sy)
    }

    private class Manifold(val nx: Float, val ny: Float, val depth: Float)

    /** Normal points from a to b. */
    private fun collide(a: Body, b: Body): Manifold? {
        return when {
            !a.isCircle && !b.isCircle -> boxBox(a, b)
            a.isCircle && b.isCircle -> circleCircle(a, b)
            !a.isCircle && b.isCircle -> boxCircle(a, b)
            else -> boxCircle(b, a)?.let { Manifold(-it.nx, -it.ny, it.depth) }
        }
    }

    private fun boxBox(a: Body, b: Body): Manifold? {
        val dx = b.cx - a.cx
        val ox = a.hw + b.hw - abs(dx)
        if (ox <= 0f) return null
        val dy = b.cy - a.cy
        val oy = a.hh + b.hh - abs(dy)
        if (oy <= 0f) return null
        return if (ox < oy) Manifold(if (dx < 0) -1f else 1f, 0f, ox)
        else Manifold(0f, if (dy < 0) -1f else 1f, oy)
    }

    private fun circleCircle(a: Body, b: Body): Manifold? {
        val dx = b.cx - a.cx
        val dy = b.cy - a.cy
        val rs = a.r + b.r
        val d2 = dx * dx + dy * dy
        if (d2 >= rs * rs) return null
        val d = sqrt(d2)
        return if (d < 1e-5f) Manifold(0f, 1f, rs) else Manifold(dx / d, dy / d, rs - d)
    }

    private fun boxCircle(box: Body, c: Body): Manifold? {
        val px = (c.cx).coerceIn(box.cx - box.hw, box.cx + box.hw)
        val py = (c.cy).coerceIn(box.cy - box.hh, box.cy + box.hh)
        var dx = c.cx - px
        var dy = c.cy - py
        val d2 = dx * dx + dy * dy
        if (d2 > c.r * c.r) return null
        if (d2 < 1e-8f) {
            // centre inside box: push out along smallest axis
            val ox = box.hw - abs(c.cx - box.cx)
            val oy = box.hh - abs(c.cy - box.cy)
            return if (ox < oy) Manifold(if (c.cx < box.cx) -1f else 1f, 0f, ox + c.r)
            else Manifold(0f, if (c.cy < box.cy) -1f else 1f, oy + c.r)
        }
        val d = sqrt(d2)
        dx /= d; dy /= d
        return Manifold(dx, dy, c.r - d)
    }

    private fun resolve(a: Body, b: Body, m: Manifold) {
        val ia = a.invMass
        val ib = b.invMass
        val sum = ia + ib
        if (sum == 0f) return
        // positional correction
        val corr = max(m.depth - 0.001f, 0f) / sum * 0.9f
        if (ia > 0f) moveWorld(a.go, -m.nx * corr * ia, -m.ny * corr * ia)
        if (ib > 0f) moveWorld(b.go, m.nx * corr * ib, m.ny * corr * ib)

        // grounded flags (normal a->b pointing down means a is on top of b)
        if (m.ny < -0.5f) a.rb?.grounded = true
        if (m.ny > 0.5f) b.rb?.grounded = true

        val avx = a.rb?.takeIf { it.bodyType != 2 }?.vx ?: 0f
        val avy = a.rb?.takeIf { it.bodyType != 2 }?.vy ?: 0f
        val bvx = b.rb?.takeIf { it.bodyType != 2 }?.vx ?: 0f
        val bvy = b.rb?.takeIf { it.bodyType != 2 }?.vy ?: 0f
        val rvx = bvx - avx
        val rvy = bvy - avy
        val vn = rvx * m.nx + rvy * m.ny
        if (vn > 0f) return
        val e = max(a.rb?.bounciness ?: 0f, b.rb?.bounciness ?: 0f)
        val j = -(1f + e) * vn / sum
        applyImpulse(a, -j * m.nx, -j * m.ny)
        applyImpulse(b, j * m.nx, j * m.ny)

        // friction
        val tx = -m.ny
        val ty = m.nx
        val vt = rvx * tx + rvy * ty
        val mu = sqrt((a.rb?.friction ?: 0.4f) * (b.rb?.friction ?: 0.4f))
        var jt = -vt / sum
        val maxF = j * mu
        jt = jt.coerceIn(-maxF, maxF)
        applyImpulse(a, -jt * tx, -jt * ty)
        applyImpulse(b, jt * tx, jt * ty)
    }

    private fun applyImpulse(b: Body, ix: Float, iy: Float) {
        val inv = b.invMass
        if (inv == 0f) return
        val rb = b.rb ?: return
        rb.vx += ix * inv
        rb.vy += iy * inv
    }

    /** Returns first active object whose collider contains the world point. */
    fun overlapPoint(scene: Scene, x: Float, y: Float): GameObject? {
        for (go in scene.objects.asReversed()) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider2D>() ?: continue
            val b = Body(go, null, col)
            refresh(b)
            val hit = if (b.isCircle) {
                val dx = x - b.cx; val dy = y - b.cy; dx * dx + dy * dy <= b.r * b.r
            } else abs(x - b.cx) <= b.hw && abs(y - b.cy) <= b.hh
            if (hit) return go
        }
        return null
    }

    /** Ray vs 2D colliders (boxes and circles); returns the first object hit within [maxDist]. */
    fun raycast(scene: Scene, ox: Float, oy: Float, dx: Float, dy: Float, maxDist: Float): GameObject? =
        raycastAll(scene, ox, oy, dx, dy, maxDist).firstOrNull()?.second

    /** All collider hits along the ray, sorted by distance (nearest first). */
    fun raycastAll(scene: Scene, ox: Float, oy: Float, dx: Float, dy: Float, maxDist: Float): List<Pair<Float, GameObject>> {
        val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9f)
        val ux = dx / l
        val uy = dy / l
        val hits = ArrayList<Pair<Float, GameObject>>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider2D>() ?: continue
            val b = Body(go, null, col)
            refresh(b)
            val t = if (b.isCircle) rayCircle(ox, oy, ux, uy, b) else rayBox(ox, oy, ux, uy, b)
            if (t != null && t >= 0f && t <= maxDist) hits.add(t to go)
        }
        return hits.sortedBy { it.first }
    }

    /** All active colliders overlapping the world-space circle. Optional [tag] filters results. */
    fun overlapCircle(scene: Scene, x: Float, y: Float, radius: Float, tag: String = ""): List<GameObject> {
        val out = ArrayList<GameObject>()
        val r2 = radius * radius
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            if (tag.isNotEmpty() && go.tag != tag) continue
            val col = go.get<Collider2D>() ?: continue
            val b = Body(go, null, col)
            refresh(b)
            val hit = if (b.isCircle) {
                val ddx = x - b.cx; val ddy = y - b.cy
                val rr = radius + b.r
                ddx * ddx + ddy * ddy <= rr * rr
            } else {
                val px = x.coerceIn(b.cx - b.hw, b.cx + b.hw)
                val py = y.coerceIn(b.cy - b.hh, b.cy + b.hh)
                val ddx = x - px; val ddy = y - py
                ddx * ddx + ddy * ddy <= r2
            }
            if (hit) out.add(go)
        }
        return out
    }

    private fun rayBox(ox: Float, oy: Float, dx: Float, dy: Float, b: Body): Float? {
        val hw = abs(b.hw); val hh = abs(b.hh)
        var tmin = -Float.MAX_VALUE
        var tmax = Float.MAX_VALUE
        if (abs(dx) < 1e-8f) {
            if (ox < b.cx - hw || ox > b.cx + hw) return null
        } else {
            var t1 = (b.cx - hw - ox) / dx
            var t2 = (b.cx + hw - ox) / dx
            if (t1 > t2) { val s = t1; t1 = t2; t2 = s }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
        }
        if (abs(dy) < 1e-8f) {
            if (oy < b.cy - hh || oy > b.cy + hh) return null
        } else {
            var t1 = (b.cy - hh - oy) / dy
            var t2 = (b.cy + hh - oy) / dy
            if (t1 > t2) { val s = t1; t1 = t2; t2 = s }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
        }
        if (tmin > tmax) return null
        return if (tmin >= 0f) tmin else if (tmax >= 0f) 0f else null
    }

    private fun rayCircle(ox: Float, oy: Float, dx: Float, dy: Float, b: Body): Float? {
        val lx = ox - b.cx
        val ly = oy - b.cy
        val c = lx * lx + ly * ly - b.r * b.r
        if (c <= 0f) return 0f // origin inside
        val bb = lx * dx + ly * dy
        val disc = bb * bb - c
        if (disc < 0f) return null
        val t = -bb - kotlin.math.sqrt(disc)
        return if (t >= 0f) t else null
    }
}
