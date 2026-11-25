package com.sengine.project

import org.json.JSONObject
import java.io.File

/**
 * Tiny durable key/value store for player progression (unlocked levels, best
 * lap times, high scores, settings). Survives scene loads, play/stop cycles and
 * app restarts; scripts use `game.save(key, value)` / `game.load(key, default)`.
 */
class GameStateStore(dir: File) {
    private val folder = File(dir, "userdata")
    private val file = File(folder, "save.json")
    private val data = JSONObject()

    init {
        try {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                for (k in o.keys()) data.put(k, o.get(k))
            }
        } catch (_: Exception) {
        }
    }

    fun save(key: String, value: Any?) {
        val v = when (value) {
            null -> JSONObject.NULL
            is Number -> value.toDouble()
            is Boolean -> value
            is String -> value
            is CharSequence -> value.toString()
            else -> value.toString()
        }
        data.put(key, v)
        persist()
    }

    fun load(key: String, fallback: Any? = null): Any? {
        if (!data.has(key)) return fallback
        val v = data.opt(key) ?: return fallback
        if (v === JSONObject.NULL) return null
        return when (v) {
            is Number -> v.toDouble()
            is Boolean -> v
            else -> v.toString()
        }
    }

    fun has(key: String): Boolean = data.has(key)
    fun keys(): List<String> = data.keys().asSequence().toList().sorted()

    fun clear() {
        for (k in keys()) data.remove(k)
        persist()
    }

    private fun persist() {
        try {
            folder.mkdirs()
            file.writeText(data.toString(2))
        } catch (_: Exception) {
        }
    }
}
