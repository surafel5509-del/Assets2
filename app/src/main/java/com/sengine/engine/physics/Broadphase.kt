package com.sengine.engine.physics

import kotlin.math.floor

/** Axis-aligned bounding box used by the broad phase. */
data class Aabb2(
    val minX: Float, val minY: Float, val maxX: Float, val maxY: Float, val id: Int = 0
) {
    fun overlaps(o: Aabb2): Boolean =
        minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY
}

data class Aabb3(
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float, val id: Int = 0
) {
    fun overlaps(o: Aabb3): Boolean =
        minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY &&
            minZ <= o.maxZ && maxZ >= o.minZ
}

/**
 * Uniform-grid spatial hash. Instead of testing every collider against every
 * other collider (O(n²)) we only test bodies that share a cell. Very large
 * bodies (a ground plane, a terrain chunk) are inserted into a "global" list
 * and tested against everything, so the result is always a superset of the
 * brute-force overlapping pairs — never a missed collision.
 *
 * Candidate pairs are returned in deterministic (i, j) order, which keeps
 * physics reproducible across runs and devices.
 */
class SpatialHash2D(var cellSize: Float = 4f, var maxCellsPerBody: Int = 96) {

    var lastPairCount = 0
        private set
    var lastBruteForcePairs = 0
        private set

    fun candidatePairs(boxes: List<Aabb2>): List<Pair<Int, Int>> {
        lastBruteForcePairs = boxes.size * (boxes.size - 1) / 2
        val cs = cellSize.coerceAtLeast(0.01f)
        val cells = HashMap<Long, ArrayList<Int>>()
        val global = ArrayList<Int>()
        for (i in boxes.indices) {
            val b = boxes[i]
            val x0 = floor(b.minX / cs).toInt()
            val x1 = floor(b.maxX / cs).toInt()
            val y0 = floor(b.minY / cs).toInt()
            val y1 = floor(b.maxY / cs).toInt()
            val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
            if (count > maxCellsPerBody) {
                global.add(i)
                continue
            }
            for (x in x0..x1) for (y in y0..y1) {
                val key = (x.toLong() shl 32) xor (y.toLong() and 0xFFFFFFFFL)
                cells.getOrPut(key) { ArrayList() }.add(i)
            }
        }
        val seen = HashSet<Long>()
        val out = ArrayList<Pair<Int, Int>>()
        fun add(a: Int, b: Int) {
            if (a == b) return
            val lo = minOf(a, b); val hi = maxOf(a, b)
            val key = (lo.toLong() shl 32) xor (hi.toLong() and 0xFFFFFFFFL)
            if (seen.add(key)) out.add(lo to hi)
        }
        for (list in cells.values) {
            for (i in list.indices) for (j in i + 1 until list.size) add(list[i], list[j])
        }
        // global bodies pair with everything
        for (g in global) for (i in boxes.indices) add(g, i)
        for (i in global.indices) for (j in i + 1 until global.size) add(global[i], global[j])
        out.sortWith(compareBy({ it.first }, { it.second }))
        lastPairCount = out.size
        return out
    }

    /** Indices whose cells intersect [box] (may include non-overlapping neighbours). */
    fun query(boxes: List<Aabb2>, box: Aabb2): List<Int> {
        val cs = cellSize.coerceAtLeast(0.01f)
        val out = HashSet<Int>()
        for (i in boxes.indices) if (boxes[i].overlaps(box)) out.add(i)
        return out.sorted()
    }
}

/** 3D counterpart of [SpatialHash2D] (same guarantees, deterministic order). */
class SpatialHash3D(var cellSize: Float = 6f, var maxCellsPerBody: Int = 216) {

    data class Cell(val x: Int, val y: Int, val z: Int)

    var lastPairCount = 0
        private set
    var lastBruteForcePairs = 0
        private set

    fun candidatePairs(boxes: List<Aabb3>): List<Pair<Int, Int>> {
        lastBruteForcePairs = boxes.size * (boxes.size - 1) / 2
        val cs = cellSize.coerceAtLeast(0.01f)
        val cells = HashMap<Cell, ArrayList<Int>>()
        val global = ArrayList<Int>()
        for (i in boxes.indices) {
            val b = boxes[i]
            val x0 = floor(b.minX / cs).toInt(); val x1 = floor(b.maxX / cs).toInt()
            val y0 = floor(b.minY / cs).toInt(); val y1 = floor(b.maxY / cs).toInt()
            val z0 = floor(b.minZ / cs).toInt(); val z1 = floor(b.maxZ / cs).toInt()
            val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1) * (z1 - z0 + 1)
            if (count > maxCellsPerBody) { global.add(i); continue }
            for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) {
                cells.getOrPut(Cell(x, y, z)) { ArrayList() }.add(i)
            }
        }
        val seen = HashSet<Long>()
        val out = ArrayList<Pair<Int, Int>>()
        fun add(a: Int, b: Int) {
            if (a == b) return
            val lo = minOf(a, b); val hi = maxOf(a, b)
            val key = (lo.toLong() shl 32) xor (hi.toLong() and 0xFFFFFFFFL)
            if (seen.add(key)) out.add(lo to hi)
        }
        for (list in cells.values) {
            for (i in list.indices) for (j in i + 1 until list.size) add(list[i], list[j])
        }
        for (g in global) for (i in boxes.indices) add(g, i)
        out.sortWith(compareBy({ it.first }, { it.second }))
        lastPairCount = out.size
        return out
    }
}
