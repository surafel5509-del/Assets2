package com.sengine.engine.terrain

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Ultimate Edition Terrain System - Unity Terrain + Unreal Landscape equivalent
 */

class Heightmap(var width: Int, var height: Int) {
    var data: FloatArray = FloatArray(width * height)

    fun get(x: Int, y: Int): Float {
        if (x !in 0 until width || y !in 0 until height) return 0f
        return data[y * width + x]
    }

    fun set(x: Int, y: Int, value: Float) {
        if (x !in 0 until width || y !in 0 until height) return
        data[y * width + x] = value
    }

    fun getBilinear(u: Float, v: Float): Float {
        val x = u * (width - 1)
        val y = v * (height - 1)
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceIn(0, width - 1)
        val y1 = (y0 + 1).coerceIn(0, height - 1)
        val fx = x - x0
        val fy = y - y0
        val h00 = get(x0, y0); val h10 = get(x1, y0)
        val h01 = get(x0, y1); val h11 = get(x1, y1)
        val hx0 = h00 * (1 - fx) + h10 * fx
        val hx1 = h01 * (1 - fx) + h11 * fx
        return hx0 * (1 - fy) + hx1 * fy
    }

    fun generatePerlin(scale: Float = 0.05f, octaves: Int = 4, persistence: Float = 0.5f, lacunarity: Float = 2f) {
        for (y in 0 until height) for (x in 0 until width) {
            var amplitude = 1f
            var frequency = 1f
            var noiseHeight = 0f
            var maxNoiseHeight = 0f
            for (o in 0 until octaves) {
                val sampleX = x * scale * frequency
                val sampleY = y * scale * frequency
                val perlin = perlinNoise(sampleX, sampleY) * 2f - 1f
                noiseHeight += perlin * amplitude
                maxNoiseHeight += amplitude
                amplitude *= persistence
                frequency *= lacunarity
            }
            noiseHeight /= maxNoiseHeight
            data[y * width + x] = (noiseHeight + 1f) * 0.5f
        }
    }

    private fun perlinNoise(x: Float, y: Float): Float {
        // Simple pseudo-perlin using sin
        return (kotlin.math.sin(x * 12.9898f + y * 78.233f) * 43758.5453f).let { it - kotlin.math.floor(it) }
    }

    fun generateFractalDiamondSquare(roughness: Float = 0.5f) {
        // Diamond-square algorithm
        val size = maxOf(width, height)
        var step = size - 1
        data[0] = Random.nextFloat()
        data[width - 1] = Random.nextFloat()
        data[(height - 1) * width] = Random.nextFloat()
        data[height * width - 1] = Random.nextFloat()

        var scale = roughness
        while (step > 1) {
            val half = step / 2
            // Diamond
            for (y in 0 until height - 1 step step) for (x in 0 until width - 1 step step) {
                val avg = (get(x, y) + get(x + step, y) + get(x, y + step) + get(x + step, y + step)) * 0.25f
                set(x + half, y + half, avg + (Random.nextFloat() * 2f - 1f) * scale)
            }
            // Square
            for (y in 0 until height step half) for (x in (if (y % step == 0) half else 0) until width step step) {
                var sum = 0f; var count = 0
                if (x - half >= 0) { sum += get(x - half, y); count++ }
                if (x + half < width) { sum += get(x + half, y); count++ }
                if (y - half >= 0) { sum += get(x, y - half); count++ }
                if (y + half < height) { sum += get(x, y + half); count++ }
                if (count > 0) set(x, y, sum / count + (Random.nextFloat() * 2f - 1f) * scale)
            }
            step /= 2
            scale *= 0.5f
        }
    }
}

class Splatmap(var width: Int, var height: Int, var layers: Int = 4) {
    var data: FloatArray = FloatArray(width * height * layers)

    fun get(x: Int, y: Int, layer: Int): Float {
        if (x !in 0 until width || y !in 0 until height || layer !in 0 until layers) return 0f
        return data[(y * width + x) * layers + layer]
    }

    fun set(x: Int, y: Int, layer: Int, value: Float) {
        if (x !in 0 until width || y !in 0 until height || layer !in 0 until layers) return
        data[(y * width + x) * layers + layer] = value
    }

    fun normalize(x: Int, y: Int) {
        var sum = 0f
        for (l in 0 until layers) sum += get(x, y, l)
        if (sum > 0f) for (l in 0 until layers) set(x, y, l, get(x, y, l) / sum)
    }
}

class TerrainLayer(var name: String = "Layer") {
    var diffuseTexture: String = ""
    var normalTexture: String = ""
    var maskTexture: String = ""
    var tileSize: FloatArray = floatArrayOf(10f, 10f)
    var tileOffset: FloatArray = floatArrayOf(0f, 0f)
    var metallic: Float = 0f
    var smoothness: Float = 0.5f
    var specular: Float = 0f
}

class TerrainData(var width: Float = 100f, var height: Float = 100f, var heightmapResolution: Int = 513) {
    var heightmap = Heightmap(heightmapResolution, heightmapResolution)
    var splatmap = Splatmap(512, 512, 4)
    var layers = mutableListOf<TerrainLayer>()
    var size: FloatArray = floatArrayOf(width, 50f, height) // x, y (height scale), z

    fun getHeightAtWorld(x: Float, z: Float): Float {
        val u = (x / size[0] + 0.5f).coerceIn(0f, 1f)
        val v = (z / size[2] + 0.5f).coerceIn(0f, 1f)
        return heightmap.getBilinear(u, v) * size[1]
    }

    fun getInterpolatedNormal(x: Float, z: Float): FloatArray {
        val eps = 0.1f
        val hL = getHeightAtWorld(x - eps, z)
        val hR = getHeightAtWorld(x + eps, z)
        val hD = getHeightAtWorld(x, z - eps)
        val hU = getHeightAtWorld(x, z + eps)
        val nx = hL - hR
        val nz = hD - hU
        val ny = 2f * eps
        val len = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat().coerceAtLeast(0.001f)
        return floatArrayOf(nx / len, ny / len, nz / len)
    }
}

class Terrain(var data: TerrainData = TerrainData()) {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var drawInstanced: Boolean = true
    var pixelError: Float = 5f
    var basemapDistance: Float = 1000f
    var shadowCastingMode: Int = 1
    var reflectionProbeUsage: Int = 1
    var materialTemplate: String = ""
    var allowAutoConnect: Boolean = true
    var groupingID: Int = 0
    var detailObjectDensity: Float = 1f
    var detailObjectDistance: Float = 80f
    var treeDistance: Float = 5000f
    var treeBillboardDistance: Float = 400f
    var treeCrossFadeLength: Float = 20f
    var treeMaximumFullLODCount: Int = 50

    val detailPrototypes = mutableListOf<DetailPrototype>()
    val treePrototypes = mutableListOf<TreePrototype>()
    val treeInstances = mutableListOf<TreeInstance>()

    data class DetailPrototype(var texture: String = "", var minWidth: Float = 1f, var maxWidth: Float = 2f, var minHeight: Float = 1f, var maxHeight: Float = 2f, var noiseSpread: Float = 0.1f, var healthyColor: Int = 0xFF7AB04B.toInt(), var dryColor: Int = 0xFFB0B04B.toInt())
    data class TreePrototype(var prefab: String = "", var bendFactor: Float = 0f)
    data class TreeInstance(var x: Float, var z: Float, var widthScale: Float = 1f, var heightScale: Float = 1f, var rotation: Float = 0f, var prototypeIndex: Int = 0, var color: Int = 0xFFFFFFFF.toInt(), var lightmapColor: Int = 0xFFFFFFFF.toInt())

    fun sampleHeight(worldX: Float, worldZ: Float): Float {
        return data.getHeightAtWorld(worldX - x, worldZ - z) + y
    }
}

class TerrainSystem {
    val terrains = mutableListOf<Terrain>()

    fun addTerrain(terrain: Terrain) { terrains.add(terrain) }

    fun getHeightAt(x: Float, z: Float): Float {
        for (t in terrains) {
            val localX = x - t.x
            val localZ = z - t.z
            if (localX >= -t.data.size[0] / 2 && localX <= t.data.size[0] / 2 &&
                localZ >= -t.data.size[2] / 2 && localZ <= t.data.size[2] / 2) {
                return t.sampleHeight(x, z)
            }
        }
        return 0f
    }

    fun raycast(origin: FloatArray, direction: FloatArray, maxDistance: Float): FloatArray? {
        // Simple heightfield raycast
        var t = 0f
        val step = 0.5f
        while (t < maxDistance) {
            val px = origin[0] + direction[0] * t
            val py = origin[1] + direction[1] * t
            val pz = origin[2] + direction[2] * t
            val ground = getHeightAt(px, pz)
            if (py <= ground) return floatArrayOf(px, ground, pz)
            t += step
        }
        return null
    }
}
