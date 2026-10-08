package com.sengine.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sengine.project.AssetStore
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import java.util.concurrent.Executors

/**
 * Browses the offline Asset Store: the packs built from the repository's
 * `Assets/` ZIPs (see [AssetStore]).
 *
 * Three levels -- packs, then the files inside a pack, then import.  Packs are
 * extracted on demand the first time one is opened or imported from, so opening
 * the browser never unpacks 60 MB.
 */
class AssetPacksActivity : AppCompatActivity() {

    private lateinit var project: Project
    private lateinit var manifest: AssetStore.Manifest
    private lateinit var grid: GridLayout
    private lateinit var chips: LinearLayout
    private lateinit var title: TextView
    private lateinit var subtitle: TextView

    private var packFilter: AssetStore.Pack? = null
    private var category: String? = null
    private var query = ""

    private val pool = Executors.newFixedThreadPool(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)
        manifest = try {
            AssetStore.load(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Store manifest missing: ${e.message}", Toast.LENGTH_LONG).show()
            finish(); return
        }

        val root = vbox().apply { setBackgroundColor(C.BG) }

        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(button("←") { onBackPressedCompat() })
        val titles = vbox()
        title = label("📦 Asset Packs", 15f, C.TEXT, true)
        subtitle = label("", 11f, C.DIM)
        titles.addView(title); titles.addView(subtitle)
        bar.addView(titles, lp(0, WRAP, 1f).margins(dp(10), 0, dp(10), 0))
        bar.addView(button("Free cache", C.PANEL2) { freeCache() }.apply { textSize = 12f })
        root.addView(bar, lp(MATCH, WRAP))

        chips = hbox().apply { setPadding(dp(6), dp(4), dp(6), dp(4)) }
        root.addView(HorizontalScrollView(this).apply {
            addView(chips); setBackgroundColor(C.PANEL); isHorizontalScrollBarEnabled = false
        }, lp(MATCH, WRAP))

        grid = GridLayout(this).apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        root.addView(ScrollView(this).apply { addView(grid) }, lp(MATCH, 0, 1f))

        setContentView(root)
        grid.post { rebuild() }
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
        AssetStore.clearThumbnailCache()
    }

    private fun onBackPressedCompat() {
        if (packFilter != null) { packFilter = null; category = null; rebuild() } else finish()
    }

    override fun onBackPressed() { onBackPressedCompat() }

    // ------------------------------------------------------------------ build
    private fun rebuild() {
        chips.removeAllViews()
        grid.removeAllViews()

        if (packFilter == null) {
            val t = manifest.totals
            title.text = "📦 Asset Packs"
            subtitle.text = "${t["packs"] ?: 0} packs · ${t["files"] ?: 0} files · " +
                "${(t["bytes"] ?: 0) / 1_048_576} MB · offline"

            // Category chips across the whole store.
            chips.addView(chip("All packs", category == null) { category = null; rebuild() })
            for (c in manifest.categoryOrder) {
                val n = manifest.categoryTotals[c] ?: 0
                if (n == 0) continue
                chips.addView(chip("$c ($n)", category == c) { category = c; rebuild() })
            }

            val cols = columns()
            val w = cardWidth(cols)
            var i = 0
            for (p in manifest.packs) {
                if (category != null && (p.categories[category] ?: 0) == 0) continue
                grid.addView(packCard(p), cell(cols, w))
                i++
            }
            if (i == 0) grid.addView(label("Nothing in this category.", 13f, C.DIM))
            return
        }

        val pack = packFilter!!
        title.text = pack.title
        subtitle.text = "${pack.fileCount} files · ${(pack.bytes / 1048576.0).let { String.format("%.1f", it) }} MB · ${pack.license}"

        chips.addView(chip("← All packs", false) { packFilter = null; category = null; rebuild() })
        chips.addView(chip("Import pack", true) { importAll(pack) })
        for ((c, n) in pack.categories) chips.addView(chip("$c ($n)", category == c) {
            category = if (category == c) null else c; rebuild()
        })

        val entries = pack.files.filter { category == null || it.category == category }
        if (!AssetStore.isExtracted(this, pack)) {
            grid.addView(extractBanner(pack, entries.size), lp(MATCH, WRAP).margins(dp(4), dp(4), dp(4), dp(4)))
        }
        val cols = columns()
        val w = cardWidth(cols)
        for (e in entries) grid.addView(entryCard(pack, e), cell(cols, w))
    }

    private fun columns(): Int = (grid.width / (dp(150) + dp(8))).coerceAtLeast(2)
    private fun cardWidth(cols: Int) = (grid.width - dp(12)) / cols - dp(8)
    private fun cell(cols: Int, w: Int) = GridLayout.LayoutParams().apply {
        width = w; setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun chip(text: String, active: Boolean, onClick: () -> Unit) =
        button(text, if (active) C.ACCENT else C.PANEL2, if (active) 0xFFFFFFFF.toInt() else C.TEXT) { onClick() }
            .apply { textSize = 12f }.also {
                it.layoutParams = lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0)
            }

    // ------------------------------------------------------------------ cards
    private fun packCard(p: AssetStore.Pack): LinearLayout {
        val c = card()
        c.addView(label(p.primaryCategory, 22f, C.ACCENT, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0xFF2A2D32.toInt()); cornerRadius = dp(6).toFloat() }
        }, lp(MATCH, dp(84)))
        c.addView(label(p.title, 13f, C.TEXT, true).apply { maxLines = 2; minLines = 2 },
            lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        c.addView(label("${p.fileCount} files · ${(p.bytes / 1048576.0).let { String.format("%.1f", it) }} MB",
            10f, C.DIM))
        if (p.sheetCount > 0) c.addView(label("${p.sheetCount} auto-sliced sheets", 10f, C.ACCENT))
        if (!p.distributable) c.addView(label("⚠ restricted licence", 10f, 0xFFE0A040.toInt()))
        val extracted = AssetStore.isExtracted(this, p)
        c.addView(label(if (extracted) "✓ extracted" else "in APK · extracts on use", 10f,
            if (extracted) C.GREEN else C.DIM))

        val row = hbox()
        row.addView(button("Browse", C.ACCENT, 0xFFFFFFFF.toInt()) {
            packFilter = p; category = null; rebuild()
        }.apply { textSize = 12f }, lp(0, WRAP, 1f).margins(0, dp(6), dp(4), 0))
        row.addView(button("Import", C.GREEN, 0xFFFFFFFF.toInt()) { importAll(p) }
            .apply { textSize = 12f }, lp(0, WRAP, 1f).margins(0, dp(6), 0, 0))
        c.addView(row, lp(MATCH, WRAP))
        return c
    }

    private fun entryCard(pack: AssetStore.Pack, e: AssetStore.Entry): LinearLayout {
        val c = card()
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { setColor(0xFF2A2D32.toInt()); cornerRadius = dp(6).toFloat() }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        c.addView(img, lp(MATCH, dp(84)))
        if (e.isImage) {
            pool.execute {
                val b: Bitmap? = try { AssetStore.thumbnail(this, pack, e, 128) } catch (_: Throwable) { null }
                if (b != null) runOnUiThread {
                    img.setImageDrawable(BitmapDrawable(resources, b).apply {
                        // Nearest-neighbour keeps pixel art crisp; only smooth
                        // genuinely large art, where blockiness looks like a bug.
                        paint.isFilterBitmap = (e.w > 256 || e.h > 256)
                    })
                }
            }
        } else {
            img.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        c.addView(label(e.name, 11f, C.TEXT, true).apply { maxLines = 2; minLines = 2 },
            lp(MATCH, WRAP).margins(0, dp(4), 0, 0))
        c.addView(label("${e.dimensionLabel} · ${e.sizeLabel}", 10f, C.DIM))
        val s = e.sheet
        if (s != null) {
            c.addView(label("sheet ${s.frameW}×${s.frameH} · ${s.frames}f" + if (s.guess) " (est.)" else "",
                10f, C.ACCENT))
        }
        c.addView(button("Import", C.PANEL2) { importOne(pack, e) }
            .apply { textSize = 11f }, lp(MATCH, WRAP).margins(0, dp(4), 0, 0))
        return c
    }

    private fun card() = vbox().apply {
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply { setColor(C.PANEL); cornerRadius = dp(8).toFloat() }
    }

    private fun extractBanner(pack: AssetStore.Pack, count: Int): LinearLayout = vbox().apply {
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = GradientDrawable().apply { setColor(C.PANEL2); cornerRadius = dp(8).toFloat() }
        addView(label("This pack is still packed in the APK.", 12f, C.TEXT, true))
        addView(label("${count} files · ${(pack.bytes / 1048576.0).let { String.format("%.1f", it) }} MB " +
            "will be extracted to app storage the first time a thumbnail or import needs it.",
            11f, C.DIM))
        addView(button("Extract now", C.ACCENT, 0xFFFFFFFF.toInt()) { extract(pack) }
            .apply { textSize = 12f }, lp(WRAP, WRAP).margins(0, dp(6), 0, 0))
    }

    // ---------------------------------------------------------------- actions
    private fun extract(pack: AssetStore.Pack) {
        val dialog = progress("Extracting ${pack.title}…", pack.fileCount)
        pool.execute {
            try {
                AssetStore.extract(this, pack) { done, total ->
                    runOnUiThread { dialog.setMessage("Extracting ${pack.title}…  $done/$total") }
                }
                runOnUiThread { dialog.dismiss(); Toast.makeText(this, "Extracted ${pack.fileCount} files", Toast.LENGTH_SHORT).show(); rebuild() }
            } catch (e: Exception) {
                runOnUiThread { dialog.dismiss(); Toast.makeText(this, "Extract failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun importOne(pack: AssetStore.Pack, e: AssetStore.Entry) = runImport(pack, listOf(e))

    private fun importAll(pack: AssetStore.Pack) {
        val entries = pack.files.filter { category == null || it.category == category }
        runImport(pack, entries)
    }

    private fun runImport(pack: AssetStore.Pack, entries: List<AssetStore.Entry>) {
        if (entries.isEmpty()) return
        val dialog = progress("Importing into ${project.name}…", entries.size)
        pool.execute {
            val r = AssetStore.importInto(this, project, pack, entries) { done, total ->
                runOnUiThread { dialog.setMessage("Importing…  $done/$total") }
            }
            runOnUiThread {
                dialog.dismiss()
                val msg = buildString {
                    append("Imported ${r.imported} files")
                    if (r.clips > 0) append(", ${r.clips} animation clips")
                    if (r.skipped > 0) append(", ${r.skipped} skipped")
                    if (r.errors.isNotEmpty()) append(" · ${r.errors.size} failed")
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun freeCache() {
        AssetStore.clearCache(this)
        Toast.makeText(this, "Store cache cleared", Toast.LENGTH_SHORT).show()
        rebuild()
    }

    private fun progress(msg: String, max: Int) =
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Asset Store")
            .setMessage("$msg\n0/$max")
            .setCancelable(false)
            .show()
}
