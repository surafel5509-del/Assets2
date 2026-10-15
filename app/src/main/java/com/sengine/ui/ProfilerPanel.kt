package com.sengine.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.LinearLayout
import com.sengine.engine.Engine
import java.util.Locale

/** Samples actual engine timings at the editor's 5 Hz refresh rate. */
class ProfilerPanel(context: Context) : LinearLayout(context) {
    private val summary = context.label("Waiting for frame samples…", 12f).apply { typeface = android.graphics.Typeface.MONOSPACE }
    private val graph = FrameGraph(context)
    init {
        orientation = HORIZONTAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        addView(summary, lp(0, MATCH, 1f))
        addView(graph, lp(0, MATCH, 1f))
    }
    fun sample(engine: Engine) {
        synchronized(engine.lock) {
            val heap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576
            summary.text = String.format(Locale.US,
                "%.0f FPS   ·   %d draws   ·   %d MB heap\nCPU scripts %.2f ms   physics %.2f ms\nRender %.2f ms   ·   Fixed steps %d\nDropped physics time %.3f s",
                engine.fps, engine.drawCalls, heap, engine.scriptMs, engine.physicsMs, engine.renderMs,
                engine.physics.clock.stepsLastFrame, engine.physics.clock.droppedSeconds)
            graph.push(if (engine.fps > 0f) 1000f / engine.fps else 0f)
        }
    }
    private class FrameGraph(context: Context) : View(context) {
        private val samples = ArrayDeque<Float>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun push(ms: Float) { samples.addLast(ms); while (samples.size > 120) samples.removeFirst(); invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val top = context.dp(18).toFloat()
            val bottom = height - context.dp(4).toFloat()
            val scale = maxOf(33.3f, (samples.maxOrNull() ?: 0f) * 1.1f)
            fun y(ms: Float) = bottom - (ms / scale).coerceIn(0f, 1f) * (bottom - top)
            paint.style = Paint.Style.FILL; paint.color = C.DIM; paint.textSize = context.dp(10).toFloat()
            canvas.drawText("FRAME INTERVAL · 24s history · 16.7ms target", 0f, context.dp(11).toFloat(), paint)
            paint.color = C.BORDER; paint.strokeWidth = context.dp(1).toFloat()
            canvas.drawLine(0f, y(16.667f), width.toFloat(), y(16.667f), paint)
            val path = Path()
            samples.forEachIndexed { index, ms ->
                val x = index * width / 119f
                if (index == 0) path.moveTo(x, y(ms)) else path.lineTo(x, y(ms))
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = context.dp(2).toFloat(); paint.color = C.ACCENT
            canvas.drawPath(path, paint)
        }
    }
}
