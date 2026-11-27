package com.sengine.engine.physics

import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Joint2D
import com.sengine.engine.core.PhysicsMaterial2D
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.Tilemap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lightweight impulse-based 2D physics: boxes, circles, convex polygons,
 * tilemap solids, physics materials, distance/spring joints, gravity,
 * restitution, friction, triggers and collision callbacks.
 */
class PhysicsWorld {

    interface Listener {
        fun onCollisionEnter(a: GameObject, b: GameObject)
        fun onTriggerEnter(a: GameObject, b: GameObject)
        fun onTriggerExit(a: GameObject, b: GameObject)
    }

    var listener: Listener? = null
    private var accumulator = 0f
    private val fixedDt = 1f / 60f

    private class Body(
        val go: GameObject,
        val rb: Rigidbody2D?,
        val col: Collider2D,
        var cx: Float = 0f, var cy: Float = 0f,
        var hw: Float = 0f, var hh: Float = 0f, var r: Float = 0f,
        val mat: PhysicsMaterial2D? = null,
        var points: Array<FloatArray>? = null,
    ) {
        val isCircle get() = col.shape == 1
        val isPolygon get() = col.shape == 2 && (points?.size ?: 0) >= 3
        val invMass: Float
            get() {
                if (rb == null || rb.bodyType != 0) return 0f
                val mass = (rb.mass * (mat?.density ?: 1f)).coerceAtLeast(0.001f)
                return 1f / mass
            }
    }

    /** Layer filter: both colliders must accept the other's layer bit. */
    private fun layersMatch(a: Collider2D, b: Collider2D): Boolean =
        (a.mask and (1 shl b.layer)) != 0 && (b.mask and (1 shl a.layer)) != 0

    private var prevContacts = HashSet<Long>()
    private var prevTriggers = HashSet<Long>()

    fun reset() {
        accumulator = 0f
        prevContacts = HashSet(); prevTriggers = HashSet()
    }

    fun step(scene: Scene, dt: Float) {
        accumulator += min(dt, 0.25f)
        var steps = 0
        while (accumulator >= fixedDt && steps < 5) {
            fixedStep(scene, fixedDt)
            accumulator -= fixedDt
            steps++
        }
        if (steps == 5) accumulator = 0f
    }

    private fun fixedStep(scene: Scene, dt: Float) {
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

        // gather ordinary bodies + solid tilemap cells
        val bodies = ArrayList<Body>()
        for (go in scene.objects) colliderBody(go)?.let { bodies.add(it) }
        for (go in scene.objects) bodies.addAll(tileBodiesOf(go))

        val contacts = HashSet<Long>()
        val triggers = HashSet<Long>()
        val carried = HashSet<Long>()
        for (i in 0 until bodies.size) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                if (!layersMatch(a.col, b.col)) continue
                if (a.invMass == 0f && b.invMass == 0f && !a.col.isTrigger && !b.col.isTrigger) continue
                val m = collide(a, b) ?: continue
                val key = pairKey(a.go.id, b.go.id)
                if (a.col.isTrigger || b.col.isTrigger) {
                    triggers.add(key)
                    if (key !in prevTriggers) listener?.onTriggerEnter(a.go, b.go)
                    continue
                }
                contacts.add(key)
                resolve(a, b, m)
                // ride kinematic movers (platforms): apply their step delta to resting dynamics
                val kin = if (a.rb?.bodyType == 1) a else if (b.rb?.bodyType == 1) b else null
                val dyn = if (kin === a) b else a
                val delta = kin?.let { carry[it.go.id] }
                // A rider can be above the mover in either pair order: the normal
                // is +Y when the kinematic body is `a`, -Y when it is `b`, so
                // both orientations must reach the onTop check below.
                if (delta != null && dyn.rb?.bodyType == 0 && dyn.go.id !in carried) {
                    val onTop = if (kin === a) m.ny > 0.55f else m.ny < -0.55f
                    val sidePush = abs(m.nx) > 0.55f
                    if (onTop || sidePush) {
                        moveWorld(dyn.go, delta[0], delta[1])
                        carried.add(dyn.go.id)
                    }
                }
                if (key !in prevContacts) listener?.onCollisionEnter(a.go, b.go)
            }
        }
        applyJoints(scene, dt)
        for (k in prevTriggers) if (k !in triggers) {
            val a = scene.findById(k ushr 32)
            val b = scene.findById(k and 0xFFFFFFFFL)
            if (a != null && b != null) listener?.onTriggerExit(a, b)
        }
        prevContacts = contacts
        prevTriggers = triggers
    }

    private fun clampSpeed(rb: Rigidbody2D) {
        if (rb.maxSpeed <= 0f) return
        val s = sqrt(rb.vx * rb.vx + rb.vy * rb.vy)
        if (s > rb.maxSpeed) {
            val k = rb.maxSpeed / s
            rb.vx *= k; rb.vy *= k
        }
    }

    /** Distance / spring constraints with anchor offsets and optional stretch break. */
    private fun applyJoints(scene: Scene, dt: Float) {
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val joint = go.get<Joint2D>() ?: continue
            if (joint.broken || joint.target.isBlank()) continue
            val other = scene.find(joint.target) ?: continue
            if (other === go || !other.isActiveInHierarchy()) continue
            val rbA = go.get<Rigidbody2D>()
            val rbB = other.get<Rigidbody2D>()
            val matA = go.get<PhysicsMaterial2D>()
            val matB = other.get<PhysicsMaterial2D>()
            val invA = if (rbA?.bodyType == 0) 1f / (rbA.mass * (matA?.density ?: 1f)).coerceAtLeast(0.001f) else 0f
            val invB = if (rbB?.bodyType == 0) 1f / (rbB.mass * (matB?.density ?: 1f)).coerceAtLeast(0.001f) else 0f
            val sum = invA + invB
            if (sum <= 0f) continue
            val wa = go.computeWorld(); val wb = other.computeWorld()
            val ax = wa.mapX(joint.anchorX, joint.anchorY); val ay = wa.mapY(joint.anchorX, joint.anchorY)
            val bx = wb.mapX(joint.targetAnchorX, joint.targetAnchorY); val by = wb.mapY(joint.targetAnchorX, joint.targetAnchorY)
            val dx = bx - ax; val dy = by - ay
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6f)
            val stretch = dist - joint.length
            joint.stressed = abs(stretch) > max(0.05f, joint.length * 0.1f)
            if (joint.maxStretch > 0f && stretch > joint.maxStretch) {
                joint.broken = true; joint.stressed = true; joint.enabled = false
                continue
            }
            val nx = dx / dist; val ny = dy / dist
            if (joint.mode == 0) {
                val corr = stretch / sum * 0.9f
                if (invA > 0f) moveWorld(go, nx * corr * invA, ny * corr * invA)
                if (invB > 0f) moveWorld(other, -nx * corr * invB, -ny * corr * invB)
            } else {
                val rvx = (rbB?.vx ?: 0f) - (rbA?.vx ?: 0f)
                val rvy = (rbB?.vy ?: 0f) - (rbA?.vy ?: 0f)
                val relV = rvx * nx + rvy * ny
                val impulse = (stretch * joint.stiffness + relV * joint.damping) * dt
                if (invA > 0f && rbA != null) {
                    rbA.vx += nx * impulse * invA; rbA.vy += ny * impulse * invA
                }
                if (invB > 0f && rbB != null) {
                    rbB.vx -= nx * impulse * invB; rbB.vy -= ny * impulse * invB
                }
                val corr = stretch / sum * min(1f, dt * joint.stiffness * 0.05f)
                if (invA > 0f) moveWorld(go, nx * corr * invA, ny * corr * invA)
                if (invB > 0f) moveWorld(other, -nx * corr * invB, -ny * corr * invB)
            }
        }
    }

    /** Swept collision for fast bodies: stop before a solid instead of tunneling through it. */
    private fun ccdAdjust(scene: Scene, go: GameObject, rb: Rigidbody2D, dt: Float): FloatArray? {
        val dx = rb.vx * dt
        val dy = rb.vy * dt
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.01f) return null
        val ux = dx / len
        val uy = dy / len
        val w = go.computeWorld()
        val col = go.get<Collider2D>() ?: return null
        val hw = abs(col.width * w.scaleX * 0.5f)
        val hh = abs(col.height * w.scaleY * 0.5f)
        val poly = if (col.shape == 2) col.worldPoints(w) else null
        var r = if (col.shape == 1) col.radius * max(w.scaleX, w.scaleY) else max(hw, hh)
        if (poly != null) {
            var far = 0f
            for (p in poly) {
                val ddx = p[0] - w.tx; val ddy = p[1] - w.ty
                far = max(far, sqrt(ddx * ddx + ddy * ddy))
            }
            if (far > 0f) r = far
        }
        val ox = w.tx
        val oy = w.ty
        var best = len
        var hit = false
        for (other in scene.objects) {
            if (other === go || !other.isActiveInHierarchy()) continue
            val bodies = ArrayList<Body>()
            other.get<Collider2D>()?.let { oc ->
                if (!oc.isTrigger && layersMatch(col, oc)) {
                    val ob = Body(other, other.get<Rigidbody2D>(), oc, mat = other.get<PhysicsMaterial2D>())
                    refresh(ob); bodies.add(ob)
                }
            }
            for (tb in tileBodiesOf(other)) if (!tb.col.isTrigger && layersMatch(col, tb.col)) bodies.add(tb)
            for (ob in bodies) {
                val t = when {
                    ob.isCircle -> rayCircle(ox, oy, ux, uy, ob)
                    ob.isPolygon -> rayPolygon(ox, oy, ux, uy, ob.points!!)
                    else -> rayBox(ox, oy, ux, uy, ob)
                }
                if (t != null && t >= 0f && t < best) { best = t; hit = true }
            }
        }
        val stop = if (hit) max(best - r, 0f) else len
        if (stop < len - 1e-4f) {
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

    private fun colliderBody(go: GameObject): Body? {
        if (!go.isActiveInHierarchy()) return null
        val col = go.get<Collider2D>() ?: return null
        val b = Body(go, go.get<Rigidbody2D>(), col, mat = go.get<PhysicsMaterial2D>())
        refresh(b)
        return b
    }

    private fun tileBodiesOf(go: GameObject): List<Body> {
        if (!go.isActiveInHierarchy()) return emptyList()
        val tm = go.get<Tilemap>() ?: return emptyList()
        if (!tm.collide) return emptyList()
        val out = ArrayList<Body>()
        for (tile in tm.tiles(go)) {
            val col = Collider2D().also {
                it.shape = 0; it.layer = tm.layer; it.mask = tm.mask
                it.width = tile.hw * 2f; it.height = tile.hh * 2f
            }
            val b = Body(go, null, col)
            b.cx = tile.cx; b.cy = tile.cy; b.hw = tile.hw; b.hh = tile.hh; b.r = 0f
            out.add(b)
        }
        return out
    }

    private fun refresh(b: Body) {
        val w = b.go.computeWorld()
        val sx = w.scaleX
        val sy = w.scaleY
        b.cx = w.mapX(b.col.offsetX, b.col.offsetY)
        b.cy = w.mapY(b.col.offsetX, b.col.offsetY)
        b.hw = abs(b.col.width * sx * 0.5f)
        b.hh = abs(b.col.height * sy * 0.5f)
        b.r = b.col.radius * max(sx, sy)
        b.points = if (b.col.shape == 2) b.col.worldPoints(w) else null
    }

    private class Manifold(val nx: Float, val ny: Float, val depth: Float)

    /** Normal points from a to b. */
    private fun collide(a: Body, b: Body): Manifold? {
        val ap = a.isPolygon
        val bp = b.isPolygon
        return when {
            ap && bp -> polyPoly(a.points!!, b.points!!)
            ap && b.isCircle -> polyCircle(a.points!!, b)
            a.isCircle && bp -> polyCircle(b.points!!, a)?.let { Manifold(-it.nx, -it.ny, it.depth) }
            ap && !b.isCircle -> polyPoly(a.points!!, boxPoints(b))
            !a.isCircle && bp -> polyPoly(boxPoints(a), b.points!!)
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
            val ox = box.hw - abs(c.cx - box.cx)
            val oy = box.hh - abs(c.cy - box.cy)
            return if (ox < oy) Manifold(if (c.cx < box.cx) -1f else 1f, 0f, ox + c.r)
            else Manifold(0f, if (c.cy < box.cy) -1f else 1f, oy + c.r)
        }
        val d = sqrt(d2)
        dx /= d; dy /= d
        return Manifold(dx, dy, c.r - d)
    }

    private fun boxPoints(b: Body): Array<FloatArray> {
        val hw = abs(b.hw); val hh = abs(b.hh)
        return arrayOf(
            floatArrayOf(b.cx - hw, b.cy - hh), floatArrayOf(b.cx + hw, b.cy - hh),
            floatArrayOf(b.cx + hw, b.cy + hh), floatArrayOf(b.cx - hw, b.cy + hh),
        )
    }

    /** SAT for two CCW convex polygons. Normal always points from a to b. */
    private fun polyPoly(a: Array<FloatArray>, b: Array<FloatArray>): Manifold? {
        var bestDepth = Float.MAX_VALUE
        var nx = 0f; var ny = 1f
        // A nested function cannot return from the outer polyPoly function, so
        // use a sentinel flag for separated axes.
        var separated = false
        fun testAxes(poly: Array<FloatArray>) {
            for (i in poly.indices) {
                val p = poly[i]; val q = poly[(i + 1) % poly.size]
                val ex = q[0] - p[0]; val ey = q[1] - p[1]
                val len = sqrt(ex * ex + ey * ey)
                if (len < 1e-6f) continue
                var ax = ey / len; var ay = -ex / len
                val pa = project(a, ax, ay); val pb = project(b, ax, ay)
                val overlap = min(pa[1], pb[1]) - max(pa[0], pb[0])
                if (overlap <= 0f) { separated = true; return }
                val ca = centroid(a); val cb = centroid(b)
                if (ax * (cb[0] - ca[0]) + ay * (cb[1] - ca[1]) < 0f) { ax = -ax; ay = -ay }
                if (overlap < bestDepth) { bestDepth = overlap; nx = ax; ny = ay }
            }
        }
        testAxes(a); if (separated) return null
        testAxes(b); if (separated) return null
        return if (bestDepth == Float.MAX_VALUE) null else Manifold(nx, ny, bestDepth)
    }

    private fun project(poly: Array<FloatArray>, ax: Float, ay: Float): FloatArray {
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
        for (p in poly) {
            val d = p[0] * ax + p[1] * ay
            if (d < mn) mn = d
            if (d > mx) mx = d
        }
        return floatArrayOf(mn, mx)
    }

    private fun centroid(poly: Array<FloatArray>): FloatArray {
        var x = 0f; var y = 0f
        for (p in poly) { x += p[0]; y += p[1] }
        return floatArrayOf(x / poly.size, y / poly.size)
    }

    private fun pointInConvex(poly: Array<FloatArray>, x: Float, y: Float): Boolean {
        for (i in poly.indices) {
            val p = poly[i]; val q = poly[(i + 1) % poly.size]
            val ex = q[0] - p[0]; val ey = q[1] - p[1]
            if (ex * (y - p[1]) - ey * (x - p[0]) < -1e-5f) return false
        }
        return true
    }

    /** Exact convex polygon vs circle. Normal points from polygon to circle. */
    private fun polyCircle(poly: Array<FloatArray>, c: Body): Manifold? {
        var bestD2 = Float.MAX_VALUE
        var bestQx = 0f; var bestQy = 0f
        var bestEx = 0f; var bestEy = 0f
        for (i in poly.indices) {
            val p = poly[i]; val q = poly[(i + 1) % poly.size]
            val ex = q[0] - p[0]; val ey = q[1] - p[1]
            val l2 = ex * ex + ey * ey
            val t = if (l2 < 1e-8f) 0f else (((c.cx - p[0]) * ex + (c.cy - p[1]) * ey) / l2).coerceIn(0f, 1f)
            val qx = p[0] + ex * t; val qy = p[1] + ey * t
            val dx = c.cx - qx; val dy = c.cy - qy
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2; bestQx = qx; bestQy = qy; bestEx = ex; bestEy = ey
            }
        }
        val inside = pointInConvex(poly, c.cx, c.cy)
        val dist = sqrt(bestD2)
        if (!inside && bestD2 >= c.r * c.r) return null
        if (inside || dist < 1e-5f) {
            val len = sqrt(bestEx * bestEx + bestEy * bestEy).coerceAtLeast(1e-6f)
            var nx = bestEy / len; var ny = -bestEx / len
            val ctr = centroid(poly)
            if (nx * (ctr[0] - bestQx) + ny * (ctr[1] - bestQy) > 0f) { nx = -nx; ny = -ny }
            return Manifold(nx, ny, c.r + dist)
        }
        return Manifold((c.cx - bestQx) / dist, (c.cy - bestQy) / dist, c.r - dist)
    }

    private fun resolve(a: Body, b: Body, m: Manifold) {
        val ia = a.invMass
        val ib = b.invMass
        val sum = ia + ib
        if (sum == 0f) return
        val corr = max(m.depth - 0.001f, 0f) / sum * 0.9f
        if (ia > 0f) moveWorld(a.go, -m.nx * corr * ia, -m.ny * corr * ia)
        if (ib > 0f) moveWorld(b.go, m.nx * corr * ib, m.ny * corr * ib)

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
        val e = max(a.mat?.bounciness ?: a.rb?.bounciness ?: 0f, b.mat?.bounciness ?: b.rb?.bounciness ?: 0f)
        val j = -(1f + e) * vn / sum
        applyImpulse(a, -j * m.nx, -j * m.ny)
        applyImpulse(b, j * m.nx, j * m.ny)

        val tx = -m.ny
        val ty = m.nx
        val vt = rvx * tx + rvy * ty
        val fa = a.mat?.friction ?: a.rb?.friction ?: 0.4f
        val fb = b.mat?.friction ?: b.rb?.friction ?: 0.4f
        val mu = sqrt(max(0f, fa * fb))
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

    /** Geometric overlap between two objects, including solid tilemap cells. */
    fun overlaps(aGo: GameObject, bGo: GameObject): Boolean {
        if (aGo === bGo || aGo.destroyed || bGo.destroyed) return false
        if (!aGo.isActiveInHierarchy() || !bGo.isActiveInHierarchy()) return false
        val a = colliderBody(aGo)
        val b = colliderBody(bGo)
        if (a != null && b != null && layersMatch(a.col, b.col) && collide(a, b) != null) return true
        val aTiles = tileBodiesOf(aGo)
        val bTiles = tileBodiesOf(bGo)
        for (tb in aTiles) {
            if (b != null && layersMatch(tb.col, b.col) && collide(tb, b) != null) return true
            for (bt in bTiles) if (layersMatch(tb.col, bt.col) && collide(tb, bt) != null) return true
        }
        if (a != null) for (bt in bTiles) {
            if (layersMatch(a.col, bt.col) && collide(a, bt) != null) return true
        }
        return false
    }

    /** Returns first active object whose collider or solid tile contains the world point. */
    fun overlapPoint(scene: Scene, x: Float, y: Float): GameObject? {
        for (go in scene.objects.asReversed()) {
            if (!go.isActiveInHierarchy()) continue
            val bodies = ArrayList<Body>()
            colliderBody(go)?.let { bodies.add(it) }
            bodies.addAll(tileBodiesOf(go))
            for (b in bodies) {
                val hit = when {
                    b.isCircle -> {
                        val dx = x - b.cx; val dy = y - b.cy; dx * dx + dy * dy <= b.r * b.r
                    }
                    b.isPolygon -> pointInConvex(b.points!!, x, y)
                    else -> abs(x - b.cx) <= b.hw && abs(y - b.cy) <= b.hh
                }
                if (hit) return go
            }
        }
        return null
    }

    /** Ray vs all 2D colliders and solid tiles; returns the first object hit within [maxDist]. */
    fun raycast(scene: Scene, ox: Float, oy: Float, dx: Float, dy: Float, maxDist: Float): GameObject? =
        raycastAll(scene, ox, oy, dx, dy, maxDist).firstOrNull()?.second

    /** All hits along the ray, sorted by distance (nearest first). */
    fun raycastAll(scene: Scene, ox: Float, oy: Float, dx: Float, dy: Float, maxDist: Float): List<Pair<Float, GameObject>> {
        val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9f)
        val ux = dx / l
        val uy = dy / l
        val hits = ArrayList<Pair<Float, GameObject>>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val bodies = ArrayList<Body>()
            colliderBody(go)?.let { bodies.add(it) }
            bodies.addAll(tileBodiesOf(go))
            for (b in bodies) {
                val t = when {
                    b.isCircle -> rayCircle(ox, oy, ux, uy, b)
                    b.isPolygon -> rayPolygon(ox, oy, ux, uy, b.points!!)
                    else -> rayBox(ox, oy, ux, uy, b)
                }
                if (t != null && t >= 0f && t <= maxDist) hits.add(t to go)
            }
        }
        return hits.sortedBy { it.first }
    }

    /** All active colliders / tiles overlapping the world-space circle. Optional [tag] filters results. */
    fun overlapCircle(scene: Scene, x: Float, y: Float, radius: Float, tag: String = ""): List<GameObject> {
        val out = ArrayList<GameObject>()
        val queryCol = Collider2D().also { it.shape = 1; it.radius = radius }
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            if (tag.isNotEmpty() && go.tag != tag) continue
            val q = Body(go, null, queryCol)
            q.cx = x; q.cy = y; q.r = radius
            val bodies = ArrayList<Body>()
            colliderBody(go)?.let { bodies.add(it) }
            bodies.addAll(tileBodiesOf(go))
            for (b in bodies) {
                if (!layersMatch(q.col, b.col)) continue
                if (collide(q, b) != null) { out.add(go); break }
            }
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
        if (c <= 0f) return 0f
        val bb = lx * dx + ly * dy
        val disc = bb * bb - c
        if (disc < 0f) return null
        val t = -bb - sqrt(disc)
        return if (t >= 0f) t else null
    }

    /** Ray vs CCW convex polygon using its edge half-planes. */
    private fun rayPolygon(ox: Float, oy: Float, dx: Float, dy: Float, poly: Array<FloatArray>): Float? {
        var tmin = -Float.MAX_VALUE
        var tmax = Float.MAX_VALUE
        for (i in poly.indices) {
            val p = poly[i]; val q = poly[(i + 1) % poly.size]
            val ex = q[0] - p[0]; val ey = q[1] - p[1]
            val c0 = ex * (oy - p[1]) - ey * (ox - p[0])
            val cd = ex * dy - ey * dx
            if (abs(cd) < 1e-8f) {
                if (c0 < 0f) return null
            } else {
                val t = -c0 / cd
                if (cd > 0f) tmin = max(tmin, t) else tmax = min(tmax, t)
            }
        }
        if (tmin > tmax) return null
        return if (tmin >= 0f) tmin else if (tmax >= 0f) 0f else null
    }
}
