package com.sengine.engine.world

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Foliage & Procedural Ecosystem System
 * Foliage painting, instanced foliage, wind, interaction
 */
enum class FoliageType { Grass, Flower, Bush, Tree, Rock, Custom }
enum class FoliageDistribution { Random, Uniform, Clustered, Noise, Slope }

data class FoliagePrototype(
    var name: String = "Grass",
    var meshName: String = "GrassMesh",
    var materialName: String = "GrassMaterial",
    var type: FoliageType = FoliageType.Grass,
    var minScale: Float = 0.8f,
    var maxScale: Float = 1.2f,
    var density: Float = 100f, // per 100m2
    var cullDistance: Float = 50f,
    var windStrength: Float = 0.5f,
    var windFrequency: Float = 1f,
    var castShadow: Boolean = false,
    var affectNavMesh: Boolean = false,
    var collision: Boolean = false
)

data class FoliageInstance(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var scale: Float = 1f,
    var rotY: Float = 0f,
    var prototypeIndex: Int = 0,
    var windPhase: Float = 0f,
    var health: Float = 1f
) {
    fun distanceTo(px: Float, pz: Float): Float {
        val dx = x-px; val dz = z-pz; return sqrt(dx*dx+dz*dz)
    }
}

class FoliageSystem {
    var prototypes = mutableListOf<FoliagePrototype>()
    var instances = mutableListOf<FoliageInstance>()
    var enabled: Boolean = true
    var maxInstances: Int = 100000
    var windSpeed: Float = 1f
    var windDirectionX: Float = 1f
    var windDirectionZ: Float = 0.5f
    var time: Float = 0f
    var cullDistanceScale: Float = 1f

    fun addPrototype(name: String, type: FoliageType = FoliageType.Grass, density: Float = 100f): FoliagePrototype {
        val p = FoliagePrototype(name=name, type=type, density=density); prototypes.add(p); return p
    }

    fun paintFoliage(prototypeName: String, centerX: Float, centerZ: Float, radius: Float, terrainHeight: (Float,Float)->Float, density: Float = 1f) {
        val protoIdx = prototypes.indexOfFirst { it.name == prototypeName }
        if (protoIdx == -1) return
        val proto = prototypes[protoIdx]
        val area = PI.toFloat()*radius*radius
        val count = (area/100f * proto.density * density).toInt()
        for (i in 0 until count) {
            if (instances.size >= maxInstances) break
            val angle = Math.random().toFloat()*2f*PI.toFloat()
            val r = sqrt(Math.random().toFloat())*radius
            val x = centerX + cos(angle)*r
            val z = centerZ + sin(angle)*r
            val y = terrainHeight(x, z)
            val scale = proto.minScale + Math.random().toFloat()*(proto.maxScale-proto.minScale)
            val rotY = Math.random().toFloat()*360f
            instances.add(FoliageInstance(x=x, y=y, z=z, scale=scale, rotY=rotY, prototypeIndex=protoIdx, windPhase=Math.random().toFloat()*6.28f))
        }
    }

    fun generateProcedural(worldSize: Float, terrainHeight: (Float,Float)->Float, terrainSlope: (Float,Float)->Float) {
        instances.clear()
        for (proto in prototypes) {
            val count = (worldSize*worldSize/100f * proto.density * 0.1f).toInt()
            for (i in 0 until count) {
                if (instances.size >= maxInstances) break
                val x = (Math.random().toFloat()-0.5f)*worldSize
                val z = (Math.random().toFloat()-0.5f)*worldSize
                val slope = terrainSlope(x,z)
                if (proto.type == FoliageType.Grass && slope > 0.5f) continue
                if (proto.type == FoliageType.Tree && slope > 0.7f) continue
                val y = terrainHeight(x,z)
                val scale = proto.minScale + Math.random().toFloat()*(proto.maxScale-proto.minScale)
                instances.add(FoliageInstance(x=x, y=y, z=z, scale=scale, rotY=Math.random().toFloat()*360f, prototypeIndex=prototypes.indexOf(proto), windPhase=Math.random().toFloat()*6.28f))
            }
        }
    }

    fun update(dt: Float, playerX: Float, playerZ: Float) {
        if (!enabled) return
        time+=dt
        // Wind animation - update wind phase
        for (inst in instances) {
            // Simple wind sway
            val wind = sin(time*windSpeed*prototypes.getOrNull(inst.prototypeIndex)?.windFrequency?:1f + inst.windPhase) * (prototypes.getOrNull(inst.prototypeIndex)?.windStrength?:0.5f)
            // Apply wind to position slightly
            // inst.x+=wind*dt*0.1f - but we don't modify base pos, shader does
        }
    }

    fun getVisibleInstances(playerX: Float, playerZ: Float): List<FoliageInstance> {
        return instances.filter { inst ->
            val proto = prototypes.getOrNull(inst.prototypeIndex) ?: return@filter false
            val dist = inst.distanceTo(playerX, playerZ)
            dist < proto.cullDistance * cullDistanceScale
        }
    }

    fun interact(x: Float, z: Float, radius: Float) {
        // Trample grass
        for (inst in instances) {
            if (inst.distanceTo(x,z) < radius) {
                val proto = prototypes.getOrNull(inst.prototypeIndex) ?: continue
                if (proto.type == FoliageType.Grass) inst.health-=0.5f
            }
        }
        instances.removeAll { it.health <= 0f }
    }

    fun clear() { instances.clear() }
    fun getStats(): String = "Foliage Protos:${prototypes.size} Instances:${instances.size}/$maxInstances"
}
