package com.sengine.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.core.AssetKind
import com.sengine.engine.render.BitmapData
import com.sengine.engine.render.TextureDecoders
import com.sengine.engine.sprite.SpriteSheetDoc
import com.sengine.engine.sprite.SpriteSheetFrame
import com.sengine.engine.sprite.SpriteSlicer
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import com.sengine.project.SpriteSheetAssets

/**
 * Professional sprite-sheet editor: slice any texture by grid, fixed cell or
 * automatic alpha detection, arrange and rename frames, preview playback and
 * export a packed PNG + named atlas + animation clip + texture profile.
 */
class SpriteSheetEditorActivity : AppCompatActivity() {
    private lateinit var project: Project
    private var asset: String? = null
    private var doc = SpriteSheetDoc()
    private var bitmap: Bitmap? = null
    private var saved = ""

    private var alphaThreshold = SpriteSlicer.DEFAULT_ALPHA
    private var minRegion = SpriteSlicer.DEFAULT_MIN_SIZE
    private var selected = -1

    private lateinit var title: TextView
    private lateinit var sheet: SheetView
    private lateinit var preview: PreviewView
    private lateinit var texBtn: TextView
    private lateinit var info: TextView
    private lateinit var status: TextView
    private lateinit var colsField: EditText
    private lateinit var rowsField: EditText
    private lateinit var cellWField: EditText
    private lateinit var cellHField: EditText
    private lateinit var fpsField: EditText
    private lateinit var alphaField: EditText
    private lateinit var minField: EditText
    private lateinit var loopBox: CheckBox
    private lateinit var frameList: LinearLayout
    private lateinit var modeButtons: Map<String, TextView>
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)
        asset = intent.getStringExtra("asset")

        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(button("←") { onBackPressedDispatcher.onBackPressed() })
        title = label("", 15f, C.TEXT, true).apply { setPadding(dp(10), 0, dp(10), 0); isSingleLine = true }
        bar.addView(title, lp(0, WRAP, 1f))
        bar.addView(button("Open…") { openDialog() }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button("New") { newSheet() }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button("Export") { exportDialog() }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button("Save", C.ACCENT, 0xFFFFFFFF.toInt()) { save() }, lp(WRAP, WRAP).margins(dp(3), 0, 0, 0))
        root.addView(bar, lp(MATCH, WRAP))

        val middle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // ---- left: settings, slicing, frame list
        val left = vbox().apply { setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(C.PANEL) }
        preview = PreviewView(this)
        left.addView(preview, lp(MATCH, dp(150)))
        val playRow = hbox()
        playRow.addView(button("⏯") { preview.playing = !preview.playing }, lp(0, WRAP, 1f).margins(0, dp(4), dp(2), 0))
        playRow.addView(button("⏮") { preview.step(-1) }, lp(0, WRAP, 1f).margins(dp(2), dp(4), dp(2), 0))
        playRow.addView(button("⏭") { preview.step(1) }, lp(0, WRAP, 1f).margins(dp(2), dp(4), 0, 0))
        left.addView(playRow, lp(MATCH, WRAP))
        info = label("", 11f, C.DIM)
        left.addView(info)

        fun row(t: String, v: View) {
            val r = hbox()
            r.addView(label(t, 12f, C.DIM), lp(dp(72), WRAP))
            r.addView(v, lp(0, WRAP, 1f))
            left.addView(r, lp(MATCH, WRAP).margins(0, dp(3), 0, dp(3)))
        }

        texBtn = button("(choose texture)") { chooseTexture() }.apply { textSize = 12f }
        row("Texture", texBtn)

        // slice mode selector
        val modeRow = hbox()
        modeButtons = listOf(
            SpriteSheetDoc.MODE_GRID to "Grid",
            SpriteSheetDoc.MODE_CELL to "Cell",
            SpriteSheetDoc.MODE_MANUAL to "Auto/Manual",
        ).map { (m, label) ->
            val b = button(label) { setMode(m) }.apply { textSize = 11f; setPadding(dp(8), dp(6), dp(8), dp(6)) }
            modeRow.addView(b, lp(0, WRAP, 1f).margins(dp(2), 0, dp(2), 0))
            m to b
        }.toMap()
        left.addView(modeRow, lp(MATCH, WRAP).margins(0, dp(6), 0, dp(2)))

        colsField = field("4", numeric = true); row("Columns", colsField)
        rowsField = field("1", numeric = true); row("Rows", rowsField)
        cellWField = field("32", numeric = true); row("Cell W", cellWField)
        cellHField = field("32", numeric = true); row("Cell H", cellHField)
        alphaField = field("16", numeric = true); row("Alpha ≥", alphaField)
        minField = field("2", numeric = true); row("Min size", minField)
        fpsField = field("10", numeric = true); row("FPS", fpsField)
        loopBox = CheckBox(this).apply { text = "Loop playback"; setTextColor(C.TEXT) }
        left.addView(loopBox)

        val sliceRow = hbox()
        sliceRow.addView(button("Slice") { slice() }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(0, 0, dp(2), 0))
        sliceRow.addView(button("Add Rect") { addRect() }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(dp(2), 0, dp(2), 0))
        sliceRow.addView(button("Clear") { clearFrames() }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(dp(2), 0, 0, 0))
        left.addView(sliceRow, lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        val editRow = hbox()
        editRow.addView(button("Rename") { renameDialog() }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(0, 0, dp(2), 0))
        editRow.addView(button("▲") { moveSelected(-1) }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(dp(2), 0, dp(2), 0))
        editRow.addView(button("▼") { moveSelected(1) }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(dp(2), 0, dp(2), 0))
        editRow.addView(button("Delete") { deleteSelected() }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(dp(2), 0, 0, 0))
        left.addView(editRow, lp(MATCH, WRAP).margins(0, dp(4), 0, 0))

        left.addView(label("Frames", 12f, C.DIM, true).apply { setPadding(0, dp(10), 0, dp(2)) })
        frameList = vbox()
        left.addView(frameList, lp(MATCH, WRAP))
        left.addView(
            label(
                "Tap a frame on the sheet to select it. In Auto/Manual mode tap empty space to add a rect, drag to move, drag the corner handle to resize, pinch to zoom.",
                11f, C.DIM,
            ),
            lp(MATCH, WRAP).margins(0, dp(8), 0, 0),
        )
        middle.addView(ScrollView(this).apply { addView(left) }, lp(dp(280), MATCH))

        // ---- right: sheet view + status
        val right = vbox()
        sheet = SheetView(
            this,
            onSelect = { i -> select(i) },
            onEdited = { refreshList(); updateStatus() },
        )
        right.addView(sheet, lp(MATCH, 0, 1f))
        status = label("", 11f, C.DIM).apply { setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(C.HEADER) }
        right.addView(status, lp(MATCH, WRAP))
        middle.addView(right, lp(0, MATCH, 1f))
        root.addView(middle, lp(MATCH, 0, 1f))
        setContentView(root)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (!updating) readFields() }
        }
        listOf(colsField, rowsField, cellWField, cellHField, fpsField).forEach { it.addTextChangedListener(watcher) }
        listOf(alphaField, minField).forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (updating) return
                    alphaThreshold = (alphaField.text.toString().toIntOrNull() ?: 16).coerceIn(0, 255)
                    minRegion = (minField.text.toString().toIntOrNull() ?: 2).coerceIn(1, 512)
                }
            })
        }
        loopBox.setOnCheckedChangeListener { _, b -> if (!updating) { doc.loop = b; refresh(false) } }

        val a = asset
        if (a != null) load(a) else newSheet(ask = false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (doc.toJson().toString() == saved || asset == null && doc.texture.isBlank()) {
                    finish(); return
                }
                MaterialAlertDialogBuilder(this@SpriteSheetEditorActivity)
                    .setTitle("Unsaved changes").setMessage("Save sprite sheet?")
                    .setPositiveButton("Save") { _, _ -> if (save()) finish() }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Cancel", null).show()
            }
        })
    }

    // ------------------------------------------------------------------ data
    private fun load(name: String) {
        asset = name
        doc = try {
            SpriteSheetDoc.parse(project.readAsset(name) ?: "") ?: SpriteSheetDoc()
        } catch (_: Exception) {
            SpriteSheetDoc()
        }
        saved = doc.toJson().toString()
        selected = -1
        loadBitmap()
        writeFields(); refresh(true)
    }

    private fun newSheet(ask: Boolean = true) {
        if (!ask) {
            asset = null; doc = SpriteSheetDoc(); bitmap = null
            saved = doc.toJson().toString(); selected = -1
            writeFields(); refresh(true); return
        }
        val f = field("NewSpriteSheet")
        MaterialAlertDialogBuilder(this).setTitle("New sprite sheet")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("Create") { _, _ ->
                var n = f.text.toString().trim().replace(Regex("[^A-Za-z0-9_\\-]"), "").ifBlank { "NewSpriteSheet" }
                if (!n.endsWith(".sheet")) n += ".sheet"
                n = project.uniqueAssetName(n)
                doc = SpriteSheetDoc()
                project.writeAsset(n, doc.toJson().toString(2))
                load(n)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun openDialog() {
        val items = project.listAssets(AssetKind.SHEET)
        if (items.isEmpty()) { toast("No sprite sheets yet — tap New"); return }
        MaterialAlertDialogBuilder(this).setTitle("Open sprite sheet")
            .setItems(items.toTypedArray()) { _, i -> load(items[i]) }.show()
    }

    private fun chooseTexture() {
        val items = project.listAssets(AssetKind.TEXTURE)
        if (items.isEmpty()) { toast("Import an image first (Assets → Import Image)"); return }
        MaterialAlertDialogBuilder(this).setTitle("Source texture")
            .setItems(items.toTypedArray()) { _, i -> setTexture(items[i]) }.show()
    }

    private fun setTexture(name: String) {
        if (doc.texture != name) {
            doc.texture = name
            doc.clearFrames()
            selected = -1
        }
        loadBitmap()
        if (bitmap == null && doc.texture.isNotBlank()) { toast("Can't decode ${doc.texture}"); return }
        val b = bitmap
        if (b != null && doc.frames.isEmpty()) {
            when (doc.mode) {
                SpriteSheetDoc.MODE_GRID -> {
                    // Guess a square-cell row layout, e.g. 512x64 -> 8x1.
                    if (b.width >= b.height && b.width % b.height == 0) {
                        doc.columns = b.width / b.height; doc.rows = 1
                    } else if (b.height > b.width && b.height % b.width == 0) {
                        doc.columns = 1; doc.rows = b.height / b.width
                    } else {
                        doc.columns = 4; doc.rows = 1
                    }
                    writeFields(); doc.sliceGrid(b.width, b.height)
                }
                SpriteSheetDoc.MODE_CELL -> {
                    val s = minOf(b.width, b.height).coerceAtLeast(1)
                    doc.cellW = s; doc.cellH = s
                    writeFields(); doc.sliceCell(b.width, b.height)
                }
            }
        }
        writeFields(); refresh(true)
    }

    private fun loadBitmap() {
        bitmap = if (doc.texture.isBlank()) null else try {
            TextureDecoders.loadBitmap(project.assetFile(doc.texture))
        } catch (_: Throwable) {
            null
        }
        sheet.setBitmap(bitmap)
        preview.setBitmap(bitmap)
    }

    private fun setMode(mode: String) {
        doc.mode = mode
        refresh(false)
    }

    private fun slice() {
        val b = bitmap ?: run { toast("Choose a texture first"); return }
        when (doc.mode) {
            SpriteSheetDoc.MODE_GRID -> {
                readFields()
                val n = doc.sliceGrid(b.width, b.height)
                if (n == 0) toast("Grid is larger than the texture")
            }
            SpriteSheetDoc.MODE_CELL -> {
                readFields()
                val n = doc.sliceCell(b.width, b.height)
                if (n == 0) toast("Cell is larger than the texture")
            }
            else -> autoSlice()
        }
        if (selected >= doc.frames.size) selected = doc.frames.size - 1
        refresh(false)
    }

    private fun autoSlice() {
        val b = bitmap ?: run { toast("Choose a texture first"); return }
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val found = SpriteSlicer.autoSlice(px, b.width, b.height, alphaThreshold, minRegion)
        if (found.isEmpty()) { toast("No regions found — lower Alpha or Min size"); return }
        doc.setRects(found)
        selected = 0
        toast("Auto-sliced ${found.size} frames")
    }

    private fun addRect() {
        val b = bitmap ?: run { toast("Choose a texture first"); return }
        doc.mode = SpriteSheetDoc.MODE_MANUAL
        val size = minOf(32, b.width, b.height).coerceAtLeast(1)
        var cx = b.width / 2
        var cy = b.height / 2
        if (selected in doc.frames.indices) {
            val f = doc.frames[selected]
            cx = f.x + f.w / 2
            cy = f.y + f.h / 2
        }
        val x = (cx - size / 2).coerceIn(0, b.width - size)
        val y = (cy - size / 2).coerceIn(0, b.height - size)
        doc.addRect(x, y, size, size)
        selected = doc.frames.size - 1
        refresh(false)
    }

    private fun clearFrames() {
        if (doc.frames.isEmpty()) return
        MaterialAlertDialogBuilder(this).setTitle("Clear all frames?")
            .setMessage("${doc.frames.size} frames will be removed from this sheet.")
            .setPositiveButton("Clear") { _, _ ->
                doc.clearFrames(); selected = -1; refresh(false)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun renameDialog() {
        if (selected !in doc.frames.indices) { toast("Select a frame first"); return }
        val f = field(doc.frames[selected].name)
        MaterialAlertDialogBuilder(this).setTitle("Rename frame")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("Rename") { _, _ ->
                if (!doc.renameFrame(selected, f.text.toString())) toast("Name taken or empty")
                refresh(false)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun moveSelected(delta: Int) {
        if (selected !in doc.frames.indices) { toast("Select a frame first"); return }
        val to = selected + delta
        if (to !in doc.frames.indices) return
        if (doc.moveFrame(selected, to)) { selected = to; refresh(false) }
    }

    private fun deleteSelected() {
        if (selected !in doc.frames.indices) { toast("Select a frame first"); return }
        doc.removeFrame(selected)
        selected = if (doc.frames.isEmpty()) -1 else minOf(selected, doc.frames.size - 1)
        refresh(false)
    }

    private fun select(i: Int) {
        selected = i
        sheet.setSelected(i)
        refreshList()
        updateStatus()
    }

    private fun readFields() {
        doc.columns = (colsField.text.toString().toIntOrNull() ?: 4).coerceIn(1, 256)
        doc.rows = (rowsField.text.toString().toIntOrNull() ?: 1).coerceIn(1, 256)
        doc.cellW = (cellWField.text.toString().toIntOrNull() ?: 32).coerceIn(1, 4096)
        doc.cellH = (cellHField.text.toString().toIntOrNull() ?: 32).coerceIn(1, 4096)
        doc.fps = (fpsField.text.toString().toFloatOrNull() ?: 10f).coerceIn(0.1f, 120f)
        sheet.invalidate()
    }

    private fun writeFields() {
        updating = true
        colsField.setText(doc.columns.toString())
        rowsField.setText(doc.rows.toString())
        cellWField.setText(doc.cellW.toString())
        cellHField.setText(doc.cellH.toString())
        fpsField.setText(fmt(doc.fps))
        alphaField.setText(alphaThreshold.toString())
        minField.setText(minRegion.toString())
        loopBox.isChecked = doc.loop
        updating = false
    }

    private fun refresh(full: Boolean) {
        title.text = "Sprite Sheet: " + (asset ?: "(unsaved)")
        texBtn.text = doc.texture.ifBlank { "(choose texture)" }
        updating = true
        modeButtons.forEach { (m, b) ->
            b.setButtonColor(if (doc.mode == m) C.ACCENT else C.PANEL2)
            b.setTextColor(if (doc.mode == m) 0xFFFFFFFF.toInt() else C.TEXT)
        }
        updating = false
        // grid controls only apply to their modes
        val gridMode = doc.mode == SpriteSheetDoc.MODE_GRID
        val cellMode = doc.mode == SpriteSheetDoc.MODE_CELL
        setEnabledRow(colsField, gridMode); setEnabledRow(rowsField, gridMode)
        setEnabledRow(cellWField, cellMode); setEnabledRow(cellHField, cellMode)
        sheet.setDoc(doc, doc.mode, selected)
        preview.setDoc(doc)
        info.text = buildString {
            append("${doc.frames.size} frames")
            bitmap?.let { append(" • source ${it.width}×${it.height}") }
            append(" • ${fmt(doc.durationHint())} s")
        }
        refreshList()
        updateStatus()
        if (full) { preview.restart(); sheet.fit() }
    }

    private fun setEnabledRow(v: View, enabled: Boolean) {
        v.alpha = if (enabled) 1f else 0.4f
        v.isEnabled = enabled
    }

    private fun refreshList() {
        frameList.removeAllViews()
        doc.frames.forEachIndexed { i, f ->
            val r = hbox().apply {
                background = round(if (i == selected) C.SEL else C.PANEL2, dp(4).toFloat())
                setPadding(dp(6), dp(3), dp(4), dp(3))
            }
            val nameView = label(f.name, 12f, if (i == selected) 0xFFFFFFFF.toInt() else C.TEXT).apply {
                isSingleLine = true
                setOnClickListener { select(i) }
                setOnLongClickListener { select(i); renameDialog(); true }
            }
            r.addView(nameView, lp(0, WRAP, 1f))
            r.addView(label("${f.w}×${f.h}", 10f, C.DIM).apply { setPadding(dp(4), 0, dp(4), 0) }, lp(WRAP, WRAP))
            r.addView(button("▲") { moveFrameTo(i, -1) }.apply { textSize = 10f; setPadding(dp(6), dp(2), dp(6), dp(2)) }, lp(WRAP, WRAP))
            r.addView(button("▼") { moveFrameTo(i, 1) }.apply { textSize = 10f; setPadding(dp(6), dp(2), dp(6), dp(2)) }, lp(WRAP, WRAP))
            r.addView(button("✕") {
                doc.removeFrame(i)
                if (selected == i) selected = if (doc.frames.isEmpty()) -1 else minOf(i, doc.frames.size - 1)
                else if (selected > i) selected--
                refresh(false)
            }.apply { textSize = 10f; setPadding(dp(6), dp(2), dp(6), dp(2)); setTextColor(C.RED) }, lp(WRAP, WRAP))
            frameList.addView(r, lp(MATCH, WRAP).margins(0, dp(2), 0, dp(2)))
        }
        if (doc.frames.isEmpty()) {
            frameList.addView(label("No frames yet — pick a texture and slice.", 11f, C.DIM))
        }
    }

    private fun moveFrameTo(i: Int, delta: Int) {
        val to = i + delta
        if (to !in doc.frames.indices) return
        if (doc.moveFrame(i, to)) {
            if (selected == i) selected = to else if (selected == to) selected = i
            refresh(false)
        }
    }

    private fun updateStatus() {
        val b = bitmap
        val zoom = (sheet.scale * 100).toInt()
        status.text = buildString {
            append(if (b != null) "${b.width}×${b.height}" else "no texture")
            append(" • ${doc.frames.size} frames")
            append(" • zoom $zoom%")
            if (selected in doc.frames.indices) append(" • selected: ${doc.frames[selected].name}")
        }
    }

    private fun exportDialog() {
        if (doc.frames.isEmpty()) { toast("Slice some frames first"); return }
        val b = bitmap ?: run { toast("Choose a texture first"); return }
        val nameField = field((asset ?: doc.texture.substringBeforeLast('.', "SpriteSheet")).substringBeforeLast('.'))
        val padField = field("1", numeric = true)
        val potBox = CheckBox(this).apply { text = "Power-of-two sheet size"; isChecked = true; setTextColor(C.TEXT) }
        val box = vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        box.addView(label("Asset name (stem)", 12f, C.DIM)); box.addView(nameField, lp(MATCH, WRAP).margins(0, dp(2), 0, dp(6)))
        box.addView(label("Padding (px)", 12f, C.DIM)); box.addView(padField, lp(MATCH, WRAP).margins(0, dp(2), 0, dp(6)))
        box.addView(potBox)
        box.addView(label("Exports ${'$'}stem.png, ${'$'}stem.atlas, ${'$'}stem.anim and the .sheet document.", 11f, C.DIM).apply { setPadding(0, dp(6), 0, 0) })
        MaterialAlertDialogBuilder(this).setTitle("Export sprite sheet")
            .setView(box)
            .setPositiveButton("Export") { _, _ ->
                val stem = nameField.text.toString().trim().replace(Regex("[^A-Za-z0-9_\\-]"), "").ifBlank { "SpriteSheet" }
                val padding = (padField.text.toString().toIntOrNull() ?: 1).coerceIn(0, 32)
                doExport(stem, padding, potBox.isChecked, b)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun doExport(stem: String, padding: Int, powerOfTwo: Boolean, b: Bitmap) {
        try {
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            val target = asset ?: project.uniqueAssetName("$stem.sheet").also { asset = it }
            val r = SpriteSheetAssets.export(
                project, doc, target,
                BitmapData(b.width, b.height, px), padding, powerOfTwo,
            )
            saved = doc.toJson().toString()
            refresh(false)
            MaterialAlertDialogBuilder(this).setTitle("Exported")
                .setMessage(
                    "${r.png} — packed ${r.width}×${r.height} sheet with ${r.frameCount} frames\n" +
                        "${r.atlas} — named UV + pixel rectangles\n" +
                        "${r.animation} — named-frame animation clip\n" +
                        "${r.textureProfile} — nearest filtering\n" +
                        "${r.sheet} — document saved",
                )
                .setPositiveButton("OK", null).show()
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
    }

    private fun save(): Boolean {
        var name = asset
        if (name == null) {
            val stem = doc.texture.substringBeforeLast('.').ifBlank { "SpriteSheet" }
            name = project.uniqueAssetName("$stem.sheet")
            asset = name
        }
        val error = com.sengine.engine.core.AssetValidator.validate(name, doc.toJson().toString())
        if (error != null) { toast("Invalid sheet: $error"); return false }
        project.writeAsset(name, doc.toJson().toString(2))
        saved = doc.toJson().toString()
        refresh(false)
        toast("Saved $name")
        return true
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun SpriteSheetDoc.durationHint(): Float = if (fps <= 0f) 0f else frames.size / fps

    // ------------------------------------------------------------------ views

    companion object {
        fun drawChecker(c: Canvas, r: RectF, size: Float, p: Paint) {
            var y = r.top
            var yi = 0
            while (y < r.bottom) {
                var x = r.left
                var xi = yi
                while (x < r.right) {
                    p.color = if (xi % 2 == 0) 0xFF3A3D42.toInt() else 0xFF2E3136.toInt()
                    c.drawRect(x, y, minOf(x + size, r.right), minOf(y + size, r.bottom), p)
                    x += size; xi++
                }
                y += size; yi++
            }
        }
    }

    /** Zoomable, pannable sheet canvas with frame hit-testing and manual rect editing. */
    @SuppressLint("ViewConstructor")
    class SheetView(
        ctx: Context,
        private val onSelect: (Int) -> Unit,
        private val onEdited: () -> Unit,
    ) : View(ctx) {
        private var bmp: Bitmap? = null
        private var doc = SpriteSheetDoc()
        private var mode = SpriteSheetDoc.MODE_GRID
        private var selected = -1
        var scale = 1f; private set
        private var tx = 0f
        private var ty = 0f
        private var fitScale = 1f
        private var lastBmp: Bitmap? = null

        private val p = Paint().apply { isFilterBitmap = false }
        private val line = Paint().apply { color = 0x88FFFFFF.toInt(); strokeWidth = 1f; style = Paint.Style.STROKE }
        private val framePaint = Paint().apply { color = 0xAAFFFFFF.toInt(); strokeWidth = 2f; style = Paint.Style.STROKE }
        private val selFill = Paint().apply { color = 0x444C8DFF }
        private val selPaint = Paint().apply { color = 0xFFFFD54F.toInt(); strokeWidth = 2.5f; style = Paint.Style.STROKE }
        private val handlePaint = Paint().apply { color = 0xFFFFD54F.toInt(); style = Paint.Style.FILL }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 11f * resources.displayMetrics.density; isFakeBoldText = true
        }
        private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9AA0A6.toInt(); textSize = 13f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER
        }
        private val dst = RectF()

        // gesture state
        private var modeGesture = GESTURE_NONE
        private var lastX = 0f
        private var lastY = 0f
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var lastTapTime = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f
        private var pinchDist = 0f
        private var activeFrame = -1

        fun setBitmap(b: Bitmap?) {
            bmp = b
            if (b !== lastBmp) { lastBmp = b; fit() }
            invalidate()
        }

        fun setDoc(d: SpriteSheetDoc, m: String, sel: Int) {
            doc = d; mode = m; selected = sel
            invalidate()
        }

        fun setSelected(sel: Int) { selected = sel; invalidate() }
        fun setScale(s: Float) { scale = s }

        fun fit() {
            val b = bmp ?: return
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            fitScale = minOf((w - 24f) / b.width, (h - 24f) / b.height).coerceAtLeast(0.01f)
            scale = fitScale
            tx = (w - b.width * scale) / 2f
            ty = (h - b.height * scale) / 2f
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            fit()
        }

        private fun toWorld(sx: Float, sy: Float): Pair<Float, Float> = Pair((sx - tx) / scale, (sy - ty) / scale)

        private fun frameAt(wx: Float, wy: Float): Int {
            for (i in doc.frames.indices.reversed()) {
                val f = doc.frames[i]
                if (wx >= f.x && wx < f.right && wy >= f.y && wy < f.bottom) return i
            }
            return -1
        }

        private fun handleHit(sx: Float, sy: Float): Boolean {
            if (selected !in doc.frames.indices || mode != SpriteSheetDoc.MODE_MANUAL) return false
            val f = doc.frames[selected]
            val hx = tx + f.right * scale
            val hy = ty + f.bottom * scale
            val r = 16f * resources.displayMetrics.density
            return sx >= hx - r && sx <= hx + r && sy >= hy - r && sy <= hy + r
        }

        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF1B1D21.toInt())
            val b = bmp
            if (b == null) {
                c.drawText("Choose a source texture", width / 2f, height / 2f, hint)
                return
            }
            val w = b.width * scale
            val h = b.height * scale
            dst.set(tx, ty, tx + w, ty + h)
            drawChecker(c, dst, 12f * resources.displayMetrics.density, p)
            c.drawBitmap(b, null, dst, p)

            // slicing grid overlay
            if (mode == SpriteSheetDoc.MODE_GRID) {
                val cols = doc.columns.coerceAtLeast(1)
                val rows = doc.rows.coerceAtLeast(1)
                for (i in 0..cols) {
                    val x = tx + i * w / cols
                    c.drawLine(x, ty, x, ty + h, line)
                }
                for (j in 0..rows) {
                    val y = ty + j * h / rows
                    c.drawLine(tx, y, tx + w, y, line)
                }
            } else if (mode == SpriteSheetDoc.MODE_CELL && doc.cellW >= 1 && doc.cellH >= 1) {
                var i = 0
                while (i * doc.cellW <= b.width) {
                    val x = tx + i * doc.cellW * scale
                    c.drawLine(x, ty, x, ty + h, line); i++
                }
                i = 0
                while (i * doc.cellH <= b.height) {
                    val y = ty + i * doc.cellH * scale
                    c.drawLine(tx, y, tx + w, y, line); i++
                }
            }

            // frame rectangles + order labels
            doc.frames.forEachIndexed { i, f ->
                val l = tx + f.x * scale
                val t = ty + f.y * scale
                val rgt = tx + f.right * scale
                val btm = ty + f.bottom * scale
                if (i == selected) {
                    c.drawRect(l, t, rgt, btm, selFill)
                    c.drawRect(l, t, rgt, btm, selPaint)
                    val hs = 8f * resources.displayMetrics.density
                    c.drawRect(rgt - hs, btm - hs, rgt + hs, btm + hs, handlePaint)
                } else {
                    c.drawRect(l, t, rgt, btm, framePaint)
                }
                c.drawText("${i + 1}", l + 3f, t + txt.textSize + 1f, txt)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val b = bmp ?: return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; downTime = SystemClock.uptimeMillis()
                    modeGesture = GESTURE_NONE
                    val (wx, wy) = toWorld(e.x, e.y)
                    activeFrame = frameAt(wx, wy)
                    if (handleHit(e.x, e.y)) {
                        modeGesture = GESTURE_RESIZE
                    } else if (activeFrame >= 0) {
                        if (activeFrame != selected) onSelect(activeFrame)
                        if (mode == SpriteSheetDoc.MODE_MANUAL && activeFrame == selected) {
                            modeGesture = GESTURE_MOVE
                            dragOffX = wx - doc.frames[activeFrame].x
                            dragOffY = wy - doc.frames[activeFrame].y
                        } else {
                            modeGesture = GESTURE_PAN
                        }
                    } else {
                        modeGesture = GESTURE_PAN
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount >= 2) {
                        pinchDist = spacing(e)
                        modeGesture = GESTURE_PINCH
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (e.pointerCount >= 2 && modeGesture == GESTURE_PINCH) {
                        val d = spacing(e)
                        if (pinchDist > 1f && d > 1f) zoomBy(d / pinchDist, (e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f)
                        pinchDist = d
                    } else when (modeGesture) {
                        GESTURE_MOVE -> {
                            if (activeFrame in doc.frames.indices) {
                                val (wx, wy) = toWorld(e.x, e.y)
                                val f = doc.frames[activeFrame]
                                val nx = (wx - dragOffX).toInt().coerceIn(0, (b.width - f.w).coerceAtLeast(0))
                                val ny = (wy - dragOffY).toInt().coerceIn(0, (b.height - f.h).coerceAtLeast(0))
                                f.x = nx; f.y = ny
                                invalidate()
                            }
                        }
                        GESTURE_RESIZE -> {
                            if (selected in doc.frames.indices) {
                                val (wx, wy) = toWorld(e.x, e.y)
                                val f = doc.frames[selected]
                                f.w = (wx - f.x).toInt().coerceIn(1, b.width - f.x)
                                f.h = (wy - f.y).toInt().coerceIn(1, b.height - f.y)
                                invalidate()
                            }
                        }
                        GESTURE_PAN -> {
                            tx += e.x - lastX
                            ty += e.y - lastY
                            invalidate()
                        }
                    }
                    lastX = e.x; lastY = e.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val moved = kotlin.math.hypot(e.x - downX, e.y - downY)
                    val dt = SystemClock.uptimeMillis() - downTime
                    val wasGesture = modeGesture
                    modeGesture = GESTURE_NONE
                    when (wasGesture) {
                        GESTURE_MOVE, GESTURE_RESIZE -> onEdited()
                        GESTURE_PAN -> {
                            // tap empty space: create a rect in manual mode, or select nothing
                            if (moved < 10f && dt < 400) {
                                val now = SystemClock.uptimeMillis()
                                val dbl = now - lastTapTime < 300 &&
                                    kotlin.math.hypot(e.x - lastTapX, e.y - lastTapY) < 40f
                                lastTapTime = now; lastTapX = e.x; lastTapY = e.y
                                if (dbl) {
                                    fit()
                                } else if (mode == SpriteSheetDoc.MODE_MANUAL && activeFrame < 0) {
                                    createRectAt(e.x, e.y, b)
                                } else if (activeFrame < 0) {
                                    onSelect(-1)
                                }
                            }
                        }
                    }
                }
            }
            return true
        }

        private var dragOffX = 0f
        private var dragOffY = 0f

        private fun spacing(e: MotionEvent): Float {
            val dx = e.getX(0) - e.getX(1)
            val dy = e.getY(0) - e.getY(1)
            return kotlin.math.hypot(dx, dy)
        }

        private fun zoomBy(factor: Float, mx: Float, my: Float) {
            val ns = (scale * factor).coerceIn(0.05f, 32f)
            if (ns == scale) return
            tx = mx - (mx - tx) * (ns / scale)
            ty = my - (my - ty) * (ns / scale)
            scale = ns
            invalidate()
        }

        private fun createRectAt(sx: Float, sy: Float, b: Bitmap) {
            val size = minOf(32, b.width, b.height).coerceAtLeast(1)
            val (wx, wy) = toWorld(sx, sy)
            val x = (wx - size / 2f).toInt().coerceIn(0, b.width - size)
            val y = (wy - size / 2f).toInt().coerceIn(0, b.height - size)
            doc.mode = SpriteSheetDoc.MODE_MANUAL
            doc.addRect(x, y, size, size)
            onSelect(doc.frames.size - 1)
            onEdited()
        }

        private companion object {
            const val GESTURE_NONE = 0
            const val GESTURE_PAN = 1
            const val GESTURE_MOVE = 2
            const val GESTURE_RESIZE = 3
            const val GESTURE_PINCH = 4
        }
    }

    /** Animated preview of the ordered frame rectangles. */
    class PreviewView(ctx: Context) : View(ctx) {
        private var bmp: Bitmap? = null
        private var doc = SpriteSheetDoc()
        var playing = true
        private var start = SystemClock.uptimeMillis()
        private var manual = 0
        private val p = Paint().apply { isFilterBitmap = false }
        private val src = Rect()
        private val dst = RectF()
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9AA0A6.toInt(); textSize = 11f * resources.displayMetrics.density
        }
        private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9AA0A6.toInt(); textSize = 12f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER
        }

        fun setBitmap(b: Bitmap?) { bmp = b; invalidate() }
        fun setDoc(d: SpriteSheetDoc) { doc = d; invalidate() }
        fun restart() { start = SystemClock.uptimeMillis(); manual = 0 }
        fun step(d: Int) {
            playing = false
            manual = Math.floorMod(currentIndex() + d, doc.frames.size.coerceAtLeast(1))
            invalidate()
        }

        private fun currentIndex(): Int {
            val n = doc.frames.size
            if (n == 0) return 0
            if (!playing) return manual.coerceIn(0, n - 1)
            val idx = (((SystemClock.uptimeMillis() - start) / 1000f) * doc.fps).toInt()
            return if (doc.loop) idx % n else minOf(idx, n - 1)
        }

        override fun onDraw(c: Canvas) {
            dst.set(0f, 0f, width.toFloat(), height.toFloat())
            drawChecker(c, dst, 10f * resources.displayMetrics.density, p)
            val b = bmp
            if (b == null || doc.frames.isEmpty()) {
                c.drawText(if (b == null) "No texture" else "No frames", width / 2f, height / 2f, hint)
                if (playing) postInvalidateOnAnimation()
                return
            }
            val i = currentIndex()
            if (playing) manual = i
            val f = doc.frames[i]
            if (f.x >= 0 && f.y >= 0 && f.right <= b.width && f.bottom <= b.height) {
                src.set(f.x, f.y, f.right, f.bottom)
                val s = minOf(width * 0.9f / src.width(), height * 0.85f / src.height())
                val w = src.width() * s
                val h = src.height() * s
                dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
                c.drawBitmap(b, src, dst, p)
            }
            c.drawText("${f.name}  ${i + 1}/${doc.frames.size}", 8f, height - 8f, txt)
            if (playing) postInvalidateOnAnimation()
        }
    }
}
