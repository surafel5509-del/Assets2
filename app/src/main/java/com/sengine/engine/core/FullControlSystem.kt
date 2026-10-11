package com.sengine.engine.core

import org.json.JSONObject

/**
 * S Engine Ultimate 6.1.1 [Titan] - Full Engine Control & Most Powerful Edition
 * Full Control Panel, Console V2, Inspector, Hierarchy, Project, SceneView, Package Manager, Hot Reload
 */
enum class ControlPanelTab { Hierarchy, Inspector, Project, Console, Profiler, SceneView, GameView, PackageManager, AssetStore, BuildSettings, Preferences }
enum class LogLevel { Log, Warning, Error, Exception, Assert }

data class ConsoleLog(
    var message: String = "",
    var level: LogLevel = LogLevel.Log,
    var timestamp: Long = System.currentTimeMillis(),
    var stackTrace: String = "",
    var count: Int = 1
)

data class InspectorProperty(
    var name: String = "Property",
    var type: String = "Float",
    var value: Any = 0f,
    var isEditable: Boolean = true,
    var category: String = "General",
    var tooltip: String = ""
)

data class HierarchyNode(
    var id: Long = 0L,
    var name: String = "GameObject",
    var parent: Long = -1L,
    var children: MutableList<Long> = mutableListOf(),
    var isActive: Boolean = true,
    var isExpanded: Boolean = true,
    var isSelected: Boolean = false,
    var icon: String = "GameObject",
    var order: Int = 0
)

data class ProjectAsset(
    var guid: String = "",
    var path: String = "",
    var name: String = "",
    var type: String = "Texture",
    var size: Long = 0L,
    var isFolder: Boolean = false,
    var children: MutableList<String> = mutableListOf(),
    var isSelected: Boolean = false,
    var isFavorite: Boolean = false,
    var labels: MutableList<String> = mutableListOf()
)

data class SceneViewSettings(
    var showGrid: Boolean = true,
    var gridSize: Float = 1f,
    var showGizmos: Boolean = true,
    var gizmoSize: Float = 1f,
    var shadingMode: Int = 0, // 0 shaded, 1 wireframe, 2 shaded wireframe
    var showSkybox: Boolean = true,
    var showFog: Boolean = true,
    var cameraMode: Int = 0, // 0 perspective, 1 orthographic
    var fov: Float = 60f,
    var nearClip: Float = 0.1f,
    var farClip: Float = 1000f
)

data class BuildSettingsV2(
    var target: String = "Android",
    var developmentBuild: Boolean = false,
    var scriptingBackend: String = "Mono",
    var apiCompatibility: String = ".NET Standard 2.1",
    var scenes: MutableList<String> = mutableListOf(),
    var productName: String = "S Engine Game",
    var companyName: String = "S Engine Studio",
    var version: String = "1.0.0",
    var bundleId: String = "com.sengine.game",
    var autoIncrementVersion: Boolean = false
)

class FullControlSystem {
    var currentTab: ControlPanelTab = ControlPanelTab.Hierarchy
    var consoleLogs = mutableListOf<ConsoleLog>()
    var maxLogs: Int = 1000
    var hierarchy = mutableMapOf<Long, HierarchyNode>()
    var projectAssets = mutableMapOf<String, ProjectAsset>()
    var inspectorProperties = mutableListOf<InspectorProperty>()
    var sceneView = SceneViewSettings()
    var buildSettings = BuildSettingsV2()
    var selectedObjectId: Long = -1L
    var selectedAssetGuid: String = ""
    var isPlaying: Boolean = false
    var isPaused: Boolean = false
    var timeScale: Float = 1f
    var frameCount: Long = 0L
    var fps: Float = 60f
    var memoryMB: Float = 100f
    var drawCalls: Int = 0
    var triangles: Int = 0
    var batches: Int = 0
    var nextHierarchyId: Long = 0L

    fun log(message: String, level: LogLevel = LogLevel.Log) {
        // Check if same message already exists
        val existing = consoleLogs.find { it.message == message && it.level == level }
        if (existing != null) existing.count++
        else {
            consoleLogs.add(ConsoleLog(message=message, level=level, timestamp=System.currentTimeMillis()))
            if (consoleLogs.size > maxLogs) consoleLogs.removeAt(0)
        }
    }

    fun clearConsole() { consoleLogs.clear() }

    fun addHierarchyNode(name: String, parent: Long = -1L): HierarchyNode {
        val id = nextHierarchyId++
        val node = HierarchyNode(id=id, name=name, parent=parent)
        hierarchy[id]=node
        if (parent != -1L) hierarchy[parent]?.children?.add(id)
        return node
    }

    fun removeHierarchyNode(id: Long) {
        val node = hierarchy[id] ?: return
        // Remove children recursively
        for (childId in node.children.toList()) removeHierarchyNode(childId)
        // Remove from parent
        if (node.parent != -1L) hierarchy[node.parent]?.children?.remove(id)
        hierarchy.remove(id)
        if (selectedObjectId == id) selectedObjectId = -1L
    }

    fun selectObject(id: Long) {
        for (n in hierarchy.values) n.isSelected=false
        hierarchy[id]?.isSelected=true
        selectedObjectId=id
        // Update inspector
        updateInspectorForObject(id)
    }

    fun updateInspectorForObject(id: Long) {
        inspectorProperties.clear()
        val node = hierarchy[id] ?: return
        inspectorProperties.add(InspectorProperty(name="Name", type="String", value=node.name, category="General"))
        inspectorProperties.add(InspectorProperty(name="Active", type="Bool", value=node.isActive, category="General"))
        inspectorProperties.add(InspectorProperty(name="Tag", type="String", value="Untagged", category="General"))
        inspectorProperties.add(InspectorProperty(name="Layer", type="Int", value=0, category="General"))
        inspectorProperties.add(InspectorProperty(name="Position X", type="Float", value=0f, category="Transform"))
        inspectorProperties.add(InspectorProperty(name="Position Y", type="Float", value=0f, category="Transform"))
        inspectorProperties.add(InspectorProperty(name="Rotation", type="Float", value=0f, category="Transform"))
        inspectorProperties.add(InspectorProperty(name="Scale X", type="Float", value=1f, category="Transform"))
        inspectorProperties.add(InspectorProperty(name="Scale Y", type="Float", value=1f, category="Transform"))
    }

    fun addProjectAsset(path: String, name: String, type: String, isFolder: Boolean = false): ProjectAsset {
        val guid = "guid_${System.currentTimeMillis()}_${(Math.random()*1000).toInt()}"
        val asset = ProjectAsset(guid=guid, path=path, name=name, type=type, isFolder=isFolder)
        projectAssets[guid]=asset; return asset
    }

    fun searchAssets(query: String): List<ProjectAsset> {
        return projectAssets.values.filter { it.name.contains(query, ignoreCase=true) || it.type.contains(query, ignoreCase=true) }
    }

    fun getHierarchyTree(): List<HierarchyNode> {
        return hierarchy.values.filter { it.parent == -1L }.sortedBy { it.order }
    }

    fun getConsoleByLevel(level: LogLevel): List<ConsoleLog> = consoleLogs.filter { it.level == level }

    fun getStats(): String = "Control Panel Tab:$currentTab Hierarchy:${hierarchy.size} Assets:${projectAssets.size} Logs:${consoleLogs.size} FPS:$fps Memory:${memoryMB}MB DC:$drawCalls Tris:$triangles"
}

class PackageManagerSystem {
    data class Package(
        var id: String = "com.sengine.package",
        var name: String = "Package",
        var version: String = "1.0.0",
        var description: String = "",
        var author: String = "S Engine",
        var isInstalled: Boolean = false,
        var isUpdateAvailable: Boolean = false,
        var latestVersion: String = "1.0.0",
        var dependencies: MutableList<String> = mutableListOf(),
        var sizeMB: Float = 1f
    )

    var packages = mutableMapOf<String, Package>()
    var installedPackages = mutableListOf<String>()

    init {
        // Built-in packages
        registerPackage("com.unity.2d.sprite", "2D Sprite", "2.0.0", "2D Sprite package", "Unity", true)
        registerPackage("com.unity.2d.tilemap", "2D Tilemap", "1.0.0", "Tilemap system", "Unity", true)
        registerPackage("com.unity.2d.animation", "2D Animation", "9.0.0", "2D Animation with bones", "Unity", true)
        registerPackage("com.unity.2d.psdimporter", "PSD Importer", "8.0.0", "Photoshop importer", "Unity", true)
        registerPackage("com.unity.cinemachine", "Cinemachine", "2.10.0", "Advanced camera system", "Unity", true)
        registerPackage("com.unity.render-pipelines.universal", "Universal RP", "16.0.0", "Universal Render Pipeline", "Unity", true)
        registerPackage("com.sengine.quantum", "Quantum Features", "6.1.0", "RayTracing, Nanite, Lumen, Chaos", "S Engine", true)
        registerPackage("com.sengine.titan", "Titan 2D", "6.1.1", "Unity 2D systems, Full Control", "S Engine", false)
    }

    fun registerPackage(id: String, name: String, version: String, description: String, author: String, installed: Boolean): Package {
        val pkg = Package(id=id, name=name, version=version, description=description, author=author, isInstalled=installed)
        packages[id]=pkg; if (installed) installedPackages.add(id); return pkg
    }

    fun installPackage(id: String): Boolean {
        val pkg = packages[id] ?: return false
        pkg.isInstalled=true; if (id !in installedPackages) installedPackages.add(id); return true
    }

    fun uninstallPackage(id: String): Boolean {
        val pkg = packages[id] ?: return false
        pkg.isInstalled=false; installedPackages.remove(id); return true
    }

    fun updatePackage(id: String): Boolean {
        val pkg = packages[id] ?: return false
        if (!pkg.isUpdateAvailable) return false
        pkg.version=pkg.latestVersion; pkg.isUpdateAvailable=false; return true
    }

    fun getInstalled(): List<Package> = installedPackages.mapNotNull { packages[it] }
    fun getAvailable(): List<Package> = packages.values.filter { !it.isInstalled }
    fun getStats(): String = "Packages:${packages.size} Installed:${installedPackages.size}"
}

class HotReloadSystem {
    var enabled: Boolean = true
    var autoReload: Boolean = true
    var watchedFiles = mutableMapOf<String, Long>() // path -> lastModified
    var reloadCount: Int = 0
    var lastReloadTime: Long = 0L
    var onReload: ((String)->Unit)? = null

    fun watchFile(path: String, lastModified: Long) { watchedFiles[path]=lastModified }

    fun checkForChanges(currentFiles: Map<String, Long>): List<String> {
        if (!enabled) return emptyList()
        val changed = mutableListOf<String>()
        for ((path, modTime) in currentFiles) {
            val lastMod = watchedFiles[path]
            if (lastMod == null || modTime > lastMod) { changed.add(path); watchedFiles[path]=modTime }
        }
        return changed
    }

    fun triggerReload(changedFiles: List<String>) {
        if (changedFiles.isEmpty()) return
        reloadCount++; lastReloadTime=System.currentTimeMillis()
        for (file in changedFiles) onReload?.invoke(file)
    }

    fun getStats(): String = "HotReload Enabled:$enabled Auto:$autoReload Watched:${watchedFiles.size} Reloads:$reloadCount"
}
