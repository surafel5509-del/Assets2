package com.sengine.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sengine.engine.render.ShaderPresets
import com.sengine.project.AssetFolders
import com.sengine.project.Project
import com.sengine.project.ProjectManager

/**
 * Browses the built-in shader presets and saves any of them into the project's
 * `Shaders/` branch as an editable `.glsl` asset. Assign the result to a
 * SpriteRenderer / MeshRenderer "Shader" or a camera "FX Shader".
 */
class ShaderLibraryActivity : AppCompatActivity() {

    private lateinit var project: Project
    private lateinit var list: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)

        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(button("←") { finish() })
        bar.addView(label("Shader Library", 15f, C.TEXT, true).apply { setPadding(dp(10), 0, 0, 0) }, lp(0, -2, 1f))
        root.addView(bar, lp(-1, -2))

        val intro = label(
            "Presets are GLSL effects. Save one to Shaders/, then assign it to a sprite, mesh or camera FX. " +
                "uTime, uParam (the component's Shader Param), uTex and uResolution are available.",
            12f, C.DIM,
        ).apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        root.addView(intro, lp(-1, -2))

        list = vbox().apply { setPadding(dp(8), 0, dp(8), dp(8)) }
        root.addView(ScrollView(this).apply { addView(list) }, lp(-1, 0, 1f))
        setContentView(root)
        build()
    }

    private fun build() {
        list.removeAllViews()
        for (cat in ShaderPresets.CATEGORIES) {
            list.addView(label(cat.uppercase(), 11f, C.ACCENT, true).apply { setPadding(dp(4), dp(10), 0, dp(4)) })
            for (p in ShaderPresets.inCategory(cat)) {
                val card = vbox().apply {
                    background = round(C.PANEL, dp(8).toFloat())
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setOnClickListener { showSource(p) }
                }
                card.addView(label(p.name, 14f, C.TEXT, true), lp(-1, -2))
                card.addView(label(p.description, 12f, C.DIM), lp(-1, -2))
                list.addView(card, lp(-1, -2).margins(0, dp(3), 0, dp(3)))
            }
        }
    }

    private fun showSource(p: ShaderPresets.Preset) {
        val text = TextView(this).apply {
            text = p.source.trim()
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(C.TEXT)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(text) }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(p.name)
            .setMessage(p.description)
            .setView(scroll)
            .setPositiveButton("Save to Shaders") { _, _ -> save(p, open = false) }
            .setNeutralButton("Save & open") { _, _ -> save(p, open = true) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun save(p: ShaderPresets.Preset, open: Boolean) {
        // Never overwrite a shader the user may have edited: pick a free name instead.
        val name = AssetFolders.unique(p.fileName) { candidate -> project.assetFile(candidate).exists() }
        project.writeAsset(name, p.source.trimStart() + "\n")
        toast("Saved $name")
        if (open) {
            startActivity(Intent(this, ScriptEditorActivity::class.java)
                .putExtra("project", project.dir.name)
                .putExtra("asset", name))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
