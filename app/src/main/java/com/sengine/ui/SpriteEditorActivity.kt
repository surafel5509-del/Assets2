package com.sengine.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.R
import com.sengine.engine.anim.AnimationClip
import com.sengine.project.AssetFolders
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import com.sengine.studio.Argb
import com.sengine.studio.FitMode
import com.sengine.studio.SpriteDocument
import com.sengine.studio.SpriteFile
import com.sengine.studio.Tool
import java.io.ByteArrayOutputStream

/**
 * Sprite Editor Studio: pixel art with layers, frames, full colour control and
 * pixel animation. Works on `Sprites/Studio/<name>.sprite` files and can
 * export a sprite straight to an animation clip (`.anim` + sheet PNG).
 *
 * Reached from the Animation Editor's "Create Sprite" action, or with
 * [EXTRA_ASSET] to reopen a saved sprite.
 */
class SpriteEditorActivity : AppCompatActivity() {

    private lateinit var project: Project
    private var asset: String? = null
    private var doc = SpriteDocument(32, 32, "sprite")
    private var dirty = false
    private var color = 0xFF000000.toInt()

    private lateinit var canvas: PixelCanvasView
    private lateinit var nameField: EditText
    private lateinit var status: TextView
    private lateinit var undoBtn: ImageView
    private lateinit var redoBtn: ImageView
    private lateinit var toolIcons: LinkedHashMap<Tool, ImageView>
    private lateinit var optionRow: LinearLayout
    private lateinit var shadeBtn: TextView
    private lateinit var swatch: View
    private lateinit var hexField: EditText
    private lateinit var rSeek: SeekBar
    private lateinit var gSeek: SeekBar
    private lateinit var bSeek: SeekBar
    private lateinit var aSeek: SeekBar
    private lateinit var paletteBox: LinearLayout
    private lateinit var frameStrip: LinearLayout
    private lateinit var layerList: LinearLayout
    private lateinit var previewView: ImageView
    private lateinit var fpsField: EditText
    private lateinit var loopBox: CheckBox
    private lateinit var playBtn: TextView
    private lateinit var durationField: EditText
    private lateinit var panels: LinkedHashMap<String, LinearLayout>
    private lateinit var tabs: LinkedHashMap<String, TextView>

    private var tab = "color"
    private var updatingColor = false
    private var updatingFields = false
    private var playing = false
    private var playIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!playing) return
            playIndex = (playIndex + 1) % doc.frameCount
            previewFrame(playIndex)
            handler.postDelayed(this, doc.frames[playIndex].durationMs.toLong())
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) askImportMode(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra(EXTRA_PROJECT)!!)
        asset = intent.getStringExtra(EXTRA_ASSET)
        val existing = asset
        if (existing != null) {
            val text = project.readAsset(existing)
            doc = try { if (text != null) SpriteFile.decode(text) else doc } catch (e: Exception) {
                toast("Could not open $existing: ${e.message}"); doc
            }
        }
        doc.name = existing?.substringAfterLast('/')?.substringBeforeLast('.') ?: "sprite"

        val root = vbox().apply { setBackgroundColor(C.BG) }
        root.addView(buildHeader(), lp(-1, -2))
        canvas = PixelCanvasView(this).apply {
            setBackgroundColor(C.BG)
            onChanged = { markDirty(); refreshStatus(); undoRedoState() }
            onColorPicked = { c -> setColor(c); tab = "color"; showTab() }
            onStrokeStart = { }
            doc = this@SpriteEditorActivity.doc
            tool = Tool.PENCIL
            color = this@SpriteEditorActivity.color
        }
        root.addView(canvas, lp(-1, 0, 1f))
        status = label("", 11f, C.DIM).apply { setPadding(dp(8), dp(2), dp(8), dp(2)) }
        root.addView(status, lp(-1, -2))
        root.addView(buildToolRow(), lp(-1, -2))
        root.addView(buildOptionRow(), lp(-1, -2))
        root.addView(buildTabs(), lp(-1, -2))
        root.addView(buildPanels(), lp(-1, dp(230)))
        setContentView(root)

        canvas.post { canvas.fitToView() }
        refreshAll()
        showTab()
    }

    override fun onPause() {
        super.onPause()
        stopPlay()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (!dirty) { finish(); return }
        MaterialAlertDialogBuilder(this).setTitle("Unsaved sprite")
            .setMessage("Save changes to ${currentName()} before leaving?")
            .setPositiveButton("Save") { _, _ -> if (saveSprite()) finish() }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ layout

    private fun buildHeader(): View {
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(4), dp(4), dp(4), dp(4)); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(icon(R.drawable.ic_chevron_left, "Back") { onBackPressed() }, lp(dp(40), dp(40)))
        nameField = field(currentName()).apply {
            setSingleLine(true)
            setTextColor(C.TEXT)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (!updatingFields) { doc.name = s?.toString()?.trim().orEmpty().ifBlank { "sprite" }; markDirty() } }
            })
        }
        bar.addView(nameField, lp(0, -2, 1f))
        undoBtn = icon(R.drawable.ic_undo, "Undo") { if (doc.undo()) afterDocChange() else toast("Nothing to undo") }
        redoBtn = icon(R.drawable.ic_redo, "Redo") { if (doc.redo()) afterDocChange() else toast("Nothing to redo") }
        bar.addView(undoBtn, lp(dp(40), dp(40)))
        bar.addView(redoBtn, lp(dp(40), dp(40)))
        bar.addView(icon(R.drawable.ic_import, "Upload image") { imagePicker.launch(arrayOf("image/*")) }, lp(dp(40), dp(40)))
        bar.addView(icon(R.drawable.ic_build, "Layer tools") { showLayerTools() }, lp(dp(40), dp(40)))
        bar.addView(icon(R.drawable.ic_resize_canvas, "Canvas size") { showResize() }, lp(dp(40), dp(40)))
        bar.addView(icon(R.drawable.ic_save, "Save sprite") { saveSprite() }, lp(dp(40), dp(40)))
        bar.addView(button("To animation", C.ACCENT, 0xFFFFFFFF.toInt()) { exportAnimation() }.apply { textSize = 12f }, lp(-2, dp(40)).margins(dp(4), 0, 0, 0))
        return bar
    }

    private fun buildToolRow(): View {
        toolIcons = LinkedHashMap()
        val row = hbox().apply { setBackgroundColor(C.PANEL); setPadding(dp(4), dp(2), dp(4), dp(2)) }
        val tools = listOf(
            Tool.PENCIL to R.drawable.ic_pencil, Tool.ERASER to R.drawable.ic_eraser,
            Tool.FILL to R.drawable.ic_bucket, Tool.EYEDROPPER to R.drawable.ic_eyedropper,
            Tool.LINE to R.drawable.ic_line, Tool.RECT to R.drawable.ic_rect, Tool.RECT_FILL to R.drawable.ic_rect_fill,
            Tool.ELLIPSE to R.drawable.ic_ellipse, Tool.ELLIPSE_FILL to R.drawable.ic_ellipse_fill,
            Tool.SHADE to R.drawable.ic_shade, Tool.MOVE to R.drawable.ic_move,
        )
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val inner = hbox()
        for ((t, res) in tools) {
            val v = icon(res, t.label) { selectTool(t) }
            toolIcons[t] = v
            inner.addView(v, lp(dp(42), dp(42)))
        }
        scroll.addView(inner)
        row.addView(scroll, lp(0, -2, 1f))
        return row
    }

    private fun buildOptionRow(): View {
        optionRow = hbox().apply { setBackgroundColor(C.PANEL); setPadding(dp(6), 0, dp(6), dp(4)); gravity = Gravity.CENTER_VERTICAL }
        shadeBtn = button("Lighten", C.PANEL2) { canvas.shadeSign = -canvas.shadeSign; refreshOptions() }.apply { textSize = 11f }
        optionRow.addView(toggleChip("Grid", { canvas.showGrid }) { canvas.showGrid = !canvas.showGrid; canvas.invalidate(); refreshOptions() })
        optionRow.addView(toggleChip("Sym X", { doc.symmetryX }) { doc.symmetryX = !doc.symmetryX; markDirty(); refreshOptions() })
        optionRow.addView(toggleChip("Sym Y", { doc.symmetryY }) { doc.symmetryY = !doc.symmetryY; markDirty(); refreshOptions() })
        optionRow.addView(toggleChip("Onion", { doc.onionSkin }) { doc.onionSkin = !doc.onionSkin; canvas.pixelsChanged(); refreshOptions() })
        optionRow.addView(toggleChip("Round", { doc.roundBrush }) { doc.roundBrush = !doc.roundBrush; refreshOptions() })
        optionRow.addView(shadeBtn, lp(-2, dp(30)))
        val sizeSeek = SeekBar(this).apply { max = 7; progress = doc.brushSize - 1 }
        sizeSeek.setOnSeekBarChangeListener(seekListener { p -> doc.brushSize = p + 1 })
        optionRow.addView(label("Brush", 11f, C.DIM).apply { setPadding(dp(6), 0, dp(4), 0) }, lp(-2, -2))
        optionRow.addView(sizeSeek, lp(dp(90), -2))
        return optionRow
    }

    private fun toggleChip(text: String, on: () -> Boolean, action: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(C.TEXT)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            tag = on
            background = round(if (on()) C.SEL else C.PANEL2, dp(6).toFloat())
            setOnClickListener { action() }
        }.also { chips += it }

    private val chips = mutableListOf<TextView>()

    private fun buildTabs(): View {
        tabs = LinkedHashMap()
        val row = hbox().apply { setBackgroundColor(C.HEADER) }
        for ((key, text) in listOf("color" to "Colour", "frames" to "Animation", "layers" to "Layers", "palette" to "Palette")) {
            val t = TextView(this).apply {
                this.text = text
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { tab = key; showTab() }
            }
            tabs[key] = t
            row.addView(t, lp(0, -2, 1f))
        }
        return row
    }

    private fun buildPanels(): View {
        panels = LinkedHashMap()
        val holder = FrameLayout(this).apply { setBackgroundColor(C.PANEL) }
        panels["color"] = buildColorPanel()
        panels["frames"] = buildFramesPanel()
        panels["layers"] = buildLayersPanel()
        panels["palette"] = buildPalettePanel()
        for (p in panels.values) { holder.addView(scrollOf(p)) }
        return holder
    }

    private fun scrollOf(child: View): ScrollView =
        ScrollView(this).apply { addView(child); isFillViewport = false; tag = child }

    private fun buildColorPanel(): LinearLayout {
        val col = vbox().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val top = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        swatch = View(this).apply { background = round(color, dp(6).toFloat(), 2, C.TEXT) }
        top.addView(swatch, lp(dp(56), dp(56)))
        hexField = field(Argb.toHex(color)).apply {
            setSingleLine(true)
            setTextColor(C.TEXT)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (updatingColor) return
                    parseHex(s?.toString().orEmpty())?.let { setColor(it, fromHex = true) }
                }
            })
        }
        top.addView(hexField, lp(0, -2, 1f).margins(dp(8), 0, 0, 0))
        top.addView(button("+ Palette", C.PANEL2) { doc.addPaletteColor(color); refreshPalette(); toast("Added to palette") }, lp(-2, -2))
        col.addView(top, lp(-1, -2))
        rSeek = channelSeek { updateFromSliders() }
        gSeek = channelSeek { updateFromSliders() }
        bSeek = channelSeek { updateFromSliders() }
        aSeek = channelSeek { updateFromSliders() }
        col.addView(rowOf("R", rSeek), lp(-1, -2))
        col.addView(rowOf("G", gSeek), lp(-1, -2))
        col.addView(rowOf("B", bSeek), lp(-1, -2))
        col.addView(rowOf("A", aSeek), lp(-1, -2))
        val hsv = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        hsv.addView(button("Darker", C.PANEL2) { nudge(-0.1f) }, lp(0, -2, 1f))
        hsv.addView(button("Lighter", C.PANEL2) { nudge(0.1f) }, lp(0, -2, 1f).margins(dp(4), 0, 0, 0))
        hsv.addView(button("Hue +30", C.PANEL2) { hueShift(30f) }, lp(0, -2, 1f).margins(dp(4), 0, 0, 0))
        hsv.addView(button("Desat", C.PANEL2) { desat(0.15f) }, lp(0, -2, 1f).margins(dp(4), 0, 0, 0))
        col.addView(hsv, lp(-1, -2).margins(0, dp(6), 0, 0))
        return col
    }

    private fun channelSeek(onChange: () -> Unit): SeekBar =
        SeekBar(this).apply {
            max = 255
            setOnSeekBarChangeListener(seekListener { if (!updatingColor) onChange() })
        }

    private fun rowOf(name: String, seek: SeekBar): LinearLayout =
        hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(name, 12f, C.DIM), lp(dp(20), -2))
            addView(seek, lp(0, -2, 1f))
        }

    private fun buildFramesPanel(): LinearLayout {
        val col = vbox().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val actions = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        playBtn = button("▶ Play", C.GREEN, 0xFFFFFFFF.toInt()) { togglePlay() }.apply { textSize = 12f }
        actions.addView(playBtn, lp(dp(96), dp(36)))
        actions.addView(icon(R.drawable.ic_frame_add, "Add frame") { doc.addFrame(copy = false); afterStructural() }, lp(dp(40), dp(40)))
        actions.addView(icon(R.drawable.ic_frame_copy, "Duplicate frame") { doc.addFrame(copy = true); afterStructural() }, lp(dp(40), dp(40)))
        actions.addView(icon(R.drawable.ic_frame_remove, "Delete frame") {
            if (doc.frameCount <= 1) toast("A sprite needs at least one frame")
            else { doc.deleteFrame(doc.activeFrame); afterStructural() }
        }, lp(dp(40), dp(40)))
        actions.addView(icon(R.drawable.ic_chevron_left, "Move frame left") { moveActive(-1) }, lp(dp(40), dp(40)))
        actions.addView(icon(R.drawable.ic_chevron_right, "Move frame right") { moveActive(1) }, lp(dp(40), dp(40)))
        col.addView(actions, lp(-1, -2))

        val settings = hbox().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        fpsField = field(fmt(doc.fps)).apply { setSingleLine(true); setTextColor(C.TEXT); hint = "fps" }
        fpsField.addTextChangedListener(textWatch { if (!updatingFields) { it.toFloatOrNull()?.let { v -> doc.fps = v.coerceIn(1f, 60f); markDirty() } } })
        durationField = field("100").apply { setSingleLine(true); setTextColor(C.TEXT); hint = "ms" }
        durationField.addTextChangedListener(textWatch { if (!updatingFields) { it.toIntOrNull()?.let { v -> doc.setFrameDuration(doc.activeFrame, v); markDirty(); refreshFrameChips() } } })
        loopBox = CheckBox(this).apply { text = "Loop"; setTextColor(C.TEXT); isChecked = doc.loop; setOnCheckedChangeListener { _, on -> doc.loop = on; markDirty() } }
        settings.addView(label("FPS", 12f, C.DIM), lp(-2, -2))
        settings.addView(fpsField, lp(dp(64), -2).margins(dp(4), 0, dp(8), 0))
        settings.addView(label("Frame ms", 12f, C.DIM), lp(-2, -2))
        settings.addView(durationField, lp(dp(70), -2).margins(dp(4), 0, dp(8), 0))
        settings.addView(loopBox, lp(-2, -2))
        col.addView(settings, lp(-1, -2))

        val body = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        frameStrip = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(frameStrip) }, lp(0, dp(80), 1f))
        previewView = ImageView(this).apply { setBackgroundColor(0xFF15171B.toInt()); scaleType = ImageView.ScaleType.FIT_CENTER }
        body.addView(previewView, lp(dp(96), dp(96)).margins(dp(6), 0, 0, 0))
        col.addView(body, lp(-1, -2))
        return col
    }

    private fun buildLayersPanel(): LinearLayout {
        val col = vbox().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val actions = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(label("Layers in this frame", 12f, C.DIM), lp(0, -2, 1f))
        actions.addView(icon(R.drawable.ic_layer_add, "Add layer") { doc.addLayer(); afterStructural() }, lp(dp(40), dp(40)))
        actions.addView(icon(R.drawable.ic_merge_down, "Merge down") {
            if (doc.activeLayer == 0) toast("Nothing below to merge into") else { doc.mergeDown(doc.activeLayer); afterStructural() }
        }, lp(dp(40), dp(40)))
        col.addView(actions, lp(-1, -2))
        layerList = vbox()
        col.addView(layerList, lp(-1, -2))
        return col
    }

    private fun buildPalettePanel(): LinearLayout {
        val col = vbox().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
        col.addView(label("Tap a swatch to use it. Long-press to remove.", 12f, C.DIM), lp(-1, -2))
        paletteBox = vbox()
        col.addView(paletteBox, lp(-1, -2))
        return col
    }

    // ------------------------------------------------------------------ actions

    private fun icon(res: Int, desc: String, onClick: () -> Unit): ImageView =
        ImageView(this).apply {
            setImageResource(res)
            setColorFilter(C.TEXT)
            contentDescription = desc
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = round(C.PANEL2, dp(8).toFloat())
            setOnClickListener { onClick() }
        }

    private fun selectTool(t: Tool) {
        canvas.tool = t
        if (t == Tool.EYEDROPPER) toast("Tap the canvas to pick a colour")
        refreshTools()
    }

    private fun showTab() {
        for ((k, v) in panels) v.parent?.let { (it as View).visibility = if (k == tab) View.VISIBLE else View.GONE }
        for ((k, v) in tabs) {
            v.setTextColor(if (k == tab) C.TEXT else C.DIM)
            v.setBackgroundColor(if (k == tab) C.PANEL else C.HEADER)
        }
        if (tab == "layers") refreshLayers()
        if (tab == "palette") refreshPalette()
        if (tab == "frames") refreshFrameChips()
    }

    private fun setColor(c: Int, fromHex: Boolean = false) {
        color = c
        canvas.color = c
        updatingColor = true
        rSeek.progress = Argb.r(c); gSeek.progress = Argb.g(c); bSeek.progress = Argb.b(c); aSeek.progress = Argb.a(c)
        if (!fromHex) hexField.setText(Argb.toHex(c))
        updatingColor = false
        swatch.background = round(c, dp(6).toFloat(), 2, C.TEXT)
        refreshStatus()
    }

    private fun updateFromSliders() {
        val c = Argb.of(aSeek.progress, rSeek.progress, gSeek.progress, bSeek.progress)
        color = c
        canvas.color = c
        updatingColor = true
        hexField.setText(Argb.toHex(c))
        updatingColor = false
        swatch.background = round(c, dp(6).toFloat(), 2, C.TEXT)
        refreshStatus()
    }

    private fun parseHex(s: String): Int? = Argb.parse(s)

    private fun nudge(amount: Float) {
        val (h, s, v) = rgbToHsv(color)
        val nv = (v + amount).coerceIn(0f, 1f)
        setColor(hsvToRgb(h, s, nv, Argb.a(color)))
    }

    private fun hueShift(deg: Float) {
        val (h, s, v) = rgbToHsv(color)
        setColor(hsvToRgb((h + deg + 360f) % 360f, s, v, Argb.a(color)))
    }

    private fun desat(amount: Float) {
        val (h, s, v) = rgbToHsv(color)
        setColor(hsvToRgb(h, (s - amount).coerceIn(0f, 1f), v, Argb.a(color)))
    }

    private fun moveActive(d: Int) {
        val to = doc.activeFrame + d
        if (to !in 0 until doc.frameCount) return
        doc.moveFrame(doc.activeFrame, to)
        afterStructural()
    }

    private fun togglePlay() = if (playing) stopPlay() else startPlay()

    private fun startPlay() {
        if (doc.frameCount < 2) { toast("Add a second frame to preview motion"); return }
        playing = true
        playBtn.text = "❚❚ Pause"
        playIndex = doc.activeFrame
        handler.post(ticker)
    }

    private fun stopPlay() {
        if (!playing && !::playBtn.isInitialized) return
        playing = false
        if (::playBtn.isInitialized) playBtn.text = "▶ Play"
        handler.removeCallbacks(ticker)
        if (::previewView.isInitialized) previewFrame(doc.activeFrame)
    }

    private fun previewFrame(i: Int) {
        previewView.setImageBitmap(renderFrame(i))
    }

    /** One frame flattened to a bitmap at native sprite resolution. */
    private fun renderFrame(i: Int): Bitmap {
        val px = doc.composite(i)
        return Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(px, 0, doc.width, 0, 0, doc.width, doc.height)
        }
    }

    private fun thumbFor(i: Int, size: Int): Bitmap {
        val src = renderFrame(i)
        val s = size.toFloat() / maxOf(doc.width, doc.height)
        val w = maxOf(1, (doc.width * s).toInt()); val h = maxOf(1, (doc.height * s).toInt())
        return Bitmap.createScaledBitmap(src, w, h, false)
    }

    private fun showLayerTools() {
        val items = arrayOf(
            "Flip horizontal", "Flip vertical", "Rotate 90°", "Outline (current colour)",
            "Clear layer", "Remap to palette (this frame)", "Remap to palette (all frames)",
            "Colour grade…", "Rename layer…"
        )
        MaterialAlertDialogBuilder(this).setTitle("Layer tools").setItems(items) { _, i ->
            when (i) {
                0 -> doc.flipLayer(true)
                1 -> doc.flipLayer(false)
                2 -> doc.rotateLayer90()
                3 -> doc.outline(color)
                4 -> doc.clearLayer()
                5 -> doc.remapToPalette(false)
                6 -> doc.remapToPalette(true)
                7 -> showGrade()
                8 -> showRename()
            }
            afterDocChange()
        }.show()
    }

    private fun showGrade() {
        val col = vbox().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val hue = field("0").apply { hint = "Hue shift (degrees)" }
        val sat = field("1.0").apply { hint = "Saturation (0–2)" }
        val bri = field("1.0").apply { hint = "Brightness (0–2)" }
        val con = field("1.0").apply { hint = "Contrast (0–2)" }
        val all = CheckBox(this).apply { text = "All frames"; setTextColor(C.TEXT) }
        listOf(hue, sat, bri, con).forEach { col.addView(it, lp(-1, -2)) }
        col.addView(all, lp(-1, -2))
        MaterialAlertDialogBuilder(this).setTitle("Colour grade").setView(col)
            .setPositiveButton("Apply") { _, _ ->
                doc.pushUndo("Colour grade")
                doc.grade(
                    hue.text.toString().toFloatOrNull() ?: 0f,
                    sat.text.toString().toFloatOrNull() ?: 1f,
                    bri.text.toString().toFloatOrNull() ?: 1f,
                    con.text.toString().toFloatOrNull() ?: 1f,
                    all.isChecked,
                )
                afterDocChange()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showRename() {
        val input = field(doc.currentLayer.name).apply { setSingleLine(true); setTextColor(C.TEXT) }
        MaterialAlertDialogBuilder(this).setTitle("Layer name").setView(vbox().apply { setPadding(dp(16), dp(8), dp(16), dp(8)); addView(input, lp(-1, -2)) })
            .setPositiveButton("Rename") { _, _ ->
                doc.currentLayer.name = input.text.toString().trim().ifBlank { doc.currentLayer.name }
                markDirty(); refreshLayers()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showResize() {
        val col = vbox().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val w = field(doc.width.toString()).apply { hint = "Width (1–${SpriteDocument.MAX_SIZE})" }
        val h = field(doc.height.toString()).apply { hint = "Height (1–${SpriteDocument.MAX_SIZE})" }
        col.addView(w, lp(-1, -2)); col.addView(h, lp(-1, -2))
        MaterialAlertDialogBuilder(this).setTitle("Canvas size").setView(col)
            .setMessage("Existing pixels stay centred. Larger canvases add transparent space.")
            .setPositiveButton("Resize") { _, _ ->
                val nw = w.text.toString().toIntOrNull(); val nh = h.text.toString().toIntOrNull()
                if (nw == null || nh == null || nw !in 1..SpriteDocument.MAX_SIZE || nh !in 1..SpriteDocument.MAX_SIZE) {
                    toast("Size must be 1–${SpriteDocument.MAX_SIZE}")
                } else {
                    doc.resizeCanvas(nw, nh, 0.5f, 0.5f)
                    canvas.doc = doc
                    canvas.fitToView()
                    afterDocChange()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun askImportMode(uri: android.net.Uri) {
        val items = arrayOf("New layer, fit inside canvas", "New layer, stretch to canvas", "Replace active layer, fit inside canvas")
        MaterialAlertDialogBuilder(this).setTitle("Upload image").setItems(items) { _, i ->
            val px = decodeImage(uri) ?: return@setItems
            val (pixels, sw, sh) = px
            when (i) {
                0 -> doc.importAsLayer(pixels, sw, sh, FitMode.CONTAIN, "Upload")
                1 -> doc.importAsLayer(pixels, sw, sh, FitMode.STRETCH, "Upload")
                2 -> doc.importIntoActiveLayer(pixels, sw, sh, FitMode.CONTAIN)
            }
            afterStructural()
            toast("Image added")
        }.show()
    }

    /** Decodes an image to ARGB pixels, downsampling very large images to keep memory bounded. */
    private fun decodeImage(uri: android.net.Uri): Triple<IntArray, Int, Int>? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
        if (bmp == null) { toast("That file is not a supported image"); null } else {
            val w = bmp.width; val h = bmp.height
            val out = IntArray(w * h)
            bmp.getPixels(out, 0, w, 0, 0, w, h)
            bmp.recycle()
            Triple(out, w, h)
        }
    } catch (e: Exception) {
        toast("Could not read image: ${e.message}"); null
    }

    private fun exportAnimation() {
        if (!saveSprite()) return
        val base = currentName()
        val n = doc.frameCount
        val sheetW = doc.width * n
        val sheet = Bitmap.createBitmap(sheetW, doc.height, Bitmap.Config.ARGB_8888)
        for (i in 0 until n) {
            val f = renderFrame(i)
            val row = IntArray(doc.width * doc.height)
            f.getPixels(row, 0, doc.width, 0, 0, doc.width, doc.height)
            sheet.setPixels(row, 0, sheetW, i * doc.width, 0, doc.width, doc.height)
            f.recycle()
        }
        val bytes = ByteArrayOutputStream().also { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        sheet.recycle()
        val sheetName = project.uniqueAssetName(AssetFolders.join(AssetFolders.STUDIO.path, "$base.png"))
        project.writeAssetBytes(sheetName, bytes)
        val clip = AnimationClip(
            texture = sheetName,
            columns = n,
            rows = 1,
            frames = MutableList(n) { it },
            fps = doc.fps,
            loop = doc.loop,
        )
        val animName = project.uniqueAssetName("$base.anim")
        project.writeAsset(animName, clip.toJson().toString(2))
        MaterialAlertDialogBuilder(this).setTitle("Exported to animation")
            .setMessage("Sheet: $sheetName\nClip: $animName\n\n$n frames at ${fmt(doc.fps)} fps.")
            .setPositiveButton("Open in Animation Editor") { _, _ ->
                startActivity(android.content.Intent(this, AnimationEditorActivity::class.java)
                    .putExtra("project", project.dir.name)
                    .putExtra("asset", animName))
            }
            .setNegativeButton("Close", null).show()
    }

    /** Writes the `.sprite` source. Returns false if the write failed. */
    private fun saveSprite(): Boolean = try {
        val name = currentName()
        val path = asset ?: project.uniqueAssetName(AssetFolders.join(AssetFolders.STUDIO.path, "$name.sprite"))
        asset = path
        project.writeAsset(path, SpriteFile.encode(doc))
        dirty = false
        refreshStatus()
        toast("Saved ${AssetFolders.nameOf(path)}")
        true
    } catch (e: Exception) {
        toast("Save failed: ${e.message}"); false
    }

    // ------------------------------------------------------------------ refresh

    private fun currentName(): String = if (::nameField.isInitialized) nameField.text.toString().ifBlank { "sprite" } else doc.name

    private fun markDirty() { dirty = true }

    private fun afterDocChange() {
        canvas.doc = doc
        canvas.pixelsChanged()
        dirty = true
        refreshAll()
    }

    private fun afterStructural() {
        canvas.doc = doc
        canvas.pixelsChanged()
        dirty = true
        refreshAll()
    }

    private fun refreshAll() {
        if (::undoBtn.isInitialized) undoRedoState()
        refreshTools()
        refreshOptions()
        refreshStatus()
        refreshFrameChips()
        refreshLayers()
        refreshPalette()
        previewFrame(doc.activeFrame)
        canvas.invalidate()
    }

    private fun undoRedoState() {
        undoBtn.alpha = if (doc.canUndo) 1f else 0.35f
        redoBtn.alpha = if (doc.canRedo) 1f else 0.35f
    }

    private fun refreshTools() {
        if (!::toolIcons.isInitialized) return
        for ((t, v) in toolIcons) v.background = round(if (canvas.tool == t) C.SEL else C.PANEL2, dp(8).toFloat())
    }

    private fun refreshOptions() {
        if (!::optionRow.isInitialized) return
        chips.forEach { c ->
            @Suppress("UNCHECKED_CAST")
            val on = c.tag as () -> Boolean
            c.background = round(if (on()) C.SEL else C.PANEL2, dp(6).toFloat())
        }
        shadeBtn.text = if (canvas.shadeSign >= 0) "Lighten" else "Darken"
        shadeBtn.visibility = if (canvas.tool == Tool.SHADE) View.VISIBLE else View.GONE
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val layer = doc.currentLayer.name
        status.text = "${doc.width}×${doc.height} · frame ${doc.activeFrame + 1}/${doc.frameCount} · layer $layer · " +
            "zoom ${canvas.zoomLabel()} · ${canvas.tool.label} · ${Argb.toHex(color)}" + if (dirty) " · unsaved" else ""
    }

    private fun refreshFrameChips() {
        if (!::frameStrip.isInitialized) return
        frameStrip.removeAllViews()
        for (i in 0 until doc.frameCount) {
            val box = vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = round(if (i == doc.activeFrame) C.SEL else C.PANEL2, dp(6).toFloat())
                setOnClickListener {
                    if (playing) stopPlay()
                    doc.selectFrame(i)
                    refreshFrameChips(); refreshLayers(); refreshStatus(); canvas.pixelsChanged()
                    updatingFields = true
                    durationField.setText(doc.frames[i].durationMs.toString())
                    updatingFields = false
                }
            }
            val thumb = ImageView(this).apply {
                setImageBitmap(thumbFor(i, dp(48)))
                setBackgroundColor(0xFF15171B.toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            box.addView(thumb, lp(dp(56), dp(56)))
            box.addView(label("${i + 1} · ${doc.frames[i].durationMs}ms", 10f, C.TEXT), lp(-2, -2))
            frameStrip.addView(box, lp(-2, -2).margins(dp(3), 0, dp(3), 0))
        }
        updatingFields = true
        durationField.setText(doc.currentFrame.durationMs.toString())
        updatingFields = false
    }

    private fun refreshLayers() {
        if (!::layerList.isInitialized) return
        layerList.removeAllViews()
        val layers = doc.currentFrame.layers
        for (i in layers.indices.reversed()) {
            val l = layers[i]
            val row = hbox().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(2), dp(4), dp(2))
                background = round(if (i == doc.activeLayer) C.SEL else C.PANEL2, dp(6).toFloat())
                setOnClickListener { doc.selectLayer(i); refreshLayers(); refreshStatus() }
            }
            row.addView(icon(if (l.visible) R.drawable.ic_eye else R.drawable.ic_eye_off, "Toggle visibility") { doc.toggleLayerVisible(i); afterStructural() }, lp(dp(36), dp(36)))
            row.addView(icon(R.drawable.ic_lock, if (l.locked) "Unlock" else "Lock") { l.locked = !l.locked; markDirty(); refreshLayers() }
                .apply { alpha = if (l.locked) 1f else 0.35f }, lp(dp(36), dp(36)))
            row.addView(label(l.name, 13f, C.TEXT), lp(0, -2, 1f).margins(dp(6), 0, 0, 0))
            row.addView(icon(R.drawable.ic_duplicate, "Duplicate layer") { doc.duplicateLayer(i); afterStructural() }, lp(dp(36), dp(36)))
            row.addView(icon(R.drawable.ic_chevron_up, "Move layer up") { if (i < layers.size - 1) { doc.moveLayer(i, i + 1); afterStructural() } }, lp(dp(36), dp(36)))
            row.addView(icon(R.drawable.ic_chevron_down, "Move layer down") { if (i > 0) { doc.moveLayer(i, i - 1); afterStructural() } }, lp(dp(36), dp(36)))
            row.addView(icon(R.drawable.ic_trash, "Delete layer") {
                if (layers.size <= 1) toast("A frame needs at least one layer") else { doc.deleteLayer(i); afterStructural() }
            }, lp(dp(36), dp(36)))
            layerList.addView(row, lp(-1, -2).margins(0, dp(2), 0, dp(2)))
        }
    }

    private fun refreshPalette() {
        if (!::paletteBox.isInitialized) return
        paletteBox.removeAllViews()
        val cols = 8
        val colors = doc.palette.toList()
        var i = 0
        while (i < colors.size) {
            val rowBox = hbox().apply { setPadding(0, dp(2), 0, dp(2)) }
            for (j in 0 until cols) {
                val idx = i + j
                if (idx >= colors.size) break
                val c = colors[idx]
                rowBox.addView(View(this).apply {
                    background = round(c, dp(4).toFloat(), 2, if (c == color) C.ACCENT else C.PANEL2)
                    setOnClickListener { setColor(c) }
                    setOnLongClickListener { doc.removePaletteColor(c); refreshPalette(); markDirty(); true }
                }, lp(0, dp(34), 1f).margins(dp(2), 0, dp(2), 0))
            }
            paletteBox.addView(rowBox, lp(-1, -2))
            i += cols
        }
        if (colors.isEmpty()) paletteBox.addView(label("Palette is empty. Use + Palette on the colour tab.", 12f, C.DIM))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_PROJECT = "project"
        const val EXTRA_ASSET = "asset"

        /** Starts the Studio on a new sprite in [project]. */
        fun newIntent(ctx: Context, project: String): android.content.Intent =
            android.content.Intent(ctx, SpriteEditorActivity::class.java).putExtra(EXTRA_PROJECT, project)

        /** Opens an existing `.sprite` asset. */
        fun openIntent(ctx: Context, project: String, asset: String): android.content.Intent =
            newIntent(ctx, project).putExtra(EXTRA_ASSET, asset)

        private fun seekListener(fn: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { fn(progress) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }

        private fun textWatch(fn: (String) -> Unit) = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { fn(s?.toString().orEmpty()) }
        }

        private fun rgbToHsv(c: Int): Triple<Float, Float, Float> {
            val r = Argb.r(c) / 255f; val g = Argb.g(c) / 255f; val b = Argb.b(c) / 255f
            val hsv = FloatArray(3)
            Color.RGBToHSV((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv)
            return Triple(hsv[0], hsv[1], hsv[2])
        }

        private fun hsvToRgb(h: Float, s: Float, v: Float, a: Int): Int {
            val rgb = Color.HSVToColor(floatArrayOf(h, s, v))
            return Argb.of(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
        }
    }
}
