package com.sengine.engine.core

/**
 * S Engine Ultimate 6.1 [Quantum] - Modding & Plugin System
 * Mod loading, Plugin architecture, Script mods, Asset mods
 */
enum class ModType { Asset, Script, Code, TotalConversion, Patch }
enum class ModLoadState { Unloaded, Loading, Loaded, Failed, Disabled }

data class ModInfo(
    var id: String = "mod_0",
    var name: String = "My Mod",
    var version: String = "1.0.0",
    var author: String = "Modder",
    var description: String = "A cool mod",
    var type: ModType = ModType.Asset,
    var dependencies: MutableList<String> = mutableListOf(),
    var loadState: ModLoadState = ModLoadState.Unloaded,
    var filePath: String = "",
    var enabled: Boolean = true,
    var loadOrder: Int = 0
)

data class PluginInfo(
    var id: String = "plugin_0",
    var name: String = "My Plugin",
    var version: String = "1.0.0",
    var author: String = "Developer",
    var description: String = "A plugin",
    var entryPoint: String = "",
    var isEnabled: Boolean = true,
    var isLoaded: Boolean = false,
    var apiVersion: Int = 610
)

interface IPlugin {
    fun onLoad()
    fun onUnload()
    fun onUpdate(dt: Float)
    fun getName(): String
    fun getVersion(): String
}

class ModdingSystem {
    var mods = mutableMapOf<String, ModInfo>()
    var plugins = mutableMapOf<String, PluginInfo>()
    var loadedPlugins = mutableMapOf<String, IPlugin>()
    var modDirectory: String = "mods"
    var enabled: Boolean = true
    var loadOrder = mutableListOf<String>()

    fun registerMod(id: String, name: String, version: String, author: String, type: ModType = ModType.Asset): ModInfo {
        val mod = ModInfo(id=id, name=name, version=version, author=author, type=type); mods[id]=mod; return mod
    }

    fun loadMod(id: String): Boolean {
        val mod = mods[id] ?: return false
        if (mod.loadState == ModLoadState.Loaded) return true
        // Check dependencies
        for (dep in mod.dependencies) {
            val depMod = mods[dep]
            if (depMod == null || depMod.loadState != ModLoadState.Loaded) {
                if (!loadMod(dep)) { mod.loadState=ModLoadState.Failed; return false }
            }
        }
        mod.loadState = ModLoadState.Loading
        // Simulate loading
        mod.loadState = ModLoadState.Loaded
        loadOrder.add(id)
        return true
    }

    fun unloadMod(id: String): Boolean {
        val mod = mods[id] ?: return false
        mod.loadState = ModLoadState.Unloaded
        loadOrder.remove(id)
        return true
    }

    fun enableMod(id: String) { mods[id]?.enabled=true }
    fun disableMod(id: String) { mods[id]?.enabled=false; unloadMod(id) }

    fun registerPlugin(id: String, name: String, version: String, entryPoint: String): PluginInfo {
        val plugin = PluginInfo(id=id, name=name, version=version, entryPoint=entryPoint); plugins[id]=plugin; return plugin
    }

    fun loadPlugin(id: String, plugin: IPlugin): Boolean {
        val info = plugins[id] ?: return false
        if (info.isLoaded) return true
        try {
            plugin.onLoad()
            loadedPlugins[id]=plugin
            info.isLoaded=true
            return true
        } catch (e: Exception) {
            info.isLoaded=false
            return false
        }
    }

    fun unloadPlugin(id: String): Boolean {
        val plugin = loadedPlugins[id] ?: return false
        try { plugin.onUnload() } catch (e: Exception) {}
        loadedPlugins.remove(id)
        plugins[id]?.isLoaded=false
        return true
    }

    fun updatePlugins(dt: Float) {
        for (plugin in loadedPlugins.values) try { plugin.onUpdate(dt) } catch (e: Exception) {}
    }

    fun getLoadedMods(): List<ModInfo> = mods.values.filter { it.loadState == ModLoadState.Loaded }
    fun getEnabledMods(): List<ModInfo> = mods.values.filter { it.enabled }
    fun getStats(): String = "Mods:${mods.size} Loaded:${getLoadedMods().size} Plugins:${plugins.size} Loaded:${loadedPlugins.size}"
}

// Example built-in plugins
class PerformanceOverlayPlugin : IPlugin {
    override fun onLoad() {}
    override fun onUnload() {}
    override fun onUpdate(dt: Float) {}
    override fun getName(): String = "PerformanceOverlay"
    override fun getVersion(): String = "1.0.0"
}

class DebugToolsPlugin : IPlugin {
    override fun onLoad() {}
    override fun onUnload() {}
    override fun onUpdate(dt: Float) {}
    override fun getName(): String = "DebugTools"
    override fun getVersion(): String = "1.0.0"
}
