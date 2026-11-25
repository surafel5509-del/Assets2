package com.sengine.project

import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import org.json.JSONObject
import java.io.File

/** A game project stored in app-private storage. v3 projects carry a schema version + settings. */
class Project(val dir: File) {
    val name: String get() = dir.name
    val assetsDir = File(dir, "assets")
    val scenesDir = File(dir, "scenes")
    private val metaFile = File(dir, "project.json")

    var startScene = "Main"
    var created = System.currentTimeMillis()
    var orientation = 0 // 0 landscape, 1 portrait
    var schemaVersion = EngineInfo.SCHEMA_VERSION
    var engineVersion = EngineInfo.VERSION
    /** Applied at play start; editable from the editor's Project Settings dialog. */
    var settings = ProjectSettings()
    /** Descriptions of migrations applied when this project was opened. */
    val appliedMigrations = ArrayList<String>()

    init {
        if (metaFile.exists()) {
            try {
                val o = JSONObject(metaFile.readText())
                startScene = o.optString("startScene", "Main")
                created = o.optLong("created", created)
                orientation = o.optInt("orientation", 0)
                engineVersion = o.optString("engineVersion", EngineInfo.VERSION)
                schemaVersion = o.optInt("schemaVersion", 0)
                settings = ProjectSettings.fromJson(o.optJSONObject("settings"))
                if (settings.autoMigrate && schemaVersion < EngineInfo.SCHEMA_VERSION) {
                    appliedMigrations.addAll(ProjectSchema.migrate(o))
                    schemaVersion = o.optInt("schemaVersion", EngineInfo.SCHEMA_VERSION)
                    engineVersion = o.optString("engineVersion", EngineInfo.VERSION)
                    settings = ProjectSettings.fromJson(o.optJSONObject("settings"))
                    metaFile.writeText(o.toString(2)) // persist the upgrade once
                }
                if (!o.has("settings")) saveMeta()
            } catch (_: Exception) {
            }
        }
    }

    fun saveMeta() {
        dir.mkdirs(); assetsDir.mkdirs(); scenesDir.mkdirs()
        val o = JSONObject()
        o.put("name", name)
        o.put("engine", "${EngineInfo.ENGINE_ID} ${EngineInfo.VERSION}")
        o.put("engineVersion", EngineInfo.VERSION)
        o.put("edition", EngineInfo.EDITION)
        o.put("schemaVersion", EngineInfo.SCHEMA_VERSION)
        o.put("startScene", startScene)
        o.put("created", created)
        o.put("orientation", orientation)
        o.put("settings", settings.toJson())
        metaFile.writeText(o.toString(2))
    }

    /** Persists edited [settings] without touching scenes or assets. */
    fun saveSettings() = saveMeta()

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

    fun assetFile(n: String) = File(assetsDir, n)

    fun listAssets(kind: AssetKind? = null): List<String> =
        (assetsDir.listFiles() ?: emptyArray())
            .filter { it.isFile && (kind == null || AssetKind.of(it.name) == kind) }
            .map { it.name }
            .sortedWith(compareBy({ AssetKind.of(it)?.ordinal ?: 9 }, { it.lowercase() }))

    fun readAsset(n: String): String? = assetFile(n).takeIf { it.exists() }?.readText()

    fun writeAsset(n: String, text: String) {
        assetsDir.mkdirs(); assetFile(n).writeText(text)
    }

    fun uniqueAssetName(base: String): String {
        if (!assetFile(base).exists()) return base
        val stem = base.substringBeforeLast('.')
        val ext = base.substringAfterLast('.', "")
        var i = 1
        while (assetFile("${stem}_$i.$ext").exists()) i++
        return "${stem}_$i.$ext"
    }
}
