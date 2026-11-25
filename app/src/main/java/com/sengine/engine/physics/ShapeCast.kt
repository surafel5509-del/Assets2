package com.sengine.engine.physics

import kotlin.math.abs
import kotlin.math.sqrt

/** A collider footprint for shape casting (world space, axis aligned). */
data class CastShape2(
    val cx: Float, val cy: Float, val hw: Float, val hh: Float,
    val circle: Boolean = false, val radius: Float = 0f, val id: Int = 0
)

data class CastShape3(
    val cx: Float, val cy: Float, val cz: Float,
    val hx: Float, val hy: Float, val hz: Float,
    val sphere: Boolean = false, val radius: Float = 0f, val id: Int = 0
)

data class CastHit2(val index: Int, val distance: Float, val nx: Float, val ny: Float)
data class CastHit3(val index: Int, val distance: Float, val nx: Float, val ny: Float, val nz: Float)

/**
 * Swept-shape queries (Minkowski-expanded slab tests). A circle cast against a
 * box becomes a ray against the box grown by the circle radius; against a
 * circle it becomes a ray against the sum of the radii. This is the standard
 * conservative approximation used by 2D engines: it never misses a hit, and
 * corner rounding is handled by the narrow phase / CCD afterwards.
 */
object ShapeCasts {

    fun rayAabb(ox: Float, oy: Float, dx: Float, dy: Float, cx: Float, cy: Float, hw: Float, hh: Float): Float? {
        var tmin = -Float.MAX_VALUE
        var tmax = Float.MAX_VALUE
        if (abs(dx) < 1e-9f) {
            if (ox < cx - hw || ox > cx + hw) return null
        } else {
            var t1 = (cx - hw - ox) / dx; var t2 = (cx + hw - ox) / dx
            if (t1 > t2) { val s = t1; t1 = t2; t2 = s }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
        }
        if (abs(dy) < 1e-9f) {
            if (oy < cy - hh || oy > cy + hh) return null
        } else {
            var t1 = (cy - hh - oy) / dy; var t2 = (cy + hh - oy) / dy
            if (t1 > t2) { val s = t1; t1 = t2; t2 = s }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
        }
        if (tmin > tmax) return null
        return if (tmin >= 0f) tmin else if (tmax >= 0f) 0f else null
    }

    fun rayCircle(ox: Float, oy: Float, dx: Float, dy: Float, cx: Float, cy: Float, r: Float): Float? {
        val lx = ox - cx; val ly = oy - cy
        val c = lx * lx + ly * ly - r * r
        if (c <= 0f) return 0f
        val b = lx * dx + ly * dy
        val disc = b * b - c
        if (disc < 0f) return null
        val t = -b - sqrt(disc)
        return if (t >= 0f) t else null
    }

    fun rayAabb3(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
                 cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float): Float? {
        var tmin = -Float.MAX_VALUE
        var tmax = Float.MAX_VALUE
        fun slab(o: Float, d: Float, c: Float, h: Float): Boolean {
            if (abs(d) < 1e-9f) return o >= c - h && o <= c + h
            var t1 = (c - h - o) / d; var t2 = (c + h - o) / d
            if (t1 > t2) { val s = t1; t1 = t2; t2 = s }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
            return true
        }
        if (!slab(ox, dx, cx, hx) || !slab(oy, dy, cy, hy) || !slab(oz, dz, cz, hz)) return null
        if (tmin > tmax) return null
        return if (tmin >= 0f) tmin else if (tmax >= 0f) 0f else null
    }

    fun raySphere3(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
                   cx: Float, cy: Float, cz: Float, r: Float): Float? {
        val lx = ox - cx; val ly = oy - cy; val lz = oz - cz
        val c = lx * lx + ly * ly + lz * lz - r * r
        if (c <= 0f) return 0f
        val b = lx * dx + ly * dy + lz * dz
        val disc = b * b - c
        if (disc < 0f) return null
        val t = -b - sqrt(disc)
        return if (t >= 0f) t else null
    }

    /** Sweeps a circle of [radius] from (ox,oy) along (dx,dy). Direction need not be normalized. */
    fun circleCast(shapes: List<CastShape2>, ox: Float, oy: Float, dx: Float, dy: Float,
                   radius: Float, maxDist: Float, accept: (Int) -> Boolean = { true }): CastHit2? {
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9f)
        val ux = dx / len; val uy = dy / len
        var best: CastHit2? = null
        for (i in shapes.indices) {
            val s = shapes[i]
            if (!accept(s.id)) continue
            val t = if (s.circle) rayCircle(ox, oy, ux, uy, s.cx, s.cy, s.radius + radius)
            else rayAabb(ox, oy, ux, uy, s.cx, s.cy, s.hw + radius, s.hh + radius)
            if (t == null || t < 0f || t > maxDist) continue
            if (best == null || t < best!!.distance) {
                val hx = ox + ux * t; val hy = oy + uy * t
                val nx = hx - s.cx; val ny = hy - s.cy
                val nl = sqrt(nx * nx + ny * ny).coerceAtLeast(1e-6f)
                best = CastHit2(i, t, nx / nl, ny / nl)
            }
        }
        return best
    }

    /** Sweeps an AABB of half-size (hw,hh). */
    fun boxCast(shapes: List<CastShape2>, ox: Float, oy: Float, dx: Float, dy: Float,
                hw: Float, hh: Float, maxDist: Float, accept: (Int) -> Boolean = { true }): CastHit2? {
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9f)
        val ux = dx / len; val uy = dy / len
        var best: CastHit2? = null
        for (i in shapes.indices) {
            val s = shapes[i]
            if (!accept(s.id)) continue
            val t = if (s.circle) {
                // conservative: grow a bounding box around the swept circle
                rayAabb(ox, oy, ux, uy, s.cx, s.cy, s.radius + hw, s.radius + hh)
            } else rayAabb(ox, oy, ux, uy, s.cx, s.cy, s.hw + hw, s.hh + hh)
            if (t == null || t < 0f || t > maxDist) continue
            if (best == null || t < best!!.distance) {
                val hx = ox + ux * t; val hy = oy + uy * t
                val nx = hx - s.cx; val ny = hy - s.cy
                val nl = sqrt(nx * nx + ny * ny).coerceAtLeast(1e-6f)
                best = CastHit2(i, t, nx / nl, ny / nl)
            }
        }
        return best
    }

    fun sphereCast(shapes: List<CastShape3>, ox: Float, oy: Float, oz: Float,
                   dx: Float, dy: Float, dz: Float, radius: Float, maxDist: Float,
                   accept: (Int) -> Boolean = { true }): CastHit3? {
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9f)
        val ux = dx / len; val uy = dy / len; val uz = dz / len
        var best: CastHit3? = null
        for (i in shapes.indices) {
            val s = shapes[i]
            if (!accept(s.id)) continue
            val t = if (s.sphere) raySphere3(ox, oy, oz, ux, uy, uz, s.cx, s.cy, s.cz, s.radius + radius)
            else rayAabb3(ox, oy, oz, ux, uy, uz, s.cx, s.cy, s.cz, s.hx + radius, s.hy + radius, s.hz + radius)
            if (t == null || t < 0f || t > maxDist) continue
            if (best == null || t < best!!.distance) {
                val hx = ox + ux * t; val hy = oy + uy * t; val hz = oz + uz * t
                val nx = hx - s.cx; val ny = hy - s.cy; val nz = hz - s.cz
                val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                best = CastHit3(i, t, nx / nl, ny / nl, nz / nl)
            }
        }
        return best
    }
}
