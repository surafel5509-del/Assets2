package com.sengine.engine.render

import org.json.JSONObject

/**
 * Ultimate Edition Post-Processing Stack v3 - Unity URP/HDRP + Unreal equivalent
 * Full modern post effects
 */

enum class PostEffectType {
    Bloom,
    Vignette,
    ChromaticAberration,
    ColorGrading,
    Tonemapping,
    SSAO,
    SSR,
    DepthOfField,
    MotionBlur,
    FilmGrain,
    LensDistortion,
    PaniniProjection,
    LUT,
    WhiteBalance,
    ChannelMixer,
    ShadowsMidtonesHighlights,
    LiftGammaGain,
    SplitToning,
    Fog,
    VolumetricFog,
    ScreenSpaceReflections,
    AutoExposure,
    LensFlare,
    Glare
}

data class PostEffectSettings(
    val type: PostEffectType,
    var enabled: Boolean = true,
    var intensity: Float = 1f,
    var parameters: MutableMap<String, Float> = mutableMapOf(),
    var colorParams: MutableMap<String, Int> = mutableMapOf(),
    var textureParams: MutableMap<String, String> = mutableMapOf()
)

class PostProcessVolume(var name: String = "Global Volume") {
    var isGlobal: Boolean = true
    var priority: Float = 0f
    var blendDistance: Float = 0f
    var weight: Float = 1f
    var effects = mutableListOf<PostEffectSettings>()

    fun addEffect(type: PostEffectType, init: PostEffectSettings.() -> Unit = {}): PostEffectSettings {
        val e = PostEffectSettings(type)
        e.init()
        effects.add(e)
        return e
    }

    fun getEffect(type: PostEffectType): PostEffectSettings? = effects.firstOrNull { it.type == type }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("isGlobal", isGlobal)
        o.put("priority", priority.toDouble())
        o.put("weight", weight.toDouble())
        val arr = org.json.JSONArray()
        for (eff in effects) {
            val eo = JSONObject()
            eo.put("type", eff.type.name)
            eo.put("enabled", eff.enabled)
            eo.put("intensity", eff.intensity.toDouble())
            val p = JSONObject()
            for ((k, v) in eff.parameters) p.put(k, v.toDouble())
            eo.put("params", p)
            arr.put(eo)
        }
        o.put("effects", arr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): PostProcessVolume {
            val v = PostProcessVolume(o.optString("name", "Volume"))
            v.isGlobal = o.optBoolean("isGlobal", true)
            v.priority = o.optDouble("priority", 0.0).toFloat()
            v.weight = o.optDouble("weight", 1.0).toFloat()
            val arr = o.optJSONArray("effects")
            if (arr != null) for (i in 0 until arr.length()) {
                val eo = arr.getJSONObject(i)
                try {
                    val type = PostEffectType.valueOf(eo.optString("type", "Bloom"))
                    val eff = PostEffectSettings(type)
                    eff.enabled = eo.optBoolean("enabled", true)
                    eff.intensity = eo.optDouble("intensity", 1.0).toFloat()
                    val p = eo.optJSONObject("params")
                    if (p != null) for (k in p.keys()) eff.parameters[k] = p.optDouble(k, 0.0).toFloat()
                    v.effects.add(eff)
                } catch (_: Exception) {}
            }
            return v
        }

        fun defaultGlobal(): PostProcessVolume {
            val vol = PostProcessVolume("Global Post Process")
            vol.isGlobal = true
            vol.addEffect(PostEffectType.Bloom) {
                intensity = 0.8f
                parameters["threshold"] = 1.0f
                parameters["softKnee"] = 0.5f
                parameters["clamp"] = 65472f
                parameters["diffusion"] = 5f
                parameters["anamorphicRatio"] = 0f
            }
            vol.addEffect(PostEffectType.Vignette) {
                intensity = 0.3f
                parameters["smoothness"] = 0.4f
                parameters["roundness"] = 1f
            }
            vol.addEffect(PostEffectType.Tonemapping) {
                parameters["mode"] = 1f // 0 None, 1 ACES, 2 Neutral, 3 AgX
            }
            vol.addEffect(PostEffectType.ColorGrading) {
                parameters["temperature"] = 0f
                parameters["tint"] = 0f
                parameters["hueShift"] = 0f
                parameters["saturation"] = 0f
                parameters["contrast"] = 0f
            }
            vol.addEffect(PostEffectType.SSAO) {
                intensity = 1f
                parameters["radius"] = 0.3f
                parameters["intensity"] = 2f
                parameters["falloff"] = 20f
            }
            vol.addEffect(PostEffectType.DepthOfField) {
                enabled = false
                parameters["focusDistance"] = 10f
                parameters["aperture"] = 5.6f
                parameters["focalLength"] = 50f
            }
            return vol
        }
    }
}

class PostProcessStack {
    private val volumes = mutableListOf<PostProcessVolume>()
    var globalVolume: PostProcessVolume = PostProcessVolume.defaultGlobal()

    init {
        volumes.add(globalVolume)
    }

    fun addVolume(volume: PostProcessVolume) {
        volumes.add(volume)
        volumes.sortByDescending { it.priority }
    }

    fun removeVolume(name: String) { volumes.removeIf { it.name == name && it !== globalVolume } }

    fun getVolumes(): List<PostProcessVolume> = volumes.toList()

    fun blendVolumes(cameraPos: FloatArray): List<PostEffectSettings> {
        // Simple blending based on distance for local volumes
        val blended = mutableMapOf<PostEffectType, PostEffectSettings>()
        // Start with global
        for (eff in globalVolume.effects) if (eff.enabled) blended[eff.type] = eff.copy()

        // Blend local volumes by weight
        for (vol in volumes.filter { !it.isGlobal }) {
            if (vol.weight <= 0f) continue
            for (eff in vol.effects) if (eff.enabled) {
                val existing = blended[eff.type]
                if (existing == null) blended[eff.type] = eff.copy()
                else {
                    // lerp parameters
                    val t = vol.weight
                    existing.intensity = existing.intensity * (1 - t) + eff.intensity * t
                    for ((k, v) in eff.parameters) {
                        val prev = existing.parameters[k] ?: v
                        existing.parameters[k] = prev * (1 - t) + v * t
                    }
                }
            }
        }
        return blended.values.toList()
    }

    fun toJson(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        for (v in volumes) arr.put(v.toJson())
        return arr
    }
}
