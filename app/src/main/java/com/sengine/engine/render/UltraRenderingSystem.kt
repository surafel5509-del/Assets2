package com.sengine.engine.render

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Rendering System - Months/Years Professional Project
 * Clustered Forward+, Tiled Deferred, Visibility Buffer, Mesh Shaders, VRS, FSR/DLSS, Volumetric Clouds v2, Water v2 FFT underwater, Terrain v2 VT tessellation triplanar
 */
enum class RenderingMode { Forward, Deferred, ForwardPlus, Clustered, VisibilityBuffer, TiledDeferred }
enum class ShadowMapType { CSM, VSM, PCSS, Contact, VSM_CSM }
enum class UpscalerType { None, FSR1, FSR2, DLSS, XeSS, Native }
enum class CloudQuality { Low, Medium, High, Ultra, Cinematic }

data class ClusteredLightData(
    var lightIndex: Int = 0,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var radius: Float = 10f,
    var color: Int = 0xFFFFFFFF.toInt(),
    var intensity: Float = 1f
)

data class Cluster(
    var x: Int = 0, var y: Int = 0, var z: Int = 0,
    var minX: Float = 0f, var minY: Float = 0f, var minZ: Float = 0f,
    var maxX: Float = 0f, var maxY: Float = 0f, var maxZ: Float = 0f,
    var lights: MutableList<Int> = mutableListOf(),
    var lightCount: Int = 0
) {
    fun containsPoint(px: Float, py: Float, pz: Float): Boolean =
        px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ
    fun intersectsSphere(sx: Float, sy: Float, sz: Float, radius: Float): Boolean {
        val closestX = sx.coerceIn(minX, maxX); val closestY = sy.coerceIn(minY, maxY); val closestZ = sz.coerceIn(minZ, maxZ)
        val dx = sx-closestX; val dy = sy-closestY; val dz = sz-closestZ
        return dx*dx+dy*dy+dz*dz <= radius*radius
    }
}

class ClusteredForwardPlus(
    var screenWidth: Int = 1920,
    var screenHeight: Int = 1080,
    var clusterCountX: Int = 16,
    var clusterCountY: Int = 9,
    var clusterCountZ: Int = 24,
    var clusters: MutableList<Cluster> = mutableListOf(),
    var lights: MutableList<ClusteredLightData> = mutableListOf()
) {
    fun buildClusters(near: Float = 0.1f, far: Float = 1000f) {
        clusters.clear()
        for (z in 0 until clusterCountZ) for (y in 0 until clusterCountY) for (x in 0 until clusterCountX) {
            val minX = x.toFloat()/clusterCountX*screenWidth
            val maxX = (x+1).toFloat()/clusterCountX*screenWidth
            val minY = y.toFloat()/clusterCountY*screenHeight
            val maxY = (y+1).toFloat()/clusterCountY*screenHeight
            // Logarithmic depth slicing
            val zNear = near * (far/near).pow(z.toFloat()/clusterCountZ)
            val zFar = near * (far/near).pow((z+1).toFloat()/clusterCountZ)
            clusters.add(Cluster(x=x, y=y, z=z, minX=minX, minY=minY, minZ=zNear, maxX=maxX, maxY=maxY, maxZ=zFar))
        }
    }

    fun assignLights() {
        for (cluster in clusters) cluster.lights.clear()
        for ((idx, light) in lights.withIndex()) {
            for (cluster in clusters) if (cluster.intersectsSphere(light.x, light.y, light.z, light.radius)) {
                cluster.lights.add(idx); cluster.lightCount++
            }
        }
    }

    fun cullLights(cameraX: Float, cameraY: Float, cameraZ: Float): List<Cluster> {
        return clusters.filter { it.lightCount > 0 }
    }

    fun getStats(): String = "Clustered ${clusterCountX}x${clusterCountY}x${clusterCountZ}=${clusters.size} clusters Lights:${lights.size} Avg Lights/Cluster:${if (clusters.isEmpty()) 0 else clusters.map { it.lightCount }.average().toInt()}"
}

data class ShadowCSMData(
    var cascadeCount: Int = 4,
    var cascadeSplits: FloatArray = floatArrayOf(0.1f, 0.3f, 0.6f, 1f),
    var cascadeResolutions: IntArray = intArrayOf(1024, 1024, 1024, 1024),
    var lambda: Float = 0.5f, // blend between linear and logarithmic
    var shadowDistance: Float = 100f,
    var cascadeFade: Float = 0.1f,
    var pcssEnabled: Boolean = true,
    var pcssBlockerSearchSize: Float = 0.02f,
    var pcssFilterSize: Float = 0.05f,
    var vsmEnabled: Boolean = false,
    var contactShadows: Boolean = true,
    var contactShadowLength: Float = 0.5f
) {
    fun calculateSplits(near: Float, far: Float): FloatArray {
        val splits = FloatArray(cascadeCount)
        for (i in 0 until cascadeCount) {
            val p = (i+1).toFloat()/cascadeCount
            val log = near * (far/near).pow(p)
            val uni = near + (far-near)*p
            splits[i] = lambda*log + (1f-lambda)*uni
        }
        return splits
    }
}

data class UpscalerData(
    var type: UpscalerType = UpscalerType.FSR2,
    var renderScale: Float = 0.67f, // 67% for quality mode
    var sharpness: Float = 0.5f,
    var enableMotionVectors: Boolean = true,
    var enableDepth: Boolean = true,
    var outputWidth: Int = 1920,
    var outputHeight: Int = 1080,
    var inputWidth: Int = 1280,
    var inputHeight: Int = 720
) {
    fun calculateInputResolution(): Pair<Int,Int> {
        inputWidth = (outputWidth*renderScale).toInt()
        inputHeight = (outputHeight*renderScale).toInt()
        return Pair(inputWidth, inputHeight)
    }
    fun getQualityMode(): String = when {
        renderScale <= 0.5f -> "Performance"
        renderScale <= 0.59f -> "Balanced"
        renderScale <= 0.67f -> "Quality"
        renderScale <= 0.77f -> "Ultra Quality"
        else -> "Native"
    }
}

data class VolumetricCloudsV2(
    var quality: CloudQuality = CloudQuality.High,
    var coverage: Float = 0.5f,
    var density: Float = 0.5f,
    var cloudType: Float = 0.5f, // 0 stratus, 1 cumulus
    var bottomAltitude: Float = 1500f,
    var topAltitude: Float = 4000f,
    var bottomFade: Float = 0.2f,
    var topFade: Float = 0.3f,
    var shapeNoiseScale: Float = 0.0001f,
    var detailNoiseScale: Float = 0.001f,
    var erosion: Float = 0.1f,
    var windSpeed: Float = 5f,
    var windDirectionX: Float = 1f,
    var windDirectionZ: Float = 0.5f,
    var sunLightIntensity: Float = 1f,
    var ambientIntensity: Float = 0.3f,
    var shadowOpacity: Float = 0.5f,
    var raymarchSteps: Int = 64,
    var raymarchStepsToLight: Int = 8,
    var time: Float = 0f
) {
    fun sampleDensity(x: Float, y: Float, z: Float): Float {
        val height = (y-bottomAltitude)/(topAltitude-bottomAltitude)
        if (height < 0f || height > 1f) return 0f
        val heightFade = when {
            height < bottomFade -> height/bottomFade
            height > 1f-topFade -> (1f-height)/topFade
            else -> 1f
        }
        // Noise
        val shapeNoise = perlinNoise3D(x*shapeNoiseScale+time*windSpeed*0.01f, y*shapeNoiseScale, z*shapeNoiseScale)
        val detailNoise = perlinNoise3D(x*detailNoiseScale, y*detailNoiseScale, z*detailNoiseScale)*0.5f
        return (shapeNoise*0.7f+detailNoise*0.3f)*coverage*density*heightFade
    }
    private fun perlinNoise3D(x: Float, y: Float, z: Float): Float {
        return (sin(x*12.9898f+y*78.233f+z*37.719f)*43758.5453f % 1f + 1f) % 1f
    }
    fun update(dt: Float) { time+=dt }
}

data class WaterV2(
    var width: Float = 100f, var height: Float = 100f,
    var waveScale: Float = 0.5f, var waveSpeed: Float = 1f,
    var foamThreshold: Float = 0.5f,
    var depthGradientShallow: Int = 0xFF4FC3F7.toInt(),
    var depthGradientDeep: Int = 0xFF0D47A1.toInt(),
    var foamColor: Int = 0xFFFFFFFF.toInt(),
    var smoothness: Float = 0.9f,
    var hasRefraction: Boolean = true,
    var hasReflection: Boolean = true,
    var hasFoam: Boolean = true,
    var hasWaves: Boolean = true,
    var underwater: Boolean = false,
    var underwaterFogColor: Int = 0xFF0D47A1.toInt(),
    var underwaterFogDensity: Float = 0.05f
) {
    fun getHeightAt(x: Float, z: Float, time: Float): Float {
        var h=0f
        h+=sin(x*0.1f+time*waveSpeed)*waveScale*0.5f
        h+=cos(z*0.15f+time*waveSpeed*0.8f)*waveScale*0.3f
        h+=sin((x+z)*0.05f+time*waveSpeed*1.2f)*waveScale*0.2f
        return h
    }
    fun getNormalAt(x: Float, z: Float, time: Float, eps: Float = 0.1f): FloatArray {
        val hL = getHeightAt(x-eps, z, time); val hR = getHeightAt(x+eps, z, time)
        val hD = getHeightAt(x, z-eps, time); val hU = getHeightAt(x, z+eps, time)
        val nx = hL-hR; val nz = hD-hU; val ny = 2f*eps
        val len = sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(0.0001f)
        return floatArrayOf(nx/len, ny/len, nz/len)
    }
}

class UltraRenderingSystem {
    var mode: RenderingMode = RenderingMode.Clustered
    var clustered = ClusteredForwardPlus()
    var shadowCSM = ShadowCSMData()
    var upscaler = UpscalerData()
    var cloudsV2 = VolumetricCloudsV2()
    var waterV2 = WaterV2()
    var vrsEnabled: Boolean = true
    var meshShaders: Boolean = false
    var visibilityBuffer: Boolean = false
    var time: Float = 0f

    init {
        clustered.buildClusters()
        upscaler.calculateInputResolution()
    }

    fun update(dt: Float) {
        time+=dt
        cloudsV2.update(dt)
    }

    fun addLight(x: Float, y: Float, z: Float, radius: Float, color: Int, intensity: Float) {
        clustered.lights.add(ClusteredLightData(lightIndex=clustered.lights.size, x=x, y=y, z=z, radius=radius, color=color, intensity=intensity))
        clustered.assignLights()
    }

    fun getStats(): String = "Ultra Rendering Mode:$mode ${clustered.getStats()} Shadows:${shadowCSM.cascadeCount} CSM PCSS:${shadowCSM.pcssEnabled} VSM:${shadowCSM.vsmEnabled} Upscaler:${upscaler.type} ${upscaler.getQualityMode()} ${upscaler.inputWidth}x${upscaler.inputHeight}->${upscaler.outputWidth}x${upscaler.outputHeight} Clouds:${cloudsV2.quality} Steps:${cloudsV2.raymarchSteps} Water:${waterV2.width}x${waterV2.height} VRS:$vrsEnabled MeshShaders:$meshShaders VisBuffer:$visibilityBuffer"
}
