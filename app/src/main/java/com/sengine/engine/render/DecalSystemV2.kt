package com.sengine.engine.render

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Decal System V2 & Advanced Rendering
 * Deferred decals, mesh decals, decal atlas, projection
 */
enum class DecalType { Diffuse, Normal, Emissive, Combined }
enum class DecalProjection { Planar, Box, Sphere, Mesh }

data class Decal(
    var id: String = "decal_0",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var rotX: Float = 0f, var rotY: Float = 0f, var rotZ: Float = 0f,
    var sizeX: Float = 1f, var sizeY: Float = 1f, var sizeZ: Float = 1f,
    var type: DecalType = DecalType.Combined,
    var projection: DecalProjection = DecalProjection.Planar,
    var material: String = "DecalMaterial",
    var texture: String = "DecalTexture",
    var normalMap: String = "",
    var emissiveMap: String = "",
    var opacity: Float = 1f,
    var fadeDistance: Float = 10f,
    var fadeScale: Float = 1f,
    var cullAngle: Float = 90f,
    var sortOrder: Int = 0,
    var isVisible: Boolean = true,
    var lifeTime: Float = 0f, // 0 = infinite
    var age: Float = 0f
) {
    fun containsPoint(px: Float, py: Float, pz: Float): Boolean {
        // Simple AABB check
        return abs(px-x) <= sizeX*0.5f && abs(py-y) <= sizeY*0.5f && abs(pz-z) <= sizeZ*0.5f
    }
    fun distanceTo(px: Float, pz: Float): Float {
        val dx = x-px; val dz = z-pz; return sqrt(dx*dx+dz*dz)
    }
    fun update(dt: Float): Boolean {
        if (lifeTime > 0f) {
            age+=dt
            if (age >= lifeTime) return false // should be removed
            opacity = 1f - (age/lifeTime)
        }
        return true
    }
}

class DecalSystemV2 {
    var decals = mutableMapOf<String, Decal>()
    var maxDecals: Int = 1024
    var enabled: Boolean = true
    var atlasSize: Int = 2048
    var fadeEnabled: Boolean = true
    var nextId: Int = 0

    fun createDecal(x: Float, y: Float, z: Float, sizeX: Float = 1f, sizeY: Float = 1f, sizeZ: Float = 0.1f, texture: String = "BulletHole"): Decal {
        if (decals.size >= maxDecals) {
            // Remove oldest
            val oldest = decals.values.minByOrNull { it.age }
            if (oldest != null) decals.remove(oldest.id)
        }
        val id = "decal_${nextId++}"
        val decal = Decal(id=id, x=x, y=y, z=z, sizeX=sizeX, sizeY=sizeY, sizeZ=sizeZ, texture=texture)
        decals[id]=decal
        return decal
    }

    fun createBulletHole(x: Float, y: Float, z: Float, normalX: Float, normalY: Float, normalZ: Float): Decal {
        val decal = createDecal(x+normalX*0.01f, y+normalY*0.01f, z+normalZ*0.01f, 0.2f, 0.2f, 0.05f, "BulletHole")
        // Orient to normal
        decal.rotY = atan2(normalX, normalZ)*180f/PI.toFloat()
        decal.rotX = asin(normalY)*180f/PI.toFloat()
        decal.lifeTime = 30f
        return decal
    }

    fun createBloodSplatter(x: Float, y: Float, z: Float): Decal {
        val decal = createDecal(x, y+0.01f, z, 0.5f+Math.random().toFloat()*0.5f, 0.01f, 0.5f+Math.random().toFloat()*0.5f, "Blood")
        decal.type = DecalType.Diffuse
        decal.lifeTime = 60f
        return decal
    }

    fun createFootstep(x: Float, y: Float, z: Float, rotY: Float): Decal {
        val decal = createDecal(x, y+0.01f, z, 0.3f, 0.01f, 0.6f, "Footstep")
        decal.rotY = rotY
        decal.lifeTime = 20f
        decal.opacity = 0.6f
        return decal
    }

    fun update(dt: Float, playerX: Float, playerZ: Float) {
        if (!enabled) return
        val toRemove = mutableListOf<String>()
        for (decal in decals.values) {
            if (!decal.update(dt)) toRemove.add(decal.id)
            // Fade by distance
            if (fadeEnabled) {
                val dist = decal.distanceTo(playerX, playerZ)
                if (dist > decal.fadeDistance) {
                    decal.isVisible = false
                } else {
                    decal.isVisible = true
                    decal.fadeScale = 1f - (dist/decal.fadeDistance).coerceIn(0f,1f)*0.5f
                }
            }
        }
        for (id in toRemove) decals.remove(id)
    }

    fun getVisibleDecals(playerX: Float, playerZ: Float, maxDistance: Float = 50f): List<Decal> {
        return decals.values.filter { it.isVisible && it.distanceTo(playerX, playerZ) < maxDistance }.sortedBy { it.sortOrder }
    }

    fun clear() { decals.clear() }

    fun getStats(): String = "Decals:${decals.size}/$maxDecals Atlas:${atlasSize}x$atlasSize"
}

// Lightmapping System
data class LightmapBakeSettings(
    var resolution: Int = 64, // texels per unit
    var bounces: Int = 2,
    var samples: Int = 128,
    var denoiser: Boolean = true,
    var directSamples: Int = 32,
    var indirectSamples: Int = 64,
    var maxDistance: Float = 100f,
    var aoEnabled: Boolean = true,
    var aoDistance: Float = 1f
)

class LightmappingSystem {
    var settings = LightmapBakeSettings()
    var isBaking: Boolean = false
    var bakeProgress: Float = 0f
    var lightmaps = mutableMapOf<String, LightmapData>()
    var bakeTimeMs: Float = 0f

    fun bakeScene(objects: List<Pair<String, FloatArray>>, lights: List<Pair<FloatArray, FloatArray>>): Boolean {
        isBaking = true
        val t0 = System.nanoTime()
        for ((idx, obj) in objects.withIndex()) {
            val name = obj.first
            val lm = LightmapData(width=settings.resolution, height=settings.resolution)
            // Simple bake - accumulate lights
            for (y in 0 until lm.height) for (x in 0 until lm.width) {
                var r=0f; var g=0f; var b=0f
                for (light in lights) {
                    val lx = light.first[0]; val ly = light.first[1]; val lz = light.first[2]
                    val lr = light.second[0]; val lg = light.second[1]; val lb = light.second[2]
                    // Distance attenuation
                    val dx = lx-obj.second[0]; val dy = ly-obj.second[1]; val dz = lz-obj.second[2]
                    val dist = sqrt(dx*dx+dy*dy+dz*dz)
                    val atten = 1f/(1f+dist*0.1f)
                    r+=lr*atten; g+=lg*atten; b+=lb*atten
                }
                lm.setPixel(x,y,r,g,b)
            }
            lightmaps[name]=lm
            bakeProgress = (idx+1).toFloat()/objects.size
        }
        bakeTimeMs = (System.nanoTime()-t0)/1_000_000f
        isBaking = false
        bakeProgress = 1f
        return true
    }

    fun getLightmap(name: String): LightmapData? = lightmaps[name]
    fun getStats(): String = "Lightmaps:${lightmaps.size} Baking:$isBaking Progress:${(bakeProgress*100).toInt()}% Time:${bakeTimeMs}ms"
}
