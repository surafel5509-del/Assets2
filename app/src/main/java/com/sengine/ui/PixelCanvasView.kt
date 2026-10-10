package com.sengine.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.sengine.studio.Argb
import com.sengine.studio.SpriteDocument
import com.sengine.studio.Tool
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The drawing surface of the Sprite Editor Studio.
 *
 * One finger draws with the current [tool]; two fingers pan and pinch-zoom.
 * Every stroke is one undo step: the document is snapshotted when the finger
 * goes down. Shape tools preview while dragged and commit on release.
 *
 * Pixels are drawn with nearest-neighbour sampling so pixel art stays crisp at
 * any zoom, over a checkerboard that makes transparency visible.
 */
class PixelCanvasView(context: Context) : View(context) {

    var doc: SpriteDocument? = null
        set(value) { field = value; dirty = true; fitToView(); invalidate() }

    var tool: Tool = Tool.PENCIL
    /** The colour drawing tools paint with. */
    var color: Int = 0xFF000000.toInt()
    /** +1 lightens, -1 darkens with the shade tool. */
    var shadeSign = 1f
    var showGrid = true
    var showSymmetry = true

    /** Called after the picture changed (a stroke, a fill, an undo) so panels can refresh. */
    var onChanged: (() -> Unit)? = null
    /** Called when the eyedropper picked a colour. */
    var onColorPicked: ((Int) -> Unit)? = null
    /** Called when a stroke begins, before the document changes. */
    var onStrokeStart: (() -> Unit)? = null

    /** Pixels per sprite pixel. */
    var zoom = 8f
        private set
    private var offX = 0f
    private var offY = 0f

    private var dirty = true
    private var bmp: Bitmap? = null
    private var onionBmp: Bitmap? = null
    private var buf: IntArray? = null
    private var onionBuf: IntArray? = null

    private val drawRect = RectF()
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val checkA = 0xFF3A3D45.toInt()
    private val checkB = 0xFF2C2F36.toInt()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = 0xAAFFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    }

    // gesture state
    private var drawing = false
    private var panning = false
    private var lastX = -1
    private var lastY = -1
    private var startX = -1
    private var startY = -1
    private var curX = -1
    private var curY = -1
    private var panLastCx = 0f
    private var panLastCy = 0f
    private var pinching = false

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            pinching = true
            setZoom(zoom * d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** Fits the whole sprite in the view with a margin. */
    fun fitToView() {
        val d = doc ?: return
        if (width == 0 || height == 0) return
        val z = min((width * 0.9f) / d.width, (height * 0.9f) / d.height).coerceAtLeast(1f)
        zoom = z
        offX = (width - d.width * zoom) / 2f
        offY = (height - d.height * zoom) / 2f
        invalidate()
    }

    fun setZoom(z: Float, fx: Float, fy: Float) {
        val d = doc ?: return
        val nz = z.coerceIn(1f, 96f)
        // Keep the sprite point under the focus fixed on screen.
        val px = (fx - offX) / zoom
        val py = (fy - offY) / zoom
        zoom = nz
        offX = fx - px * zoom
        offY = fy - py * zoom
        clampOffset(d)
        invalidate()
    }

    fun zoomBy(factor: Float) = setZoom(zoom * factor, width / 2f, height / 2f)

    private fun clampOffset(d: SpriteDocument) {
        val w = d.width * zoom; val h = d.height * zoom
        // Keep at least a strip of the sprite on screen.
        offX = offX.coerceIn(-w + width * 0.1f, width * 0.9f)
        offY = offY.coerceIn(-h + height * 0.1f, height * 0.9f)
    }

    /** Marks the pixel buffers stale. Call after the document changed behind the view's back (undo, frame change). */
    fun pixelsChanged() { dirty = true; invalidate() }

    private fun rebuild(d: SpriteDocument) {
        val n = d.width * d.height
        if (buf == null || buf!!.size != n) buf = IntArray(n)
        val img = d.composite(d.activeFrame, buf)
        buf = img
        bmp?.recycle()
        bmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888).also { it.setPixels(img, 0, d.width, 0, 0, d.width, d.height) }
        if (d.onionSkin && d.activeFrame > 0) {
            if (onionBuf == null || onionBuf!!.size != n) onionBuf = IntArray(n)
            val prev = d.composite(d.activeFrame - 1, onionBuf)
            onionBuf = prev
            onionBmp?.recycle()
            onionBmp = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888).also { it.setPixels(prev, 0, d.width, 0, 0, d.width, d.height) }
        } else {
            onionBmp?.recycle(); onionBmp = null
        }
        dirty = false
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF15171B.toInt())
        val d = doc ?: return
        if (dirty) rebuild(d)
        drawRect.set(offX, offY, offX + d.width * zoom, offY + d.height * zoom)
        drawChecker(canvas)
        bmp?.let { canvas.drawBitmap(it, null, drawRect, paint) }
        if (d.onionSkin) onionBmp?.let {
            paint.alpha = 90
            canvas.drawBitmap(it, null, drawRect, paint)
            paint.alpha = 255
        }
        if (showGrid && zoom >= 6f) drawGrid(canvas, d)
        if (showSymmetry) drawSymmetry(canvas, d)
        drawShapePreview(canvas)
        line.color = 0xFFFFFFFF.toInt(); line.style = Paint.Style.STROKE
        canvas.drawRect(drawRect, line.apply { color = 0x66FFFFFF; strokeWidth = dp(1f) })
    }

    private fun drawChecker(canvas: Canvas) {
        val cell = max(4f, zoom * 2f)
        fill.style = Paint.Style.FILL
        canvas.save()
        canvas.clipRect(drawRect)
        var y = drawRect.top
        var row = 0
        while (y < drawRect.bottom) {
            var x = drawRect.left
            var col = 0
            while (x < drawRect.right) {
                fill.color = if ((row + col) % 2 == 0) checkA else checkB
                canvas.drawRect(x, y, x + cell, y + cell, fill)
                x += cell; col++
            }
            y += cell; row++
        }
        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas, d: SpriteDocument) {
        line.style = Paint.Style.STROKE
        line.color = 0x22000000
        line.strokeWidth = 1f
        for (x in 0..d.width) {
            val sx = drawRect.left + x * zoom
            canvas.drawLine(sx, drawRect.top, sx, drawRect.bottom, line)
        }
        for (y in 0..d.height) {
            val sy = drawRect.top + y * zoom
            canvas.drawLine(drawRect.left, sy, drawRect.right, sy, line)
        }
    }

    private fun drawSymmetry(canvas: Canvas, d: SpriteDocument) {
        if (d.symmetryX) {
            val x = drawRect.centerX()
            canvas.drawLine(x, drawRect.top - dp(6f), x, drawRect.bottom + dp(6f), dash)
        }
        if (d.symmetryY) {
            val y = drawRect.centerY()
            canvas.drawLine(drawRect.left - dp(6f), y, drawRect.right + dp(6f), y, dash)
        }
    }

    private fun cellRect(x: Int, y: Int) = RectF(offX + x * zoom, offY + y * zoom, offX + (x + 1) * zoom, offY + (y + 1) * zoom)

    private fun drawShapePreview(canvas: Canvas) {
        if (!drawing || startX < 0 || curX < 0) return
        val tl = min(startX, curX); val tr = max(startX, curX)
        val tt = min(startY, curY); val tb = max(startY, curY)
        val a = cellRect(tl, tt); val b = cellRect(tr, tb)
        val box = RectF(a.left, a.top, b.right, b.bottom)
        fill.color = color
        line.color = color
        line.style = Paint.Style.STROKE
        line.strokeWidth = dp(1.5f)
        when (tool) {
            Tool.LINE -> canvas.drawLine(offX + (startX + 0.5f) * zoom, offY + (startY + 0.5f) * zoom, offX + (curX + 0.5f) * zoom, offY + (curY + 0.5f) * zoom, line)
            Tool.RECT -> canvas.drawRect(box, line)
            Tool.RECT_FILL -> { fill.alpha = 150; canvas.drawRect(box, fill); fill.alpha = 255 }
            Tool.ELLIPSE -> canvas.drawOval(box, line)
            Tool.ELLIPSE_FILL -> { fill.alpha = 150; canvas.drawOval(box, fill); fill.alpha = 255 }
            else -> {}
        }
        line.strokeWidth = dp(1f)
    }

    // ------------------------------------------------------------------ input

    /** Sprite pixel under a view coordinate, or null outside the sprite. */
    private fun pixelAt(x: Float, y: Float): Pair<Int, Int>? {
        val d = doc ?: return null
        val px = floor((x - offX) / zoom).toInt()
        val py = floor((y - offY) / zoom).toInt()
        return if (px in 0 until d.width && py in 0 until d.height) px to py else null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val d = doc ?: return false
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                panning = false
                beginDraw(d, e.x, e.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger turns the gesture into navigation. Abandon any stroke in progress.
                if (drawing) cancelDraw(d)
                panning = true
                panLastCx = centroidX(e); panLastCy = centroidY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    if (!pinching) {
                        val cx = centroidX(e); val cy = centroidY(e)
                        offX += cx - panLastCx; offY += cy - panLastCy
                        panLastCx = cx; panLastCy = cy
                        clampOffset(d)
                        invalidate()
                    }
                } else if (drawing && !pinching) {
                    continueDraw(d, e.x, e.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (drawing && !pinching && !panning) endDraw(d, e.x, e.y)
                drawing = false
                panning = false
                pinching = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Keep panning until the last finger lifts.
                panLastCx = centroidX(e, skip = e.actionIndex); panLastCy = centroidY(e, skip = e.actionIndex)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drawing) cancelDraw(d)
                drawing = false; panning = false; pinching = false
            }
        }
        invalidate()
        return true
    }

    private fun centroidX(e: MotionEvent, skip: Int = -1): Float {
        var s = 0f; var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { s += e.getX(i); n++ }
        return if (n == 0) 0f else s / n
    }

    private fun centroidY(e: MotionEvent, skip: Int = -1): Float {
        var s = 0f; var n = 0
        for (i in 0 until e.pointerCount) if (i != skip) { s += e.getY(i); n++ }
        return if (n == 0) 0f else s / n
    }

    private fun beginDraw(d: SpriteDocument, x: Float, y: Float) {
        val p = pixelAt(x, y)
        drawing = true
        if (p == null) { drawing = false; return }
        val (px, py) = p
        startX = px; startY = py; curX = px; curY = py; lastX = px; lastY = py
        if (tool == Tool.EYEDROPPER) {
            d.eyedrop(px, py, activeLayerOnly = false)?.let { onColorPicked?.invoke(it) }
            return
        }
        onStrokeStart?.invoke()
        d.pushUndo(tool.label)
        when (tool) {
            Tool.PENCIL -> d.strokeSegment(px, py, px, py, color)
            Tool.ERASER -> d.strokeSegment(px, py, px, py, 0, erase = true)
            Tool.SHADE -> d.shade(px, py, shadeSign)
            Tool.FILL -> { d.floodFill(px, py, color); drawing = false; notifyChanged() }
            else -> {}
        }
    }

    private fun continueDraw(d: SpriteDocument, x: Float, y: Float) {
        val p = pixelAt(x, y)
        // Track the finger even outside the sprite so shapes and strokes can end at the edge.
        val px = floor((x - offX) / zoom).toInt().coerceIn(-1, d.width)
        val py = floor((y - offY) / zoom).toInt().coerceIn(-1, d.height)
        val shape = tool == Tool.LINE || tool == Tool.RECT || tool == Tool.RECT_FILL ||
            tool == Tool.ELLIPSE || tool == Tool.ELLIPSE_FILL
        // Freehand tools stop at the edge; shapes clamp so they can reach it.
        if (p == null && !shape) return
        val cx = px.coerceIn(0, d.width - 1)
        val cy = py.coerceIn(0, d.height - 1)
        when (tool) {
            Tool.PENCIL -> { d.strokeSegment(lastX, lastY, cx, cy, color); lastX = cx; lastY = cy }
            Tool.ERASER -> { d.strokeSegment(lastX, lastY, cx, cy, 0, erase = true); lastX = cx; lastY = cy }
            Tool.SHADE -> if (cx != lastX || cy != lastY) { d.shade(cx, cy, shadeSign); lastX = cx; lastY = cy }
            Tool.MOVE -> {
                val dx = px - lastX; val dy = py - lastY
                if (dx != 0 || dy != 0) { d.shiftLayerPixels(dx, dy); lastX = px; lastY = py }
            }
            Tool.LINE, Tool.RECT, Tool.RECT_FILL, Tool.ELLIPSE, Tool.ELLIPSE_FILL -> { curX = cx; curY = cy }
            else -> {}
        }
        dirty = true
        notifyChanged()
    }

    private fun endDraw(d: SpriteDocument, x: Float, y: Float) {
        val p = pixelAt(x, y)
        if (p != null) { curX = p.first; curY = p.second }
        when (tool) {
            Tool.LINE -> d.drawLine(startX, startY, curX, curY, color)
            Tool.RECT -> d.drawRect(startX, startY, curX, curY, color, filled = false)
            Tool.RECT_FILL -> d.drawRect(startX, startY, curX, curY, color, filled = true)
            Tool.ELLIPSE -> d.drawEllipse(startX, startY, curX, curY, color, filled = false)
            Tool.ELLIPSE_FILL -> d.drawEllipse(startX, startY, curX, curY, color, filled = true)
            else -> {}
        }
        drawing = false
        dirty = true
        notifyChanged()
    }

    /** A gesture was taken over by navigation: undo the stroke's first step so nothing half-drawn is left behind. */
    private fun cancelDraw(d: SpriteDocument) {
        drawing = false
        if (tool != Tool.EYEDROPPER && d.canUndo) d.undo()
        dirty = true
        notifyChanged()
    }

    private fun notifyChanged() {
        onChanged?.invoke()
    }

    /** Zoom for the status bar, e.g. "8×". */
    fun zoomLabel(): String = "${zoom.toInt()}×"

    /** Hex of the colour under a view point, for the status bar readout. */
    fun colorUnder(x: Float, y: Float): String? {
        val d = doc ?: return null
        val p = pixelAt(x, y) ?: return null
        val c = d.eyedrop(p.first, p.second) ?: return null
        return Argb.toHex(c)
    }
}
