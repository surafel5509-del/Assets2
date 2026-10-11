package com.sengine.engine.render

import org.json.JSONObject

/**
 * Ultimate Edition Material System - PBR like Unity HDRP/URP and Unreal
 */

enum class SurfaceType { Opaque, Transparent, Cutout }
enum class BlendMode { Alpha, Premultiply, Additive, Multiply }
enum class CullMode { Off, Front, Back }

data class MaterialProperty(
    val name: String,
    val type: String, // float, color, texture, vector
    var floatValue: Float = 0f,
    var colorValue: Int = 0xFFFFFFFF.toInt(),
    var textureValue: String = "",
    var vectorValue: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)
)

class Material(var name: String = "New Material", var shader: String = "Standard") {
    var guid: String = java.util.UUID.randomUUID().toString()
    var surface: SurfaceType = SurfaceType.Opaque
    var blend: BlendMode = BlendMode.Alpha
    var cull: CullMode = CullMode.Back
    var properties = mutableMapOf<String, MaterialProperty>()

    // PBR properties
    var baseColor: Int = 0xFFFFFFFF.toInt()
    var metallic: Float = 0f
    var smoothness: Float = 0.5f
    var emissionColor: Int = 0xFF000000.toInt()
    var emissionIntensity: Float = 0f
    var normalScale: Float = 1f
    var occlusionStrength: Float = 1f
    var alphaClipThreshold: Float = 0.5f
    var tiling: Float = 1f

    // Textures
    var albedoMap: String = ""
    var metallicMap: String = ""
    var normalMap: String = ""
    var emissionMap: String = ""
    var occlusionMap: String = ""
    var heightMap: String = ""
    var detailAlbedoMap: String = ""
    var detailNormalMap: String = ""

    // Advanced
    var doubleSidedGI: Boolean = false
    var enableGPUInstancing: Boolean = true
    var enableEmission: Boolean = false
    var enableAlphaClipping: Boolean = false
    var renderQueue: Int = 2000

    init {
        initDefaultProperties()
    }

    private fun initDefaultProperties() {
        properties["_BaseColor"] = MaterialProperty("_BaseColor", "color", colorValue = baseColor)
        properties["_Metallic"] = MaterialProperty("_Metallic", "float", floatValue = metallic)
        properties["_Smoothness"] = MaterialProperty("_Smoothness", "float", floatValue = smoothness)
        properties["_EmissionColor"] = MaterialProperty("_EmissionColor", "color", colorValue = emissionColor)
    }

    fun setFloat(name: String, value: Float) {
        properties[name]?.floatValue = value
        when (name) {
            "_Metallic" -> metallic = value
            "_Smoothness" -> smoothness = value
            "_EmissionIntensity" -> emissionIntensity = value
            "_NormalScale" -> normalScale = value
        }
    }

    fun setColor(name: String, value: Int) {
        properties[name]?.colorValue = value
        when (name) {
            "_BaseColor" -> baseColor = value
            "_EmissionColor" -> emissionColor = value
        }
    }

    fun setTexture(name: String, tex: String) {
        properties[name]?.textureValue = tex
        when (name) {
            "_BaseMap" -> albedoMap = tex
            "_MetallicGlossMap" -> metallicMap = tex
            "_BumpMap" -> normalMap = tex
            "_EmissionMap" -> emissionMap = tex
        }
    }

    fun getFloat(name: String): Float = properties[name]?.floatValue ?: 0f
    fun getColor(name: String): Int = properties[name]?.colorValue ?: 0xFFFFFFFF.toInt()
    fun getTexture(name: String): String = properties[name]?.textureValue ?: ""

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("shader", shader)
        o.put("guid", guid)
        o.put("surface", surface.name)
        o.put("baseColor", String.format("#%08X", baseColor))
        o.put("metallic", metallic.toDouble())
        o.put("smoothness", smoothness.toDouble())
        o.put("emissionColor", String.format("#%08X", emissionColor))
        o.put("emissionIntensity", emissionIntensity.toDouble())
        o.put("albedoMap", albedoMap)
        o.put("normalMap", normalMap)
        o.put("metallicMap", metallicMap)
        o.put("tiling", tiling.toDouble())
        o.put("renderQueue", renderQueue)
        o.put("gpuInstancing", enableGPUInstancing)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Material {
            val m = Material(o.optString("name", "Material"), o.optString("shader", "Standard"))
            m.guid = o.optString("guid", m.guid)
            try { m.surface = SurfaceType.valueOf(o.optString("surface", "Opaque")) } catch (_: Exception) {}
            m.baseColor = parseColor(o.optString("baseColor", "#FFFFFFFF"))
            m.metallic = o.optDouble("metallic", 0.0).toFloat()
            m.smoothness = o.optDouble("smoothness", 0.5).toFloat()
            m.emissionColor = parseColor(o.optString("emissionColor", "#FF000000"))
            m.emissionIntensity = o.optDouble("emissionIntensity", 0.0).toFloat()
            m.albedoMap = o.optString("albedoMap", "")
            m.normalMap = o.optString("normalMap", "")
            m.metallicMap = o.optString("metallicMap", "")
            m.tiling = o.optDouble("tiling", 1.0).toFloat()
            m.renderQueue = o.optInt("renderQueue", 2000)
            m.enableGPUInstancing = o.optBoolean("gpuInstancing", true)
            return m
        }

        private fun parseColor(s: String): Int {
            var h = s.trim().removePrefix("#")
            if (h.length == 6) h = "FF$h"
            return try { h.toLong(16).toInt() } catch (_: Exception) { 0xFFFFFFFF.toInt() }
        }
    }
}

class MaterialLibrary {
    private val materials = mutableMapOf<String, Material>()

    fun create(name: String): Material {
        val m = Material(name)
        materials[m.guid] = m
        materials[name] = m
        return m
    }

    fun get(idOrName: String): Material? = materials[idOrName]
    fun all(): List<Material> = materials.values.distinctBy { it.guid }
    fun remove(idOrName: String) {
        val m = materials[idOrName] ?: return
        materials.remove(m.guid)
        materials.remove(m.name)
    }
}
