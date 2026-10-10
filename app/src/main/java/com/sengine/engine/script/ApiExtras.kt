package com.sengine.engine.script

import com.sengine.engine.Engine
import org.json.JSONObject
import org.mozilla.javascript.Context
import java.io.File

/**
 * `ui` -- drive UI Builder screens from a script.
 *
 * Element ids are unique across all open screens, so scripts address an element
 * by its id alone: `ui.setText("score", "120")`. Screens are opened with
 * `ui.open("HUD")`, which loads `UI/HUD.ui.json` (or any asset matching the name).
 */
class SUi(private val engine: Engine) {
    private val overlays get() = engine.overlays

    fun open(name: String): Boolean = overlays.open(name)
    fun close(name: String) = overlays.close(name)
    fun isOpen(name: String): Boolean = overlays.isOpen(name)

    fun setText(id: String, text: Any?) = overlays.setText(id, Context.toString(text))
    fun getText(id: String): String = overlays.getText(id)

    fun setValue(id: String, v: Double) = overlays.setValue(id, v.toFloat())
    fun getValue(id: String): Double = overlays.getValue(id).toDouble()

    fun show(id: String) = overlays.setVisible(id, true)
    fun hide(id: String) = overlays.setVisible(id, false)
    fun isVisible(id: String): Boolean = overlays.isVisible(id)

    fun setColor(id: String, hex: String) {
        val c = try { com.sengine.engine.core.Component.parseColor(hex) } catch (_: Exception) { return }
        overlays.setColor(id, c)
    }

    fun setBackground(id: String, hex: String) {
        val c = try { com.sengine.engine.core.Component.parseColor(hex) } catch (_: Exception) { return }
        overlays.setBackground(id, c)
    }
}

/**
 * `storage` -- small persistent key/value store for save games and settings.
 *
 * Values are JSON primitives (numbers, strings, booleans). Data lives in
 * `saves/<slot>.json` inside the project, so it survives app restarts and is
 * included when a project is exported or backed up. `save()` writes to disk;
 * nothing is written automatically, so a script decides when a game is saved.
 */
class SStorage(private val engine: Engine) {
    private val data = JSONObject()
    private var loaded = false
    private var slot = "default"

    private fun file(): File = File(engine.project.dir, "saves/$slot.json")

    fun slot(name: String) {
        val safe = name.replace(Regex("[^A-Za-z0-9_\\-]"), "").ifBlank { "default" }
        if (safe != slot) { slot = safe; loaded = false; data.keys().forEach { data.remove(it) } }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val f = file()
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            for (k in o.keys()) data.put(k, o.get(k))
        } catch (e: Exception) {
            engine.log(2, "storage: could not read ${f.name}: ${e.message}")
        }
    }

    fun load(): Boolean {
        loaded = false
        data.keys().forEach { data.remove(it) }
        ensureLoaded()
        return file().exists()
    }

    fun save(): Boolean {
        ensureLoaded()
        return try {
            file().parentFile?.mkdirs()
            file().writeText(data.toString(2))
            true
        } catch (e: Exception) {
            engine.log(2, "storage: save failed: ${e.message}")
            false
        }
    }

    fun get(key: String, default: Any?): Any? {
        ensureLoaded()
        return if (data.has(key)) data.get(key) else default
    }

    fun set(key: String, value: Any?) {
        ensureLoaded()
        when (value) {
            null -> data.remove(key)
            is Double -> data.put(key, value)
            is Number -> data.put(key, value.toDouble())
            is Boolean -> data.put(key, value)
            else -> data.put(key, Context.toString(value))
        }
    }

    fun has(key: String): Boolean { ensureLoaded(); return data.has(key) }
    fun remove(key: String) { ensureLoaded(); data.remove(key) }
    fun clear() { ensureLoaded(); data.keys().forEach { data.remove(it) } }
    fun keyCount(): Double { ensureLoaded(); return data.length().toDouble() }
}
