package com.sengine.engine.render

import com.sengine.engine.core.Scene
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.math.Mat4
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Ultimate Edition Lighting System - Unity HDRP/URP + Unreal Lumen equivalent
 * Supports many lights, shadows, probes, and GI
 */

enum class LightShadows { None, Hard, Soft }
enum class LightShadowResolution { Low256, Medium512, High1024, VeryHigh2048, Ultra4096 }
enum class LightType { Directional, Point, Spot, Area, Disc }

data class ShadowSettings(
    var enabled: Boolean = true,
    var type: LightShadows = LightShadows.Soft,
    var resolution: LightShadowResolution = LightShadowResolution.High1024,
    var strength: Float = 1f,
    var bias: Float = 0.05f,
    var normalBias: Float = 0.4f,
    var nearPlane: Float = 0.2f,
    var cullingMatrix: FloatArray = Mat4.identity()
)

data class LightData(
    val go: GameObject,
    val light: Light,
    var worldPos: FloatArray = floatArrayOf(0f, 0f, 0f),
    var worldDir: FloatArray = floatArrayOf(0f, -1f, 0f),
    var color: FloatArray = floatArrayOf(1f, 1f, 1f),
    var intensity: Float = 1f,
    var range: Float = 10f,
    var spotAngle: Float = 30f,
    var innerSpotAngle: Float = 21f,
    var shadows: ShadowSettings = ShadowSettings(),
    var shadowMapId: Int = -1,
    var visible: Boolean = true,
    var distanceToCamera: Float = 0f
)

class ShadowAtlas(val size: Int = 4096) {
    var usedSlots = 0
    data class Slot(val x: Int, val y: Int, val size: Int, val lightId: Long)
    val slots = mutableListOf<Slot>()

    fun allocate(lightId: Long, resolution: Int): Slot? {
        // Simple atlas packing
        val slotSize = when (resolution) {
            256 -> 256
            512 -> 512
            1024 -> 1024
            2048 -> 2048
            else -> 1024
        }
        // naive row packing
        val cols = size / slotSize
        val rows = size / slotSize
        if (usedSlots >= cols * rows) return null
        val col = usedSlots % cols
        val row = usedSlots / cols
        val slot = Slot(col * slotSize, row * slotSize, slotSize, lightId)
        slots.add(slot)
        usedSlots++
        return slot
    }

    fun clear() { slots.clear(); usedSlots = 0 }
}

class LightingSystem {
    private val lights = mutableListOf<LightData>()
    var mainLight: LightData? = null
    var shadowAtlas = ShadowAtlas(4096)
    var maxAdditionalLights: Int = 16
    var maxShadows: Int = 8

    var ambientMode: Int = 0 // 0 skybox, 1 gradient, 2 color
    var ambientColor: Int = 0xFF4A505A.toInt()
    var ambientIntensity: Float = 1f
    var reflectionIntensity: Float = 1f
    var reflectionBounces: Int = 1

    // GI approximation
    var enableGI: Boolean = true
    var giIntensity: Float = 1f
    var lightProbesEnabled: Boolean = true
    var reflectionProbesEnabled: Boolean = true

    fun cullAndSetup(scene: Scene, view: View3D) {
        lights.clear()
        shadowAtlas.clear()
        var hasMain = false

        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val l = go.get<Light>() ?: continue
            val w = go.world3
            val pos = floatArrayOf(w[12], w[13], w[14])
            val dir = Mat4.dir(w, 0f, 0f, -1f)
            val dist = view.distanceTo(pos[0], pos[1], pos[2])

            val ld = LightData(
                go = go,
                light = l,
                worldPos = pos,
                worldDir = dir,
                color = floatArrayOf(((l.color shr 16) and 0xFF) / 255f, ((l.color shr 8) and 0xFF) / 255f, (l.color and 0xFF) / 255f),
                intensity = l.intensity,
                range = l.range,
                visible = true,
                distanceToCamera = dist
            )
            // Main light is first directional
            if (l.kind == 0 && !hasMain) {
                mainLight = ld
                hasMain = true
                lights.add(0, ld)
            } else {
                lights.add(ld)
            }
        }

        // Sort additional lights by importance (intensity / distance)
        if (lights.size > 1) {
            val main = lights.firstOrNull { it.light.kind == 0 }
            val additional = lights.filter { it.light.kind != 0 }
                .sortedByDescending { it.intensity / maxOf(1f, it.distanceToCamera) }
                .take(maxAdditionalLights)
            lights.clear()
            if (main != null) lights.add(main)
            lights.addAll(additional)
        }

        // Allocate shadow maps for most important lights
        var shadowCount = 0
        for (ld in lights) {
            if (shadowCount >= maxShadows) break
            // allocate
            val slot = shadowAtlas.allocate(ld.go.id, 1024)
            if (slot != null) {
                ld.shadowMapId = shadowCount++
                ld.shadows.enabled = true
            }
        }
    }

    fun getLights(): List<LightData> = lights
    @JvmName("getMainLightExplicit")
    fun getMainLight(): LightData? = mainLight
    fun getAdditionalLights(): List<LightData> = lights.filter { it !== mainLight }

    fun estimateIrradiance(pos: FloatArray): FloatArray {
        // Simple SH approximation from lights
        val result = floatArrayOf(
            ((ambientColor shr 16) and 0xFF) / 255f * ambientIntensity,
            ((ambientColor shr 8) and 0xFF) / 255f * ambientIntensity,
            (ambientColor and 0xFF) / 255f * ambientIntensity
        )
        for (ld in lights.take(4)) {
            val atten = if (ld.light.kind == 0) 1f else {
                val dx = pos[0] - ld.worldPos[0]
                val dy = pos[1] - ld.worldPos[1]
                val dz = pos[2] - ld.worldPos[2]
                val d2 = dx * dx + dy * dy + dz * dz
                val r2 = ld.range * ld.range
                if (d2 > r2) 0f else (1f - d2 / r2).coerceIn(0f, 1f)
            }
            result[0] += ld.color[0] * ld.intensity * atten * 0.5f
            result[1] += ld.color[1] * ld.intensity * atten * 0.5f
            result[2] += ld.color[2] * ld.intensity * atten * 0.5f
        }
        return result
    }
}

/**
 * Light Probes - like Unity's Light Probe Group
 */
class LightProbeGroup {
    data class Probe(val x: Float, val y: Float, val z: Float, var sh: FloatArray = FloatArray(27))

    val probes = mutableListOf<Probe>()

    fun addProbe(x: Float, y: Float, z: Float) { probes.add(Probe(x, y, z)) }

    fun interpolate(pos: FloatArray): FloatArray {
        if (probes.isEmpty()) return FloatArray(27)
        if (probes.size == 1) return probes[0].sh

        // Find 8 nearest for trilinear-ish interpolation
        val sorted = probes.sortedBy {
            val dx = it.x - pos[0]; val dy = it.y - pos[1]; val dz = it.z - pos[2]
            dx * dx + dy * dy + dz * dz
        }.take(8)

        val result = FloatArray(27)
        var totalW = 0f
        for (p in sorted) {
            val dx = p.x - pos[0]; val dy = p.y - pos[1]; val dz = p.z - pos[2]
            val d2 = dx * dx + dy * dy + dz * dz
            val w = 1f / maxOf(0.01f, d2)
            totalW += w
            for (i in 0 until 27) result[i] += p.sh[i] * w
        }
        if (totalW > 0f) for (i in 0 until 27) result[i] /= totalW
        return result
    }
}

/**
 * Reflection Probes - like Unity's Reflection Probe
 */
class ReflectionProbe(var x: Float, var y: Float, var z: Float) {
    var sizeX: Float = 10f
    var sizeY: Float = 10f
    var sizeZ: Float = 10f
    var resolution: Int = 128
    var intensity: Float = 1f
    var boxProjection: Boolean = false
    var blendDistance: Float = 1f
    var importance: Int = 0
    var cubemapId: Int = -1

    fun contains(px: Float, py: Float, pz: Float): Boolean {
        return px >= x - sizeX / 2 && px <= x + sizeX / 2 &&
               py >= y - sizeY / 2 && py <= y + sizeY / 2 &&
               pz >= z - sizeZ / 2 && pz <= z + sizeZ / 2
    }

    fun influenceWeight(px: Float, py: Float, pz: Float): Float {
        if (!contains(px, py, pz)) return 0f
        val dx = 1f - kotlin.math.abs(px - x) / (sizeX / 2)
        val dy = 1f - kotlin.math.abs(py - y) / (sizeY / 2)
        val dz = 1f - kotlin.math.abs(pz - z) / (sizeZ / 2)
        return (dx * dy * dz).coerceIn(0f, 1f)
    }
}

class ReflectionProbeSystem {
    val probes = mutableListOf<ReflectionProbe>()
    var defaultProbe: ReflectionProbe? = null

    fun addProbe(probe: ReflectionProbe) { probes.add(probe); probes.sortByDescending { it.importance } }

    fun getBlendedProbes(pos: FloatArray): List<Pair<ReflectionProbe, Float>> {
        val result = mutableListOf<Pair<ReflectionProbe, Float>>()
        for (p in probes) {
            val w = p.influenceWeight(pos[0], pos[1], pos[2])
            if (w > 0f) result.add(p to w)
        }
        return result.sortedByDescending { it.second }.take(2)
    }
}
