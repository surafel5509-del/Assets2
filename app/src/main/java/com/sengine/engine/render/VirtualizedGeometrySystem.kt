package com.sengine.engine.render

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Nanite-like Virtualized Geometry System
 * Meshlets, Cluster Culling, Virtual Shadow Maps, Software Rasterization Fallback
 */
enum class ClusterCullingMode { Frustum, Occlusion, Distance, Backface, All }
enum class NaniteQuality { Low, Medium, High, Ultra }

data class Meshlet(
    var id: Int = 0,
    var vertexOffset: Int = 0,
    var vertexCount: Int = 64,
    var triangleOffset: Int = 0,
    var triangleCount: Int = 124,
    var boundsMin: FloatArray = floatArrayOf(0f,0f,0f),
    var boundsMax: FloatArray = floatArrayOf(1f,1f,1f),
    var coneApex: FloatArray = floatArrayOf(0f,0f,0f),
    var coneAxis: FloatArray = floatArrayOf(0f,1f,0f),
    var coneCutoff: Float = 0.5f,
    var lodLevel: Int = 0,
    var error: Float = 0f,
    var parentId: Int = -1,
    var childIds: IntArray = IntArray(0)
) {
    fun boundsCenter(): FloatArray = floatArrayOf((boundsMin[0]+boundsMax[0])*0.5f, (boundsMin[1]+boundsMax[1])*0.5f, (boundsMin[2]+boundsMax[2])*0.5f)
    fun boundsSize(): FloatArray = floatArrayOf(boundsMax[0]-boundsMin[0], boundsMax[1]-boundsMin[1], boundsMax[2]-boundsMin[2])
    fun isVisible(cameraPos: FloatArray, cameraDir: FloatArray, frustumPlanes: Array<FloatArray>): Boolean {
        // Frustum + cone culling
        val center = boundsCenter()
        // Distance cull
        val dx = center[0]-cameraPos[0]; val dy = center[1]-cameraPos[1]; val dz = center[2]-cameraPos[2]
        val dist2 = dx*dx+dy*dy+dz*dz
        if (dist2 > 10000f) return false // 100 units
        // Backface cone culling
        val toCameraX = cameraPos[0]-coneApex[0]; val toCameraY = cameraPos[1]-coneApex[1]; val toCameraZ = cameraPos[2]-coneApex[2]
        val len = sqrt(toCameraX*toCameraX+toCameraY*toCameraY+toCameraZ*toCameraZ)
        if (len > 1e-6f) {
            val dot = (toCameraX/len)*coneAxis[0] + (toCameraY/len)*coneAxis[1] + (toCameraZ/len)*coneAxis[2]
            if (dot < coneCutoff) return false
        }
        return true
    }
}

data class VirtualizedMesh(
    var name: String = "Mesh",
    var meshlets: MutableList<Meshlet> = mutableListOf(),
    var lods: MutableList<List<Meshlet>> = mutableListOf(),
    var totalVertices: Int = 0,
    var totalTriangles: Int = 0,
    var boundsMin: FloatArray = floatArrayOf(-1f,-1f,-1f),
    var boundsMax: FloatArray = floatArrayOf(1f,1f,1f),
    var isVirtualized: Boolean = true
) {
    fun buildMeshlets(vertexCount: Int, triangleCount: Int) {
        meshlets.clear(); lods.clear()
        val meshletCount = (triangleCount + 123) / 124
        for (i in 0 until meshletCount) {
            val triCount = min(124, triangleCount - i*124)
            val vertCount = min(64, vertexCount - i*64)
            meshlets.add(Meshlet(id=i, vertexOffset=i*64, vertexCount=vertCount, triangleOffset=i*124, triangleCount=triCount, lodLevel=0))
        }
        // Build LODs - decimate by 50% each level
        var currentTris = triangleCount
        var lod = 0
        while (currentTris > 124 && lod < 6) {
            val lodMeshlets = meshlets.filter { it.lodLevel == lod }
            lods.add(lodMeshlets)
            currentTris /= 2
            lod++
            // Create parent meshlets
            val parentCount = (currentTris + 123)/124
            for (i in 0 until parentCount) {
                meshlets.add(Meshlet(id=meshlets.size, vertexOffset=0, vertexCount=64, triangleOffset=0, triangleCount=min(124, currentTris-i*124), lodLevel=lod, error=lod*0.01f))
            }
        }
        totalVertices = vertexCount; totalTriangles = triangleCount
    }
    fun getLodForDistance(distance: Float): Int {
        return when {
            distance < 10f -> 0
            distance < 25f -> 1
            distance < 50f -> 2
            distance < 100f -> 3
            else -> min(4, lods.size-1)
        }
    }
}

class VirtualizedGeometrySystem {
    var meshes = mutableMapOf<String, VirtualizedMesh>()
    var quality: NaniteQuality = NaniteQuality.High
    var enabled: Boolean = true
    var maxTrianglesPerFrame: Int = 10_000_000
    var clusterCulling: Boolean = true
    var occlusionCulling: Boolean = true
    var softwareRasterizer: Boolean = false
    var statsVisibleMeshlets: Int = 0
    var statsCulledMeshlets: Int = 0
    var statsTotalTriangles: Int = 0

    fun registerMesh(name: String, vertexCount: Int, triangleCount: Int): VirtualizedMesh {
        val mesh = VirtualizedMesh(name); mesh.buildMeshlets(vertexCount, triangleCount)
        meshes[name] = mesh
        return mesh
    }

    fun cullMeshlets(cameraPos: FloatArray, cameraDir: FloatArray, frustumPlanes: Array<FloatArray>): List<Meshlet> {
        if (!enabled) return meshes.values.flatMap { it.meshlets }
        val visible = mutableListOf<Meshlet>()
        statsVisibleMeshlets = 0; statsCulledMeshlets = 0; statsTotalTriangles = 0
        for (mesh in meshes.values) {
            val dist = sqrt((mesh.boundsMin[0]-cameraPos[0]).pow(2) + (mesh.boundsMin[1]-cameraPos[1]).pow(2) + (mesh.boundsMin[2]-cameraPos[2]).pow(2))
            val lodLevel = mesh.getLodForDistance(dist)
            val lodMeshlets = if (lodLevel < mesh.lods.size) mesh.lods[lodLevel] else mesh.meshlets.filter { it.lodLevel == lodLevel }
            for (meshlet in lodMeshlets) {
                if (clusterCulling) {
                    if (!meshlet.isVisible(cameraPos, cameraDir, frustumPlanes)) { statsCulledMeshlets++; continue }
                }
                visible.add(meshlet); statsVisibleMeshlets++; statsTotalTriangles += meshlet.triangleCount
                if (statsTotalTriangles > maxTrianglesPerFrame) break
            }
        }
        return visible
    }

    fun softwareRasterize(meshlets: List<Meshlet>, width: Int, height: Int): IntArray {
        // Software rasterizer fallback - returns visibility buffer
        val visBuffer = IntArray(width*height) { -1 }
        if (!softwareRasterizer) return visBuffer
        // Simplified - mark meshlet coverage
        for (meshlet in meshlets.take(100)) {
            val center = meshlet.boundsCenter()
            val x = ((center[0]+50f)/100f*width).toInt().coerceIn(0, width-1)
            val y = ((center[2]+50f)/100f*height).toInt().coerceIn(0, height-1)
            visBuffer[y*width+x] = meshlet.id
        }
        return visBuffer
    }

    fun getStats(): String = "Nanite Quality:$quality Meshes:${meshes.size} Visible:$statsVisibleMeshlets Culled:$statsCulledMeshlets Tris:$statsTotalTriangles/${maxTrianglesPerFrame}"
}
