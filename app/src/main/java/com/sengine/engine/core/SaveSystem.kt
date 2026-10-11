package com.sengine.engine.core

import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/**
 * Ultimate Edition Save System - Unity-like PlayerPrefs + SaveGame + Addressables + Cloud Save
 */

object PlayerPrefs {
    private val prefs = mutableMapOf<String, Any>()
    private var file: File? = null

    fun init(file: File) {
        this.file = file
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                for (k in json.keys()) prefs[k] = json.get(k)
            } catch (_: Exception) {}
        }
    }

    fun setInt(key: String, value: Int) { prefs[key] = value; save() }
    fun getInt(key: String, default: Int = 0): Int = (prefs[key] as? Number)?.toInt() ?: default

    fun setFloat(key: String, value: Float) { prefs[key] = value.toDouble(); save() }
    fun getFloat(key: String, default: Float = 0f): Float = (prefs[key] as? Number)?.toFloat() ?: default

    fun setString(key: String, value: String) { prefs[key] = value; save() }
    fun getString(key: String, default: String = ""): String = prefs[key] as? String ?: default

    fun setBool(key: String, value: Boolean) { prefs[key] = value; save() }
    fun getBool(key: String, default: Boolean = false): Boolean = prefs[key] as? Boolean ?: default

    fun hasKey(key: String): Boolean = key in prefs
    fun deleteKey(key: String) { prefs.remove(key); save() }
    fun deleteAll() { prefs.clear(); save() }

    private fun save() {
        val f = file ?: return
        try {
            val json = JSONObject()
            for ((k, v) in prefs) json.put(k, v)
            f.writeText(json.toString(2))
        } catch (_: Exception) {}
    }
}

class SaveGameSystem(private val saveDir: File) {
    data class SaveSlot(var name: String, var timestamp: Long = System.currentTimeMillis(), var data: JSONObject = JSONObject(), var screenshot: String = "", var playtime: Float = 0f)

    private val slots = mutableMapOf<String, SaveSlot>()

    init { saveDir.mkdirs(); loadAll() }

    private fun loadAll() {
        val files = saveDir.listFiles { f -> f.name.endsWith(".save") } ?: return
        for (f in files) {
            try {
                val json = JSONObject(f.readText())
                val slot = SaveSlot(
                    name = json.optString("name", f.nameWithoutExtension),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    data = json.optJSONObject("data") ?: JSONObject(),
                    screenshot = json.optString("screenshot", ""),
                    playtime = json.optDouble("playtime", 0.0).toFloat()
                )
                slots[slot.name] = slot
            } catch (_: Exception) {}
        }
    }

    fun save(slotName: String, data: JSONObject, playtime: Float = 0f): Boolean {
        return try {
            val slot = SaveSlot(slotName, System.currentTimeMillis(), data, "", playtime)
            slots[slotName] = slot
            val json = JSONObject()
            json.put("name", slot.name)
            json.put("timestamp", slot.timestamp)
            json.put("data", slot.data)
            json.put("playtime", slot.playtime.toDouble())
            File(saveDir, "$slotName.save").writeText(json.toString(2))
            true
        } catch (e: Exception) { false }
    }

    fun load(slotName: String): JSONObject? = slots[slotName]?.data

    fun delete(slotName: String): Boolean {
        slots.remove(slotName)
        return File(saveDir, "$slotName.save").delete()
    }

    fun listSlots(): List<SaveSlot> = slots.values.sortedByDescending { it.timestamp }

    fun exists(slotName: String): Boolean = slotName in slots

    fun getMostRecent(): SaveSlot? = slots.values.maxByOrNull { it.timestamp }
}

class AddressablesSystem {
    data class AssetEntry(var address: String, var guid: String, var path: String, var labels: List<String> = emptyList(), var bundle: String = "")

    private val assets = mutableMapOf<String, AssetEntry>()
    private val bundles = mutableMapOf<String, MutableList<AssetEntry>>()
    private val loaded = mutableMapOf<String, Any>()

    fun register(entry: AssetEntry) {
        assets[entry.address] = entry
        assets[entry.guid] = entry
        bundles.getOrPut(entry.bundle) { mutableListOf() }.add(entry)
    }

    fun loadAsset(address: String): Any? {
        if (address in loaded) return loaded[address]
        val entry = assets[address] ?: return null
        // Simulate loading
        val data = "Loaded: ${entry.path}"
        loaded[address] = data
        return data
    }

    fun loadAssetsByLabel(label: String): List<Any> {
        return assets.values.filter { label in it.labels }.mapNotNull { loadAsset(it.address) }
    }

    fun release(address: String) { loaded.remove(address) }
    fun releaseAll() { loaded.clear() }
    fun getBundleSize(bundle: String): Int = bundles[bundle]?.size ?: 0
    fun listBundles(): List<String> = bundles.keys.toList()
    fun listAssets(): List<String> = assets.keys.filter { !it.contains("-") }.toList() // addresses not guids
}

class CloudSaveSystem {
    var enabled: Boolean = false
    var autoSync: Boolean = true
    var lastSync: Long = 0

    data class CloudSlot(var key: String, var data: String, var timestamp: Long, var version: Int)

    private val cloud = mutableMapOf<String, CloudSlot>()

    fun save(key: String, data: String) {
        val slot = cloud[key]
        val version = (slot?.version ?: 0) + 1
        cloud[key] = CloudSlot(key, data, System.currentTimeMillis(), version)
        lastSync = System.currentTimeMillis()
    }

    fun load(key: String): String? = cloud[key]?.data

    fun sync(): Boolean {
        // Simulate cloud sync
        lastSync = System.currentTimeMillis()
        return true
    }

    fun listKeys(): List<String> = cloud.keys.toList()
}
