package com.sengine.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.core.AssetKind
import com.sengine.engine.render.TextureDecoders
import com.sengine.engine.sprite.SpriteDocument
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import com.sengine.project.SpriteAssets

private enum class SpriteTool { PENCIL, ERASER, LINE, RECT, ELLIPSE, FILL, PICKER }

/**
 * Phase 3 professional 2D sprite editor: layered frames, brushes, shapes,
 * flood fill, eyedropper, palette, onion skin, grid, zoom, playback, undo/redo,
 * transforms and one-command export to PNG + atlas + clip + texture profile.
 */
class SpriteEditorActivity : AppCompatActivity() {
    private lateinit var project: Project
    private var asset: String? = null
    private var doc = SpriteDocument(32, 32)
    private var saved = ""
    private var dirty = false

    private var tool = SpriteTool.PENCIL
    private var currentColor = 0xFFFFFFFF.toInt()
    private var brushSize = 2
    private var filledShapes = false
    private var showGrid = true
    private var onion = false
    private var zoom = 1f
    private var playing = false
    private var previewFrame = 0

    private lateinit var canvasView: SpriteCanvas
    private lateinit var status: TextView
    private lateinit var frameLabel: TextView
    private lateinit var layerLabel: TextView
    private lateinit var brushLabel: TextView
    private lateinit var colorSwatch: View
    private val toolButtons = LinkedHashMap<SpriteTool, TextView>()

    private var strokeStart: Pair<Int, Int>? = null
    private var strokeLast: Pair<Int, Int>? = null
    private var strokeBase: IntArray? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (playing && doc.frameCount > 1) {
                previewFrame = (previewFrame + 1) % doc.frameCount
                canvasView.invalidate()
                refreshUi()
            }
            handler.postDelayed(this, (1000f / doc.fps).toLong().coerceIn(16L, 1000L))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)
        asset = intent.getStringExtra("asset")
        loadAsset(asset)
        saved = doc.toJson().toString()
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (dirty) confirmExit() else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ------------------------------------------------------------------ UI
    private fun buildUi() {
        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(button("←") { onBackPressedDispatcher.onBackPressed() })
        bar.addView(label("Sprite Editor", 15f, C.TEXT, true).apply { setPadding(dp(10), 0, dp(10), 0) })
        bar.addView(View(this), lp(0, 1, 1f))
        bar.addView(button("New") { newDialog() })
        bar.addView(button("Open…") { openDialog() })
        bar.addView(button("Undo") { if (doc.undo()) markDirty() })
        bar.addView(button("Redo") { if (doc.redo()) markDirty() })
        bar.addView(button("Export") { save() }, lp(WRAP, WRAP).margins(dp(3), 0, 0, 0))
        bar.addView(button("Save", C.ACCENT, 0xFFFFFFFF.toInt()) { save() }, lp(WRAP, WRAP).margins(dp(3), 0, 0, 0))
        root.addView(bar, lp(MATCH, WRAP))

        val tools = hbox().apply { setPadding(dp(6), dp(4), dp(6), dp(4)); setBackgroundColor(C.PANEL) }
        for (t in SpriteTool.values()) {
            val b = button(t.name.lowercase().replaceFirstChar { it.uppercase() }) { setTool(t) }.apply { textSize = 11f }
            toolButtons[t] = b
            tools.addView(b, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        }
        colorSwatch = View(this).apply {
            setOnClickListener { ColorPickerDialog.show(this@SpriteEditorActivity, currentColor) { c -> currentColor = c; refreshUi() } }
        }
        tools.addView(colorSwatch, lp(dp(30), dp(30)).margins(dp(6), 0, dp(6), 0))
        tools.addView(button("−") { brushSize = (brushSize - 1).coerceAtLeast(1); refreshUi() }, lp(WRAP, WRAP))
        brushLabel = label("", 11f, C.DIM)
        tools.addView(brushLabel, lp(WRAP, WRAP).margins(dp(4), 0, dp(4), 0))
        tools.addView(button("+") { brushSize = (brushSize + 1).coerceAtMost(64); refreshUi() }, lp(WRAP, WRAP))
        val fillButton = button("Outline") { v -> filledShapes = !filledShapes; (v as? TextView)?.text = if (filledShapes) "Filled" else "Outline"; refreshUi() }.apply { textSize = 11f }
        tools.addView(fillButton, lp(WRAP, WRAP).margins(dp(6), 0, dp(4), 0))
        root.addView(HorizontalScrollView(this).apply { addView(tools); isHorizontalScrollBarEnabled = false }, lp(MATCH, WRAP))

        val palette = hbox().apply { setPadding(dp(6), dp(4), dp(6), dp(4)); setBackgroundColor(C.PANEL2) }
        for (c in PALETTE) {
            palette.addView(View(this).apply {
                background = round(c, dp(4).toFloat(), dp(1), 0xFF555555.toInt())
                setOnClickListener { currentColor = c; refreshUi() }
            }, lp(dp(26), dp(26)).margins(dp(2), 0, dp(2), 0))
        }
        root.addView(HorizontalScrollView(this).apply { addView(palette); isHorizontalScrollBarEnabled = false }, lp(MATCH, WRAP))

        val middle = hbox()
        val left = vbox().apply { setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(C.PANEL) }
        frameLabel = label("", 12f, C.TEXT, true)
        layerLabel = label("", 11f, C.DIM)
        left.addView(frameLabel); left.addView(layerLabel)
        left.addView(button("◀ Frame") { selectFrame(doc.currentFrame - 1) }, lp(MATCH, WRAP).margins(0, dp(4), 0, 0))
        left.addView(button("Frame ▶") { selectFrame(doc.currentFrame + 1) }, lp(MATCH, WRAP))
        left.addView(button("+ Frame") { doc.addFrame(false); markDirty() }, lp(MATCH, WRAP).margins(0, dp(4), 0, 0))
        left.addView(button("Duplicate Frame") { doc.addFrame(true); markDirty() }, lp(MATCH, WRAP))
        left.addView(button("Delete Frame") { if (doc.deleteFrame()) markDirty() }, lp(MATCH, WRAP))
        left.addView(button("+ Layer") { doc.addLayer(); markDirty() }, lp(MATCH, WRAP).margins(0, dp(8), 0, 0))
        left.addView(button("Layer ▲") { if (doc.moveLayer(doc.currentLayer, -1)) markDirty() }, lp(MATCH, WRAP))
        left.addView(button("Layer ▼") { if (doc.moveLayer(doc.currentLayer, 1)) markDirty() }, lp(MATCH, WRAP))
        left.addView(button("Toggle Layer") { doc.setLayerVisible(doc.currentLayer, !doc.layer().visible); markDirty() }, lp(MATCH, WRAP))
        left.addView(button("Delete Layer") { if (doc.deleteLayer()) markDirty() }, lp(MATCH, WRAP))
        left.addView(button("Flip H") { doc.checkpoint(); doc.flipHorizontal(); markDirty() }, lp(MATCH, WRAP).margins(0, dp(8), 0, 0))
        left.addView(button("Flip V") { doc.checkpoint(); doc.flipVertical(); markDirty() }, lp(MATCH, WRAP))
        left.addView(button("Rotate 90°") { doc.checkpoint(); doc.rotate90(true); markDirty() }, lp(MATCH, WRAP))
        left.addView(button("Clear Frame") { doc.clear(); markDirty() }, lp(MATCH, WRAP))
        left.addView(button("⏯ Play") { playing = !playing; previewFrame = doc.currentFrame; refreshUi() }, lp(MATCH, WRAP).margins(0, dp(8), 0, 0))
        left.addView(button("Onion Skin") { onion = !onion; refreshUi() }, lp(MATCH, WRAP))
        left.addView(button("Grid") { showGrid = !showGrid; refreshUi() }, lp(MATCH, WRAP))
        left.addView(button("Zoom −") { zoom = (zoom / 1.25f).coerceAtLeast(0.25f); refreshUi() }, lp(MATCH, WRAP))
        left.addView(button("Zoom +") { zoom = (zoom * 1.25f).coerceAtMost(12f); refreshUi() }, lp(MATCH, WRAP))
        middle.addView(left, lp(dp(164), MATCH))
        canvasView = SpriteCanvas(this).apply { setBackgroundColor(C.BG) }
        middle.addView(canvasView, lp(0, MATCH, 1f))
        root.addView(middle, lp(MATCH, 0, 1f))

        status = label("", 11f, C.DIM).apply { setPadding(dp(8), dp(5), dp(8), dp(5)); setBackgroundColor(C.HEADER) }
        root.addView(status, lp(MATCH, WRAP))
        setContentView(root)
        refreshUi()
    }

    private fun setTool(t: SpriteTool) { tool = t; refreshUi() }

    private fun selectFrame(index: Int) {
        playing = false
        doc.selectFrame(index)
        previewFrame = doc.currentFrame
        refreshUi()
    }

    private fun refreshUi() {
        if (!::canvasView.isInitialized) return
        frameLabel.text = "Frame ${doc.currentFrame + 1}/${doc.frameCount}  •  ${doc.fps} fps"
        layerLabel.text = "Layer ${doc.currentLayer + 1}/${doc.layers.size}: ${doc.layer().name}\n${doc.width}×${doc.height}  •  ${if (dirty) "unsaved" else "saved"}"
        brushLabel.text = "$brushSize px"
        colorSwatch.background = round(currentColor, dp(6).toFloat(), dp(1), 0xFF555555.toInt())
        for ((t, b) in toolButtons) b.setButtonColor(if (t == tool) C.ACCENT else C.PANEL2)
        status.text = "${tool.name.lowercase()}  •  brush $brushSize px  •  ${if (onion) "onion on" else "onion off"}  •  ${if (showGrid) "grid on" else "grid off"}  •  zoom ${String.format("%.2f", zoom)}x  •  ${doc.layers.size} layers / ${doc.frameCount} frames"
        canvasView.invalidate()
    }

    private fun markDirty() {
        dirty = true
        refreshUi()
    }

    // ------------------------------------------------------------------ canvas
    private inner class SpriteCanvas(context: Context) : View(context) {
        override fun onDraw(c: Canvas) {
            val m = metrics()
            val ox = m[0]; val oy = m[1]; val cell = m[2]
            val dw = doc.width * cell; val dh = doc.height * cell
            c.drawColor(C.BG)
            val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
            val check = (8f * zoom).coerceIn(4f, 32f)
            var yy = 0f
            while (yy < dh) {
                var xx = 0f
                while (xx < dw) {
                    paint.color = if (((xx / check).toInt() + (yy / check).toInt()) % 2 == 0) 0xFF33363B.toInt() else 0xFF3C4046.toInt()
                    c.drawRect(ox + xx, oy + yy, ox + minOf(xx + check, dw), oy + minOf(yy + check, dh), paint)
                    xx += check
                }
                yy += check
            }
            val frame = if (playing) previewFrame else doc.currentFrame
            val px = renderFrame(frame)
            val bmp = try {
                Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888).apply {
                    setPixels(px, 0, doc.width, 0, 0, doc.width, doc.height)
                }
            } catch (_: Throwable) { null }
            if (bmp != null) {
                val dst = RectF(ox, oy, ox + dw, oy + dh)
                c.drawBitmap(bmp, null, dst, paint)
                try { bmp.recycle() } catch (_: Throwable) {}
            }
            if (showGrid && cell >= 4f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = 0x44000000
                for (x in 0..doc.width) c.drawLine(ox + x * cell, oy, ox + x * cell, oy + dh, paint)
                for (y in 0..doc.height) c.drawLine(ox, oy + y * cell, ox + dw, oy + y * cell, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = C.ACCENT
            c.drawRect(ox, oy, ox + dw, oy + dh, paint)
        }

        private fun metrics(): FloatArray {
            val cell = (minOf(width.toFloat() / doc.width, height.toFloat() / doc.height) * zoom).coerceAtLeast(1f)
            val dw = doc.width * cell; val dh = doc.height * cell
            return floatArrayOf((width - dw) / 2f, (height - dh) / 2f, cell)
        }

        private fun point(e: MotionEvent): Pair<Int, Int> {
            val m = metrics()
            val x = ((e.x - m[0]) / m[2]).toInt().coerceIn(0, doc.width - 1)
            val y = ((e.y - m[1]) / m[2]).toInt().coerceIn(0, doc.height - 1)
            return x to y
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val (x, y) = point(e)
            when (e.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> beginStroke(x, y)
                MotionEvent.ACTION_MOVE -> moveStroke(x, y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endStroke(x, y)
            }
            refreshUi()
            return true
        }
    }

    private fun beginStroke(x: Int, y: Int) {
        if (tool == SpriteTool.PICKER) {
            currentColor = doc.pixel(x, y)
            refreshUi()
            return
        }
        if (tool == SpriteTool.FILL) {
            doc.floodFill(x, y, currentColor, 0)
            markDirty()
            return
        }
        doc.checkpoint()
        strokeStart = x to y
        strokeLast = x to y
        strokeBase = doc.pixels().copyOf()
        drawStroke(x, y)
        markDirty()
    }

    private fun moveStroke(x: Int, y: Int) {
        if (strokeStart == null) return
        drawStroke(x, y)
        strokeLast = x to y
        markDirty()
    }

    private fun endStroke(x: Int, y: Int) {
        if (strokeStart == null) return
        drawStroke(x, y)
        strokeStart = null; strokeLast = null; strokeBase = null
        markDirty()
    }

    private fun drawStroke(x: Int, y: Int) {
        val start = strokeStart ?: return
        val erase = tool == SpriteTool.ERASER
        when (tool) {
            SpriteTool.PENCIL, SpriteTool.ERASER -> {
                val last = strokeLast ?: start
                doc.drawLine(last.first, last.second, x, y, brushSize, currentColor, erase)
            }
            SpriteTool.LINE -> {
                restoreStrokeBase()
                doc.drawLine(start.first, start.second, x, y, brushSize, currentColor, erase)
            }
            SpriteTool.RECT -> {
                restoreStrokeBase()
                doc.drawRect(start.first, start.second, x, y, brushSize, currentColor, filledShapes, erase)
            }
            SpriteTool.ELLIPSE -> {
                restoreStrokeBase()
                doc.drawEllipse(start.first, start.second, x, y, brushSize, currentColor, filledShapes, erase)
            }
            SpriteTool.FILL, SpriteTool.PICKER -> {}
        }
    }

    private fun restoreStrokeBase() {
        val base = strokeBase ?: return
        val px = doc.pixels()
        if (base.size == px.size) System.arraycopy(base, 0, px, 0, px.size)
    }

    private fun renderFrame(frame: Int): IntArray {
        val px = doc.composite(frame).copyOf()
        if (onion && doc.frameCount > 1) {
            val prev = doc.composite((frame - 1 + doc.frameCount) % doc.frameCount)
            for (i in px.indices) px[i] = blend(px[i], withAlpha(prev[i], 0.25f))
        }
        return px
    }

    private fun withAlpha(c: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
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
        return (outA shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ------------------------------------------------------------------ files / dialogs
    private fun loadAsset(name: String?) {
        if (name == null) { doc = SpriteDocument(32, 32); return }
        val file = project.assetFile(name)
        if (name.endsWith(".sprite", ignoreCase = true)) {
            doc = try { SpriteDocument.parse(file.readText()) ?: SpriteDocument(32, 32) } catch (_: Exception) { SpriteDocument(32, 32) }
        } else {
            doc = try {
                val first = TextureDecoders.loadBitmap(file)
                if (first != null) {
                    var bmp: Bitmap = first
                    if (bmp.width > 1024 || bmp.height > 1024) {
                        val s = 1024f / maxOf(bmp.width, bmp.height)
                        bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), false)
                    }
                    val d = SpriteDocument(bmp.width.coerceIn(1, 1024), bmp.height.coerceIn(1, 1024))
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    d.pixels().let { target -> System.arraycopy(px, 0, target, 0, minOf(px.size, target.size)) }
                    d
                } else SpriteDocument(32, 32)
            } catch (_: Throwable) { SpriteDocument(32, 32) }
        }
        previewFrame = doc.currentFrame
        dirty = false
    }

    private fun save() {
        val target = asset?.takeIf { it.endsWith(".sprite", ignoreCase = true) }
        if (target != null) writeSave(target) else saveAsDialog()
    }

    private fun writeSave(name: String) {
        try {
            project.writeAsset(name, doc.toJson().toString(2))
            val r = SpriteAssets.export(project, doc, name)
            asset = name
            saved = doc.toJson().toString()
            dirty = false
            toast("Saved $name; exported ${r.png}, ${r.atlas}, ${r.animation} and ${r.textureProfile}")
            refreshUi()
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        }
    }

    private fun saveAsDialog(onDone: () -> Unit = {}) {
        val f: EditText = field(asset?.substringBeforeLast('.') ?: "NewSprite")
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Save sprite")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val stem = f.text.toString().trim().replace(Regex("[^A-Za-z0-9_ -]"), "").ifBlank { "NewSprite" }
                writeSave(project.uniqueAssetName("$stem.sprite"))
                onDone()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDialog() {
        val items = project.listAssets(AssetKind.SPRITE).toTypedArray()
        if (items.isEmpty()) { toast("No .sprite documents yet"); return }
        MaterialAlertDialogBuilder(this)
            .setTitle("Open sprite")
            .setItems(items) { _, which ->
                asset = items[which]
                loadAsset(asset)
                saved = doc.toJson().toString()
                refreshUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newDialog() {
        val w: EditText = field("32", true)
        val h: EditText = field("32", true)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
            addView(label("Width", 11f, C.DIM)); addView(w, lp(MATCH, WRAP))
            addView(label("Height", 11f, C.DIM)); addView(h, lp(MATCH, WRAP))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New sprite")
            .setView(box)
            .setPositiveButton("Create") { _, _ ->
                val width = w.text.toString().toIntOrNull()?.coerceIn(1, 1024) ?: 32
                val height = h.text.toString().toIntOrNull()?.coerceIn(1, 1024) ?: 32
                doc = SpriteDocument(width, height)
                asset = null; playing = false; previewFrame = 0; saved = doc.toJson().toString(); dirty = false
                refreshUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unsaved sprite")
            .setMessage("Save changes before leaving the sprite editor?")
            .setPositiveButton("Save") { _, _ ->
                val target = asset?.takeIf { it.endsWith(".sprite", ignoreCase = true) }
                if (target != null) { writeSave(target); finish() } else saveAsDialog { finish() }
            }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        private val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF9E9E9E.toInt(), 0xFFEF5350.toInt(),
            0xFFFF7043.toInt(), 0xFFFFCA28.toInt(), 0xFFFFEE58.toInt(), 0xFF66BB6A.toInt(),
            0xFF26A69A.toInt(), 0xFF42A5F5.toInt(), 0xFF5C6BC0.toInt(), 0xFFAB47BC.toInt(),
            0xFFEC407A.toInt(), 0xFF8D6E63.toInt(), 0xFFB0BEC5.toInt(), 0x00000000,
        )
    }
}
