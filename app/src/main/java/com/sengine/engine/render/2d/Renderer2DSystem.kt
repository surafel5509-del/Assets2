package com.sengine.engine.render.`2d`

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Unity 2D URP Renderer System
 * 2D Renderer, 2D Lights, Shadows, Sorting, Camera Stacking
 */
enum class Renderer2DMode { Forward, Deferred, Custom }
enum class Camera2DType { Main, Overlay, Base, Additional }
enum class Light2DType { Point, Spot, Freeform, Sprite, Parametric, Global }
enum class Light2DBlendStyle { Additive, Multiply, Custom }

data class Renderer2DData(
    var mode: Renderer2DMode = Renderer2DMode.Forward,
    var useDepthStencil: Boolean = true,
    var useHDR: Boolean = true,
    var msaa: Int = 4,
    var shadowQuality: Int = 2,
    var lightCulling: Boolean = true,
    var maxLights: Int = 32,
    var maxShadowLights: Int = 8
)

data class Light2D(
    var id: String = "light2d_0",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var type: Light2DType = Light2DType.Point,
    var color: Int = 0xFFFFFFFF.toInt(),
    var intensity: Float = 1f,
    var innerRadius: Float = 0f,
    var outerRadius: Float = 5f,
    var innerAngle: Float = 0f,
    var outerAngle: Float = 360f,
    var falloff: Float = 1f,
    var falloffIntensity: Float = 1f,
    var shadowsEnabled: Boolean = true,
    var shadowIntensity: Float = 0.5f,
    var shadowVolumeIntensity: Float = 1f,
    var blendStyle: Light2DBlendStyle = Light2DBlendStyle.Additive,
    var blendStyleIndex: Int = 0,
    var cookieSprite: String = "",
    var normalMapQuality: Int = 1,
    var lightOrder: Int = 0,
    var alphaBlendOnOverlap: Boolean = false,
    var volumetricEnabled: Boolean = false,
    var isVisible: Boolean = true
) {
    fun containsPoint(px: Float, py: Float): Boolean {
        val dx = px-x; val dy = py-y
        return sqrt(dx*dx+dy*dy) <= outerRadius
    }
    fun calculateIntensityAt(px: Float, py: Float): Float {
        val dx = px-x; val dy = py-y
        val dist = sqrt(dx*dx+dy*dy)
        if (dist > outerRadius) return 0f
        if (dist < innerRadius) return intensity
        val t = (dist-innerRadius)/(outerRadius-innerRadius)
        return intensity * (1f-t).pow(falloff) * falloffIntensity
    }
    fun getColorAt(px: Float, py: Float): Int {
        val intens = calculateIntensityAt(px, py)
        val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
        val nr = (r*intens).toInt().coerceIn(0,255); val ng = (g*intens).toInt().coerceIn(0,255); val nb = (b*intens).toInt().coerceIn(0,255)
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
}

data class ShadowCaster2D(
    var id: String = "shadow_0",
    var x: Float = 0f, var y: Float = 0f,
    var selfShadows: Boolean = true,
    var castShadows: Boolean = true,
    var shadowIntensity: Float = 1f,
    var useRendererSilhouette: Boolean = true,
    var shapePath: MutableList<FloatArray> = mutableListOf(), // polygon points
    var isVisible: Boolean = true
) {
    fun generateShadowMesh(lightX: Float, lightY: Float, extrudeDistance: Float = 20f): List<FloatArray> {
        val shadowVerts = mutableListOf<FloatArray>()
        for (point in shapePath) {
            val dx = point[0]-lightX; val dy = point[1]-lightY
            val len = sqrt(dx*dx+dy*dy).coerceAtLeast(0.0001f)
            val ex = point[0]+dx/len*extrudeDistance
            val ey = point[1]+dy/len*extrudeDistance
            shadowVerts.add(floatArrayOf(point[0], point[1]))
            shadowVerts.add(floatArrayOf(ex, ey))
        }
        return shadowVerts
    }
}

data class Camera2DStack(
    var baseCamera: String = "MainCamera",
    var overlayCameras: MutableList<String> = mutableListOf(),
    var clearFlags: Int = 1, // 0 skybox, 1 solid color, 2 depth only, 3 nothing
    var backgroundColor: Int = 0xFF1A1A2E.toInt(),
    var cullingMask: Int = -1,
    var depth: Float = 0f
)

class Renderer2DSystem {
    var data = Renderer2DData()
    var lights = mutableMapOf<String, Light2D>()
    var shadowCasters = mutableMapOf<String, ShadowCaster2D>()
    var cameraStacks = mutableMapOf<String, Camera2DStack>()
    var enabled: Boolean = true
    var sortLights: Boolean = true
    var pixelPerfect: Boolean = false
    var pixelsPerUnit: Float = 100f
    var nextLightId: Int = 0
    var nextShadowId: Int = 0

    fun createLight(x: Float, y: Float, type: Light2DType = Light2DType.Point, color: Int = 0xFFFFFFFF.toInt(), intensity: Float = 1f, radius: Float = 5f): Light2D {
        val id = "light2d_${nextLightId++}"
        val light = Light2D(id=id, x=x, y=y, type=type, color=color, intensity=intensity, outerRadius=radius)
        lights[id]=light; return light
    }

    fun createGlobalLight(color: Int = 0xFFAAAAAA.toInt(), intensity: Float = 0.5f): Light2D {
        return createLight(0f, 0f, Light2DType.Global, color, intensity, 1000f)
    }

    fun createPointLight(x: Float, y: Float, color: Int, intensity: Float, radius: Float): Light2D {
        return createLight(x, y, Light2DType.Point, color, intensity, radius)
    }

    fun createShadowCaster(x: Float, y: Float, shape: List<FloatArray> = emptyList()): ShadowCaster2D {
        val id = "shadow_${nextShadowId++}"
        val caster = ShadowCaster2D(id=id, x=x, y=y, shapePath=shape.toMutableList())
        shadowCasters[id]=caster; return caster
    }

    fun createCameraStack(baseCamera: String): Camera2DStack {
        val stack = Camera2DStack(baseCamera=baseCamera); cameraStacks[baseCamera]=stack; return stack
    }

    fun getLightsAt(x: Float, y: Float): List<Light2D> {
        return lights.values.filter { it.isVisible && it.containsPoint(x,y) }.sortedBy { it.lightOrder }
    }

    fun calculateLightingAt(x: Float, y: Float): Int {
        var r=0; var g=0; var b=0; var a=0
        for (light in getLightsAt(x,y)) {
            val col = light.getColorAt(x,y)
            val lr = (col shr 16) and 0xFF; val lg = (col shr 8) and 0xFF; val lb = col and 0xFF
            when (light.blendStyle) {
                Light2DBlendStyle.Additive -> { r+=lr; g+=lg; b+=lb }
                Light2DBlendStyle.Multiply -> {
                    if (a==0) { r=lr; g=lg; b=lb; a=1 } else { r=r*lr/255; g=g*lg/255; b=b*lb/255 }
                }
                else -> { r+=lr; g+=lg; b+=lb }
            }
        }
        r=r.coerceIn(0,255); g=g.coerceIn(0,255); b=b.coerceIn(0,255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun cullLights(cameraX: Float, cameraY: Float, cameraSize: Float): List<Light2D> {
        if (!data.lightCulling) return lights.values.filter { it.isVisible }.toList()
        return lights.values.filter { light ->
            if (!light.isVisible) return@filter false
            val dx = light.x-cameraX; val dy = light.y-cameraY
            val dist = sqrt(dx*dx+dy*dy)
            dist < cameraSize + light.outerRadius
        }.sortedBy { it.lightOrder }.take(data.maxLights)
    }

    fun getStats(): String = "2D Renderer Mode:${data.mode} Lights:${lights.size}/${data.maxLights} Shadows:${shadowCasters.size} PixelPerfect:$pixelPerfect PPU:$pixelsPerUnit"
}
