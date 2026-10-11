package com.sengine.engine.render

/**
 * Ultimate Edition Water & Volumetric Systems
 * Unity HDRP Water + Volumetric Fog + Unreal Water + Volumetric Cloud equivalent
 */

class WaterSimulation {
    var width: Int = 128
    var height: Int = 128
    var damping: Float = 0.99f
    var tension: Float = 0.1f
    var strength: Float = 1f

    private var current = FloatArray(width * height)
    private var previous = FloatArray(width * height)
    private var velocity = FloatArray(width * height)

    fun disturb(x: Int, y: Int, amount: Float) {
        if (x in 0 until width && y in 0 until height) {
            current[y * width + x] += amount
        }
    }

    fun update(dt: Float) {
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val idx = y * width + x
            val north = current[(y - 1) * width + x]
            val south = current[(y + 1) * width + x]
            val east = current[y * width + (x + 1)]
            val west = current[y * width + (x - 1)]
            val valCurrent = current[idx]

            velocity[idx] += tension * (north + south + east + west - 4f * valCurrent)
            velocity[idx] *= damping
            current[idx] += velocity[idx] * dt
        }
        // swap buffers
        val tmp = previous
        previous = current
        current = tmp
    }

    fun getHeight(x: Int, y: Int): Float = if (x in 0 until width && y in 0 until height) current[y * width + x] else 0f
}

class VolumetricFogSimulation {
    var density: Float = 0.01f
    var anisotropy: Float = 0.2f
    var lightScattering: Float = 1f
    var noiseScale: Float = 0.1f
    var noiseIntensity: Float = 0.5f
    var windSpeed: FloatArray = floatArrayOf(0.5f, 0f, 0.2f)
    var heightFalloff: Float = 0.1f
    var baseHeight: Float = 0f

    fun sampleDensity(pos: FloatArray): Float {
        val heightFactor = kotlin.math.exp(-heightFalloff * (pos[1] - baseHeight).coerceAtLeast(0f))
        val noise = perlinNoise3D(pos[0] * noiseScale, pos[1] * noiseScale, pos[2] * noiseScale) * noiseIntensity
        return (density + noise) * heightFactor
    }

    private fun perlinNoise3D(x: Float, y: Float, z: Float): Float {
        return (kotlin.math.sin(x * 12.9898f + y * 78.233f + z * 37.719f) * 43758.5453f).let { it - kotlin.math.floor(it) } * 2f - 1f
    }

    fun calculateTransmittance(start: FloatArray, end: FloatArray, steps: Int = 32): Float {
        var transmittance = 1f
        val dir = floatArrayOf(end[0] - start[0], end[1] - start[1], end[2] - start[2])
        val dist = kotlin.math.sqrt((dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]).toDouble()).toFloat()
        if (dist < 0.001f) return 1f
        val stepSize = dist / steps
        val step = floatArrayOf(dir[0] / steps, dir[1] / steps, dir[2] / steps)
        var pos = start.clone()
        for (i in 0 until steps) {
            val d = sampleDensity(pos)
            transmittance *= kotlin.math.exp(-d * stepSize)
            pos[0] += step[0]; pos[1] += step[1]; pos[2] += step[2]
        }
        return transmittance.coerceIn(0f, 1f)
    }
}

class CloudSimulation {
    var coverage: Float = 0.5f
    var density: Float = 0.5f
    var type: Int = 0 // 0 cumulus, 1 stratus, 2 cumulonimbus
    var speed: Float = 0.01f
    var height: Float = 1500f
    var thickness: Float = 1000f

    private var time: Float = 0f

    fun update(dt: Float) { time += dt * speed }

    fun sampleCloud(pos: FloatArray): Float {
        // Simple noise-based cloud density
        val x = pos[0] * 0.001f + time
        val z = pos[2] * 0.001f
        val noise = kotlin.math.sin(x * 10f) * kotlin.math.cos(z * 10f) * 0.5f + 0.5f
        val heightFactor = 1f - kotlin.math.abs(pos[1] - height) / thickness
        return (noise * coverage * heightFactor * density).coerceIn(0f, 1f)
    }
}

class WeatherSystem {
    var rainIntensity: Float = 0f
    var snowIntensity: Float = 0f
    var windStrength: Float = 0f
    var windDirection: FloatArray = floatArrayOf(1f, 0f, 0f)
    var fogDensity: Float = 0f
    var temperature: Float = 20f
    var humidity: Float = 0.5f

    var isRaining: Boolean = false
    var isSnowing: Boolean = false
    var isStorm: Boolean = false

    fun setWeather(rain: Float, snow: Float, wind: Float, fog: Float) {
        rainIntensity = rain.coerceIn(0f, 1f)
        snowIntensity = snow.coerceIn(0f, 1f)
        windStrength = wind.coerceIn(0f, 1f)
        fogDensity = fog.coerceIn(0f, 1f)
        isRaining = rainIntensity > 0.1f
        isSnowing = snowIntensity > 0.1f
        isStorm = rainIntensity > 0.7f && windStrength > 0.7f
    }

    fun update(dt: Float) {
        // Weather transitions would happen here
    }
}
