package com.sengine.project

import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import org.json.JSONObject
import java.io.File

/** A game project stored in app-private storage. */
class Project(val dir: File) {
    val name: String get() = dir.name
    val assetsDir = File(dir, "assets")
    val scenesDir = File(dir, "scenes")
    private val metaFile = File(dir, "project.json")

    var startScene = "Main"
    var created = System.currentTimeMillis()
    var orientation = 0 // 0 landscape, 1 portrait
    /** True when [loadScene] had to fall back to the previous saved copy. */
    var lastLoadRecovered = false
        private set

    init {
        val meta = listOf(metaFile, backupFile(metaFile)).firstOrNull { it.isFile }
        if (meta != null) {
            try {
                val o = JSONObject(meta.readText())
                startScene = o.optString("startScene", "Main")
                created = o.optLong("created", created)
                orientation = o.optInt("orientation", 0)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ENGINE_VERSION = "S Engine 3.0.0"

        /** Reduces any user/script-supplied name to a single safe file name. */
        fun safeName(raw: String): String {
            val base = raw.replace('\\', '/').substringAfterLast('/')
            if (base.isEmpty() || base == "." || base == ".." || base.contains('\u0000')) return ""
            return base.filter { it.isLetterOrDigit() || it in "._-() " }
        }

        fun isSafeName(raw: String): Boolean = safeName(raw).isNotBlank() && safeName(raw) == raw
    }

    private fun backupFile(f: File) = File(f.parentFile, ".${f.name}.bak")

    /**
     * Writes text atomically (temp file + rename) and keeps one previous copy
     * in a dot-prefixed `.bak` file. Dot files are excluded from builds and
     * from asset/scene listings, and [loadScene] falls back to the backup when
     * the main file is truncated or corrupt (power loss, killed app, bad edit).
     */
    private fun atomicWrite(file: File, text: String, backup: Boolean) {
        file.parentFile?.mkdirs()
        if (backup && file.isFile) {
            try { file.copyTo(backupFile(file), overwrite = true) } catch (_: Exception) {}
        }
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun saveMeta() {
        dir.mkdirs(); assetsDir.mkdirs(); scenesDir.mkdirs()
        val o = JSONObject()
        o.put("name", name)
        o.put("engine", ENGINE_VERSION)
        o.put("startScene", startScene)
        o.put("created", created)
        o.put("orientation", orientation)
        atomicWrite(metaFile, o.toString(2), backup = true)
    }

    fun sceneFile(n: String) = File(scenesDir, "${safeName(n)}.scene.json")
    fun sceneExists(n: String) = sceneFile(n).isFile

    fun listScenes(): List<String> =
        (scenesDir.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.startsWith(".") && it.name.endsWith(".scene.json") }
            .map { it.name.removeSuffix(".scene.json") }
            .sorted()

    fun loadScene(n: String): Scene {
        lastLoadRecovered = false
        val f = sceneFile(n)
        if (!f.isFile) return Scene(n)
        fun parse(source: File): Scene =
            SceneSerializer.fromJson(JSONObject(source.readText())).also { it.name = safeName(n) }
        return try {
            parse(f)
        } catch (e: Exception) {
            val bak = backupFile(f)
            if (bak.isFile) {
                try {
                    parse(bak).also { lastLoadRecovered = true }
                } catch (_: Exception) { Scene(safeName(n)) }
            } else Scene(safeName(n))
        }
    }

    fun saveScene(scene: Scene) {
        atomicWrite(sceneFile(scene.name), SceneSerializer.toJson(scene).toString(1), backup = true)
    }

    fun deleteScene(n: String) = sceneFile(n).delete()

    fun assetFile(n: String) = File(assetsDir, safeName(n))

    fun listAssets(kind: AssetKind? = null): List<String> =
        (assetsDir.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.startsWith(".") && (kind == null || AssetKind.of(it.name) == kind) }
            .map { it.name }
            .sortedWith(compareBy({ AssetKind.of(it)?.ordinal ?: 9 }, { it.lowercase() }))

    /** Reads a text asset by name; path traversal attempts resolve inside assets/. */
    fun readAsset(n: String): String? = assetFile(n).takeIf { it.isFile }?.readText()

    fun writeAsset(n: String, text: String) {
        require(isSafeName(n)) { "Invalid asset name: $n" }
        atomicWrite(assetFile(n), text, backup = false)
    }

    fun uniqueAssetName(base: String): String {
        val b = safeName(base)
        if (!assetFile(b).exists()) return b
        val dot = b.lastIndexOf('.')
        val stem = if (dot > 0) b.substring(0, dot) else b
        val ext = if (dot > 0) b.substring(dot) else ""
        var i = 1
        while (assetFile("${stem}_$i$ext").exists()) i++
        return "${stem}_$i$ext"
    }
}
