package com.sengine.engine.render

/**
 * Ultimate Edition Skybox & Atmosphere - Unity HDRP Sky + Unreal Sky Atmosphere equivalent
 */

enum class SkyboxType { Procedural, Cubemap, HDRI, Gradient, SixSided, PhysicallyBased }

class ProceduralSkybox {
    var sunSize: Float = 0.04f
    var sunSizeConvergence: Float = 5f
    var atmosphereThickness: Float = 1f
    var skyTint: Int = 0xFF6B8EDE.toInt()
    var groundColor: Int = 0xFF6E5A3E.toInt()
    var exposure: Float = 1.3f

    var sunDisk: Int = 2 // 0 none, 1 simple, 2 high quality
    var sunIntensity: Float = 1000f
}

class PhysicallyBasedSky {
    var type: Int = 0 // 0 Earth simple, 1 Earth advanced, 2 custom
    var planetaryRadius: Float = 6378100f
    var atmosphereThickness: Float = 100000f
    var groundAlbedo: Int = 0xFF8E8E8E.toInt()
    var rayleighScattering: FloatArray = floatArrayOf(0.005802f, 0.013558f, 0.033100f)
    var mieScattering: Float = 0.003996f
    var mieAnisotropy: Float = 0.8f
    var numberOfBounces: Int = 8
    var planetRotation: FloatArray = floatArrayOf(0f, 0f, 0f)
    var groundEmissionTexture: String = ""
    var aerialPerspective: Boolean = true
    var colorSaturation: Float = 1f
    var alphaSaturation: Float = 1f
    var alphaMultiplier: Float = 1f
    var horizonTint: Int = 0xFFFFFFFF.toInt()
    var horizonZenithShift: Float = 0f
}

class GradientSkybox {
    var topColor: Int = 0xFF3B7BD4.toInt()
    var middleColor: Int = 0xFFBFD8F0.toInt()
    var bottomColor: Int = 0xFF8E8E8E.toInt()
    var gradientDiffusion: Float = 1f
    var exposure: Float = 1f
}

class CubemapSkybox {
    var cubemap: String = ""
    var exposure: Float = 1f
    var rotation: Float = 0f
    var tint: Int = 0xFF808080.toInt()
}

class SkyboxComponent {
    var type: SkyboxType = SkyboxType.Procedural
    var procedural = ProceduralSkybox()
    var physicallyBased = PhysicallyBasedSky()
    var gradient = GradientSkybox()
    var cubemap = CubemapSkybox()
    var intensity: Float = 1f
    var enableFog: Boolean = true
    var fogColor: Int = 0xFF9DB4CF.toInt()
    var fogDensity: Float = 0.01f
    var fogStart: Float = 0f
    var fogEnd: Float = 300f
}

class VolumetricClouds {
    var enabled: Boolean = false
    var cloudMap: String = ""
    var coverage: Float = 0.5f
    var density: Float = 0.5f
    var speed: FloatArray = floatArrayOf(0.01f, 0f)
    var erosion: Float = 0.1f
    var bottomAltitude: Float = 1000f
    var topAltitude: Float = 3000f
    var shadowOpacity: Float = 0.5f
    var ambientOcclusion: Float = 0.5f
}

class FogSystem {
    var enabled: Boolean = false
    var mode: Int = 0 // 0 linear, 1 exponential, 2 exp squared
    var color: Int = 0xFF9DB4CF.toInt()
    var density: Float = 0.01f
    var start: Float = 0f
    var end: Float = 300f
    var height: Float = 0f
    var heightDensity: Float = 0.01f

    // Volumetric
    var volumetricEnabled: Boolean = false
    var volumetricAlbedo: Int = 0xFFFFFFFF.toInt()
    var volumetricMeanFreePath: Float = 10f
    var volumetricAnisotropy: Float = 0f
    var volumetricGlobalAnisotropy: Float = 0.2f
    var volumetricDepthExtent: Float = 100f
    var volumetricSliceDistributionUniformity: Float = 0.5f
}

open class WaterSystem {
    var enabled: Boolean = false
    var waterColor: Int = 0xFF0A4A6A.toInt()
    var depthColor: Int = 0xFF001E2F.toInt()
    var specularColor: Int = 0xFFFFFFFF.toInt()
    var smoothness: Float = 0.9f
    var waveSpeed: Float = 1f
    var waveScale: Float = 0.5f
    var foamColor: Int = 0xFFFFFFFF.toInt()
    var foamDistance: Float = 0.5f
    var normalMap: String = ""
    var heightMap: String = ""
    var depth: Float = 10f
    var hasRefraction: Boolean = true
    var hasReflection: Boolean = true
    var hasFoam: Boolean = true
    var hasWaves: Boolean = true
    var tessellation: Float = 1f
}

class OceanSystem : WaterSystem() {
    var oceanLevel: Float = 0f
    var windSpeed: Float = 5f
    var windDirection: Float = 0f
    var swell: Float = 0.5f
    var chop: Float = 0.5f
}

class SkyboxSystem {
    var currentSkybox = SkyboxComponent()
    var clouds = VolumetricClouds()
    var fog = FogSystem()
    var water = WaterSystem()
    var ocean = OceanSystem()

    var timeOfDay: Float = 12f // 0-24
    var latitude: Float = 0f
    var longitude: Float = 0f
    var dayOfYear: Int = 172

    fun updateSunPosition(): FloatArray {
        // Simple solar position calculation
        val hourAngle = (timeOfDay - 12f) * 15f * Math.PI.toFloat() / 180f
        val declination = 23.45f * Math.PI.toFloat() / 180f * kotlin.math.sin(2f * Math.PI.toFloat() * (284 + dayOfYear) / 365f)
        val latRad = latitude * Math.PI.toFloat() / 180f
        val sinAlt = kotlin.math.sin(latRad) * kotlin.math.sin(declination) + kotlin.math.cos(latRad) * kotlin.math.cos(declination) * kotlin.math.cos(hourAngle)
        val altitude = kotlin.math.asin(sinAlt.coerceIn(-1f, 1f))
        val cosAz = (kotlin.math.sin(declination) - kotlin.math.sin(latRad) * sinAlt) / (kotlin.math.cos(latRad) * kotlin.math.cos(altitude).coerceAtLeast(0.001f))
        val azimuth = kotlin.math.acos(cosAz.coerceIn(-1f, 1f))
        return floatArrayOf(
            kotlin.math.cos(altitude) * kotlin.math.sin(azimuth),
            kotlin.math.sin(altitude),
            kotlin.math.cos(altitude) * kotlin.math.cos(azimuth)
        )
    }

    fun getSkyColor(direction: FloatArray): Int {
        // Simplified sky color based on direction and time
        val sunDir = updateSunPosition()
        val dot = direction[0] * sunDir[0] + direction[1] * sunDir[1] + direction[2] * sunDir[2]
        val sunInfluence = (dot.coerceIn(0f, 1f)).let { it * it }
        val base = currentSkybox.gradient.topColor
        // blend with sun color near sun
        return base // simplified
    }
}
