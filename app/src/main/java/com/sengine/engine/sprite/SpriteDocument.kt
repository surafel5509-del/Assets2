package com.sengine.engine.sprite

import com.sengine.engine.core.Base64Codec
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One layer's pixels for every frame. ARGB pixels are stored top-to-bottom. */
class SpriteLayer(
    var name: String,
    var visible: Boolean = true,
    var opacity: Int = 255,
    val frames: MutableList<IntArray> = mutableListOf(),
)

/**
 * Pure-JVM sprite project. The Android editor draws this document, but every
 * pixel operation, layer/frame edit, undo step and serialization is testable
 * without Android graphics stubs.
 */
class SpriteDocument(var width: Int = 32, var height: Int = 32) {
    val layers = ArrayList<SpriteLayer>()
    var frameCount = 1
        private set
    var currentFrame = 0
    var currentLayer = 0
    var fps = 12
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    init {
        require(width in 1..1024 && height in 1..1024) { "invalid sprite size" }
        layers.add(SpriteLayer("Layer 1", true, 255, mutableListOf(IntArray(width * height))))
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val layerNames: List<String> get() = layers.map { it.name }

    fun pixels(frame: Int = currentFrame, layer: Int = currentLayer): IntArray {
        val f = frame.coerceIn(0, frameCount - 1)
        val l = layer.coerceIn(0, layers.size - 1)
        return layers[l].frames[f]
    }

    fun layer(frame: Int = currentFrame, layer: Int = currentLayer): SpriteLayer =
        layers[layer.coerceIn(0, layers.size - 1)]

    /** Flattens visible layers with alpha compositing. */
    fun composite(frame: Int = currentFrame): IntArray {
        val f = frame.coerceIn(0, frameCount - 1)
        val out = IntArray(width * height)
        for (l in layers) {
            if (!l.visible || l.opacity <= 0) continue
            val px = l.frames[f]
            val op = l.opacity.coerceIn(0, 255) / 255f
            for (i in out.indices) out[i] = blend(out[i], withOpacity(px[i], op))
        }
        return out
    }

    fun validate(): String? = when {
        width !in 1..1024 || height !in 1..1024 -> "invalid sprite size"
        frameCount < 1 -> "no frames"
        layers.isEmpty() -> "no layers"
        layers.any { it.frames.size != frameCount || it.frames.any { px -> px.size != width * height } } -> "layer frame mismatch"
        currentFrame !in 0 until frameCount -> "invalid current frame"
        currentLayer !in layers.indices -> "invalid current layer"
        fps <= 0 -> "invalid fps"
        else -> null
    }

    // ------------------------------------------------------------------ history
    fun checkpoint() {
        undoStack.addLast(toJson().toString())
        while (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(): Boolean {
        val state = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(toJson().toString())
        return try { loadJson(JSONObject(state)); true } catch (_: Exception) { false }
    }

    fun redo(): Boolean {
        val state = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(toJson().toString())
        return try { loadJson(JSONObject(state)); true } catch (_: Exception) { false }
    }

    // ------------------------------------------------------------------ frames / layers
    fun selectFrame(index: Int) { currentFrame = index.coerceIn(0, frameCount - 1) }
    fun selectLayer(index: Int) { currentLayer = index.coerceIn(0, layers.size - 1) }

    fun addFrame(duplicateCurrent: Boolean = false): Int {
        checkpoint()
        val insert = currentFrame + 1
        for (l in layers) {
            val px = if (duplicateCurrent) l.frames[currentFrame].copyOf() else IntArray(width * height)
            l.frames.add(insert, px)
        }
        frameCount++
        currentFrame = insert
        return insert
    }

    fun deleteFrame(index: Int = currentFrame): Boolean {
        if (frameCount <= 1) return false
        checkpoint()
        val i = index.coerceIn(0, frameCount - 1)
        for (l in layers) l.frames.removeAt(i)
        frameCount--
        currentFrame = currentFrame.coerceIn(0, frameCount - 1)
        return true
    }

    fun addLayer(name: String? = null): Int {
        checkpoint()
        val l = SpriteLayer(name?.takeIf { it.isNotBlank() } ?: "Layer ${layers.size + 1}")
        repeat(frameCount) { l.frames.add(IntArray(width * height)) }
        val insert = currentLayer + 1
        layers.add(insert, l)
        currentLayer = insert
        return insert
    }

    fun deleteLayer(index: Int = currentLayer): Boolean {
        if (layers.size <= 1) return false
        checkpoint()
        layers.removeAt(index.coerceIn(0, layers.size - 1))
        currentLayer = currentLayer.coerceIn(0, layers.size - 1)
        return true
    }

    fun moveLayer(index: Int, delta: Int): Boolean {
        val j = (index + delta).coerceIn(0, layers.size - 1)
        if (j == index) return false
        checkpoint()
        val l = layers.removeAt(index)
        layers.add(j, l)
        currentLayer = j
        return true
    }

    fun setLayerVisible(index: Int, visible: Boolean) { checkpoint(); layers[index].visible = visible }
    fun setLayerOpacity(index: Int, opacity: Int) { checkpoint(); layers[index].opacity = opacity.coerceIn(0, 255) }
    fun renameLayer(index: Int, name: String) { checkpoint(); layers[index].name = name.take(64) }

    // ------------------------------------------------------------------ pixels
    fun pixel(x: Int, y: Int, frame: Int = currentFrame, layer: Int = currentLayer): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        return pixels(frame, layer)[y * width + x]
    }

    fun setPixel(x: Int, y: Int, color: Int, replace: Boolean = false, frame: Int = currentFrame, layer: Int = currentLayer) {
        if (x !in 0 until width || y !in 0 until height) return
        val px = pixels(frame, layer)
        val i = y * width + x
        px[i] = if (replace) color else blend(px[i], color)
    }

    fun drawBrush(x: Int, y: Int, size: Int, color: Int, erase: Boolean = false, frame: Int = currentFrame, layer: Int = currentLayer) {
        val s = size.coerceIn(1, 128)
        val r = (s - 1) / 2f
        val minX = (x - r).toInt().coerceAtLeast(0)
        val maxX = (x + r).toInt().coerceAtMost(width - 1)
        val minY = (y - r).toInt().coerceAtLeast(0)
        val maxY = (y + r).toInt().coerceAtMost(height - 1)
        for (py in minY..maxY) for (px in minX..maxX) {
            val dx = px - x; val dy = py - y
            if (dx * dx + dy * dy <= (r + 0.25f) * (r + 0.25f)) {
                setPixel(px, py, if (erase) 0 else color, erase, frame, layer)
            }
        }
    }

    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, size: Int, color: Int, erase: Boolean = false, frame: Int = currentFrame, layer: Int = currentLayer) {
        val steps = max(abs(x1 - x0), abs(y1 - y0)).coerceAtMost(4096)
        if (steps == 0) { drawBrush(x0, y0, size, color, erase, frame, layer); return }
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            drawBrush((x0 + (x1 - x0) * t).roundToInt(), (y0 + (y1 - y0) * t).roundToInt(), size, color, erase, frame, layer)
        }
    }

    fun drawRect(x0: Int, y0: Int, x1: Int, y1: Int, size: Int, color: Int, filled: Boolean, erase: Boolean = false, frame: Int = currentFrame, layer: Int = currentLayer) {
        val xa = min(x0, x1); val xb = max(x0, x1)
        val ya = min(y0, y1); val yb = max(y0, y1)
        if (filled) {
            for (y in ya..yb) for (x in xa..xb) setPixel(x, y, if (erase) 0 else color, erase, frame, layer)
        } else {
            drawLine(xa, ya, xb, ya, size, color, erase, frame, layer)
            drawLine(xb, ya, xb, yb, size, color, erase, frame, layer)
            drawLine(xb, yb, xa, yb, size, color, erase, frame, layer)
            drawLine(xa, yb, xa, ya, size, color, erase, frame, layer)
        }
    }

    fun drawEllipse(x0: Int, y0: Int, x1: Int, y1: Int, size: Int, color: Int, filled: Boolean, erase: Boolean = false, frame: Int = currentFrame, layer: Int = currentLayer) {
        val xa = min(x0, x1); val xb = max(x0, x1)
        val ya = min(y0, y1); val yb = max(y0, y1)
        val cx = (xa + xb) / 2f; val cy = (ya + yb) / 2f
        val rx = ((xb - xa) / 2f).coerceAtLeast(0.5f); val ry = ((yb - ya) / 2f).coerceAtLeast(0.5f)
        if (filled) {
            for (y in ya..yb) for (x in xa..xb) {
                val dx = (x - cx) / rx; val dy = (y - cy) / ry
                if (dx * dx + dy * dy <= 1f) setPixel(x, y, if (erase) 0 else color, erase, frame, layer)
            }
        } else {
            var a = 0.0
            while (a < Math.PI * 2) {
                drawBrush((cx + rx * cos(a).toFloat()).roundToInt(), (cy + ry * sin(a).toFloat()).roundToInt(), size, color, erase, frame, layer)
                a += 0.015
            }
        }
    }

    fun floodFill(x: Int, y: Int, color: Int, tolerance: Int = 0, frame: Int = currentFrame, layer: Int = currentLayer): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        checkpoint()
        val px = pixels(frame, layer)
        val target = px[y * width + x]
        if (similar(target, color, tolerance)) return 0
        val seen = BooleanArray(width * height)
        val q = ArrayDeque<Int>()
        q.addLast(y * width + x)
        seen[y * width + x] = true
        var count = 0
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            if (!similar(px[i], target, tolerance)) continue
            px[i] = color
            count++
            val cx = i % width; val cy = i / width
            fun push(nx: Int, ny: Int) {
                if (nx in 0 until width && ny in 0 until height) {
                    val n = ny * width + nx
                    if (!seen[n]) { seen[n] = true; q.addLast(n) }
                }
            }
            push(cx - 1, cy); push(cx + 1, cy); push(cx, cy - 1); push(cx, cy + 1)
        }
        return count
    }

    fun clear(frame: Int = currentFrame, layer: Int = currentLayer) { checkpoint(); pixels(frame, layer).fill(0) }

    fun flipHorizontal(frame: Int = currentFrame, layer: Int = currentLayer) {
        checkpoint()
        val px = pixels(frame, layer)
        for (y in 0 until height) for (x in 0 until width / 2) {
            val a = y * width + x; val b = y * width + (width - 1 - x)
            val t = px[a]; px[a] = px[b]; px[b] = t
        }
    }

    fun flipVertical(frame: Int = currentFrame, layer: Int = currentLayer) {
        checkpoint()
        val px = pixels(frame, layer)
        for (y in 0 until height / 2) for (x in 0 until width) {
            val a = y * width + x; val b = (height - 1 - y) * width + x
            val t = px[a]; px[a] = px[b]; px[b] = t
        }
    }

    fun rotate90(clockwise: Boolean = true, frame: Int = currentFrame, layer: Int = currentLayer) {
        checkpoint()
        val src = pixels(frame, layer).copyOf()
        val dst = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val sx = if (clockwise) y else height - 1 - y
            val sy = if (clockwise) width - 1 - x else x
            if (sx in 0 until width && sy in 0 until height) dst[y * width + x] = src[sy * width + sx]
        }
        System.arraycopy(dst, 0, pixels(frame, layer), 0, dst.size)
    }

    fun shift(dx: Int, dy: Int, frame: Int = currentFrame, layer: Int = currentLayer) {
        checkpoint()
        val src = pixels(frame, layer).copyOf()
        val dst = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val sx = x - dx; val sy = y - dy
            if (sx in 0 until width && sy in 0 until height) dst[y * width + x] = src[sy * width + sx]
        }
        System.arraycopy(dst, 0, pixels(frame, layer), 0, dst.size)
    }

    // ------------------------------------------------------------------ serialization
    fun toJson(): JSONObject {
        val layerArray = JSONArray()
        for (l in layers) {
            val frames = JSONArray()
            for (px in l.frames) frames.put(Base64Codec.encode(pixelsToBytes(px)))
            layerArray.put(JSONObject()
                .put("name", l.name).put("visible", l.visible).put("opacity", l.opacity)
                .put("frames", frames))
        }
        return JSONObject()
            .put("type", "sengine.sprite").put("version", 1)
            .put("width", width).put("height", height)
            .put("frameCount", frameCount).put("currentFrame", currentFrame)
            .put("currentLayer", currentLayer).put("fps", fps)
            .put("layers", layerArray)
    }

    fun loadJson(o: JSONObject) {
        val w = o.optInt("width", width).coerceIn(1, 1024)
        val h = o.optInt("height", height).coerceIn(1, 1024)
        width = w; height = h
        val arr = o.optJSONArray("layers") ?: throw IllegalArgumentException("missing layers")
        val loaded = ArrayList<SpriteLayer>()
        var maxFrames = 0
        for (i in 0 until arr.length()) {
            val lo = arr.optJSONObject(i) ?: continue
            val l = SpriteLayer(
                lo.optString("name", "Layer ${i + 1}"),
                lo.optBoolean("visible", true),
                lo.optInt("opacity", 255).coerceIn(0, 255),
            )
            val fa = lo.optJSONArray("frames") ?: JSONArray()
            for (j in 0 until fa.length()) {
                val bytes = Base64Codec.decode(fa.optString(j, "")) ?: ByteArray(w * h * 4)
                l.frames.add(bytesToPixels(bytes, w * h))
            }
            if (l.frames.isEmpty()) l.frames.add(IntArray(w * h))
            maxFrames = max(maxFrames, l.frames.size)
            loaded.add(l)
        }
        if (loaded.isEmpty()) throw IllegalArgumentException("empty sprite")
        frameCount = o.optInt("frameCount", maxFrames).coerceIn(1, 512)
        for (l in loaded) {
            while (l.frames.size < frameCount) l.frames.add(IntArray(w * h))
            if (l.frames.size > frameCount) l.frames.subList(frameCount, l.frames.size).clear()
        }
        layers.clear(); layers.addAll(loaded)
        currentFrame = o.optInt("currentFrame", 0).coerceIn(0, frameCount - 1)
        currentLayer = o.optInt("currentLayer", 0).coerceIn(0, layers.size - 1)
        fps = o.optInt("fps", 12).coerceIn(1, 60)
    }

    companion object {
        fun fromJson(o: JSONObject): SpriteDocument {
            val d = SpriteDocument(o.optInt("width", 32), o.optInt("height", 32))
            d.loadJson(o)
            return d
        }

        fun parse(text: String): SpriteDocument? = try {
            fromJson(JSONObject(text)).takeIf { it.validate() == null }
        } catch (_: Exception) { null }

        fun validateDocument(text: String): String? = try {
            fromJson(JSONObject(text)).validate()
        } catch (e: Exception) { e.message ?: "invalid sprite document" }

        private fun pixelsToBytes(px: IntArray): ByteArray {
            val out = ByteArray(px.size * 4)
            var p = 0
            for (c in px) {
                out[p++] = ((c shr 16) and 255).toByte()
                out[p++] = ((c shr 8) and 255).toByte()
                out[p++] = (c and 255).toByte()
                out[p++] = ((c ushr 24) and 255).toByte()
            }
            return out
        }

        private fun bytesToPixels(bytes: ByteArray, count: Int): IntArray {
            val out = IntArray(count)
            var p = 0
            for (i in 0 until count) {
                if (p + 3 >= bytes.size) break
                val r = bytes[p++].toInt() and 255
                val g = bytes[p++].toInt() and 255
                val b = bytes[p++].toInt() and 255
                val a = bytes[p++].toInt() and 255
                out[i] = argb(a, r, g, b)
            }
            return out
        }

        private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
            ((a and 255) shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)
    }

    // ------------------------------------------------------------------ ARGB helpers
    private fun withOpacity(c: Int, op: Float): Int {
        if (op >= 0.999f) return c
        val a = (((c ushr 24) and 255) * op).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (c and 0x00FFFFFF)
    }

    private fun blend(dst: Int, src: Int): Int {
        val sa = (src ushr 24) and 255
        if (sa == 0) return dst
        if (sa == 255) return src
        val da = (dst ushr 24) and 255
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun ch(shift: Int): Int {
            val s = (src shr shift) and 255
            val d = (dst shr shift) and 255
            return (s * sa + d * da * (255 - sa) / 255) / outA
        }
        return argb(outA, ch(16), ch(8), ch(0))
    }

    private fun similar(a: Int, b: Int, tolerance: Int): Boolean {
        val t = tolerance.coerceIn(0, 255)
        fun diff(shift: Int) = abs(((a shr shift) and 255) - ((b shr shift) and 255))
        return diff(24) <= t && diff(16) <= t && diff(8) <= t && diff(0) <= t
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 255) shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)
}
