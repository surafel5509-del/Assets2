package com.sengine.project

import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.EngineConfig
import com.sengine.engine.core.EngineVersion
import com.sengine.engine.core.QualitySettings
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import org.json.JSONObject
import java.io.File

/**
 * On-device project storage, settings, versioned scenes, backups and recovery.
 */

class Project(val dir: File) {
    val name: String get() = dir.name
    val assetsDir = File(dir, "assets")
    val scenesDir = File(dir, "scenes")
    val prefabsDir = File(dir, "prefabs")
    val materialsDir = File(dir, "materials")
    val settingsDir = File(dir, "settings")
    private val metaFile = File(dir, "project.json")

    var startScene = "Main"
    var created = System.currentTimeMillis()
    var orientation = 0 // 0 landscape, 1 portrait
    var engineConfig = EngineConfig()
    var version = "1.0.0"
    var companyName = "S Engine Studio"
    var productName = name
    var bundleIdentifier = "com.sengine.$name"
    var buildTarget = "Android"
    var scriptingBackend = 0 // 0 Mono, 1 IL2CPP
    var apiCompatibility = 0 // 0 .NET Standard, 1 .NET Framework

    // Build settings
    var scenesInBuild = mutableListOf<String>()
    var qualitySettings = QualitySettings()
    var graphicsJobs = true
    var gpuInstancing = true
    var srpBatcher = true
    var occlusionCulling = true

    // Version control
    var lastSaved = System.currentTimeMillis()
    var autoSave = true
    var backupCount = 3

    init {
        if (metaFile.exists()) {
            try {
                val o = JSONObject(metaFile.readText())
                startScene = o.optString("startScene", "Main")
                created = o.optLong("created", created)
                orientation = o.optInt("orientation", 0)
                version = o.optString("version", "1.0.0")
                companyName = o.optString("companyName", "S Engine Studio")
                productName = o.optString("productName", name)
                bundleIdentifier = o.optString("bundleIdentifier", bundleIdentifier)
                buildTarget = o.optString("buildTarget", "Android")
                lastSaved = o.optLong("lastSaved", lastSaved)
                if (o.has("engineConfig")) engineConfig = EngineConfig.fromJson(o.getJSONObject("engineConfig"))
                if (o.has("qualitySettings")) qualitySettings = QualitySettings.fromJson(o.getJSONObject("qualitySettings"))
                if (o.has("scenesInBuild")) {
                    val arr = o.getJSONArray("scenesInBuild")
                    scenesInBuild.clear()
                    for (i in 0 until arr.length()) scenesInBuild.add(arr.getString(i))
                }
            } catch (_: Exception) {
            }
        }
        if (scenesInBuild.isEmpty()) scenesInBuild.add(startScene)
    }

    fun saveMeta() {
        dir.mkdirs(); assetsDir.mkdirs(); scenesDir.mkdirs()
        prefabsDir.mkdirs(); materialsDir.mkdirs(); settingsDir.mkdirs()
        val o = JSONObject()
        o.put("name", name)
        o.put("engine", EngineVersion.string())
        o.put("engineVersion", "${EngineVersion.MAJOR}.${EngineVersion.MINOR}.${EngineVersion.PATCH}")
        o.put("apiVersion", EngineVersion.API_VERSION)
        o.put("startScene", startScene)
        o.put("created", created)
        o.put("lastSaved", System.currentTimeMillis())
        o.put("orientation", orientation)
        o.put("version", version)
        o.put("companyName", companyName)
        o.put("productName", productName)
        o.put("bundleIdentifier", bundleIdentifier)
        o.put("buildTarget", buildTarget)
        o.put("engineConfig", engineConfig.toJson())
        o.put("qualitySettings", qualitySettings.toJson())
        val arr = org.json.JSONArray()
        for (s in scenesInBuild) arr.put(s)
        o.put("scenesInBuild", arr)
        o.put("graphicsJobs", graphicsJobs)
        o.put("gpuInstancing", gpuInstancing)
        o.put("srpBatcher", srpBatcher)
        o.put("occlusionCulling", occlusionCulling)
        AtomicFiles.write(metaFile, o.toString(2), 1)
        // Also save engine config separately
        AtomicFiles.write(File(settingsDir, "EngineConfig.json"), engineConfig.toJson().toString(2))
        AtomicFiles.write(File(settingsDir, "QualitySettings.json"), qualitySettings.toJson().toString(2))
    }

    fun sceneFile(n: String) = AtomicFiles.child(scenesDir, "$n.scene.json")
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
        AtomicFiles.write(sceneFile(scene.name), SceneSerializer.toJson(scene).toString(1), backupCount)
        recoveryFile(scene.name).delete()
        lastSaved = System.currentTimeMillis()
        if (!scenesInBuild.contains(scene.name)) scenesInBuild.add(scene.name)
    }

    private fun recoveryFile(n: String) = AtomicFiles.child(File(dir, ".recovery"), "$n.scene.json")

    /** Recovery is separate from the saved scene and never silently replaces it. */
    fun saveRecovery(scene: Scene) = AtomicFiles.write(recoveryFile(scene.name), SceneSerializer.toJson(scene).toString())
    fun hasRecovery(n: String): Boolean = recoveryFile(n).let { it.exists() && (!sceneFile(n).exists() || it.lastModified() >= sceneFile(n).lastModified()) }
    fun loadRecovery(n: String): Scene = SceneSerializer.fromJson(JSONObject(recoveryFile(n).readText())).also { it.name = n }
    fun discardRecovery(n: String) { recoveryFile(n).delete() }
    fun loadBackup(n: String): Scene = SceneSerializer.fromJson(JSONObject(File(sceneFile(n).path + ".bak1").readText())).also { it.name = n }

    fun deleteScene(n: String): Boolean {
        val deleted = sceneFile(n).delete()
        discardRecovery(n)
        scenesInBuild.remove(n)
        return deleted
    }

    fun assetFile(n: String) = AtomicFiles.child(assetsDir, n)
    fun prefabFile(n: String) = AtomicFiles.child(prefabsDir, if (n.endsWith(".prefab")) n else "$n.prefab")
    fun materialFile(n: String) = AtomicFiles.child(materialsDir, if (n.endsWith(".mat")) n else "$n.mat")

    fun listAssets(kind: AssetKind? = null): List<String> =
        (assetsDir.listFiles() ?: emptyArray())
            .filter { it.isFile && (kind == null || AssetKind.of(it.name) == kind) }
            .map { it.name }
            .sortedWith(compareBy({ AssetKind.of(it)?.ordinal ?: 9 }, { it.lowercase() }))

    fun listPrefabs(): List<String> =
        (prefabsDir.listFiles() ?: emptyArray()).filter { it.isFile }.map { it.name }.sorted()

    fun listMaterials(): List<String> =
        (materialsDir.listFiles() ?: emptyArray()).filter { it.isFile }.map { it.name }.sorted()

    fun readAsset(n: String): String? = assetFile(n).takeIf { it.exists() }?.readText()
    fun readPrefab(n: String): String? = prefabFile(n).takeIf { it.exists() }?.readText()
    fun readMaterial(n: String): String? = materialFile(n).takeIf { it.exists() }?.readText()

    fun writeAsset(n: String, text: String) {
        AtomicFiles.write(assetFile(n), text)
    }

    fun writePrefab(n: String, text: String) {
        AtomicFiles.write(prefabFile(n), text)
    }

    fun writeMaterial(n: String, text: String) {
        AtomicFiles.write(materialFile(n), text)
    }

    fun uniqueAssetName(base: String): String {
        if (!assetFile(base).exists()) return base
        val stem = base.substringBeforeLast('.')
        val ext = base.substringAfterLast('.', "")
        var i = 1
        while (assetFile("${stem}_$i.$ext").exists()) i++
        return "${stem}_$i.$ext"
    }

    fun uniquePrefabName(base: String): String {
        val name = if (base.endsWith(".prefab")) base else "$base.prefab"
        if (!prefabFile(name).exists()) return name
        var i = 1
        while (prefabFile("${base}_$i.prefab").exists()) i++
        return "${base}_$i.prefab"
    }

    fun getProjectSize(): Long {
        fun dirSize(file: File): Long {
            if (!file.exists()) return 0
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { dirSize(it) } ?: 0
        }
        return dirSize(dir)
    }

    fun getAssetCount(): Int = (assetsDir.listFiles()?.size ?: 0) + (prefabsDir.listFiles()?.size ?: 0) + (materialsDir.listFiles()?.size ?: 0)
}
