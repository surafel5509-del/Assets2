package com.sengine.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.blueprint.Blueprint
import com.sengine.engine.blueprint.BlueprintCompiler
import com.sengine.engine.script.ApiReference
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import org.mozilla.javascript.Context
import org.mozilla.javascript.RhinoException

/** Code editor for JavaScript behaviour scripts with syntax highlighting and quick keys. */
class ScriptEditorActivity : AppCompatActivity() {

    private lateinit var project: Project
    private lateinit var asset: String
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var titleView: TextView
    private var saved = ""
    private val handler = Handler(Looper.getMainLooper())
    private val history = ArrayList<String>()
    private var historyIndex = -1
    private var restoring = false

    private var glsl = false
    private var blueprint = false
    private var syntaxError: String? = null
    private val highlightTask = Runnable { highlight(editor.text) }
    private val historyTask = Runnable { pushHistory() }
    private val syntaxTask = Runnable { validateSyntax(); updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)
        asset = intent.getStringExtra("asset")!!
        glsl = asset.endsWith(".glsl")
        blueprint = asset.endsWith(".bp")
        saved = project.readAsset(asset) ?: ""

        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(button("←") { onBackPressedDispatcher.onBackPressed() })
        titleView = label(asset, 15f, C.TEXT, true).apply { setPadding(dp(10), 0, dp(10), 0) }
        bar.addView(titleView, lp(0, WRAP, 1f))
        bar.addView(button("↶") { undo() }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button("↷") { redo() }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button(if (glsl) "GLSL" else "API") { showApi() }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button("Save", C.ACCENT, 0xFFFFFFFF.toInt()) { save() }, lp(WRAP, WRAP).margins(dp(3), 0, 0, 0))
        root.addView(bar, lp(MATCH, WRAP))

        editor = EditText(this).apply {
            setText(saved)
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(0xFFD4D4D4.toInt())
            setBackgroundColor(0xFF1B1C1F.toInt())
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setHorizontallyScrolling(true)
            isVerticalScrollBarEnabled = true
        }
        root.addView(editor, lp(MATCH, 0, 1f))

        status = label("", 11f, C.DIM).apply { setPadding(dp(10), dp(2), dp(10), dp(2)); setBackgroundColor(C.HEADER) }
        root.addView(status, lp(MATCH, WRAP))

        val keys = hbox().apply { setPadding(dp(4), dp(4), dp(4), dp(4)); setBackgroundColor(C.PANEL) }
        for (k in listOf("⇥", "{", "}", "(", ")", ";", "=", "\"", ".", ",", "[", "]", "+", "-", "*", "/", "<", ">", "!", "&", "|", ":", "?")) {
            keys.addView(button(k) { insert(if (k == "⇥") "    " else k) }.apply { minWidth = dp(38) },
                lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        }
        root.addView(HorizontalScrollView(this).apply { addView(keys); isHorizontalScrollBarEnabled = false }, lp(MATCH, WRAP))
        setContentView(root)

        editor.addTextChangedListener(object : TextWatcher {
            private var autoIndent = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                autoIndent = !restoring && count == 1 && s != null && start < s.length && s[start] == '\n'
            }
            override fun afterTextChanged(s: Editable) {
                if (autoIndent) {
                    autoIndent = false
                    val pos = editor.selectionStart
                    val lineStart = s.lastIndexOf('\n', pos - 2) + 1
                    var indent = ""
                    var i = lineStart
                    while (i < pos - 1 && s[i] == ' ') { indent += " "; i++ }
                    if (pos >= 2 && s[pos - 2] == '{') indent += "    "
                    if (indent.isNotEmpty()) s.insert(pos, indent)
                }
                handler.removeCallbacks(highlightTask)
                handler.postDelayed(highlightTask, 250)
                handler.removeCallbacks(syntaxTask)
                handler.postDelayed(syntaxTask, 400)
                if (!restoring) {
                    handler.removeCallbacks(historyTask)
                    handler.postDelayed(historyTask, 600)
                }
                updateStatus()
            }
        })
        editor.setOnClickListener { updateStatus() }
        highlight(editor.text)
        pushHistory()
        validateSyntax()
        updateStatus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editor.text.toString() == saved) { finish(); return }
                MaterialAlertDialogBuilder(this@ScriptEditorActivity)
                    .setTitle("Unsaved changes")
                    .setMessage("Save changes to $asset?")
                    .setPositiveButton("Save") { _, _ -> save(); finish() }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Cancel", null)
                    .show()
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacks(highlightTask)
        handler.removeCallbacks(historyTask)
        handler.removeCallbacks(syntaxTask)
        super.onDestroy()
    }

    private fun insert(s: String) {
        val st = editor.selectionStart.coerceAtLeast(0)
        val en = editor.selectionEnd.coerceAtLeast(0)
        editor.text.replace(minOf(st, en), maxOf(st, en), s)
    }

    /** Compiles the current text with the real Rhino parser / blueprint compiler. */
    private fun validateSyntax() {
        val t = editor.text.toString()
        if (glsl) { syntaxError = null; return } // GLSL is compiled by the GL runtime
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            val source = if (blueprint) BlueprintCompiler.compile(Blueprint.parse(t)) else t
            cx.compileString(source, asset, 1, null)
            syntaxError = null
        } catch (e: RhinoException) {
            syntaxError = "Line ${e.lineNumber()}: ${e.details().trim()}"
        } catch (e: Exception) {
            syntaxError = e.message ?: "Invalid source"
        } finally {
            Context.exit()
        }
    }

    /** Real keyboard shortcuts, usable while the code field has focus. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> { save(); return true }
                KeyEvent.KEYCODE_Z -> { if (event.isShiftPressed) redo() else undo(); return true }
                KeyEvent.KEYCODE_Y -> { redo(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun save(force: Boolean = false) {
        handler.removeCallbacks(syntaxTask)
        validateSyntax()
        val error = syntaxError
        if (!force && error != null) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Syntax error")
                .setMessage("$error\n\nSave anyway? The runtime will report this error when the script runs.")
                .setPositiveButton("Save anyway") { _, _ -> save(force = true) }
                .setNegativeButton("Fix", null)
                .show()
            return
        }
        val t = editor.text.toString()
        project.writeAsset(asset, t)
        saved = t
        updateStatus()
        Toast.makeText(this, "Saved $asset", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        val pos = editor.selectionStart.coerceAtLeast(0)
        val text = editor.text
        var line = 1
        var col = 1
        for (i in 0 until minOf(pos, text.length)) {
            if (text[i] == '\n') { line++; col = 1 } else col++
        }
        val dirty = if (text.toString() != saved) "  •  modified" else ""
        val language = when {
            glsl -> "GLSL ES 1.0"
            blueprint -> "Blueprint JSON"
            else -> "JavaScript"
        }
        val check = syntaxError?.let { "  •  ⚠ $it" }
            ?: if (glsl) "" else "  •  syntax ok"
        status.text = "Ln $line, Col $col  •  ${text.count { it == '\n' } + 1} lines  •  $language$dirty$check"
    }

    private fun pushHistory() {
        val t = editor.text.toString()
        if (historyIndex >= 0 && history[historyIndex] == t) return
        while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
        history.add(t)
        if (history.size > 100) history.removeAt(0)
        historyIndex = history.size - 1
    }

    private fun restore(t: String) {
        restoring = true
        val sel = editor.selectionStart
        editor.setText(t)
        editor.setSelection(sel.coerceIn(0, t.length))
        restoring = false
        highlight(editor.text)
    }

    private fun undo() {
        pushHistory()
        if (historyIndex > 0) { historyIndex--; restore(history[historyIndex]) }
    }

    private fun redo() {
        if (historyIndex < history.size - 1) { historyIndex++; restore(history[historyIndex]) }
    }

    private fun highlight(s: Editable) {
        for (span in s.getSpans(0, s.length, ForegroundColorSpan::class.java)) s.removeSpan(span)
        val text = s.toString()
        fun paint(regex: Regex, color: Int) {
            for (m in regex.findAll(text)) {
                s.setSpan(ForegroundColorSpan(color), m.range.first, m.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        paint(NUMBER, 0xFFB5CEA8.toInt())
        if (glsl) {
            paint(GLSL_KEYWORD, 0xFF569CD6.toInt())
            paint(GLSL_API, 0xFF4EC9B0.toInt())
        } else {
            paint(KEYWORD, 0xFF569CD6.toInt())
            paint(API, 0xFF4EC9B0.toInt())
        }
        paint(FUNC, 0xFFDCDCAA.toInt())
        paint(STRING, 0xFFCE9178.toInt())
        paint(COMMENT, 0xFF6A9955.toInt())
    }

    private fun showApi() {
        val tv = label(if (glsl) GLSL_DOC else API_DOC, 12f, C.TEXT).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setTextIsSelectable(true)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (glsl) "S Engine Shaders" else "S Engine Script API")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .show()
    }

    companion object {
        private val KEYWORD = Regex("\\b(var|let|const|function|return|if|else|for|while|do|break|continue|new|this|true|false|null|undefined|typeof|in|of|switch|case|default|try|catch|finally|throw)\\b")
        private val API = Regex("\\b(self|transform|gameObject|input|time|scene|audio|console|Math)\\b")
        private val FUNC = Regex("\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\()")
        private val GLSL_KEYWORD = Regex("\\b(float|int|bool|void|vec2|vec3|vec4|mat2|mat3|mat4|sampler2D|if|else|for|return|discard|const|uniform|varying|precision|mediump|highp|lowp|true|false)\\b")
        private val GLSL_API = Regex("\\b(uTime|uParam|uTex|uColor|uUseTex|uResolution|texture2D|mix|clamp|smoothstep|step|fract|floor|sin|cos|dot|length|normalize|pow|abs|max|min|mod|distance)\\b")
        private val NUMBER = Regex("\\b\\d+(\\.\\d+)?\\b")
        private val STRING = Regex("\"(\\\\.|[^\"\\\\\\n])*\"|'(\\\\.|[^'\\\\\\n])*'")
        private val COMMENT = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")

        /** Canonical reference shared by documentation + runtime audit tests. */
        val API_DOC: String get() = ApiReference.API_DOC

        val GLSL_DOC = """
Shaders are GLSL ES 1.0 effect functions:

  vec4 effect(vec4 color, vec2 uv) {
      return color;
  }

color  the lit / tinted pixel (sprite or mesh)
uv     texture coordinate (tiled for meshes)

UNIFORMS
  uTime        seconds since start
  uParam       per-object "Shader Param"
               (script: self.setShaderParam(v))
  uTex         the object's texture
  uUseTex      1.0 if a texture is bound
  uColor       tint color
  uResolution  viewport size in pixels

USE IT
  • SpriteRenderer / MeshRenderer → Shader
  • Camera → Post FX = Custom Shader,
    FX Shader = file (color = screen pixel)

Use 'discard' to cut out pixels.
Compile errors show in the Console.
""".trimIndent()
    }
}
