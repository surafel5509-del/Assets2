package com.sengine.engine.sprite

import org.json.JSONArray
import org.json.JSONObject

/** A named pixel rectangle inside a source texture. Coordinates are top-left origin, pixels. */
data class SpriteSheetFrame(
    var name: String,
    var x: Int,
    var y: Int,
    var w: Int,
    var h: Int,
) {
    val right: Int get() = x + w
    val bottom: Int get() = y + h

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("x", x).put("y", y).put("w", w).put("h", h)

    fun copy(): SpriteSheetFrame = SpriteSheetFrame(name, x, y, w, h)

    companion object {
        fun fromJson(o: JSONObject): SpriteSheetFrame = SpriteSheetFrame(
            o.optString("name", ""),
            o.optInt("x", 0), o.optInt("y", 0),
            o.optInt("w", 0), o.optInt("h", 0),
        )
    }
}

/**
 * Editable sprite-sheet document stored as a `.sheet` asset: which texture to
 * slice, how it was sliced, playback defaults and the ordered list of named
 * frame rectangles. Pure JVM — the editor UI and the offline test suite share
 * this one implementation.
 */
class SpriteSheetDoc(
    var texture: String = "",
    var mode: String = MODE_GRID,
    var columns: Int = 4,
    var rows: Int = 1,
    var cellW: Int = 32,
    var cellH: Int = 32,
    var fps: Float = 10f,
    var loop: Boolean = true,
    frames: List<SpriteSheetFrame> = emptyList(),
) {
    val frames: MutableList<SpriteSheetFrame> = frames.toMutableList()

    /** Returns null when the document is valid, otherwise a human-readable error. */
    fun validate(): String? {
        if (mode != MODE_GRID && mode != MODE_CELL && mode != MODE_MANUAL) return "unknown slice mode"
        if (columns < 1 || rows < 1 || columns > 256 || rows > 256) return "invalid grid"
        if (cellW < 1 || cellH < 1 || cellW > 4096 || cellH > 4096) return "invalid cell size"
        if (!(fps > 0f) || fps > 120f) return "invalid fps"
        if (frames.isEmpty()) return null
        if (texture.isBlank()) return "frames require a texture"
        val names = HashSet<String>()
        for (f in frames) {
            if (f.name.isBlank()) return "frame with a blank name"
            if (!names.add(f.name)) return "duplicate frame name ${f.name}"
            if (f.w < 1 || f.h < 1) return "invalid size for ${f.name}"
            if (f.x < 0 || f.y < 0) return "invalid origin for ${f.name}"
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("texture", texture)
        .put("mode", mode)
        .put("columns", columns)
        .put("rows", rows)
        .put("cellW", cellW)
        .put("cellH", cellH)
        .put("fps", fps.toDouble())
        .put("loop", loop)
        .put("frames", JSONArray().apply { frames.forEach { put(it.toJson()) } })

    fun clearFrames() = frames.clear()

    /** Splits the texture into a columns x rows grid of equal cells (row-major). Returns the frame count. */
    fun sliceGrid(texW: Int, texH: Int): Int {
        clearFrames()
        val c = columns.coerceIn(1, 256)
        val r = rows.coerceIn(1, 256)
        val cw = texW / c
        val ch = texH / r
        if (cw < 1 || ch < 1) return 0
        for (row in 0 until r) {
            for (col in 0 until c) {
                frames.add(SpriteSheetFrame(nextName(), col * cw, row * ch, cw, ch))
            }
        }
        return frames.size
    }

    /** Splits the texture into fixed cellW x cellH cells (row-major). Returns the frame count. */
    fun sliceCell(texW: Int, texH: Int): Int {
        clearFrames()
        val cw = cellW.coerceAtLeast(1)
        val ch = cellH.coerceAtLeast(1)
        val cols = texW / cw
        val rowsN = texH / ch
        if (cols < 1 || rowsN < 1) return 0
        for (row in 0 until rowsN) {
            for (col in 0 until cols) {
                frames.add(SpriteSheetFrame(nextName(), col * cw, row * ch, cw, ch))
            }
        }
        return frames.size
    }

    /** Replaces the frame list with freshly named rectangles (grid / cell / auto slice results). */
    fun setRects(rects: List<SpriteSheetFrame>) {
        clearFrames()
        for (r in rects) frames.add(SpriteSheetFrame(nextName(), r.x, r.y, r.w, r.h))
    }

    fun addRect(x: Int, y: Int, w: Int, h: Int): SpriteSheetFrame {
        val f = SpriteSheetFrame(nextName(), x, y, w, h)
        frames.add(f)
        return f
    }

    fun removeFrame(index: Int): Boolean {
        if (index !in frames.indices) return false
        frames.removeAt(index)
        return true
    }

    fun moveFrame(from: Int, to: Int): Boolean {
        if (from !in frames.indices || to !in frames.indices || from == to) return false
        frames.add(to, frames.removeAt(from))
        return true
    }

    fun renameFrame(index: Int, name: String): Boolean {
        if (index !in frames.indices) return false
        val n = name.trim()
        if (n.isEmpty()) return false
        if (frames.withIndex().any { it.index != index && it.value.name == n }) return false
        frames[index].name = n
        return true
    }

    /** First free "frame_NN" style name. */
    fun nextName(): String {
        val used = frames.mapTo(HashSet()) { it.name }
        var i = 0
        while (true) {
            val n = "frame_%02d".format(i)
            if (n !in used) return n
            i++
        }
    }

    companion object {
        const val FORMAT = "S Engine SpriteSheet"
        const val VERSION = 1
        const val MODE_GRID = "grid"
        const val MODE_CELL = "cell"
        const val MODE_MANUAL = "manual"
        val MODES = listOf(MODE_GRID, MODE_CELL, MODE_MANUAL)

        fun fromJson(o: JSONObject): SpriteSheetDoc {
            val arr = o.optJSONArray("frames")
            val list = ArrayList<SpriteSheetFrame>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    list.add(SpriteSheetFrame.fromJson(e))
                }
            }
            val mode = o.optString("mode", MODE_GRID)
            return SpriteSheetDoc(
                texture = o.optString("texture", ""),
                mode = if (mode in MODES) mode else MODE_GRID,
                columns = o.optInt("columns", 4),
                rows = o.optInt("rows", 1),
                cellW = o.optInt("cellW", 32),
                cellH = o.optInt("cellH", 32),
                fps = o.optDouble("fps", 10.0).toFloat(),
                loop = o.optBoolean("loop", true),
                frames = list,
            )
        }

        fun parse(text: String): SpriteSheetDoc? = try {
            val o = JSONObject(text)
            if (o.optString("format", FORMAT) != FORMAT) null else fromJson(o)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Automatic sprite-sheet slicing: finds the bounding boxes of opaque regions
 * with a flood fill over the alpha channel. Deterministic reading order
 * (top-to-bottom, then left-to-right) so exported frame sequences are stable.
 */
object SpriteSlicer {
    const val DEFAULT_ALPHA = 16
    const val DEFAULT_MIN_SIZE = 2
    const val MAX_REGIONS = 4096

    fun autoSlice(
        pixels: IntArray,
        width: Int,
        height: Int,
        alphaThreshold: Int = DEFAULT_ALPHA,
        minSize: Int = DEFAULT_MIN_SIZE,
    ): List<SpriteSheetFrame> {
        require(width > 0 && height > 0) { "invalid image size" }
        require(pixels.size >= width * height) { "pixel buffer too small" }
        val thr = alphaThreshold.coerceIn(0, 255)
        val min = minSize.coerceAtLeast(1)
        val visited = BooleanArray(width * height)
        var stack = IntArray(1024)
        var sp = 0
        val out = ArrayList<SpriteSheetFrame>()

        for (seed in 0 until width * height) {
            if (visited[seed]) continue
            if ((pixels[seed] ushr 24) < thr) {
                visited[seed] = true
                continue
            }
            var minX = seed % width
            var maxX = minX
            var minY = seed / width
            var maxY = minY
            sp = 0
            stack[sp++] = seed
            visited[seed] = true
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % width
                val py = p / width
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                // 4-connected neighbours that are still above the alpha threshold.
                if (px > 0) {
                    val n = p - 1
                    if (!visited[n] && (pixels[n] ushr 24) >= thr) {
                        visited[n] = true
                        if (sp == stack.size) stack = stack.copyOf(stack.size * 2)
                        stack[sp++] = n
                    }
                }
                if (px < width - 1) {
                    val n = p + 1
                    if (!visited[n] && (pixels[n] ushr 24) >= thr) {
                        visited[n] = true
                        if (sp == stack.size) stack = stack.copyOf(stack.size * 2)
                        stack[sp++] = n
                    }
                }
                if (py > 0) {
                    val n = p - width
                    if (!visited[n] && (pixels[n] ushr 24) >= thr) {
                        visited[n] = true
                        if (sp == stack.size) stack = stack.copyOf(stack.size * 2)
                        stack[sp++] = n
                    }
                }
                if (py < height - 1) {
                    val n = p + width
                    if (!visited[n] && (pixels[n] ushr 24) >= thr) {
                        visited[n] = true
                        if (sp == stack.size) stack = stack.copyOf(stack.size * 2)
                        stack[sp++] = n
                    }
                }
            }
            if (maxX - minX + 1 >= min && maxY - minY + 1 >= min && out.size < MAX_REGIONS) {
                out.add(SpriteSheetFrame("", minX, minY, maxX - minX + 1, maxY - minY + 1))
            }
        }
        out.sortWith(compareBy({ it.y }, { it.x }))
        out.forEachIndexed { i, f -> f.name = "frame_%02d".format(i) }
        return out
    }
}

data class PackPlacement(val index: Int, val x: Int, val y: Int, val w: Int, val h: Int)

data class PackResult(val width: Int, val height: Int, val placements: List<PackPlacement>)

/**
 * Shelf packer for arbitrary-size sprite frames. Produces a compact sheet with
 * padding between frames, optionally rounded up to power-of-two dimensions
 * (the safe choice for GL ES 2.0 textures).
 */
object SpritePacker {
    fun pack(
        sizes: List<Pair<Int, Int>>,
        padding: Int = 1,
        powerOfTwo: Boolean = true,
        maxSize: Int = 4096,
    ): PackResult {
        require(sizes.isNotEmpty()) { "no frames to pack" }
        val pad = padding.coerceAtLeast(0)
        require(sizes.all { it.first >= 1 && it.second >= 1 }) { "invalid frame size" }
        val maxW = sizes.maxOf { it.first }
        val maxH = sizes.maxOf { it.second }
        require(maxW + pad * 2 <= maxSize && maxH + pad * 2 <= maxSize) { "frame exceeds maximum sheet size" }

        val totalArea = sizes.sumOf { (it.first + pad).toLong() * (it.second + pad).toLong() }
        var targetW = maxOf(maxW + pad * 2, Math.ceil(Math.sqrt(totalArea.toDouble())).toInt())
        targetW = targetW.coerceAtMost(maxSize)

        // Tallest-first keeps shelves tight; ties preserve the original order.
        val order = sizes.indices.sortedWith(
            compareByDescending<Int> { sizes[it].second }
                .thenByDescending { sizes[it].first }
                .thenBy { it },
        )
        var x = pad
        var y = pad
        var shelfH = 0
        var usedW = 0
        var usedH = 0
        val out = arrayOfNulls<PackPlacement>(sizes.size)
        for (idx in order) {
            val (w, h) = sizes[idx]
            if (x > pad && x + w + pad > targetW) {
                x = pad
                y += shelfH + pad
                shelfH = 0
            }
            out[idx] = PackPlacement(idx, x, y, w, h)
            x += w + pad
            if (h > shelfH) shelfH = h
            if (x > usedW) usedW = x
            if (y + h + pad > usedH) usedH = y + h + pad
        }
        var sheetW = usedW
        var sheetH = usedH
        if (powerOfTwo) {
            sheetW = nextPow2(sheetW)
            sheetH = nextPow2(sheetH)
        }
        require(sheetW <= maxSize && sheetH <= maxSize) { "packed sheet exceeds maximum size" }
        return PackResult(sheetW, sheetH, out.map { it!! })
    }

    private fun nextPow2(v: Int): Int {
        var p = 1
        while (p < v) p = p shl 1
        return p
    }
}
