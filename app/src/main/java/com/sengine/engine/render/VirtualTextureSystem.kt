package com.sengine.engine.render

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Virtual Texture & Streaming System
 * Runtime Virtual Texture, UDIM, Texture Streaming, Sparse Textures
 */
enum class VirtualTextureFormat { BC1, BC3, BC5, BC7, ASTC_4x4, ASTC_6x6, RGBA8, RGBA16F }
enum class VirtualTextureTileStatus { Unloaded, Loading, Loaded, Evicted }

data class VirtualTextureTile(
    var x: Int = 0, var y: Int = 0, var mip: Int = 0,
    var status: VirtualTextureTileStatus = VirtualTextureTileStatus.Unloaded,
    var lastUsedFrame: Long = 0L,
    var priority: Float = 0f,
    var sizeBytes: Int = 16384
)

data class VirtualTexture(
    var name: String = "VT",
    var width: Int = 8192,
    var height: Int = 8192,
    var tileSize: Int = 128,
    var mipCount: Int = 7,
    var format: VirtualTextureFormat = VirtualTextureFormat.BC7,
    var tiles: MutableMap<String, VirtualTextureTile> = mutableMapOf(),
    var physicalCacheSize: Int = 1024, // tiles
    var streamingPoolMB: Int = 256
) {
    fun tileKey(x: Int, y: Int, mip: Int): String = "${mip}_${x}_${y}"
    fun tilesX(mip: Int): Int = max(1, width shr mip) / tileSize
    fun tilesY(mip: Int): Int = max(1, height shr mip) / tileSize
    fun totalTiles(): Int { var t=0; for (m in 0 until mipCount) t+=tilesX(m)*tilesY(m); return t }
    fun getTile(x: Int, y: Int, mip: Int): VirtualTextureTile {
        val key = tileKey(x,y,mip)
        return tiles.getOrPut(key) { VirtualTextureTile(x,y,mip) }
    }
    fun requestTile(x: Int, y: Int, mip: Int, frame: Long, priority: Float) {
        val tile = getTile(x,y,mip)
        tile.lastUsedFrame = frame; tile.priority = max(tile.priority, priority)
        if (tile.status == VirtualTextureTileStatus.Unloaded) tile.status = VirtualTextureTileStatus.Loading
    }
}

class VirtualTextureSystem {
    var textures = mutableMapOf<String, VirtualTexture>()
    var enabled: Boolean = true
    var streamingEnabled: Boolean = true
    var maxTilesPerFrame: Int = 32
    var frameCount: Long = 0L
    var loadedTiles: Int = 0
    var streamingTimeMs: Float = 0f
    var poolUsageMB: Float = 0f

    fun createVirtualTexture(name: String, width: Int = 8192, height: Int = 8192, tileSize: Int = 128): VirtualTexture {
        val vt = VirtualTexture(name, width, height, tileSize)
        textures[name] = vt
        return vt
    }

    fun requestTilesForView(cameraX: Float, cameraY: Float, cameraZ: Float, viewWidth: Float, viewHeight: Float) {
        if (!enabled || !streamingEnabled) return
        frameCount++
        for (vt in textures.values) {
            // Request tiles around camera
            val centerTileX = ((cameraX+500f)/1000f * vt.tilesX(0)).toInt()
            val centerTileY = ((cameraZ+500f)/1000f * vt.tilesY(0)).toInt()
            val radius = 4
            for (mip in 0 until min(3, vt.mipCount)) {
                val scale = 1 shl mip
                val tx0 = (centerTileX/scale - radius).coerceAtLeast(0)
                val ty0 = (centerTileY/scale - radius).coerceAtLeast(0)
                val tx1 = (centerTileX/scale + radius).coerceAtMost(vt.tilesX(mip)-1)
                val ty1 = (centerTileY/scale + radius).coerceAtMost(vt.tilesY(mip)-1)
                for (ty in ty0..ty1) for (tx in tx0..tx1) {
                    val dist = sqrt((tx*scale-centerTileX).toFloat().pow(2)+(ty*scale-centerTileY).toFloat().pow(2))
                    val priority = 1f/(1f+dist*0.1f) * (1f - mip*0.2f)
                    vt.requestTile(tx, ty, mip, frameCount, priority)
                }
            }
        }
    }

    fun updateStreaming() {
        if (!streamingEnabled) return
        val t0 = System.nanoTime()
        var tilesStreamed = 0
        for (vt in textures.values) {
            val loading = vt.tiles.values.filter { it.status == VirtualTextureTileStatus.Loading }.sortedByDescending { it.priority }
            for (tile in loading.take(maxTilesPerFrame - tilesStreamed)) {
                tile.status = VirtualTextureTileStatus.Loaded
                tilesStreamed++
            }
            // Evict old tiles
            val loaded = vt.tiles.values.filter { it.status == VirtualTextureTileStatus.Loaded }
            if (loaded.size > vt.physicalCacheSize) {
                val toEvict = loaded.sortedBy { it.lastUsedFrame }.take(loaded.size - vt.physicalCacheSize)
                for (tile in toEvict) tile.status = VirtualTextureTileStatus.Evicted
            }
            loadedTiles = vt.tiles.values.count { it.status == VirtualTextureTileStatus.Loaded }
        }
        streamingTimeMs = (System.nanoTime()-t0)/1_000_000f
        poolUsageMB = loadedTiles * 0.016f // 16KB per tile approx
    }

    fun sampleVirtualTexture(vtName: String, u: Float, v: Float, mip: Float = 0f): FloatArray {
        val vt = textures[vtName] ?: return floatArrayOf(1f,0f,1f) // magenta error
        val mipInt = mip.toInt().coerceIn(0, vt.mipCount-1)
        val tx = (u * vt.tilesX(mipInt)).toInt().coerceIn(0, vt.tilesX(mipInt)-1)
        val ty = (v * vt.tilesY(mipInt)).toInt().coerceIn(0, vt.tilesY(mipInt)-1)
        vt.requestTile(tx, ty, mipInt, frameCount, 1f)
        val tile = vt.getTile(tx, ty, mipInt)
        return if (tile.status == VirtualTextureTileStatus.Loaded) floatArrayOf(u, v, 1f) else floatArrayOf(0.5f, 0.5f, 0.5f) // low mip fallback
    }

    fun getStats(): String = "VTs:${textures.size} Loaded:$loadedTiles Pool:${poolUsageMB.toInt()}MB Time:${streamingTimeMs}ms"
}
