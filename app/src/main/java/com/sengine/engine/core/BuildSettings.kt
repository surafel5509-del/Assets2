package com.sengine.engine.core

import org.json.JSONObject

/**
 * Ultimate Edition Build Settings - Unity Build Settings + Unreal Packaging equivalent
 */

enum class BuildTarget {
    Android,
    WindowsStandalone64,
    LinuxStandalone64,
    MacStandalone,
    WebGL,
    iOS,
    XboxOne,
    PS4,
    PS5,
    Switch
}

enum class ScriptingBackend { Mono, IL2CPP }
enum class ApiCompatibilityLevel { NetStandard20, NetFramework }
enum class ManagedStrippingLevel { Disabled, Low, Medium, High }

class BuildSettings {
    var target: BuildTarget = BuildTarget.Android
    var developmentBuild: Boolean = false
    var allowDebugging: Boolean = false
    var connectWithProfiler: Boolean = false
    var enableDeepProfilingSupport: Boolean = false
    var buildWithDeepProfilingSupport: Boolean = false
    var scriptingBackend: ScriptingBackend = ScriptingBackend.IL2CPP
    var apiCompatibility: ApiCompatibilityLevel = ApiCompatibilityLevel.NetStandard20
    var strippingLevel: ManagedStrippingLevel = ManagedStrippingLevel.Medium
    var managedCodeStripping: Boolean = true
    var engineCodeStripping: Boolean = true
    var useDeterministicCompilation: Boolean = true
    var enableBurstCompilation: Boolean = true
    var enableSafetyChecks: Boolean = false
    var enableBurstDebug: Boolean = false

    // Android specific
    var androidMinSdk: Int = 26
    var androidTargetSdk: Int = 34
    var androidBuildSystem: Int = 0 // 0 Gradle, 1 Internal
    var androidExportAsGoogleAndroidProject: Boolean = false
    var androidCreateSymbols: Boolean = false
    var androidBundleVersionCode: Int = 1

    // Optimization
    var stripEngineCode: Boolean = true
    var vertexCompression: Int = 0
    var optimizeMeshData: Boolean = true
    var textureMipMapStripping: Boolean = true
    var stripUnusedMeshComponents: Boolean = true

    // Scenes
    var scenesInBuild: MutableList<String> = mutableListOf("Main")

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("target", target.name)
        o.put("developmentBuild", developmentBuild)
        o.put("scriptingBackend", scriptingBackend.name)
        o.put("androidMinSdk", androidMinSdk)
        o.put("androidTargetSdk", androidTargetSdk)
        val arr = org.json.JSONArray()
        for (s in scenesInBuild) arr.put(s)
        o.put("scenesInBuild", arr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): BuildSettings {
            val bs = BuildSettings()
            try { bs.target = BuildTarget.valueOf(o.optString("target", "Android")) } catch (_: Exception) {}
            bs.developmentBuild = o.optBoolean("developmentBuild", false)
            bs.androidMinSdk = o.optInt("androidMinSdk", 26)
            bs.androidTargetSdk = o.optInt("androidTargetSdk", 34)
            val arr = o.optJSONArray("scenesInBuild")
            if (arr != null) {
                bs.scenesInBuild.clear()
                for (i in 0 until arr.length()) bs.scenesInBuild.add(arr.getString(i))
            }
            return bs
        }
    }
}

class PlayerSettings {
    var companyName: String = "S Engine Studio"
    var productName: String = "Ultimate Game"
    var version: String = "1.0.0"
    var bundleIdentifier: String = "com.sengine.ultimategame"
    var bundleVersion: String = "1.0"
    var defaultScreenWidth: Int = 1920
    var defaultScreenHeight: Int = 1080
    var runInBackground: Boolean = true
    var displayResolutionDialog: Int = 1
    var resizableWindow: Boolean = true
    var fullscreenMode: Int = 1
    var defaultIsNativeResolution: Boolean = true
    var macRetinaSupport: Boolean = true
    var visibleInBackground: Boolean = true
    var allowFullscreenSwitch: Boolean = true
    var forceSingleInstance: Boolean = false
    var useFlipModelSwapchain: Boolean = true

    // Splash
    var showSplashScreen: Boolean = true
    var splashScreenBackgroundColor: Int = 0xFF1B2533.toInt()

    // Icons
    var icons: MutableList<String> = mutableListOf()

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("companyName", companyName)
        o.put("productName", productName)
        o.put("version", version)
        o.put("bundleIdentifier", bundleIdentifier)
        o.put("defaultScreenWidth", defaultScreenWidth)
        o.put("defaultScreenHeight", defaultScreenHeight)
        return o
    }
}

class GraphicsSettings {
    var renderPipeline: String = "Universal"
    var colorSpace: Int = 1 // 0 gamma, 1 linear
    var tierSettings: MutableList<QualitySettings> = mutableListOf()

    var enableLodCrossFade: Boolean = true
    var enableFoveatedRendering: Boolean = false
    var enableDynamicBatching: Boolean = false
    var enableInstancing: Boolean = true
    var enableSrpBatcher: Boolean = true
    var enableAlphaToMask: Boolean = false

    // Shader stripping
    var shaderStrippingLightmapMode: Int = 0
    var shaderStrippingFogMode: Int = 0
    var shaderStrippingInstancingVariants: Int = 0
    var shaderStrippingLightmapKeepPlain: Boolean = true
    var shaderStrippingLightmapKeepDirCombined: Boolean = true
    var shaderStrippingLightmapKeepDynamicDirectionality: Boolean = true
    var shaderStrippingLightmapKeepDynamicPlain: Boolean = true
    var shaderStrippingLightmapKeepShadowMask: Boolean = true
    var shaderStrippingLightmapKeepSubtractive: Boolean = true
}
