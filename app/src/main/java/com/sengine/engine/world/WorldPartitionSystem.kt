package com.sengine.engine.world

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - World Partition & Streaming System
 * World Partition Grid, HLOD, Level Streaming, World Builder
 */
enum class StreamingState { Unloaded, Loading, Loaded, Unloading, Visible, Hidden }
enum class HLODLevel { LOD0, LOD1, LOD2, LOD3, LOD4, Impostor }

data class WorldCell(
    var x: Int = 0, var z: Int = 0,
    var size: Float = 256f,
    var state: StreamingState = StreamingState.Unloaded,
    var objects: MutableList<Long> = mutableListOf(),
    var loadPriority: Float = 0f,
    var lastVisibleTime: Long = 0L,
    var hlodLevel: HLODLevel = HLODLevel.LOD0,
    var boundsMin: FloatArray = floatArrayOf(0f,0f,0f),
    var boundsMax: FloatArray = floatArrayOf(0f,0f,0f)
) {
    fun centerX(): Float = (boundsMin[0]+boundsMax[0])*0.5f
    fun centerZ(): Float = (boundsMin[2]+boundsMax[2])*0.5f
    fun contains(wx: Float, wz: Float): Boolean = wx >= boundsMin[0] && wx <= boundsMax[0] && wz >= boundsMin[2] && wz <= boundsMax[2]
    fun distanceTo(wx: Float, wz: Float): Float {
        val dx = centerX()-wx; val dz = centerZ()-wz; return sqrt(dx*dx+dz*dz)
    }
}

data class HLODCluster(
    var id: Int = 0,
    var level: HLODLevel = HLODLevel.LOD1,
    var cells: List<Pair<Int,Int>> = emptyList(),
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var boundsMin: FloatArray = floatArrayOf(0f,0f,0f),
    var boundsMax: FloatArray = floatArrayOf(0f,0f,0f),
    var meshId: String = "",
    var screenSize: Float = 0.5f
)

data class StreamingLevel(
    var name: String = "Level",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var sizeX: Float = 100f, var sizeY: Float = 50f, var sizeZ: Float = 100f,
    var state: StreamingState = StreamingState.Unloaded,
    var shouldBeLoaded: Boolean = false,
    var shouldBeVisible: Boolean = false,
    var streamingDistance: Float = 100f,
    var lodDistance: Float = 50f
) {
    fun contains(wx: Float, wy: Float, wz: Float): Boolean =
        wx >= x-sizeX*0.5f && wx <= x+sizeX*0.5f && wy >= y-sizeY*0.5f && wy <= y+sizeY*0.5f && wz >= z-sizeZ*0.5f && wz <= z+sizeZ*0.5f
    fun distanceTo(wx: Float, wy: Float, wz: Float): Float {
        val dx = x-wx; val dy = y-wy; val dz = z-wz; return sqrt(dx*dx+dy*dy+dz*dz)
    }
}

class WorldPartitionSystem {
    var worldSize: Float = 4096f
    var cellSize: Float = 256f
    var cells = mutableMapOf<String, WorldCell>()
    var hlodClusters = mutableListOf<HLODCluster>()
    var streamingLevels = mutableMapOf<String, StreamingLevel>()
    var enabled: Boolean = true
    var streamingDistance: Float = 500f
    var gcDistance: Float = 1000f
    var maxCellsPerFrame: Int = 4
    var playerX: Float = 0f; var playerY: Float = 0f; var playerZ: Float = 0f
    var frameCount: Long = 0L
    var loadedCells: Int = 0
    var visibleCells: Int = 0

    fun cellKey(x: Int, z: Int): String = "${x}_${z}"

    fun initialize() {
        val count = (worldSize/cellSize).toInt()
        for (cx in -count/2..count/2) for (cz in -count/2..count/2) {
            val key = cellKey(cx, cz)
            val minX = cx*cellSize; val maxX = minX+cellSize
            val minZ = cz*cellSize; val maxZ = minZ+cellSize
            cells[key] = WorldCell(x=cx, z=cz, size=cellSize, boundsMin=floatArrayOf(minX,0f,minZ), boundsMax=floatArrayOf(maxX,100f,maxZ))
        }
        // Build HLOD
        buildHLOD()
    }

    fun buildHLOD() {
        hlodClusters.clear()
        val clusterSize = 4
        val count = (worldSize/cellSize).toInt()
        var clusterId = 0
        for (cx in -count/2..count/2 step clusterSize) for (cz in -count/2..count/2 step clusterSize) {
            val cellList = mutableListOf<Pair<Int,Int>>()
            var minX=Float.MAX_VALUE; var minZ=Float.MAX_VALUE; var maxX=-Float.MAX_VALUE; var maxZ=-Float.MAX_VALUE
            for (dx in 0 until clusterSize) for (dz in 0 until clusterSize) {
                val key = cellKey(cx+dx, cz+dz)
                cells[key]?.let { cellList.add(Pair(cx+dx, cz+dz)); minX=min(minX, cell.boundsMin[0]); minZ=min(minZ, cell.boundsMin[2]); maxX=max(maxX, cell.boundsMax[0]); maxZ=max(maxZ, cell.boundsMax[2]) }
            }
            if (cellList.isNotEmpty()) {
                hlodClusters.add(HLODCluster(id=clusterId++, level=HLODLevel.LOD1, cells=cellList, x=(minX+maxX)*0.5f, z=(minZ+maxZ)*0.5f, boundsMin=floatArrayOf(minX,0f,minZ), boundsMax=floatArrayOf(maxX,100f,maxZ), meshId="HLOD_${clusterId}"))
            }
        }
    }

    fun updatePlayerPosition(x: Float, y: Float, z: Float) { playerX=x; playerY=y; playerZ=z; frameCount++ }

    fun updateStreaming() {
        if (!enabled) return
        var processed = 0
        // Sort cells by distance
        val sorted = cells.values.sortedBy { it.distanceTo(playerX, playerZ) }
        for (cell in sorted) {
            val dist = cell.distanceTo(playerX, playerZ)
            val shouldLoad = dist < streamingDistance
            val shouldVisible = dist < streamingDistance*0.8f
            val shouldUnload = dist > gcDistance

            when {
                shouldUnload && cell.state != StreamingState.Unloaded -> {
                    if (processed < maxCellsPerFrame) { cell.state = StreamingState.Unloading; cell.state = StreamingState.Unloaded; cell.objects.clear(); processed++ }
                }
                shouldLoad && cell.state == StreamingState.Unloaded -> {
                    if (processed < maxCellsPerFrame) { cell.state = StreamingState.Loading; cell.state = StreamingState.Loaded; cell.lastVisibleTime=frameCount; processed++ }
                }
                shouldVisible && cell.state == StreamingState.Loaded -> { cell.state = StreamingState.Visible; cell.lastVisibleTime=frameCount }
                !shouldVisible && cell.state == StreamingState.Visible -> cell.state = StreamingState.Hidden
            }
        }
        loadedCells = cells.values.count { it.state != StreamingState.Unloaded }
        visibleCells = cells.values.count { it.state == StreamingState.Visible }

        // Update streaming levels
        for (level in streamingLevels.values) {
            val dist = level.distanceTo(playerX, playerY, playerZ)
            level.shouldBeLoaded = dist < level.streamingDistance
            level.shouldBeVisible = dist < level.lodDistance
            if (level.shouldBeLoaded && level.state == StreamingState.Unloaded) level.state = StreamingState.Loaded
            else if (!level.shouldBeLoaded && level.state != StreamingState.Unloaded) level.state = StreamingState.Unloaded
        }
    }

    fun addObjectToCell(objId: Long, worldX: Float, worldZ: Float) {
        val cx = floor(worldX/cellSize).toInt(); val cz = floor(worldZ/cellSize).toInt()
        val key = cellKey(cx, cz)
        cells[key]?.objects?.add(objId)
    }

    fun getVisibleCells(): List<WorldCell> = cells.values.filter { it.state == StreamingState.Visible }
    fun getLoadedCells(): List<WorldCell> = cells.values.filter { it.state != StreamingState.Unloaded }

    fun createStreamingLevel(name: String, x: Float, y: Float, z: Float, sizeX: Float, sizeY: Float, sizeZ: Float): StreamingLevel {
        val level = StreamingLevel(name, x,y,z,sizeX,sizeY,sizeZ); streamingLevels[name]=level; return level
    }

    fun getHLODForDistance(distance: Float): HLODLevel = when {
        distance < 100f -> HLODLevel.LOD0
        distance < 250f -> HLODLevel.LOD1
        distance < 500f -> HLODLevel.LOD2
        distance < 1000f -> HLODLevel.LOD3
        else -> HLODLevel.Impostor
    }

    fun getStats(): String = "World ${worldSize}m Cells:${cells.size} Loaded:$loadedCells Visible:$visibleCells HLOD:${hlodClusters.size} Levels:${streamingLevels.size}"
}
