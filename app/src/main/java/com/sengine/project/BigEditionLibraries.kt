package com.sengine.project

import com.sengine.engine.core.MaterialAsset
import com.sengine.engine.core.SpriteAtlas
import org.json.JSONObject

/** Caches parsed .mat assets by modification time so live edits show up without a restart. */
class MaterialCache(private val project: Project) {
    private data class Entry(val stamp: Long, val asset: MaterialAsset)
    private val cache = HashMap<String, Entry>()

    private fun fileName(name: String): String =
        if (name.endsWith(".mat", ignoreCase = true)) name else "$name.mat"

    fun get(name: String): MaterialAsset? {
        if (name.isBlank()) return null
        val n = fileName(name)
        val f = project.assetFile(n)
        if (!f.isFile) return null
        val stamp = f.lastModified()
        cache[n]?.let { if (it.stamp == stamp) return it.asset }
        return try {
            MaterialAsset.fromJson(JSONObject(f.readText())).also { cache[n] = Entry(stamp, it) }
        } catch (_: Exception) { null }
    }

    fun clear() = cache.clear()
}

/** Caches parsed .atlas assets and their named UV frames. */
class AtlasCache(private val project: Project) {
    private data class Entry(val stamp: Long, val atlas: SpriteAtlas)
    private val cache = HashMap<String, Entry>()

    private fun fileName(name: String): String =
        if (name.endsWith(".atlas", ignoreCase = true)) name else "$name.atlas"

    fun atlas(name: String): SpriteAtlas? {
        if (name.isBlank()) return null
        val n = fileName(name)
        val f = project.assetFile(n)
        if (!f.isFile) return null
        val stamp = f.lastModified()
        cache[n]?.let { if (it.stamp == stamp) return it.atlas }
        return try {
            SpriteAtlas.fromJson(JSONObject(f.readText())).also { cache[n] = Entry(stamp, it) }
        } catch (_: Exception) { null }
    }

    fun uv(name: String, frame: String, out: FloatArray): Boolean =
        atlas(name)?.uv(frame, out) ?: false

    fun frameNames(name: String): List<String> = atlas(name)?.frameNames ?: emptyList()

    fun clear() = cache.clear()
}
