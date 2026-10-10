package com.sengine.studio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Drawing tools available in the Sprite Studio. */
enum class Tool(val label: String) {
    PENCIL("Pencil"), ERASER("Eraser"), FILL("Bucket fill"),
    LINE("Line"), RECT("Rectangle"), RECT_FILL("Filled rectangle"),
    ELLIPSE("Ellipse"), ELLIPSE_FILL("Filled ellipse"),
    EYEDROPPER("Eyedropper"), SHADE("Lighten / darken"),
    MOVE("Move layer contents"),
}

/** One drawable layer of pixels. */
class PixelLayer(var name: String, val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {
    var visible = true
    var locked = false
    /** 0..1, applied when the layer is composited. */
    var opacity = 1f

    fun get(x: Int, y: Int): Int = px[y * w + x]
    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h

    fun copy(): PixelLayer = PixelLayer(name, w, h, px.copyOf()).also {
        it.visible = visible; it.locked = locked; it.opacity = opacity
    }
}

/** One animation frame: a stack of layers, bottom first. */
class PixelFrame(val layers: MutableList<PixelLayer>, var durationMs: Int = 100) {
    fun copy() = PixelFrame(layers.map { it.copy() }.toMutableList(), durationMs)
}

/** How an imported image is fitted into the canvas. */
enum class FitMode { STRETCH, CONTAIN, CENTER_CROP, ORIGINAL }

/**
 * The Sprite Studio document: a fixed-size canvas, animation frames, layers,
 * a palette, and an undo history.
 *
 * Pixel data is ARGB ints. Every drawing operation writes to the *active layer
 * of the active frame* and silently skips locked or out-of-bounds pixels, so
 * the UI can pass raw touch coordinates without clamping them first.
 *
 * Undo keeps whole-document snapshots (capped at [UNDO_LIMIT]). A sprite is at
 * most [MAX_SIZE] pixels square with a handful of frames, so a snapshot is a
 * few hundred kilobytes at worst, which is cheaper than per-stroke diff bookkeeping
 * and cannot get out of sync with structural edits such as adding a frame.
 */
class SpriteDocument(var width: Int, var height: Int, var name: String = "sprite") {

    val frames = ArrayList<PixelFrame>()
    var activeFrame = 0
        private set
    var activeLayer = 0
        private set

    /** Playback speed for the preview and the exported clip. */
    var fps = 8f
    var loop = true

    /** Mirror every stamp across the vertical centre line. */
    var symmetryX = false
    /** Mirror every stamp across the horizontal centre line. */
    var symmetryY = false
    /** Show the previous frame faintly behind the current one while editing. */
    var onionSkin = true

    /** Brush radius in pixels (1 = single pixel). */
    var brushSize = 1
    /** Round brush (true) or square brush (false). */
    var roundBrush = false

    val palette = ArrayList<Int>(DEFAULT_PALETTE)
    val recent = ArrayList<Int>()

    private val undoStack = ArrayList<Snapshot>()
    private val redoStack = ArrayList<Snapshot>()
    /** Human-readable label of the last undoable action, for the status bar. */
    var lastAction = "New sprite"
        private set

    private class Snapshot(val frames: List<PixelFrame>, val frame: Int, val layer: Int, val label: String)

    init {
        require(width in 1..MAX_SIZE && height in 1..MAX_SIZE) { "Sprite size must be 1..$MAX_SIZE" }
        frames.add(PixelFrame(mutableListOf(PixelLayer("Layer 1", width, height))))
    }

    val frameCount get() = frames.size
    val layerCount get() = frames[activeFrame].layers.size
    val currentFrame get() = frames[activeFrame]
    val currentLayer get() = frames[activeFrame].layers[activeLayer]

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    // ------------------------------------------------------------------
    // Selection of frame and layer
    // ------------------------------------------------------------------

    fun selectFrame(i: Int) {
        activeFrame = i.coerceIn(0, frames.size - 1)
        activeLayer = activeLayer.coerceIn(0, frames[activeFrame].layers.size - 1)
    }

    fun selectLayer(i: Int) {
        activeLayer = i.coerceIn(0, frames[activeFrame].layers.size - 1)
    }

    // ------------------------------------------------------------------
    // Compositing
    // ------------------------------------------------------------------

    /**
     * The flattened image of one frame, honouring layer visibility and
     * opacity. [out] is reused when it has the right size, to avoid allocating
     * every frame during playback.
     */
    fun composite(frameIndex: Int, out: IntArray? = null): IntArray {
        val buf = if (out != null && out.size == width * height) out else IntArray(width * height)
        java.util.Arrays.fill(buf, 0)
        for (layer in frames[frameIndex].layers) {
            if (!layer.visible || layer.opacity <= 0f) continue
            val lo = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            for (i in buf.indices) {
                val s = layer.px[i]
                if (s == 0) continue
                val sa = (Argb.a(s) * lo) / 255
                if (sa == 0) continue
                buf[i] = Argb.over(buf[i], Argb.of(sa, Argb.r(s), Argb.g(s), Argb.b(s)))
            }
        }
        return buf
    }

    /** Single-frame composite of the current frame, for the eyedropper and the thumbnail. */
    fun compositeCurrent(): IntArray = composite(activeFrame)

    // ------------------------------------------------------------------
    // Drawing (active layer of the active frame)
    // ------------------------------------------------------------------

    private fun target(): PixelLayer? = currentLayer.takeIf { !it.locked }

    /** Bresenham line from (x0,y0) to (x1,y1), both ends included. */
    private inline fun forEachLine(x0: Int, y0: Int, x1: Int, y1: Int, fn: (Int, Int) -> Unit) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            fn(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Calls [fn] for (x, y) and, when symmetry is on, for each mirror image of it. */
    private inline fun forEachMirror(x: Int, y: Int, fn: (Int, Int) -> Unit) {
        fn(x, y)
        if (symmetryX) fn(width - 1 - x, y)
        if (symmetryY) fn(x, height - 1 - y)
        if (symmetryX && symmetryY) fn(width - 1 - x, height - 1 - y)
    }

    /** Stamps the brush centred on (x, y). [apply] transforms each pixel's existing colour instead of painting. */
    private fun stamp(layer: PixelLayer, x: Int, y: Int, color: Int, size: Int = brushSize, apply: ((Int) -> Int)? = null) {
        val r = (size - 1) / 2
        val r2 = if (size % 2 == 0) r + 1 else r
        for (dy in -r..r2) for (dx in -r..r2) {
            if (roundBrush && size > 2) {
                val cx = dx + (if (size % 2 == 0) 0.5 else 0.0)
                val cy = dy + (if (size % 2 == 0) 0.5 else 0.0)
                if (cx * cx + cy * cy > (size * 0.5) * (size * 0.5) + 0.25) continue
            }
            forEachMirror(x + dx, y + dy) { px, py ->
                if (layer.inside(px, py)) {
                    val i = py * layer.w + px
                    layer.px[i] = if (apply == null) color else apply(layer.px[i])
                }
            }
        }
    }

    /** Freehand pencil/eraser segment from (x0,y0) to (x1,y1), inclusive. */
    fun strokeSegment(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, erase: Boolean = false) {
        val layer = target() ?: return
        val c = if (erase) 0 else color
        forEachLine(x0, y0, x1, y1) { x, y -> stamp(layer, x, y, c) }
        if (!erase) remember(color)
    }

    /** Shape previews are drawn by the view; this commits the final line. */
    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val layer = target() ?: return
        forEachLine(x0, y0, x1, y1) { x, y -> stamp(layer, x, y, color) }
        remember(color)
    }

    fun drawRect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, filled: Boolean) {
        val layer = target() ?: return
        val l = min(x0, x1); val r = max(x0, x1); val t = min(y0, y1); val b = max(y0, y1)
        for (y in t..b) for (x in l..r) {
            val edge = x == l || x == r || y == t || y == b
            if (filled || edge) stamp(layer, x, y, color)
        }
        remember(color)
    }

    fun drawEllipse(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, filled: Boolean) {
        val layer = target() ?: return
        val l = min(x0, x1); val r = max(x0, x1); val t = min(y0, y1); val b = max(y0, y1)
        val cx = (l + r) / 2.0; val cy = (t + b) / 2.0
        val rx = max(0.5, (r - l) / 2.0); val ry = max(0.5, (b - t) / 2.0)
        for (y in t..b) for (x in l..r) {
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            val d = nx * nx + ny * ny
            val inside = d <= 1.0
            // A one-pixel-wide ring: inside the ellipse but not inside a slightly smaller one.
            val inner = (1.0 - 1.0 / min(rx, ry)).coerceAtLeast(0.0)
            val onRing = inside && d >= inner * inner
            if (filled && inside || !filled && onRing) stamp(layer, x, y, color)
        }
        remember(color)
    }

    /**
     * Contiguous bucket fill of the active layer, starting at (x, y). Fills
     * pixels equal to the start pixel (transparent counts as a colour).
     */
    fun floodFill(x: Int, y: Int, color: Int) {
        val layer = target() ?: return
        if (!layer.inside(x, y)) return
        val seed = layer.get(x, y)
        if (seed == color) return
        val stack = IntArray(layer.w * layer.h)
        var sp = 0
        stack[sp++] = y * layer.w + x
        layer.px[y * layer.w + x] = color
        while (sp > 0) {
            val i = stack[--sp]
            val cx = i % layer.w; val cy = i / layer.w
            fun visit(nx: Int, ny: Int) {
                if (!layer.inside(nx, ny)) return
                val j = ny * layer.w + nx
                if (layer.px[j] != seed) return
                layer.px[j] = color
                stack[sp++] = j
            }
            visit(cx + 1, cy); visit(cx - 1, cy); visit(cx, cy + 1); visit(cx, cy - 1)
        }
        remember(color)
    }

    /** Returns the colour under the cursor in the composited frame, or null when fully transparent. */
    fun eyedrop(x: Int, y: Int, activeLayerOnly: Boolean = false): Int? {
        if (x !in 0 until width || y !in 0 until height) return null
        val c = if (activeLayerOnly) currentLayer.get(x, y) else composite(activeFrame)[y * width + x]
        return if (Argb.a(c) == 0) null else c
    }

    /** Lightens (amount > 0) or darkens (amount < 0) pixels under the brush. Each call moves a colour 10% of the way. */
    fun shade(x: Int, y: Int, amount: Float) {
        val layer = target() ?: return
        val k = amount.coerceIn(-1f, 1f)
        val goal = if (k >= 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val t = abs(k) * 0.1f
        stamp(layer, x, y, 0, brushSize) { c -> if (Argb.a(c) == 0) c else Argb.mix(c, goal, t) }
    }

    /** Shift the active layer's pixels by (dx, dy). Pixels that leave the canvas are lost. */
    fun shiftLayerPixels(dx: Int, dy: Int) {
        val layer = target() ?: return
        if (dx == 0 && dy == 0) return
        val src = layer.px.copyOf()
        java.util.Arrays.fill(layer.px, 0)
        for (y in 0 until layer.h) for (x in 0 until layer.w) {
            val nx = x + dx; val ny = y + dy
            if (layer.inside(nx, ny)) layer.px[ny * layer.w + nx] = src[y * layer.w + x]
        }
    }

    // ------------------------------------------------------------------
    // Bulk operations on the active layer or the whole animation
    // ------------------------------------------------------------------

    fun clearLayer() {
        val layer = target() ?: return
        java.util.Arrays.fill(layer.px, 0)
    }

    fun flipLayer(horizontal: Boolean) {
        val layer = target() ?: return
        val src = layer.px.copyOf()
        for (y in 0 until layer.h) for (x in 0 until layer.w) {
            val sx = if (horizontal) layer.w - 1 - x else x
            val sy = if (horizontal) y else layer.h - 1 - y
            layer.px[y * layer.w + x] = src[sy * layer.w + sx]
        }
    }

    /** Rotates the active layer 90 degrees clockwise. Only square canvases are rotated, since the others would change size. */
    fun rotateLayer90() {
        val layer = target() ?: return
        if (layer.w != layer.h) return
        val n = layer.w
        val src = layer.px.copyOf()
        for (y in 0 until n) for (x in 0 until n) layer.px[x * n + (n - 1 - y)] = src[y * n + x]
    }

    /** Adds a 1-pixel outline around opaque pixels on the active layer. */
    fun outline(color: Int) {
        val layer = target() ?: return
        val src = layer.px.copyOf()
        for (y in 0 until layer.h) for (x in 0 until layer.w) {
            if (src[y * layer.w + x] != 0) continue
            val touches = listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1).any { (nx, ny) ->
                layer.inside(nx, ny) && Argb.a(src[ny * layer.w + nx]) > 0
            }
            if (touches) layer.px[y * layer.w + x] = color
        }
    }

    /** Colour grading of the active layer, or of every layer in every frame when [allFrames] is true. */
    fun grade(hue: Float, saturation: Float, brightness: Float, contrast: Float, allFrames: Boolean) {
        val targets = if (allFrames) frames.flatMap { it.layers } else listOf(currentLayer)
        for (layer in targets) {
            if (layer.locked) continue
            for (i in layer.px.indices) layer.px[i] = Argb.grade(layer.px[i], hue, saturation, brightness, contrast)
        }
    }

    /** Snaps every opaque pixel of the active layer to the nearest palette colour. */
    fun remapToPalette(allFrames: Boolean) {
        if (palette.isEmpty()) return
        val targets = if (allFrames) frames.flatMap { it.layers } else listOf(currentLayer)
        for (layer in targets) {
            if (layer.locked) continue
            for (i in layer.px.indices) {
                val c = layer.px[i]
                if (Argb.a(c) > 0) layer.px[i] = Argb.nearest(c, palette).let { Argb.of(Argb.a(c), Argb.r(it), Argb.g(it), Argb.b(it)) }
            }
        }
    }

    /**
     * Resizes the canvas on every frame and layer. [anchorX]/[anchorY] say
     * where the old pixels sit: 0 = left/top, 0.5 = centre, 1 = right/bottom.
     */
    fun resizeCanvas(newW: Int, newH: Int, anchorX: Float = 0f, anchorY: Float = 0f) {
        require(newW in 1..MAX_SIZE && newH in 1..MAX_SIZE)
        pushUndo("Resize canvas")
        val ox = ((newW - width) * anchorX).roundToInt()
        val oy = ((newH - height) * anchorY).roundToInt()
        for (f in frames) for (i in f.layers.indices) {
            val old = f.layers[i]
            val nl = PixelLayer(old.name, newW, newH)
            nl.visible = old.visible; nl.locked = old.locked; nl.opacity = old.opacity
            for (y in 0 until old.h) for (x in 0 until old.w) {
                val nx = x + ox; val ny = y + oy
                if (nl.inside(nx, ny)) nl.px[ny * newW + nx] = old.px[y * old.w + x]
            }
            f.layers[i] = nl
        }
        width = newW; height = newH
    }

    /**
     * Imports pixels from an image as a new layer on the current frame.
     * [fit] decides how the image is placed on the canvas. Sampling is nearest-neighbour
     * so pixel art stays crisp when scaled.
     */
    fun importAsLayer(src: IntArray, sw: Int, sh: Int, fit: FitMode, name: String = "Imported") {
        pushUndo("Import image")
        val layer = PixelLayer(name, width, height)
        placeImage(layer, src, sw, sh, fit)
        frames[activeFrame].layers.add(layer)
        activeLayer = frames[activeFrame].layers.size - 1
    }

    /** Imports an image into the active layer, replacing its pixels. */
    fun importIntoActiveLayer(src: IntArray, sw: Int, sh: Int, fit: FitMode) {
        val layer = target() ?: return
        pushUndo("Import into layer")
        java.util.Arrays.fill(layer.px, 0)
        placeImage(layer, src, sw, sh, fit)
    }

    private fun placeImage(layer: PixelLayer, src: IntArray, sw: Int, sh: Int, fit: FitMode) {
        if (sw <= 0 || sh <= 0) return
        val dw: Int; val dh: Int
        when (fit) {
            FitMode.STRETCH -> { dw = layer.w; dh = layer.h }
            FitMode.CONTAIN -> {
                val s = min(layer.w.toFloat() / sw, layer.h.toFloat() / sh)
                dw = max(1, (sw * s).roundToInt()); dh = max(1, (sh * s).roundToInt())
            }
            FitMode.CENTER_CROP, FitMode.ORIGINAL -> { dw = sw; dh = sh }
        }
        val ox = (layer.w - dw) / 2
        val oy = (layer.h - dh) / 2
        for (y in 0 until dh) for (x in 0 until dw) {
            val tx = ox + x; val ty = oy + y
            if (!layer.inside(tx, ty)) continue
            val sx = ((x + 0.5f) * sw / dw).toInt().coerceIn(0, sw - 1)
            val sy = ((y + 0.5f) * sh / dh).toInt().coerceIn(0, sh - 1)
            layer.px[ty * layer.w + tx] = src[sy * sw + sx]
        }
    }

    // ------------------------------------------------------------------
    // Frames and layers
    // ------------------------------------------------------------------

    /** Adds a frame after the current one. [copy] duplicates the current frame's pixels. */
    fun addFrame(copy: Boolean = false) {
        if (frames.size >= MAX_FRAMES) return
        pushUndo("Add frame")
        val nf = if (copy) currentFrame.copy() else PixelFrame(mutableListOf(PixelLayer("Layer 1", width, height)))
        frames.add(activeFrame + 1, nf)
        activeFrame++
        activeLayer = activeLayer.coerceIn(0, nf.layers.size - 1)
    }

    fun deleteFrame(i: Int) {
        if (frames.size <= 1 || i !in frames.indices) return
        pushUndo("Delete frame")
        frames.removeAt(i)
        activeFrame = activeFrame.coerceIn(0, frames.size - 1)
        activeLayer = activeLayer.coerceIn(0, frames[activeFrame].layers.size - 1)
    }

    fun moveFrame(from: Int, to: Int) {
        if (from !in frames.indices || to !in frames.indices || from == to) return
        pushUndo("Move frame")
        val f = frames.removeAt(from)
        frames.add(to, f)
        activeFrame = to
    }

    fun setFrameDuration(i: Int, ms: Int) {
        if (i in frames.indices) frames[i].durationMs = ms.coerceIn(10, 5000)
    }

    fun addLayer() {
        pushUndo("Add layer")
        val f = currentFrame
        f.layers.add(activeLayer + 1, PixelLayer("Layer ${f.layers.size + 1}", width, height))
        activeLayer++
    }

    fun deleteLayer(i: Int) {
        val layers = currentFrame.layers
        if (layers.size <= 1 || i !in layers.indices) return
        pushUndo("Delete layer")
        layers.removeAt(i)
        activeLayer = activeLayer.coerceIn(0, layers.size - 1)
    }

    fun duplicateLayer(i: Int) {
        val layers = currentFrame.layers
        if (i !in layers.indices) return
        pushUndo("Duplicate layer")
        val copy = layers[i].copy().also { it.name = "${it.name} copy" }
        layers.add(i + 1, copy)
        activeLayer = i + 1
    }

    fun moveLayer(from: Int, to: Int) {
        val layers = currentFrame.layers
        if (from !in layers.indices || to !in layers.indices || from == to) return
        pushUndo("Reorder layer")
        val l = layers.removeAt(from)
        layers.add(to, l)
        activeLayer = to
    }

    /** Merges layer [i] into the layer below it, keeping the lower layer's properties. */
    fun mergeDown(i: Int) {
        val layers = currentFrame.layers
        if (i <= 0 || i >= layers.size) return
        pushUndo("Merge layer down")
        val top = layers[i]; val bottom = layers[i - 1]
        for (k in top.px.indices) {
            val s = top.px[k]
            if (s == 0 || !top.visible) continue
            val sa = (Argb.a(s) * top.opacity).roundToInt().coerceIn(0, 255)
            bottom.px[k] = Argb.over(bottom.px[k], Argb.of(sa, Argb.r(s), Argb.g(s), Argb.b(s)))
        }
        layers.removeAt(i)
        activeLayer = i - 1
    }

    fun toggleLayerVisible(i: Int) {
        currentFrame.layers.getOrNull(i)?.let { it.visible = !it.visible }
    }

    // ------------------------------------------------------------------
    // Palette
    // ------------------------------------------------------------------

    fun addPaletteColor(c: Int) {
        if (palette.size < MAX_PALETTE && c !in palette) palette.add(c)
    }

    fun removePaletteColor(c: Int) { palette.remove(c) }

    private fun remember(c: Int) {
        if (Argb.a(c) == 0) return
        recent.remove(c)
        recent.add(0, c)
        while (recent.size > 16) recent.removeAt(recent.size - 1)
    }

    // ------------------------------------------------------------------
    // Undo / redo
    // ------------------------------------------------------------------

    /** Records the current state so the next change can be undone. Call once per user action (stroke start, menu command). */
    fun pushUndo(label: String) {
        undoStack.add(Snapshot(frames.map { it.copy() }, activeFrame, activeLayer, label))
        if (undoStack.size > UNDO_LIMIT) undoStack.removeAt(0)
        redoStack.clear()
        lastAction = label
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        redoStack.add(Snapshot(frames.map { it.copy() }, activeFrame, activeLayer, "redo"))
        restore(undoStack.removeAt(undoStack.size - 1))
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        undoStack.add(Snapshot(frames.map { it.copy() }, activeFrame, activeLayer, "undo"))
        restore(redoStack.removeAt(redoStack.size - 1))
        return true
    }

    private fun restore(s: Snapshot) {
        frames.clear(); frames.addAll(s.frames)
        val first = frames.first().layers.first()
        width = first.w; height = first.h
        activeFrame = s.frame.coerceIn(0, frames.size - 1)
        activeLayer = s.layer.coerceIn(0, frames[activeFrame].layers.size - 1)
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    /**
     * The whole animation as one sprite sheet: frames left to right, [columns]
     * per row when given. Returns the pixels plus the sheet size and the grid.
     */
    fun exportSheet(columns: Int = frames.size): SheetImage {
        val cols = columns.coerceIn(1, frames.size)
        val rows = (frames.size + cols - 1) / cols
        val sw = width * cols; val sh = height * rows
        val out = IntArray(sw * sh)
        for (f in frames.indices) {
            val img = composite(f)
            val ox = (f % cols) * width; val oy = (f / cols) * height
            for (y in 0 until height) for (x in 0 until width) out[(oy + y) * sw + ox + x] = img[y * width + x]
        }
        return SheetImage(out, sw, sh, cols, rows)
    }

    /** Frame indices and timing as used by the `.anim` clip format. */
    fun clipFrames(): List<Int> = (0 until frames.size).toList()

    /** Average frame rate implied by per-frame durations. Used when a clip is exported. */
    fun effectiveFps(): Float {
        val total = frames.sumOf { it.durationMs }.coerceAtLeast(1)
        return (frames.size * 1000f / total).coerceIn(1f, 60f)
    }

    class SheetImage(val pixels: IntArray, val width: Int, val height: Int, val columns: Int, val rows: Int)

    companion object {
        const val MAX_SIZE = 256
        const val MAX_FRAMES = 64
        const val MAX_PALETTE = 64
        const val UNDO_LIMIT = 40

        /** A 16-colour starter palette: greys, skin, earth, greens, blues, reds, gold and white. */
        val DEFAULT_PALETTE: List<Int> = listOf(
            0xFF000000.toInt(), 0xFF1A1C2C.toInt(), 0xFF5D275D.toInt(), 0xFFB13E53.toInt(),
            0xFFEF7D57.toInt(), 0xFFFFCD75.toInt(), 0xFFA7F070.toInt(), 0xFF38B764.toInt(),
            0xFF257179.toInt(), 0xFF29366F.toInt(), 0xFF3B5DC9.toInt(), 0xFF41A6F6.toInt(),
            0xFF73EFF7.toInt(), 0xFFF4F4F4.toInt(), 0xFF94B0C2.toInt(), 0xFF566C86.toInt(),
        )

        /**
         * Creates a document from an image: the canvas takes the image's size
         * (larger images are box-downscaled to [MAX_SIZE]) and the pixels become
         * the first layer. This is the "edit sprites from an uploaded image" path.
         */
        fun fromImage(src: IntArray, sw: Int, sh: Int, name: String): SpriteDocument {
            var w = sw; var h = sh; var pixels = src
            if (max(sw, sh) > MAX_SIZE) {
                val s = MAX_SIZE.toFloat() / max(sw, sh)
                w = max(1, (sw * s).roundToInt()); h = max(1, (sh * s).roundToInt())
                pixels = IntArray(w * h)
                for (y in 0 until h) for (x in 0 until w) {
                    val sx = ((x + 0.5f) * sw / w).toInt().coerceIn(0, sw - 1)
                    val sy = ((y + 0.5f) * sh / h).toInt().coerceIn(0, sh - 1)
                    pixels[y * w + x] = src[sy * sw + sx]
                }
            }
            val doc = SpriteDocument(w, h, name)
            System.arraycopy(pixels, 0, doc.frames[0].layers[0].px, 0, w * h)
            doc.lastAction = "Opened $name"
            return doc
        }
    }
}
