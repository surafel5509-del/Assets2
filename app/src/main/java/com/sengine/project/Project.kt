package com.sengine.project

import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import org.json.JSONObject
import java.io.File

/**
 * A game project stored in app-private storage.
 *
 * Assets live under `assets/` in the folder branches defined by [AssetFolders].
 * Every asset is referred to by a *relative path* ("Sprites/hero.png"), but a bare
 * file name ("hero.png") also resolves: it is searched for anywhere in the tree.
 * That is what lets the folder reorganisation leave every existing scene, script
 * and game reference working.
 */
class Project(val dir: File) {
    val name: String get() = dir.name
    val assetsDir = File(dir, "assets")
    val scenesDir = File(dir, "scenes")
    private val metaFile = File(dir, "project.json")

    var startScene = "Main"
    var created = System.currentTimeMillis()
    var orientation = 0 // 0 landscape, 1 portrait

    /** Name of the `Controls/*.ctrl.json` layout used in play mode. Empty = the built-in joystick and A/B buttons. */
    var controlsLayout = ""

    init {
        if (metaFile.exists()) {
            try {
                val o = JSONObject(metaFile.readText())
                startScene = o.optString("startScene", "Main")
                created = o.optLong("created", created)
                orientation = o.optInt("orientation", 0)
                controlsLayout = o.optString("controlsLayout", "")
            } catch (_: Exception) {
            }
        }
    }

    fun saveMeta() {
        dir.mkdirs(); assetsDir.mkdirs(); scenesDir.mkdirs(); ensureFolders()
        val o = JSONObject()
        o.put("name", name)
        o.put("engine", "S Engine 1.1")
        o.put("startScene", startScene)
        o.put("created", created)
        o.put("orientation", orientation)
        o.put("controlsLayout", controlsLayout)
        metaFile.writeText(o.toString(2))
    }

    /** Creates every branch of the asset tree, so empty folders are visible in the editor. */
    fun ensureFolders() {
        for (f in AssetFolders.ALL) File(assetsDir, f.path).mkdirs()
    }

    fun sceneFile(n: String) = File(scenesDir, "$n.scene.json")
    fun sceneExists(n: String) = sceneFile(n).exists()

    fun listScenes(): List<String> =
        (scenesDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".scene.json") }
            .map { it.name.removeSuffix(".scene.json") }
            .sorted()

    fun loadScene(n: String): Scene {
        val f = sceneFile(n)
        if (!f.exists()) return Scene(n)
        return SceneSerializer.fromJson(JSONObject(f.readText())).also { it.name = n }
    }

    fun saveScene(scene: Scene) {
        scenesDir.mkdirs()
        sceneFile(scene.name).writeText(SceneSerializer.toJson(scene).toString(1))
    }

    fun deleteScene(n: String) = sceneFile(n).delete()

    // ------------------------------------------------------------------
    // Asset resolution
    // ------------------------------------------------------------------

    /**
     * The existing file for an asset reference, or null. Accepts a relative path
     * exactly as written, or a bare name searched for in every folder (shallowest
     * match first).
     */
    fun resolveAsset(n: String): File? {
        if (n.isBlank()) return null
        if (AssetFolders.isSafeRelative(n)) {
            val direct = File(assetsDir, n)
            if (direct.isFile) return direct
        }
        if (n.contains('/')) return null
        return findByName(n)
    }

    private fun findByName(base: String): File? =
        assetsDir.walkTopDown()
            .filter { it.isFile && it.name == base }
            .minByOrNull { it.relativeTo(assetsDir).path.count { c -> c == '/' } }

    /**
     * Where a *new* asset named [n] should be written: its own path when it has
     * a folder part, otherwise the branch its extension belongs to.
     */
    fun placedPath(n: String): String =
        if (n.contains('/')) n else AssetFolders.join(AssetFolders.folderFor(n), n)

    /** The file for an asset reference: the existing file, or the place a new one would go. */
    fun assetFile(n: String): File = resolveAsset(n) ?: File(assetsDir, placedPath(n))

    /** Every asset file as a relative path, filtered by kind, in branch-then-name order. */
    fun listAssets(kind: AssetKind? = null): List<String> =
        walkAssets()
            .filter { kind == null || AssetKind.of(it) == kind }
            .sortedWith(compareBy({ AssetKind.of(it)?.ordinal ?: 99 }, { it.lowercase() }))

    /** Every folder in the asset tree as a relative path, including empty ones. */
    fun listAssetFolders(): List<String> =
        assetsDir.walkTopDown()
            .filter { it.isDirectory && it != assetsDir }
            .map { it.relativeTo(assetsDir).path.replace('\\', '/') }
            .sorted()
            .toList()

    private fun walkAssets(): List<String> =
        assetsDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { it.relativeTo(assetsDir).path.replace('\\', '/') }
            .toList()

    fun readAsset(n: String): String? = resolveAsset(n)?.readText()

    fun writeAsset(n: String, text: String) {
        val f = assetFile(n)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    fun writeAssetBytes(n: String, bytes: ByteArray) {
        val f = assetFile(n)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    /**
     * A reference that does not collide with any existing asset *name* anywhere
     * in the tree (so bare-name resolution stays unambiguous). Bare names are
     * placed into their branch first.
     */
    fun uniqueAssetName(base: String): String {
        val placed = placedPath(base)
        return AssetFolders.unique(placed) { p -> findByName(AssetFolders.nameOf(p)) != null }
    }

    /** Creates a folder in the asset tree. Returns false when the path is unsafe or already exists. */
    fun createFolder(path: String): Boolean {
        if (!AssetFolders.isSafeRelative(path)) return false
        val f = File(assetsDir, path)
        if (f.exists()) return false
        return f.mkdirs()
    }

    /** Moves an asset into another folder, keeping its name. Returns the new relative path, or null on failure. */
    fun moveAsset(n: String, folder: String): String? {
        val src = resolveAsset(n) ?: return null
        if (folder.isNotEmpty() && !AssetFolders.isSafeRelative(folder)) return null
        val rel = src.relativeTo(assetsDir).path.replace('\\', '/')
        val target = AssetFolders.join(folder, AssetFolders.nameOf(rel))
        if (target == rel) return rel
        val dest = File(assetsDir, target)
        if (dest.exists()) return null
        dest.parentFile?.mkdirs()
        return if (src.renameTo(dest)) target else null
    }

    /** Deletes an asset file. Scenes that reference it keep the name and show a placeholder, which the validator reports. */
    fun deleteAsset(n: String): Boolean = resolveAsset(n)?.delete() ?: false
}
