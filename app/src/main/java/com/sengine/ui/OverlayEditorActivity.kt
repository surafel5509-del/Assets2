package com.sengine.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.R
import com.sengine.engine.overlay.ElementType
import com.sengine.engine.overlay.HAnchor
import com.sengine.engine.overlay.Kind
import com.sengine.engine.overlay.OverlayCodec
import com.sengine.engine.overlay.OverlayDocument
import com.sengine.engine.overlay.OverlayElement
import com.sengine.engine.overlay.Shape
import com.sengine.engine.overlay.VAnchor
import com.sengine.project.AssetFolders
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import com.sengine.studio.Argb
import org.json.JSONObject

/**
 * Visual editor for control layouts (`Controls/*.ctrl.json`) and UI screens
 * (`UI/*.ui.json`). Drag to move, drag the corner handle to resize, and edit
 * exact values in the inspector. Every change is one undo step.
 */
class OverlayEditorActivity : AppCompatActivity() {

    private lateinit var project: Project
    private lateinit var doc: OverlayDocument
    private lateinit var view: OverlayView
    private lateinit var title: TextView
    private lateinit var undoBtn: ImageView
    private lateinit var redoBtn: ImageView
    private lateinit var inspector: LinearLayout
    private lateinit var chips: LinearLayout

    /** The project asset this layout is saved to. Blank until the first save of a new layout. */
    private var fileName = ""
    private var selected: String? = null
    private var committed = ""
    private val undoStack = ArrayList<String>()
    private val redoStack = ArrayList<String>()
    private var dirty = false
    private var snapStep = 0.01f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra(EXTRA_PROJECT)!!)
        val kind = if (intent.getStringExtra(EXTRA_KIND) == KIND_UI) Kind.UI else Kind.CONTROLS
        val existing = intent.getStringExtra(EXTRA_ASSET)
        doc = if (existing != null) {
            fileName = existing
            val text = project.readAsset(existing)
            if (text == null) { toast("Missing $existing"); newDoc(kind) }
            else OverlayCodec.fromJson(JSONObject(text), AssetFolders.nameOf(existing).substringBefore('.'))
        } else newDoc(kind)
        committed = snapshot()

        val root = vbox().apply { setBackgroundColor(C.BG) }
        root.addView(buildHeader(), lp(-1, -2))
        root.addView(buildToolbar(), lp(-1, -2))
        view = OverlayView(this) { listOf(doc) }.apply {
            editing = true
            aspect = doc.designAspect
            this.snapStep = this@OverlayEditorActivity.snapStep
            imageLoader = { name -> try { BitmapFactory.decodeFile(project.assetFile(name).absolutePath) } catch (_: Throwable) { null } }
            onSelect = { _, el -> selected = el?.id; refreshInspector() }
            onEdited = { commit(); refreshInspector() }
            setBackgroundColor(0xFF101216.toInt())
        }
        root.addView(view, lp(-1, 0, 1f))
        chips = hbox().apply { setPadding(dp(4), dp(4), dp(4), dp(4)); setBackgroundColor(C.HEADER) }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, lp(-1, -2))
        inspector = vbox().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        root.addView(ScrollView(this).apply { setBackgroundColor(C.PANEL); addView(inspector) }, lp(-1, dp(260)))
        setContentView(root)
        refreshAll()
    }

    override fun onBackPressed() {
        if (!dirty) { finish(); return }
        MaterialAlertDialogBuilder(this).setTitle("Unsaved layout")
            .setMessage("Save changes to ${doc.name}?")
            .setPositiveButton("Save") { _, _ -> if (save()) finish() }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null).show()
    }

    private fun newDoc(kind: Kind): OverlayDocument {
        val d = OverlayDocument(if (kind == Kind.UI) "Screen" else "Controls", kind)
        return d
    }

    // ------------------------------------------------------------------ layout

    private fun buildHeader(): View {
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(4), dp(4), dp(4), dp(4)); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(icon(R.drawable.ic_chevron_left, "Back") { onBackPressed() }, lp(dp(40), dp(40)))
        title = label(doc.name, 15f, C.TEXT, true).apply { setPadding(dp(8), 0, dp(8), 0); isSingleLine = true }
        bar.addView(title, lp(0, -2, 1f))
        undoBtn = icon(R.drawable.ic_undo, "Undo") { undo() }
        redoBtn = icon(R.drawable.ic_redo, "Redo") { redo() }
        bar.addView(undoBtn, lp(dp(40), dp(40)))
        bar.addView(redoBtn, lp(dp(40), dp(40)))
        if (doc.kind == Kind.CONTROLS) {
            bar.addView(button("Use in play", C.PANEL2) { useForPlay() }.apply { textSize = 12f }, lp(-2, dp(40)))
        }
        bar.addView(button("Save", C.ACCENT, 0xFFFFFFFF.toInt()) { save() }, lp(-2, dp(40)).margins(dp(4), 0, 0, 0))
        return bar
    }

    private fun buildToolbar(): View {
        val row = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(C.PANEL) }
        val inner = hbox().apply { setPadding(dp(4), dp(4), dp(4), dp(4)); gravity = Gravity.CENTER_VERTICAL }
        inner.addView(button("+ Add", C.GREEN, 0xFFFFFFFF.toInt()) { showAddMenu() }.apply { textSize = 12f }, lp(-2, dp(36)))
        inner.addView(icon(R.drawable.ic_duplicate, "Duplicate") { duplicateSelected() }, lp(dp(40), dp(40)))
        inner.addView(icon(R.drawable.ic_trash, "Delete") { deleteSelected() }, lp(dp(40), dp(40)))
        inner.addView(icon(R.drawable.ic_layer, "Bring to front") { bringFront() }, lp(dp(40), dp(40)))
        inner.addView(button("Aspect", C.PANEL2) { showAspectMenu() }.apply { textSize = 12f }, lp(-2, dp(36)).margins(dp(6), 0, 0, 0))
        inner.addView(button("Snap", C.PANEL2) { showSnapMenu() }.apply { textSize = 12f }, lp(-2, dp(36)).margins(dp(4), 0, 0, 0))
        inner.addView(button("Fit all", C.PANEL2) { clampAll() }.apply { textSize = 12f }, lp(-2, dp(36)).margins(dp(4), 0, 0, 0))
        row.addView(inner)
        return row
    }

    // ------------------------------------------------------------------ history

    private fun snapshot(): String = OverlayCodec.toJson(doc).toString()

    private fun restore(json: String) {
        val fresh = OverlayCodec.fromJson(JSONObject(json), doc.name)
        doc.elements.clear()
        doc.elements.addAll(fresh.elements)
        doc.designAspect = fresh.designAspect
        doc.name = fresh.name
        view.aspect = doc.designAspect
        if (selected != null && doc.find(selected!!) == null) selected = null
        view.selectedId = selected
    }

    /** Records the current state as one undo step. Call after any change. */
    private fun commit() {
        val now = snapshot()
        if (now == committed) return
        undoStack.add(committed)
        if (undoStack.size > 80) undoStack.removeAt(0)
        redoStack.clear()
        committed = now
        dirty = true
        refreshAll()
    }

    private fun undo() {
        if (undoStack.isEmpty()) { toast("Nothing to undo"); return }
        redoStack.add(committed)
        committed = undoStack.removeAt(undoStack.size - 1)
        restore(committed)
        dirty = true
        refreshAll()
    }

    private fun redo() {
        if (redoStack.isEmpty()) { toast("Nothing to redo"); return }
        undoStack.add(committed)
        committed = redoStack.removeAt(redoStack.size - 1)
        restore(committed)
        dirty = true
        refreshAll()
    }

    // ------------------------------------------------------------------ actions

    private fun showAddMenu() {
        val types = ElementType.values()
        MaterialAlertDialogBuilder(this).setTitle("Add element")
            .setItems(types.map { it.label }.toTypedArray()) { _, i ->
                val e = doc.add(types[i])
                selected = e.id
                view.selectedId = e.id
                commit()
            }.show()
    }

    private fun duplicateSelected() {
        val id = selected ?: return toast("Select an element first")
        val copy = doc.duplicate(id) ?: return
        selected = copy.id
        view.selectedId = copy.id
        commit()
    }

    private fun deleteSelected() {
        val id = selected ?: return toast("Select an element first")
        doc.remove(id)
        selected = null
        view.selectedId = null
        commit()
    }

    private fun bringFront() {
        val id = selected ?: return toast("Select an element first")
        doc.bringToFront(id)
        commit()
    }

    private fun clampAll() {
        doc.clampToScreen()
        commit()
    }

    private fun showAspectMenu() {
        val presets = listOf("16:9" to 16f / 9f, "19.5:9 (phone)" to 19.5f / 9f, "4:3" to 4f / 3f, "1:1" to 1f, "9:16 (portrait)" to 9f / 16f)
        MaterialAlertDialogBuilder(this).setTitle("Design aspect ratio")
            .setItems(presets.map { it.first }.toTypedArray()) { _, i ->
                doc.designAspect = presets[i].second
                view.aspect = doc.designAspect
                commit()
            }.show()
    }

    private fun showSnapMenu() {
        val steps = listOf("Off" to 0f, "1 %" to 0.01f, "2.5 %" to 0.025f, "5 %" to 0.05f, "10 %" to 0.1f)
        MaterialAlertDialogBuilder(this).setTitle("Snap to grid")
            .setItems(steps.map { it.first }.toTypedArray()) { _, i ->
                snapStep = steps[i].second
                view.snapStep = snapStep
                refreshAll()
            }.show()
    }

    private fun useForPlay() {
        if (!save()) return
        project.controlsLayout = fileName
        project.saveMeta()
        toast("Play mode now uses ${AssetFolders.nameOf(fileName)}")
    }

    /** Writes the layout. New layouts are named into their branch on first save. */
    private fun save(): Boolean = try {
        if (fileName.isBlank()) {
            val suffix = if (doc.kind == Kind.UI) ".ui.json" else ".ctrl.json"
            val stem = doc.name.trim().ifBlank { "Layout" }.replace(Regex("[^A-Za-z0-9 _-]"), "")
            fileName = project.uniqueAssetName("$stem$suffix")
        }
        project.writeAsset(fileName, OverlayCodec.toJson(doc).toString(2))
        dirty = false
        refreshAll()
        toast("Saved ${AssetFolders.nameOf(fileName)}")
        true
    } catch (e: Exception) {
        toast("Save failed: ${e.message}"); false
    }

    // ------------------------------------------------------------------ refresh

    private fun refreshAll() {
        if (!::title.isInitialized) return
        title.text = doc.name + if (dirty) "  •" else ""
        undoBtn.alpha = if (undoStack.isNotEmpty()) 1f else 0.35f
        redoBtn.alpha = if (redoStack.isNotEmpty()) 1f else 0.35f
        view.selectedId = selected
        view.refresh()
        refreshChips()
        refreshInspector()
    }

    private fun refreshChips() {
        chips.removeAllViews()
        for (e in doc.elements) {
            val t = TextView(this).apply {
                text = e.name.ifBlank { e.id }
                textSize = 12f
                setTextColor(if (e.id == selected) C.TEXT else C.DIM)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = round(if (e.id == selected) C.SEL else C.PANEL2, dp(14).toFloat())
                setOnClickListener { selected = e.id; view.selectedId = e.id; refreshAll() }
            }
            chips.addView(t, lp(-2, -2).margins(dp(3), 0, dp(3), 0))
        }
        if (doc.elements.isEmpty()) chips.addView(label("No elements yet. Tap + Add.", 12f, C.DIM).apply { setPadding(dp(6), dp(6), dp(6), dp(6)) })
    }

    private fun refreshInspector() {
        if (!::inspector.isInitialized) return
        inspector.removeAllViews()
        val el = selected?.let { doc.find(it) }
        if (el == null) {
            documentFields()
            return
        }
        inspector.addView(label("${el.type.label} · ${el.id}", 13f, C.TEXT, true), lp(-1, -2))

        val name = fieldFor("Name", el.name)
        val text = fieldFor("Text", el.text)
        val action = fieldFor("Action (sent to scripts)", el.action)
        val x = fieldFor("X (anchor offset)", fmt3(el.x), numeric = true)
        val y = fieldFor("Y (anchor offset)", fmt3(el.y), numeric = true)
        val w = fieldFor("Width", fmt3(el.w), numeric = true)
        val h = fieldFor("Height", fmt3(el.h), numeric = true)
        val color = fieldFor("Colour #AARRGGBB", Argb.toHex(el.color))
        val bg = fieldFor("Background #AARRGGBB", Argb.toHex(el.background))
        val opacity = fieldFor("Opacity 0–1", fmt3(el.opacity), numeric = true)
        val font = fieldFor("Font size (fraction of height)", fmt3(el.fontSize), numeric = true)
        val value = fieldFor("Value 0–1", fmt3(el.value), numeric = true)

        val h1 = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        h1.addView(cycleButton("H: ${el.hAnchor.label}") {
            el.hAnchor = HAnchor.values()[(el.hAnchor.ordinal + 1) % HAnchor.values().size]; commit()
        }, lp(0, -2, 1f))
        h1.addView(cycleButton("V: ${el.vAnchor.label}") {
            el.vAnchor = VAnchor.values()[(el.vAnchor.ordinal + 1) % VAnchor.values().size]; commit()
        }, lp(0, -2, 1f).margins(dp(4), 0, 0, 0))
        h1.addView(cycleButton("Shape: ${el.shape.label}") {
            el.shape = Shape.values()[(el.shape.ordinal + 1) % Shape.values().size]; commit()
        }, lp(0, -2, 1f).margins(dp(4), 0, 0, 0))
        inspector.addView(h1, lp(-1, -2).margins(0, dp(6), 0, dp(6)))

        val flags = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        flags.addView(CheckBox(this@OverlayEditorActivity).apply {
            text = "Visible"; setTextColor(C.TEXT); isChecked = el.visible
            setOnCheckedChangeListener { _, on -> if (on != el.visible) { el.visible = on; commit() } }
        }, lp(0, -2, 1f))
        flags.addView(CheckBox(this@OverlayEditorActivity).apply {
            text = "Locked"; setTextColor(C.TEXT); isChecked = el.locked
            setOnCheckedChangeListener { _, on -> if (on != el.locked) { el.locked = on; commit() } }
        }, lp(0, -2, 1f))
        inspector.addView(flags, lp(-1, -2))

        inspector.addView(button("Apply changes", C.ACCENT, 0xFFFFFFFF.toInt()) {
            el.name = name.text.toString()
            el.text = text.text.toString()
            el.action = action.text.toString().trim()
            el.x = num(x, el.x); el.y = num(y, el.y)
            el.w = num(w, el.w).coerceIn(0.01f, 1f); el.h = num(h, el.h).coerceIn(0.01f, 1f)
            Argb.parse(color.text.toString())?.let { el.color = it }
            Argb.parse(bg.text.toString())?.let { el.background = it }
            el.opacity = num(opacity, el.opacity).coerceIn(0f, 1f)
            el.fontSize = num(font, el.fontSize).coerceIn(0.005f, 0.5f)
            el.value = num(value, el.value).coerceIn(0f, 1f)
            commit()
        }, lp(-1, dp(44)).margins(0, dp(8), 0, 0))
        inspector.addView(button("Delete element", C.RED, 0xFFFFFFFF.toInt()) { deleteSelected() }, lp(-1, dp(40)).margins(0, dp(6), 0, 0))
    }

    private fun documentFields() {
        inspector.addView(label("Layout", 13f, C.TEXT, true), lp(-1, -2))
        inspector.addView(label(
            "Tap an element to edit it. Drag to move, drag the blue corner to resize. " +
                "Positions are fractions of the screen, so the layout adapts to any phone.",
            12f, C.DIM), lp(-1, -2))
        val nameF = fieldFor("Layout name", doc.name)
        inspector.addView(button("Rename", C.PANEL2) {
            val n = nameF.text.toString().trim()
            if (n.isNotBlank() && n != doc.name) { doc.name = n; commit() }
        }, lp(-1, dp(40)).margins(0, dp(6), 0, 0))
        inspector.addView(label("Design aspect ${fmt3(doc.designAspect)} · snap ${if (snapStep <= 0f) "off" else "${(snapStep * 100).toInt()}%"}", 12f, C.DIM), lp(-1, -2).margins(0, dp(6), 0, 0))
    }

    private fun fieldFor(hint: String, value: String, numeric: Boolean = false): EditText =
        field(value, numeric = numeric).apply {
            this.hint = hint
            setSingleLine(true)
            setTextColor(C.TEXT)
        }

    private fun cycleButton(text: String, onClick: () -> Unit): TextView =
        button(text, C.PANEL2) { onClick() }.apply { textSize = 11f }

    private fun num(e: EditText, fallback: Float): Float = e.text.toString().toFloatOrNull() ?: fallback

    private fun fmt3(v: Float): String = String.format("%.3f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    private fun icon(res: Int, desc: String, onClick: () -> Unit): ImageView =
        ImageView(this).apply {
            setImageResource(res)
            setColorFilter(C.TEXT)
            contentDescription = desc
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = round(C.PANEL2, dp(8).toFloat())
            setOnClickListener { onClick() }
        }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_PROJECT = "project"
        const val EXTRA_ASSET = "asset"
        const val EXTRA_KIND = "kind"
        const val KIND_UI = "ui"
        const val KIND_CONTROLS = "controls"

        fun intent(ctx: Context, project: String, kind: String, asset: String? = null): android.content.Intent =
            android.content.Intent(ctx, OverlayEditorActivity::class.java)
                .putExtra(EXTRA_PROJECT, project)
                .putExtra(EXTRA_KIND, kind)
                .apply { if (asset != null) putExtra(EXTRA_ASSET, asset) }
    }
}
