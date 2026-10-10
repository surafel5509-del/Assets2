package com.sengine.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.sengine.engine.Input
import com.sengine.engine.overlay.ElementType
import com.sengine.engine.overlay.OverlayDocument
import com.sengine.engine.overlay.OverlayElement
import com.sengine.engine.overlay.Shape
import com.sengine.engine.overlay.snap
import kotlin.math.hypot
import kotlin.math.min

/**
 * Draws and drives overlay layouts: on-screen controls and UI Builder screens.
 *
 * Two modes share one drawing path:
 *  - **runtime** ([editing] = false): buttons, d-pads and joysticks feed the game
 *    through [onAction] and [onAxis]; toggles and buttons on UI screens report
 *    taps through [onUiTap]. A touch that hits nothing returns false, so it falls
 *    through to the game underneath.
 *  - **editing** ([editing] = true): elements are selected, dragged and resized;
 *    the view consumes every touch. Changes are reported through [onSelect] and [onEdited].
 *
 * Geometry is computed in the *screen rectangle*, which is the whole view at
 * runtime and an aspect-fitted rectangle in the editor, so the editor previews
 * the layout for a chosen device shape.
 */
class OverlayView(context: Context, private val source: () -> List<OverlayDocument>) : View(context) {

    var editing = false
        set(value) { field = value; invalidate() }

    /** Editor preview shape (width / height). 0 = fill the view. */
    var aspect = 0f
        set(value) { field = value; layoutScreen(); invalidate() }

    var selectedId: String? = null
        set(value) { field = value; invalidate() }

    /** Grid snapping step in screen fractions while editing (0 = off). */
    var snapStep = 0f

    var onSelect: ((OverlayDocument, OverlayElement?) -> Unit)? = null
    var onEdited: (() -> Unit)? = null

    /** Runtime: a button or d-pad went down (true) or up (false). */
    var onAction: ((String, Boolean) -> Unit)? = null
    /** Runtime: the joystick or d-pad moved. Both axes are in -1..1. */
    var onAxis: ((Float, Float) -> Unit)? = null
    /** Runtime: a UI button or toggle was tapped. */
    var onUiTap: ((String) -> Unit)? = null
    /** Image elements resolve their file name here. */
    var imageLoader: ((String) -> Bitmap?)? = null

    private val screen = RectF()
    private val ptrs = HashMap<Int, Ptr>()
    private val knob = HashMap<String, FloatArray>()
    private val pressed = HashSet<String>()
    private val bitmaps = HashMap<String, Bitmap?>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.ACCENT; style = Paint.Style.FILL }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = C.ACCENT
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
    }

    private enum class Kind { PRESS, JOY, DRAG, RESIZE, UI_TAP }

    private class Ptr(
        val kind: Kind,
        val doc: OverlayDocument,
        val el: OverlayElement,
        val startX: Float,
        val startY: Float,
    ) {
        var lastX = startX
        var lastY = startY
        var moved = false
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); layoutScreen() }

    private fun layoutScreen() {
        val vw = width.toFloat(); val vh = height.toFloat()
        if (aspect <= 0f || vw <= 0f || vh <= 0f) { screen.set(0f, 0f, vw, vh); return }
        val fitW = min(vw, vh * aspect)
        val fitH = fitW / aspect
        val pad = dp(10f)
        val sw = min(fitW, vw - pad * 2); val sh = min(fitH, vh - pad * 2)
        val rw = if (sw / aspect <= sh) sw else sh * aspect
        val rh = rw / aspect
        screen.set((vw - rw) / 2f, (vh - rh) / 2f, (vw + rw) / 2f, (vh + rh) / 2f)
    }

    /** The screen rectangle in view coordinates, for the editor's hit-testing and rulers. */
    fun screenRect(): RectF = RectF(screen)

    fun refresh() = invalidate()

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (editing) {
            canvas.drawColor(0xFF15171B.toInt())
            fill.color = 0xFF101216.toInt(); fill.style = Paint.Style.FILL
            canvas.drawRoundRect(screen, dp(4f), dp(4f), fill)
            stroke.color = 0xFF3A3C42.toInt()
            canvas.drawRoundRect(screen, dp(4f), dp(4f), stroke)
            drawSafeArea(canvas)
        }
        canvas.save()
        canvas.clipRect(screen)
        canvas.translate(screen.left, screen.top)
        val w = screen.width(); val h = screen.height()
        for (doc in source()) for (e in doc.elements) {
            if (!e.visible && !editing) continue
            drawElement(canvas, doc, e, w, h)
        }
        if (editing) for (doc in source()) for (e in doc.elements) {
            if (e.id == selectedId) drawSelection(canvas, doc, e, w, h)
        }
        canvas.restore()
    }

    private fun drawSafeArea(canvas: Canvas) {
        // A faint inset rectangle marks the area phones may crop (notches, rounded corners).
        dash.color = 0x33FFFFFF
        val m = screen.width() * 0.03f
        canvas.drawRect(screen.left + m, screen.top + m, screen.right - m, screen.bottom - m, dash)
    }

    private fun drawElement(canvas: Canvas, doc: OverlayDocument, e: OverlayElement, w: Float, h: Float) {
        val r = doc.rect(e, w, h)
        val rect = RectF(r[0], r[1], r[2], r[3])
        val alpha = (e.opacity.coerceIn(0f, 1f) * 255).toInt()
        val isPressed = e.id in pressed
        val bg = e.background
        if (Color.alpha(bg) > 0) {
            fill.style = Paint.Style.FILL
            fill.color = withAlpha(if (isPressed) lighten(bg) else bg, alpha)
            shapeRect(canvas, rect, e.shape, fill)
        }
        val fg = withAlpha(e.color, alpha)
        when (e.type) {
            ElementType.PANEL -> if (Color.alpha(bg) == 0) outline(canvas, rect, e.shape, fg)
            ElementType.LABEL -> drawText(canvas, e.text, rect, e.fontSize * min(w, h), fg, false)
            ElementType.BUTTON -> {
                if (e.text.isNotEmpty()) drawText(canvas, e.text, rect, e.fontSize * min(w, h), fg, true)
                outline(canvas, rect, e.shape, withAlpha(0x66FFFFFF, alpha))
            }
            ElementType.JOYSTICK -> {
                val cx = rect.centerX(); val cy = rect.centerY()
                val radius = min(rect.width(), rect.height()) / 2f
                outline(canvas, rect, Shape.CIRCLE, withAlpha(0x88FFFFFF.toInt(), alpha))
                val k = knob[e.id] ?: floatArrayOf(0f, 0f)
                fill.color = withAlpha(0xCCFFFFFF.toInt(), alpha)
                canvas.drawCircle(cx + k[0] * radius * 0.6f, cy + k[1] * radius * 0.6f, radius * 0.38f, fill)
            }
            ElementType.DPAD -> drawDpad(canvas, rect, e, alpha)
            ElementType.IMAGE -> {
                val bmp = bitmapFor(e.text)
                if (bmp != null) {
                    val src = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                    fill.style = Paint.Style.FILL
                    fill.alpha = alpha
                    canvas.drawBitmap(bmp, src, rect, fill)
                    fill.alpha = 255
                } else {
                    outline(canvas, rect, e.shape, withAlpha(0x66FFFFFF, alpha))
                    drawText(canvas, if (e.text.isBlank()) "IMAGE" else e.text.substringAfterLast('/'), rect, e.fontSize * min(w, h), fg, false)
                }
            }
            ElementType.PROGRESS -> {
                val v = e.value.coerceIn(0f, 1f)
                fill.style = Paint.Style.FILL
                fill.color = withAlpha(e.color, alpha)
                canvas.drawRoundRect(RectF(rect.left, rect.top, rect.left + rect.width() * v, rect.bottom), dp(3f), dp(3f), fill)
            }
            ElementType.TOGGLE -> {
                val on = e.value >= 0.5f
                val track = RectF(rect.left, rect.top, rect.left + rect.height() * 1.9f, rect.bottom)
                fill.style = Paint.Style.FILL
                fill.color = withAlpha(if (on) 0xFF38B764.toInt() else 0xFF566C86.toInt(), alpha)
                canvas.drawRoundRect(track, rect.height() / 2, rect.height() / 2, fill)
                val d = rect.height() * 0.78f
                val kx = if (on) track.right - d * 0.6f - dp(2f) else track.left + d * 0.6f + dp(2f)
                fill.color = withAlpha(0xFFFFFFFF.toInt(), alpha)
                canvas.drawCircle(kx, rect.centerY(), d / 2, fill)
                drawText(canvas, e.text, RectF(track.right + dp(6f), rect.top, rect.right, rect.bottom), e.fontSize * min(w, h), fg, false, left = true)
            }
        }
    }

    private fun drawDpad(canvas: Canvas, rect: RectF, e: OverlayElement, alpha: Int) {
        val cx = rect.centerX(); val cy = rect.centerY()
        val s = min(rect.width(), rect.height()) * 0.5f
        val arm = s * 0.36f
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(0x44FFFFFF, alpha)
        canvas.drawRoundRect(RectF(cx - arm, cy - s, cx + arm, cy + s), dp(6f), dp(6f), fill)
        canvas.drawRoundRect(RectF(cx - s, cy - arm, cx + s, cy + arm), dp(6f), dp(6f), fill)
        val k = knob[e.id] ?: floatArrayOf(0f, 0f)
        if (k[0] != 0f || k[1] != 0f) {
            fill.color = withAlpha(0x88FFFFFF.toInt(), alpha)
            canvas.drawCircle(cx + k[0] * s * 0.7f, cy + k[1] * s * 0.7f, arm * 0.7f, fill)
        }
    }

    private fun drawSelection(canvas: Canvas, doc: OverlayDocument, e: OverlayElement, w: Float, h: Float) {
        val r = doc.rect(e, w, h)
        val rect = RectF(r[0], r[1], r[2], r[3]).apply { inset(-dp(3f), -dp(3f)) }
        dash.color = C.ACCENT
        canvas.drawRect(rect, dash)
        val hs = dp(7f)
        canvas.drawRect(rect.right - hs, rect.bottom - hs, rect.right + hs / 2, rect.bottom + hs / 2, handle)
    }

    private fun shapeRect(canvas: Canvas, rect: RectF, shape: Shape, p: Paint) {
        when (shape) {
            Shape.RECT -> canvas.drawRect(rect, p)
            Shape.ROUNDED -> canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.2f, min(rect.width(), rect.height()) * 0.2f, p)
            Shape.CIRCLE -> canvas.drawOval(rect, p)
        }
    }

    private fun outline(canvas: Canvas, rect: RectF, shape: Shape, color: Int) {
        stroke.color = color
        when (shape) {
            Shape.RECT -> canvas.drawRect(rect, stroke)
            Shape.ROUNDED -> canvas.drawRoundRect(rect, min(rect.width(), rect.height()) * 0.2f, min(rect.width(), rect.height()) * 0.2f, stroke)
            Shape.CIRCLE -> canvas.drawOval(rect, stroke)
        }
    }

    private fun drawText(canvas: Canvas, s: String, rect: RectF, size: Float, color: Int, centered: Boolean, left: Boolean = false) {
        if (s.isEmpty()) return
        text.color = color
        text.textSize = size.coerceAtLeast(dp(8f))
        if (left) text.textAlign = Paint.Align.LEFT else text.textAlign = Paint.Align.CENTER
        val x = if (left) rect.left else rect.centerX()
        val y = rect.centerY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(s, x, y, text)
    }

    private fun bitmapFor(name: String): Bitmap? {
        if (name.isBlank()) return null
        return bitmaps.getOrPut(name) { imageLoader?.invoke(name) }
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun lighten(c: Int): Int {
        val r = minOf(255, Color.red(c) + 40); val g = minOf(255, Color.green(c) + 40); val b = minOf(255, Color.blue(c) + 40)
        return Color.argb(Color.alpha(c), r, g, b)
    }

    /** Forget bitmap cache entries (after an asset was imported or replaced). */
    fun clearImageCache() { bitmaps.clear(); invalidate() }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> return down(id, e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_MOVE -> {
                var handled = false
                for (i in 0 until e.pointerCount) {
                    val p = ptrs[e.getPointerId(i)] ?: continue
                    move(p, e.getX(i), e.getY(i))
                    handled = true
                }
                return handled || editing
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { up(id); return true }
            MotionEvent.ACTION_CANCEL -> { ptrs.keys.toList().forEach { up(it) }; return true }
        }
        return false
    }

    private fun localX(x: Float) = (x - screen.left)
    private fun localY(y: Float) = (y - screen.top)

    private fun down(pid: Int, x: Float, y: Float): Boolean {
        val lx = localX(x); val ly = localY(y)
        val w = screen.width(); val h = screen.height()
        if (editing) return editDown(pid, lx, ly, w, h)
        for (doc in source().asReversed()) {
            for (el in doc.elements.asReversed()) {
                if (!el.visible || !el.type.interactive) continue
                val r = doc.rect(el, w, h)
                if (lx < r[0] || lx > r[2] || ly < r[1] || ly > r[3]) continue
                val kind = when (el.type) {
                    ElementType.JOYSTICK, ElementType.DPAD -> Kind.JOY
                    ElementType.TOGGLE -> Kind.UI_TAP
                    else -> if (doc.kind == com.sengine.engine.overlay.Kind.UI) Kind.UI_TAP else Kind.PRESS
                }
                ptrs[pid] = Ptr(kind, doc, el, lx, ly)
                when (kind) {
                    Kind.JOY -> { pressed.add(el.id); moveJoy(ptrs[pid]!!, lx, ly) }
                    Kind.PRESS -> { pressed.add(el.id); if (el.action.isNotBlank()) onAction?.invoke(el.action, true) }
                    else -> pressed.add(el.id)
                }
                invalidate()
                return true
            }
        }
        return false
    }

    private fun move(p: Ptr, x: Float, y: Float) {
        val lx = localX(x); val ly = localY(y)
        val w = screen.width(); val h = screen.height()
        if (editing) { editMove(p, lx, ly, w, h); return }
        when (p.kind) {
            Kind.JOY -> moveJoy(p, lx, ly)
            Kind.PRESS, Kind.UI_TAP -> {
                val r = p.doc.rect(p.el, w, h)
                val inside = lx >= r[0] && lx <= r[2] && ly >= r[1] && ly <= r[3]
                val was = p.el.id in pressed
                if (inside && !was) { pressed.add(p.el.id); if (p.kind == Kind.PRESS && p.el.action.isNotBlank()) onAction?.invoke(p.el.action, true) }
                if (!inside && was) { pressed.remove(p.el.id); if (p.kind == Kind.PRESS && p.el.action.isNotBlank()) onAction?.invoke(p.el.action, false) }
                invalidate()
            }
            else -> {}
        }
    }

    private fun up(pid: Int) {
        val p = ptrs.remove(pid) ?: return
        if (editing) { if (p.moved) onEdited?.invoke(); invalidate(); return }
        when (p.kind) {
            Kind.JOY -> {
                knob.remove(p.el.id); pressed.remove(p.el.id)
                onAxis?.invoke(0f, 0f); invalidate()
            }
            Kind.PRESS -> {
                pressed.remove(p.el.id)
                if (p.el.action.isNotBlank()) onAction?.invoke(p.el.action, false)
                invalidate()
            }
            Kind.UI_TAP -> {
                pressed.remove(p.el.id)
                if (p.el.type == ElementType.TOGGLE) {
                    p.el.value = if (p.el.value >= 0.5f) 0f else 1f
                }
                if (p.el.action.isNotBlank()) onUiTap?.invoke(p.el.action)
                invalidate()
            }
            else -> {}
        }
    }

    private fun moveJoy(p: Ptr, lx: Float, ly: Float) {
        val w = screen.width(); val h = screen.height()
        val r = p.doc.rect(p.el, w, h)
        val cx = (r[0] + r[2]) / 2f; val cy = (r[1] + r[3]) / 2f
        val radius = min(r[2] - r[0], r[3] - r[1]) / 2f
        if (radius <= 0f) return
        var dx = (lx - cx) / radius; var dy = (ly - cy) / radius
        val len = hypot(dx, dy)
        if (len > 1f) { dx /= len; dy /= len }
        if (p.el.type == ElementType.DPAD) {
            // Snap to the four directions with a dead zone, as a d-pad does.
            dx = when { dx > 0.35f -> 1f; dx < -0.35f -> -1f; else -> 0f }
            dy = when { dy > 0.35f -> -1f; dy < -0.35f -> 1f; else -> 0f }
            // Screen y grows downwards; the game's axisY grows upwards.
            onAxis?.invoke(dx, -dy)
            knob[p.el.id] = floatArrayOf(dx, dy)
        } else {
            knob[p.el.id] = floatArrayOf(dx, dy)
            onAxis?.invoke(dx, -dy)
        }
        invalidate()
    }

    // ------------------------------------------------------------------ editing

    private fun editDown(pid: Int, lx: Float, ly: Float, w: Float, h: Float): Boolean {
        for (doc in source().asReversed()) for (el in doc.elements.asReversed()) {
            val r = doc.rect(el, w, h)
            if (lx < r[0] - dp(4f) || lx > r[2] + dp(4f) || ly < r[1] - dp(4f) || ly > r[3] + dp(4f)) continue
            selectedId = el.id
            val onHandle = hypot(lx - r[2], ly - r[3]) < dp(20f)
            ptrs[pid] = Ptr(if (onHandle) Kind.RESIZE else Kind.DRAG, doc, el, lx, ly)
            onSelect?.invoke(doc, el)
            invalidate()
            return true
        }
        selectedId = null
        onSelect?.invoke(source().firstOrNull() ?: return true, null)
        invalidate()
        return true
    }

    private fun editMove(p: Ptr, lx: Float, ly: Float, w: Float, h: Float) {
        val dxPx = lx - p.lastX; val dyPx = ly - p.lastY
        if (dxPx == 0f && dyPx == 0f) return
        p.lastX = lx; p.lastY = ly
        p.moved = true
        val e = p.el
        if (p.kind == Kind.RESIZE) {
            e.w = (e.w + dxPx / w).coerceAtLeast(0.01f)
            e.h = (e.h + dyPx / h).coerceAtLeast(0.01f)
            if (snapStep > 0f) { e.w = snap(e.w, snapStep).coerceAtLeast(snapStep); e.h = snap(e.h, snapStep).coerceAtLeast(snapStep) }
        } else {
            val sx = if (e.hAnchor == com.sengine.engine.overlay.HAnchor.RIGHT) -1f else 1f
            val sy = if (e.vAnchor == com.sengine.engine.overlay.VAnchor.BOTTOM) -1f else 1f
            e.x += sx * dxPx / w
            e.y += sy * dyPx / h
            if (snapStep > 0f) { e.x = snap(e.x, snapStep); e.y = snap(e.y, snapStep) }
        }
        invalidate()
    }
}
