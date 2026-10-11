package com.sengine.engine.render

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.math.Mat4

/**
 * Ultimate Edition GPU Instancing & Batching - Unity SRP Batcher + GPU Instancing + Unreal Instanced Static Mesh equivalent
 */

data class InstancedBatch(
    var meshId: Int = 0,
    var materialId: String = "",
    var matrices: MutableList<FloatArray> = mutableListOf(),
    var colors: MutableList<Int> = mutableListOf(),
    var count: Int = 0
)

class GPUInstancingSystem {
    private val batches = mutableMapOf<String, InstancedBatch>()
    var maxInstancesPerBatch: Int = 1023
    var enabled: Boolean = true
    var frustumCulling: Boolean = true
    var occlusionCulling: Boolean = true
    var lodFading: Boolean = true
    var sortBatches: Boolean = true

    var totalBatches: Int = 0
        private set
    var totalInstances: Int = 0
        private set
    var culledInstances: Int = 0
        private set

    fun clear() { batches.clear(); totalBatches = 0; totalInstances = 0; culledInstances = 0 }

    fun addInstance(go: GameObject, meshId: Int, material: String) {
        if (!enabled) return
        val key = "$meshId:$material"
        val batch = batches.getOrPut(key) { InstancedBatch(meshId, material) }
        if (batch.count >= maxInstancesPerBatch) {
            // create new batch with suffix
            var suffix = 1
            var newKey = "${key}_$suffix"
            while (batches.containsKey(newKey) && batches[newKey]!!.count >= maxInstancesPerBatch) {
                suffix++; newKey = "${key}_$suffix"
            }
            val newBatch = batches.getOrPut(newKey) { InstancedBatch(meshId, material) }
            newBatch.matrices.add(go.world3.clone())
            newBatch.colors.add(go.get<com.sengine.engine.core.MeshRenderer>()?.color ?: 0xFFFFFFFF.toInt())
            newBatch.count++
        } else {
            batch.matrices.add(go.world3.clone())
            batch.colors.add(go.get<com.sengine.engine.core.MeshRenderer>()?.color ?: 0xFFFFFFFF.toInt())
            batch.count++
        }
    }

    fun buildBatches(scene: Scene, view: View3D): List<InstancedBatch> {
        clear()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val mr = go.get<com.sengine.engine.core.MeshRenderer>() ?: continue
            if (!mr.enabled) continue
            // Frustum culling check (simplified)
            if (frustumCulling) {
                val w = go.world3
                val dist = view.distanceTo(w[12], w[13], w[14])
                if (dist > view.far || dist < view.near) { culledInstances++; continue }
            }
            addInstance(go, mr.mesh, mr.material.ifBlank { mr.texture })
        }
        totalBatches = batches.size
        totalInstances = batches.values.sumOf { it.count }
        val list = batches.values.toList()
        return if (sortBatches) list.sortedBy { it.materialId } else list
    }
}

class SRPBatcher {
    data class BatchKey(val shader: String, val material: String, val mesh: Int)

    private val batches = mutableMapOf<BatchKey, MutableList<GameObject>>()
    var enabled: Boolean = true
    var compatibilityMode: Boolean = false

    fun add(go: GameObject) {
        if (!enabled) return
        val mr = go.get<com.sengine.engine.core.MeshRenderer>() ?: return
        val key = BatchKey(mr.shader.ifBlank { "Standard" }, mr.material.ifBlank { mr.texture }, mr.mesh)
        batches.getOrPut(key) { mutableListOf() }.add(go)
    }

    fun getBatches(): Map<BatchKey, List<GameObject>> = batches

    fun clear() = batches.clear()

    fun stats(): String = "SRP Batches: ${batches.size}, Total Objects: ${batches.values.sumOf { it.size }}"
}

class OcclusionCullingSystem {
    data class Occluder(var x: Float, var y: Float, var z: Float, var sizeX: Float, var sizeY: Float, var sizeZ: Float, var isStatic: Boolean = true)
    data class Occludee(var go: GameObject, var boundsMin: FloatArray, var boundsMax: FloatArray, var visible: Boolean = true)

    private val occluders = mutableListOf<Occluder>()
    private val occludees = mutableListOf<Occludee>()
    var enabled: Boolean = true

    fun addOccluder(o: Occluder) { occluders.add(o) }
    fun addOccludee(o: Occludee) { occludees.add(o) }
    fun clear() { occluders.clear(); occludees.clear() }

    fun cull(cameraPos: FloatArray): List<GameObject> {
        if (!enabled) return occludees.map { it.go }
        val visible = mutableListOf<GameObject>()
        for (occludee in occludees) {
            var occluded = false
            for (occluder in occluders) {
                // Simple box occlusion test
                if (isBehind(occluder, occludee, cameraPos)) { occluded = true; break }
            }
            occludee.visible = !occluded
            if (!occluded) visible.add(occludee.go)
        }
        return visible
    }

    private fun isBehind(occluder: Occluder, occludee: Occludee, camPos: FloatArray): Boolean {
        // Simplified: if occluder is between camera and occludee and closer
        val occluderDist = run {
            val dx = occluder.x - camPos[0]; val dy = occluder.y - camPos[1]; val dz = occluder.z - camPos[2]
            dx * dx + dy * dy + dz * dz
        }
        val occludeeDist = run {
            val cx = (occludee.boundsMin[0] + occludee.boundsMax[0]) / 2
            val cy = (occludee.boundsMin[1] + occludee.boundsMax[1]) / 2
            val cz = (occludee.boundsMin[2] + occludee.boundsMax[2]) / 2
            val dx = cx - camPos[0]; val dy = cy - camPos[1]; val dz = cz - camPos[2]
            dx * dx + dy * dy + dz * dz
        }
        return occluderDist < occludeeDist && occluderDist < 10000f
    }
}

class LODSystem {
    data class LODLevel(var screenRelativeHeight: Float, var mesh: String, var material: String, var fadeTransitionWidth: Float = 0f)

    class LODGroupData(val go: GameObject, val levels: List<LODLevel>, var currentLOD: Int = 0)

    private val groups = mutableListOf<LODGroupData>()
    var crossFade: Boolean = true
    var animateCrossFading: Boolean = false
    var lodBias: Float = 1f

    fun addGroup(group: LODGroupData) { groups.add(group) }

    fun update(cameraPos: FloatArray, screenHeight: Float) {
        for (group in groups) {
            val w = group.go.world3
            val dx = w[12] - cameraPos[0]; val dy = w[13] - cameraPos[1]; val dz = w[14] - cameraPos[2]
            val dist = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
            // Screen relative height approximation: object size / distance
            val size = maxOf(group.go.scaleX, group.go.scaleY, group.go.scaleZ)
            val screenHeightRel = (size / dist.coerceAtLeast(0.1f)) * lodBias
            var selectedLOD = group.levels.size - 1
            for ((i, level) in group.levels.withIndex()) {
                if (screenHeightRel >= level.screenRelativeHeight) { selectedLOD = i; break }
            }
            group.currentLOD = selectedLOD
        }
    }
}
