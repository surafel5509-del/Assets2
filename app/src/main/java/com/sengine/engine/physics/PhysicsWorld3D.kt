package com.sengine.engine.physics

import com.sengine.engine.SimulationClock
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.Terrain
import com.sengine.engine.math.Mat4
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Impulse-based 3D physics with axis-aligned boxes and spheres.
 * Shares the collision/trigger listener with the 2D world.
 */
class PhysicsWorld3D {
    var listener: PhysicsWorld.Listener? = null
    val clock = SimulationClock()
    var solverIterations = 1
    var broadphase = true
    var cellSize = 6f
    var lastBroadphasePairs = 0
        private set
    var lastBruteForcePairs = 0
        private set
    private var prevContacts = HashSet<Long>()
    private var prevTriggers = HashSet<Long>()

    private class Body(val go: GameObject, val rb: Rigidbody3D?, val col: Collider3D) {
        var cx = 0f; var cy = 0f; var cz = 0f
        var hx = 0f; var hy = 0f; var hz = 0f; var r = 0f
        val sphere get() = col.shape == 1
        val invMass get() = if (rb == null || rb.bodyType != 0) 0f else 1f / rb.mass
    }

    private class M(val nx: Float, val ny: Float, val nz: Float, val depth: Float)

    /** Layer filter: both colliders must accept the other's layer bit. */
    private fun layersMatch(a: Collider3D, b: Collider3D): Boolean =
        (a.mask and (1 shl b.layer)) != 0 && (b.mask and (1 shl a.layer)) != 0

    fun reset() { clock.reset(); prevContacts = HashSet(); prevTriggers = HashSet() }

    fun step(scene: Scene, dt: Float) {
        if (scene.objects.none { it.getAny<Collider3D>() != null || it.getAny<Rigidbody3D>() != null }) {
            clock.advance(dt)
            return
        }
        val steps = clock.advance(dt)
        repeat(steps) { stepFixed(scene, clock.fixedDt) }
    }

    private fun move(go: GameObject, dx: Float, dy: Float, dz: Float) {
        if (go.parent == null) { go.x += dx; go.y += dy; go.z += dz } else {
            val w = go.computeWorld3()
            go.setWorldPosition3(w[12] + dx, w[13] + dy, w[14] + dz)
        }
    }

    /** One deterministic 3D physics tick. */
    fun stepFixed(scene: Scene, dt: Float) {
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val rb = go.get<Rigidbody3D>() ?: continue
            rb.grounded = false
            rb.groundNx = 0f; rb.groundNy = 1f; rb.groundNz = 0f
            when (rb.bodyType) {
                0 -> {
                    rb.vx += scene.gravityX * 0f
                    rb.vy += scene.gravity3D * rb.gravityScale * dt
                    if (rb.drag > 0f) { val k = max(0f, 1f - rb.drag * dt); rb.vx *= k; rb.vy *= k; rb.vz *= k }
                    if (rb.maxSpeed > 0f) {
                        val s = sqrt(rb.vx * rb.vx + rb.vy * rb.vy + rb.vz * rb.vz)
                        if (s > rb.maxSpeed) { val k = rb.maxSpeed / s; rb.vx *= k; rb.vy *= k; rb.vz *= k }
                    }
                    var dx = rb.vx * dt; var dy = rb.vy * dt; var dz = rb.vz * dt
                    if (rb.ccd) ccdAdjust(scene, go, rb, dt)?.let { dx = it[0]; dy = it[1]; dz = it[2] }
                    move(go, dx, dy, dz)
                }
                1 -> move(go, rb.vx * dt, rb.vy * dt, rb.vz * dt)
                else -> {}
            }
        }
        val bodies = ArrayList<Body>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider3D>() ?: continue
            val b = Body(go, go.get(), col); refresh(b); bodies.add(b)
        }
        val contacts = HashSet<Long>()
        val triggers = HashSet<Long>()
        val pairs = candidatePairs(bodies)
        repeat(solverIterations.coerceIn(1, 8)) { iter ->
            if (iter > 0) for (b in bodies) refresh(b)
            for ((i, j) in pairs) {
                val a = bodies[i]; val b = bodies[j]
                if (!layersMatch(a.col, b.col)) continue
                val trig = a.col.isTrigger || b.col.isTrigger
                if (a.invMass == 0f && b.invMass == 0f && !trig) continue
                val m = collide(a, b) ?: continue
                val key = key(a.go.id, b.go.id)
                if (trig) {
                    triggers.add(key)
                    if (iter == 0 && key !in prevTriggers) listener?.onTriggerEnter(a.go, b.go)
                    continue
                }
                contacts.add(key)
                resolve(a, b, m)
                // record ground normal for slope-aware gameplay
                if (m.ny > 0.55f) b.rb?.let { it.groundNx = m.nx; it.groundNy = m.ny; it.groundNz = m.nz }
                if (m.ny < -0.55f) a.rb?.let { it.groundNx = m.nx; it.groundNy = -m.ny; it.groundNz = m.nz }
                if (iter == 0 && key !in prevContacts) listener?.onCollisionEnter(a.go, b.go)
            }
        }
        for (k in prevTriggers) if (k !in triggers) {
            val a = scene.findById(k shr 32); val b = scene.findById(k and 0xFFFFFFFFL)
            if (a != null && b != null) listener?.onTriggerExit(a, b)
        }
        terrainSupport(scene)
        prevContacts = contacts; prevTriggers = triggers
    }

    private fun candidatePairs(bodies: List<Body>): List<Pair<Int, Int>> {
        if (bodies.size < 12 || !broadphase) {
            val all = ArrayList<Pair<Int, Int>>()
            for (i in bodies.indices) for (j in i + 1 until bodies.size) all.add(i to j)
            lastBroadphasePairs = all.size; lastBruteForcePairs = all.size
            return all
        }
        val boxes = bodies.mapIndexed { i, b ->
            val hx = if (b.sphere) b.r else b.hx
            val hy = if (b.sphere) b.r else b.hy
            val hz = if (b.sphere) b.r else b.hz
            Aabb3(b.cx - hx, b.cy - hy, b.cz - hz, b.cx + hx, b.cy + hy, b.cz + hz, i)
        }
        val hash = SpatialHash3D(cellSize)
        val pairs = hash.candidatePairs(boxes)
        lastBroadphasePairs = hash.lastPairCount; lastBruteForcePairs = hash.lastBruteForcePairs
        return pairs
    }

    data class CastResult(val go: GameObject, val distance: Float, val nx: Float, val ny: Float, val nz: Float)

    /** Sweeps a sphere through the 3D colliders. */
    fun sphereCast(scene: Scene, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
                   radius: Float, maxDist: Float, tag: String = "", ignore: GameObject? = null): CastResult? {
        val shapes = ArrayList<CastShape3>()
        val owners = ArrayList<GameObject>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            if (tag.isNotEmpty() && go.tag != tag) continue
            val col = go.get<Collider3D>() ?: continue
            val b = Body(go, null, col); refresh(b)
            shapes.add(CastShape3(b.cx, b.cy, b.cz, b.hx, b.hy, b.hz, b.sphere, b.r, shapes.size))
            owners.add(go)
        }
        val hit = ShapeCasts.sphereCast(shapes, ox, oy, oz, dx, dy, dz, radius, maxDist) { owners[it] !== ignore } ?: return null
        return CastResult(owners[hit.index], hit.distance, hit.nx, hit.ny, hit.nz)
    }

    private class RigidForceAdapter(val go: GameObject, val rb: Rigidbody3D) : ForcePoint {
        private val w = go.computeWorld3()
        override val x: Float = w[12]
        override val y: Float = w[13]
        override val z: Float = w[14]
        override val mass: Float = rb.mass.coerceAtLeast(0.001f)
        override val active: Boolean = go.isActiveInHierarchy() && rb.bodyType == 0
        override var vx: Float get() = rb.vx; set(v) { rb.vx = v }
        override var vy: Float get() = rb.vy; set(v) { rb.vy = v }
        override var vz: Float get() = rb.vz; set(v) { rb.vz = v }
    }

    private fun forceBodies(scene: Scene, tag: String): List<ForcePoint> {
        val out = ArrayList<ForcePoint>()
        for (go in scene.objects) {
            if (tag.isNotEmpty() && go.tag != tag) continue
            val rb = go.get<Rigidbody3D>() ?: continue
            out.add(RigidForceAdapter(go, rb))
        }
        return out
    }

    fun explode(scene: Scene, x: Float, y: Float, z: Float, radius: Float, force: Float, tag: String = "", falloff: Int = 0): Int =
        ForceFields.explode(forceBodies(scene, tag), x, y, z, radius, force, Falloff.values().getOrElse(falloff) { Falloff.LINEAR })

    fun wind(scene: Scene, fx: Float, fy: Float, fz: Float, dt: Float, tag: String = ""): Int =
        ForceFields.wind(forceBodies(scene, tag), fx, fy, fz, dt)

    /** Ground support against Terrain heightfields: lands, walks and climbs slopes. */
    private fun terrainSupport(scene: Scene) {
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val rb = go.get<Rigidbody3D>() ?: continue
            if (rb.bodyType != 0) continue
            val col = go.get<Collider3D>() ?: continue
            val b = Body(go, rb, col); refresh(b)
            for (tg in scene.objects) {
                if (!tg.isActiveInHierarchy()) continue
                val ter = tg.get<Terrain>() ?: continue
                if (!ter.collide || !ter.enabled) continue
                if (!ter.containsWorld(b.cx, b.cz)) continue
                val h = ter.heightAtWorld(b.cx, b.cz) ?: continue
                val bottom = if (b.sphere) b.cy - b.r else b.cy - b.hy
                if (bottom >= h) continue
                val push = h - bottom
                move(go, 0f, push, 0f)
                b.cy += push
                if (rb.vy < 0f) {
                    val e = rb.bounciness
                    rb.vy = if (e > 0.05f && rb.vy < -3f) -rb.vy * e else 0f
                }
                rb.grounded = true
                val fr = (rb.friction * clock.fixedDt * 6f).coerceAtMost(1f)
                rb.vx *= 1f - fr
                rb.vz *= 1f - fr
            }
        }
    }

    private fun key(a: Long, b: Long): Long = (min(a, b) shl 32) or (max(a, b) and 0xFFFFFFFFL)

    private fun refresh(b: Body) {
        val w = b.go.computeWorld3()
        val p = Mat4.point(w, b.col.centerX, b.col.centerY, b.col.centerZ)
        b.cx = p[0]; b.cy = p[1]; b.cz = p[2]
        val sx = Mat4.scaleOf(w, 0); val sy = Mat4.scaleOf(w, 1); val sz = Mat4.scaleOf(w, 2)
        b.hx = b.col.sizeX * sx / 2; b.hy = b.col.sizeY * sy / 2; b.hz = b.col.sizeZ * sz / 2
        b.r = b.col.radius * max(sx, max(sy, sz))
    }

    private fun collide(a: Body, b: Body): M? = when {
        !a.sphere && !b.sphere -> boxBox(a, b)
        a.sphere && b.sphere -> sphereSphere(a, b)
        !a.sphere && b.sphere -> boxSphere(a, b)
        else -> boxSphere(b, a)?.let { M(-it.nx, -it.ny, -it.nz, it.depth) }
    }

    private fun boxBox(a: Body, b: Body): M? {
        val dx = b.cx - a.cx; val ox = a.hx + b.hx - abs(dx); if (ox <= 0f) return null
        val dy = b.cy - a.cy; val oy = a.hy + b.hy - abs(dy); if (oy <= 0f) return null
        val dz = b.cz - a.cz; val oz = a.hz + b.hz - abs(dz); if (oz <= 0f) return null
        return when {
            ox <= oy && ox <= oz -> M(if (dx < 0) -1f else 1f, 0f, 0f, ox)
            oy <= oz -> M(0f, if (dy < 0) -1f else 1f, 0f, oy)
            else -> M(0f, 0f, if (dz < 0) -1f else 1f, oz)
        }
    }

    private fun sphereSphere(a: Body, b: Body): M? {
        val dx = b.cx - a.cx; val dy = b.cy - a.cy; val dz = b.cz - a.cz
        val rs = a.r + b.r
        val d2 = dx * dx + dy * dy + dz * dz
        if (d2 >= rs * rs) return null
        val d = sqrt(d2)
        return if (d < 1e-5f) M(0f, 1f, 0f, rs) else M(dx / d, dy / d, dz / d, rs - d)
    }

    private fun boxSphere(box: Body, s: Body): M? {
        val px = s.cx.coerceIn(box.cx - box.hx, box.cx + box.hx)
        val py = s.cy.coerceIn(box.cy - box.hy, box.cy + box.hy)
        val pz = s.cz.coerceIn(box.cz - box.hz, box.cz + box.hz)
        val dx = s.cx - px; val dy = s.cy - py; val dz = s.cz - pz
        val d2 = dx * dx + dy * dy + dz * dz
        if (d2 > s.r * s.r) return null
        if (d2 < 1e-8f) {
            val ox = box.hx - abs(s.cx - box.cx); val oy = box.hy - abs(s.cy - box.cy); val oz = box.hz - abs(s.cz - box.cz)
            return when {
                ox <= oy && ox <= oz -> M(if (s.cx < box.cx) -1f else 1f, 0f, 0f, ox + s.r)
                oy <= oz -> M(0f, if (s.cy < box.cy) -1f else 1f, 0f, oy + s.r)
                else -> M(0f, 0f, if (s.cz < box.cz) -1f else 1f, oz + s.r)
            }
        }
        val d = sqrt(d2)
        return M(dx / d, dy / d, dz / d, s.r - d)
    }

    private fun vel(b: Body): FloatArray {
        val rb = b.rb
        return if (rb == null || rb.bodyType == 2) floatArrayOf(0f, 0f, 0f) else floatArrayOf(rb.vx, rb.vy, rb.vz)
    }

    private fun resolve(a: Body, b: Body, m: M) {
        val ia = a.invMass; val ib = b.invMass; val sum = ia + ib
        if (sum == 0f) return
        val corr = max(m.depth - 0.001f, 0f) / sum * 0.9f
        if (ia > 0f) move(a.go, -m.nx * corr * ia, -m.ny * corr * ia, -m.nz * corr * ia)
        if (ib > 0f) move(b.go, m.nx * corr * ib, m.ny * corr * ib, m.nz * corr * ib)
        if (m.ny < -0.5f) a.rb?.grounded = true
        if (m.ny > 0.5f) b.rb?.grounded = true
        val va = vel(a); val vb = vel(b)
        val rx = vb[0] - va[0]; val ry = vb[1] - va[1]; val rz = vb[2] - va[2]
        val vn = rx * m.nx + ry * m.ny + rz * m.nz
        if (vn > 0f) return
        val e = max(a.rb?.bounciness ?: 0f, b.rb?.bounciness ?: 0f)
        val j = -(1f + e) * vn / sum
        impulse(a, -j * m.nx, -j * m.ny, -j * m.nz)
        impulse(b, j * m.nx, j * m.ny, j * m.nz)
        // friction along tangential relative velocity
        var tx = rx - vn * m.nx; var ty = ry - vn * m.ny; var tz = rz - vn * m.nz
        val tl = sqrt(tx * tx + ty * ty + tz * tz)
        if (tl < 1e-5f) return
        tx /= tl; ty /= tl; tz /= tl
        val mu = sqrt((a.rb?.friction ?: 0.5f) * (b.rb?.friction ?: 0.5f))
        val jt = (-(rx * tx + ry * ty + rz * tz) / sum).coerceIn(-j * mu, j * mu)
        impulse(a, -jt * tx, -jt * ty, -jt * tz)
        impulse(b, jt * tx, jt * ty, jt * tz)
    }

    private fun impulse(b: Body, x: Float, y: Float, z: Float) {
        val inv = b.invMass
        if (inv == 0f) return
        val rb = b.rb ?: return
        rb.vx += x * inv; rb.vy += y * inv; rb.vz += z * inv
    }

    /**
     * Swept collision for fast bodies: stops the straight-line move just before
     * impact with a solid collider (boxes/spheres + terrain) instead of tunneling.
     */
    private fun ccdAdjust(scene: Scene, go: GameObject, rb: Rigidbody3D, dt: Float): FloatArray? {
        val dx = rb.vx * dt; val dy = rb.vy * dt; val dz = rb.vz * dt
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 0.01f) return null
        val ux = dx / len; val uy = dy / len; val uz = dz / len
        val col = go.get<Collider3D>() ?: return null
        val w = go.computeWorld3()
        val p = Mat4.point(w, col.centerX, col.centerY, col.centerZ)
        val sx = Mat4.scaleOf(w, 0); val sy = Mat4.scaleOf(w, 1); val sz = Mat4.scaleOf(w, 2)
        val r = if (col.shape == 1) col.radius * max(sx, max(sy, sz))
                else max(col.sizeX * sx, max(col.sizeY * sy, col.sizeZ * sz)) * 0.5f
        var best = len
        var hit = false
        for (other in scene.objects) {
            if (other === go || !other.isActiveInHierarchy()) continue
            val oc = other.get<Collider3D>() ?: continue
            if (oc.isTrigger || !layersMatch(col, oc)) continue
            val orb = other.get<Rigidbody3D>()
            if (orb != null && orb.bodyType == 0) continue
            val ob = Body(other, orb, oc); refresh(ob)
            val t = if (ob.sphere) raySphere(p[0], p[1], p[2], ux, uy, uz, ob)
                    else rayBox(p[0], p[1], p[2], ux, uy, uz, ob)
            if (t != null && t >= 0f && t < best) { best = t; hit = true }
        }
        for (tg in scene.objects) {
            if (!tg.isActiveInHierarchy()) continue
            val ter = tg.get<Terrain>() ?: continue
            if (!ter.enabled || !ter.collide) continue
            val t = rayTerrain(ter, p[0], p[1], p[2], ux, uy, uz, best)
            if (t != null && t >= 0f && t < best) { best = t; hit = true }
        }
        val stop = if (hit) max(best - r, 0f) else len
        if (stop < len - 1e-4f) {
            if (abs(ux) > 0.5f) rb.vx = 0f
            if (abs(uy) > 0.5f) rb.vy = 0f
            if (abs(uz) > 0.5f) rb.vz = 0f
            return floatArrayOf(ux * stop, uy * stop, uz * stop)
        }
        return null
    }

    /** Ray cast against colliders; returns the hit object or null. */
    fun raycast(scene: Scene, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float): GameObject? =
        raycastAll(scene, ox, oy, oz, dx, dy, dz, maxDist).firstOrNull()?.second

    /** All collider + terrain hits along the ray, sorted by distance (nearest first). */
    fun raycastAll(scene: Scene, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float): List<Pair<Float, GameObject>> {
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9f)
        val ux = dx / l; val uy = dy / l; val uz = dz / l
        val hits = ArrayList<Pair<Float, GameObject>>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider3D>() ?: continue
            val b = Body(go, null, col); refresh(b)
            val t = if (b.sphere) raySphere(ox, oy, oz, ux, uy, uz, b) else rayBox(ox, oy, oz, ux, uy, uz, b)
            if (t != null && t >= 0f && t <= maxDist) hits.add(t to go)
        }
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val ter = go.get<Terrain>() ?: continue
            if (!ter.enabled || !ter.collide) continue
            val t = rayTerrain(ter, ox, oy, oz, ux, uy, uz, maxDist)
            if (t != null && t >= 0f && t <= maxDist) hits.add(t to go)
        }
        return hits.sortedBy { it.first }
    }

    /** All active colliders overlapping the world-space sphere. Optional [tag] filters results. */
    fun overlapSphere(scene: Scene, x: Float, y: Float, z: Float, radius: Float, tag: String = ""): List<GameObject> {
        val out = ArrayList<GameObject>()
        val r2 = radius * radius
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            if (tag.isNotEmpty() && go.tag != tag) continue
            val col = go.get<Collider3D>() ?: continue
            val b = Body(go, null, col); refresh(b)
            val hit = if (b.sphere) {
                val dx = x - b.cx; val dy = y - b.cy; val dz = z - b.cz
                val rr = radius + b.r
                dx * dx + dy * dy + dz * dz <= rr * rr
            } else {
                val px = x.coerceIn(b.cx - b.hx, b.cx + b.hx)
                val py = y.coerceIn(b.cy - b.hy, b.cy + b.hy)
                val pz = z.coerceIn(b.cz - b.hz, b.cz + b.hz)
                val dx = x - px; val dy = y - py; val dz = z - pz
                dx * dx + dy * dy + dz * dz <= r2
            }
            if (hit) out.add(go)
        }
        return out
    }

    /** Marches a ray against a Terrain heightfield (with bisection refinement). */
    private fun rayTerrain(ter: Terrain, ox: Float, oy: Float, oz: Float,
                           dx: Float, dy: Float, dz: Float, maxT: Float): Float? {
        val step = (ter.size / ter.resolution.coerceAtLeast(1) * 0.5f).coerceAtLeast(0.05f)
        var t = 0f
        var prev = 0f
        while (t < maxT) {
            t = minOf(t + step, maxT)
            val x = ox + dx * t; val y = oy + dy * t; val z = oz + dz * t
            val h = ter.heightAtWorld(x, z)
            if (h != null && y <= h) {
                var lo = prev; var hi = t
                repeat(8) {
                    val mid = (lo + hi) * 0.5f
                    val mh = ter.heightAtWorld(ox + dx * mid, oz + dz * mid)
                    if (mh != null && oy + dy * mid <= mh) hi = mid else lo = mid
                }
                return hi
            }
            prev = t
            if (t >= maxT) break
        }
        return null
    }

    private fun raySphere(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, b: Body): Float? {
        val lx = ox - b.cx; val ly = oy - b.cy; val lz = oz - b.cz
        val bb = lx * dx + ly * dy + lz * dz
        val c = lx * lx + ly * ly + lz * lz - b.r * b.r
        val disc = bb * bb - c
        if (disc < 0f) return null
        return -bb - sqrt(disc)
    }

    private fun rayBox(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, b: Body): Float? =
        rayAabb(ox, oy, oz, dx, dy, dz, b.cx - b.hx, b.cy - b.hy, b.cz - b.hz, b.cx + b.hx, b.cy + b.hy, b.cz + b.hz)

    companion object {
        fun rayAabb(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
                    minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Float? {
            var tmin = -Float.MAX_VALUE; var tmax = Float.MAX_VALUE
            val o = floatArrayOf(ox, oy, oz); val d = floatArrayOf(dx, dy, dz)
            val mn = floatArrayOf(minX, minY, minZ); val mx = floatArrayOf(maxX, maxY, maxZ)
            for (i in 0 until 3) {
                if (abs(d[i]) < 1e-8f) { if (o[i] < mn[i] || o[i] > mx[i]) return null } else {
                    var t1 = (mn[i] - o[i]) / d[i]; var t2 = (mx[i] - o[i]) / d[i]
                    if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
                    tmin = max(tmin, t1); tmax = min(tmax, t2)
                    if (tmin > tmax) return null
                }
            }
            if (tmax < 0f) return null
            return if (tmin >= 0f) tmin else tmax
        }
    }
}
