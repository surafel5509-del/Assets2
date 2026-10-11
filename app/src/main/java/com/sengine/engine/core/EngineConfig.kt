package com.sengine.engine.core

import org.json.JSONObject

/**
 * Ultimate Edition Engine Configuration - Unity 6 / UE5 class settings
 * Controls global engine behavior, versioning, and quality tiers.
 */
object EngineVersion {
    const val MAJOR = 6
    const val MINOR = 1
    const val PATCH = 1
    const val NAME = "S Engine Most Powerful Edition"
    const val CODENAME = "Titan"
    const val API_VERSION = 611
    const val PREV_CODENAME = "Quantum"
    const val PREV_VERSION = "6.1.0 Ultimate Edition [Quantum]"
    fun string() = "$NAME $MAJOR.$MINOR.$PATCH [$CODENAME] API:$API_VERSION"
    fun fullString() = "$NAME $MAJOR.$MINOR.$PATCH [$CODENAME] API:$API_VERSION (Prev: $PREV_VERSION [$PREV_CODENAME]) | Features: RayTracing,Nanite,Lumen,Chaos,WorldPartition,ControlRig,MotionMatching,XR,EQS,MLAgents,PCG,2D_URP,Tilemap,2D_Physics,2D_Animation,Sorting,FullControl"
}

enum class RayTracingQuality { Off, Low, Medium, High, Ultra, Psycho }
enum class NaniteQuality { Off, Low, Medium, High, Ultra }
enum class VirtualTextureQuality { Low, Medium, High, Ultra }
enum class Titan2DQuality { Low, Medium, High, Ultra }

enum class RenderPipelineType {
    BUILT_IN,
    UNIVERSAL,      // URP equivalent
    HIGH_DEFINITION, // HDRP equivalent
    CUSTOM
}

enum class ColorSpace {
    GAMMA,
    LINEAR
}

enum class QualityLevel {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
    ULTRA,
    CUSTOM
}

data class QualitySettings(
    var level: QualityLevel = QualityLevel.HIGH,
    var renderScale: Float = 1.0f,
    var shadowQuality: Int = 2, // 0 off, 1 low, 2 med, 3 high, 4 ultra
    var shadowDistance: Float = 100f,
    var shadowCascades: Int = 4,
    var msaa: Int = 4,
    var hdr: Boolean = true,
    var postProcessing: Boolean = true,
    var volumetricFog: Boolean = true,
    var ssao: Boolean = true,
    var ssr: Boolean = true,
    var motionBlur: Boolean = true,
    var bloom: Boolean = true,
    var terrainPixelError: Float = 5f,
    var lodBias: Float = 1.5f,
    var maxLODLevel: Int = 0,
    var particleBudget: Int = 10000,
    var textureQuality: Int = 0, // 0 full, 1 half, 2 quarter
    var anisotropicFiltering: Int = 2, // 0 off, 1 per texture, 2 forced
    var vSync: Int = 1,
    var targetFrameRate: Int = 60,
    // 6.1 Quantum additions
    var rayTracing: RayTracingQuality = RayTracingQuality.High,
    var rayTracingShadows: Boolean = true,
    var rayTracingReflections: Boolean = true,
    var rayTracingGI: Boolean = true,
    var rayTracingAO: Boolean = true,
    var nanite: NaniteQuality = NaniteQuality.High,
    var naniteMaxTriangles: Int = 10_000_000,
    var virtualTexture: VirtualTextureQuality = VirtualTextureQuality.High,
    var virtualTexturePoolMB: Int = 256,
    var lumenGI: Boolean = true,
    var lumenReflections: Boolean = true,
    var virtualShadowMaps: Boolean = true,
    var chaosDestruction: Boolean = true,
    var fluidSimulation: Boolean = true,
    var worldPartitionStreaming: Boolean = true,
    var foliageDensity: Float = 1f,
    var hairQuality: Int = 2, // 0 off, 1 low, 2 high
    var computeShaders: Boolean = true,
    var xrEnabled: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("level", level.name)
        .put("renderScale", renderScale.toDouble())
        .put("shadowQuality", shadowQuality)
        .put("shadowDistance", shadowDistance.toDouble())
        .put("shadowCascades", shadowCascades)
        .put("msaa", msaa)
        .put("hdr", hdr)
        .put("postProcessing", postProcessing)
        .put("volumetricFog", volumetricFog)
        .put("ssao", ssao)
        .put("ssr", ssr)
        .put("motionBlur", motionBlur)
        .put("bloom", bloom)
        .put("lodBias", lodBias.toDouble())
        .put("maxLODLevel", maxLODLevel)
        .put("particleBudget", particleBudget)
        .put("targetFrameRate", targetFrameRate)

    companion object {
        fun fromJson(o: JSONObject): QualitySettings {
            val qs = QualitySettings()
            try { qs.level = QualityLevel.valueOf(o.optString("level", "HIGH")) } catch (_: Exception) {}
            qs.renderScale = o.optDouble("renderScale", 1.0).toFloat()
            qs.shadowQuality = o.optInt("shadowQuality", 2)
            qs.shadowDistance = o.optDouble("shadowDistance", 100.0).toFloat()
            qs.shadowCascades = o.optInt("shadowCascades", 4)
            qs.msaa = o.optInt("msaa", 4)
            qs.hdr = o.optBoolean("hdr", true)
            qs.postProcessing = o.optBoolean("postProcessing", true)
            qs.lodBias = o.optDouble("lodBias", 1.5).toFloat()
            qs.particleBudget = o.optInt("particleBudget", 10000)
            qs.targetFrameRate = o.optInt("targetFrameRate", 60)
            return qs
        }
        val presets: Map<QualityLevel, QualitySettings> = mapOf(
            QualityLevel.VERY_LOW to QualitySettings(QualityLevel.VERY_LOW, 0.5f, 0, 20f, 1, 0, false, false, false, false, false, false, false, 20f, 0.7f, 2, 2000, 2, 0, 0, 30),
            QualityLevel.LOW to QualitySettings(QualityLevel.LOW, 0.7f, 1, 40f, 1, 0, false, true, false, false, false, false, true, 10f, 1f, 1, 4000, 1, 1, 1, 30),
            QualityLevel.MEDIUM to QualitySettings(QualityLevel.MEDIUM, 0.85f, 2, 70f, 2, 2, true, true, false, true, false, false, true, 5f, 1.2f, 0, 6000, 0, 1, 1, 60),
            QualityLevel.HIGH to QualitySettings(QualityLevel.HIGH, 1f, 3, 100f, 4, 4, true, true, true, true, true, true, true, 5f, 1.5f, 0, 10000, 0, 2, 1, 60),
            QualityLevel.VERY_HIGH to QualitySettings(QualityLevel.VERY_HIGH, 1f, 3, 150f, 4, 4, true, true, true, true, true, true, true, 3f, 2f, 0, 20000, 0, 2, 1, 90),
            QualityLevel.ULTRA to QualitySettings(QualityLevel.ULTRA, 1.25f, 4, 250f, 4, 8, true, true, true, true, true, true, true, 1f, 2.5f, 0, 50000, 0, 2, 0, 120),
        )
    }
}

data class EngineConfig(
    var pipeline: RenderPipelineType = RenderPipelineType.UNIVERSAL,
    var colorSpace: ColorSpace = ColorSpace.LINEAR,
    var quality: QualitySettings = QualitySettings(),
    var graphicsJobs: Boolean = true,
    var computeSkinning: Boolean = true,
    var gpuInstancing: Boolean = true,
    var srpBatcher: Boolean = true,
    var dynamicBatching: Boolean = false,
    var occlusionCulling: Boolean = true,
    var lodCrossFade: Boolean = true,
    var allowHDRDisplay: Boolean = true,
    var version: String = EngineVersion.string(),
    // 6.1 Quantum
    var rayTracing: Boolean = true,
    var nanite: Boolean = true,
    var lumen: Boolean = true,
    var virtualTexture: Boolean = true,
    var virtualShadowMaps: Boolean = true,
    var chaosDestruction: Boolean = true,
    var worldPartition: Boolean = true,
    var computeShaders: Boolean = true,
    var xrSupport: Boolean = true,
    var mlAgents: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put("pipeline", pipeline.name)
        .put("colorSpace", colorSpace.name)
        .put("quality", quality.toJson())
        .put("graphicsJobs", graphicsJobs)
        .put("gpuInstancing", gpuInstancing)
        .put("srpBatcher", srpBatcher)
        .put("occlusionCulling", occlusionCulling)
        .put("version", version)

    companion object {
        fun fromJson(o: JSONObject): EngineConfig {
            val cfg = EngineConfig()
            try { cfg.pipeline = RenderPipelineType.valueOf(o.optString("pipeline", "UNIVERSAL")) } catch (_: Exception) {}
            try { cfg.colorSpace = ColorSpace.valueOf(o.optString("colorSpace", "LINEAR")) } catch (_: Exception) {}
            if (o.has("quality")) cfg.quality = QualitySettings.fromJson(o.getJSONObject("quality"))
            cfg.graphicsJobs = o.optBoolean("graphicsJobs", true)
            cfg.gpuInstancing = o.optBoolean("gpuInstancing", true)
            cfg.srpBatcher = o.optBoolean("srpBatcher", true)
            cfg.occlusionCulling = o.optBoolean("occlusionCulling", true)
            return cfg
        }
    }
}
