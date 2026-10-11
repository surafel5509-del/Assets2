package com.sengine.engine.render

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Global Illumination System
 * Lumen-like real-time GI, Enlighten-like lightmapping, SSGI, Radiance Probes
 */
enum class GIMode { Off, Baked, Realtime, Mixed, Lumen }
enum class LightmapResolution { Low32, Medium64, High128, VeryHigh256, Ultra512 }
enum class ProbeType { LightProbe, ReflectionProbe, RadianceProbe, IrradianceVolume }

data class LightmapData(
    var width: Int = 512,
    var height: Int = 512,
    var data: FloatArray = FloatArray(width*height*3),
    var directionality: FloatArray = FloatArray(width*height*3),
    var shadowMask: ByteArray = ByteArray(width*height),
    var baked: Boolean = false,
    var bakeTimeMs: Float = 0f
) {
    fun getPixel(x: Int, y: Int): FloatArray {
        val idx = (y*width+x)*3
        return if (idx+2 < data.size) floatArrayOf(data[idx], data[idx+1], data[idx+2]) else floatArrayOf(0f,0f,0f)
    }
    fun setPixel(x: Int, y: Int, r: Float, g: Float, b: Float) {
        val idx = (y*width+x)*3
        if (idx+2 < data.size) { data[idx]=r; data[idx+1]=g; data[idx+2]=b }
    }
    fun clear() { data.fill(0f); directionality.fill(0f); shadowMask.fill(0); baked=false }
}

data class RadianceProbe(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var intensity: Float = 1f,
    var radius: Float = 10f,
    var sh: FloatArray = FloatArray(27), // 9 coeffs * 3 channels
    var irradiance: FloatArray = FloatArray(6*3), // 6 faces cubemap low-res
    var visibility: Float = 1f,
    var dirty: Boolean = true
) {
    fun sample(dirX: Float, dirY: Float, dirZ: Float): FloatArray {
        // SH evaluation simplified - use first 3 coeffs as ambient
        return floatArrayOf(sh[0]*intensity, sh[1]*intensity, sh[2]*intensity)
    }
    fun updateSH(color: FloatArray) {
        sh[0]=color[0]; sh[1]=color[1]; sh[2]=color[2]
        sh[3]=color[0]*0.5f; sh[4]=color[1]*0.5f; sh[5]=color[2]*0.5f
        dirty=false
    }
}

data class IrradianceVolume(
    var minX: Float = -50f, var minY: Float = 0f, var minZ: Float = -50f,
    var maxX: Float = 50f, var maxY: Float = 20f, var maxZ: Float = 50f,
    var resX: Int = 10, var resY: Int = 5, var resZ: Int = 10,
    var probes: Array<RadianceProbe> = Array(resX*resY*resZ) { RadianceProbe() }
) {
    fun getProbeIndex(x: Int, y: Int, z: Int): Int = x + y*resX + z*resX*resY
    fun getProbeAtWorld(wx: Float, wy: Float, wz: Float): RadianceProbe? {
        val fx = (wx-minX)/(maxX-minX); val fy = (wy-minY)/(maxY-minY); val fz = (wz-minZ)/(maxZ-minZ)
        if (fx < 0f || fx > 1f || fy < 0f || fy > 1f || fz < 0f || fz > 1f) return null
        val ix = (fx*(resX-1)).toInt().coerceIn(0, resX-1)
        val iy = (fy*(resY-1)).toInt().coerceIn(0, resY-1)
        val iz = (fz*(resZ-1)).toInt().coerceIn(0, resZ-1)
        return probes[getProbeIndex(ix, iy, iz)]
    }
    fun trilinearSample(wx: Float, wy: Float, wz: Float): FloatArray {
        val fx = (wx-minX)/(maxX-minX)*(resX-1); val fy = (wy-minY)/(maxY-minY)*(resY-1); val fz = (wz-minZ)/(maxZ-minZ)*(resZ-1)
        val x0 = floor(fx).toInt().coerceIn(0, resX-1); val x1 = (x0+1).coerceIn(0, resX-1)
        val y0 = floor(fy).toInt().coerceIn(0, resY-1); val y1 = (y0+1).coerceIn(0, resY-1)
        val z0 = floor(fz).toInt().coerceIn(0, resZ-1); val z1 = (z0+1).coerceIn(0, resZ-1)
        val tx = fx-x0; val ty = fy-y0; val tz = fz-z0
        // 8 corners
        val c000 = probes[getProbeIndex(x0,y0,z0)].sh; val c100 = probes[getProbeIndex(x1,y0,z0)].sh
        val c010 = probes[getProbeIndex(x0,y1,z0)].sh; val c110 = probes[getProbeIndex(x1,y1,z0)].sh
        val c001 = probes[getProbeIndex(x0,y0,z1)].sh; val c101 = probes[getProbeIndex(x1,y0,z1)].sh
        val c011 = probes[getProbeIndex(x0,y1,z1)].sh; val c111 = probes[getProbeIndex(x1,y1,z1)].sh
        // Trilinear
        val result = FloatArray(3)
        for (ch in 0..2) {
            val c00 = c000[ch]*(1-tx)+c100[ch]*tx; val c10 = c010[ch]*(1-tx)+c110[ch]*tx
            val c01 = c001[ch]*(1-tx)+c101[ch]*tx; val c11 = c011[ch]*(1-tx)+c111[ch]*tx
            val c0 = c00*(1-ty)+c10*ty; val c1 = c01*(1-ty)+c11*ty
            result[ch] = c0*(1-tz)+c1*tz
        }
        return result
    }
}

class GlobalIlluminationSystem {
    var mode: GIMode = GIMode.Mixed
    var lightmaps = mutableMapOf<String, LightmapData>()
    var radianceProbes = mutableListOf<RadianceProbe>()
    var irradianceVolume = IrradianceVolume()
    var ssgiEnabled: Boolean = true
    var ssgiIntensity: Float = 1f
    var ssgiRadius: Float = 1f
    var ssgiThickness: Float = 0.5f
    var rtaoEnabled: Boolean = true
    var bounceCount: Int = 2
    var indirectIntensity: Float = 1f
    var emissiveGI: Boolean = true
    var bakeProgress: Float = 0f
    var isBaking: Boolean = false

    fun addLightmap(name: String, width: Int = 512, height: Int = 512) {
        lightmaps[name] = LightmapData(width, height, FloatArray(width*height*3), FloatArray(width*height*3), ByteArray(width*height))
    }

    fun bakeLightmap(name: String, lightPositions: List<FloatArray>, lightColors: List<FloatArray>) {
        val lm = lightmaps[name] ?: return
        isBaking = true
        val t0 = System.nanoTime()
        // Simple CPU lightmap bake - for each pixel, accumulate light
        for (y in 0 until lm.height) for (x in 0 until lm.width) {
            var r=0f; var g=0f; var b=0f
            val px = x.toFloat()/lm.width*100f - 50f
            val pz = y.toFloat()/lm.height*100f - 50f
            for (i in lightPositions.indices) {
                val lx = lightPositions[i][0]; val ly = lightPositions[i][1]; val lz = lightPositions[i][2]
                val dx = lx-px; val dy = ly-5f; val dz = lz-pz
                val dist2 = dx*dx+dy*dy+dz*dz; val dist = sqrt(dist2)
                if (dist < 50f) {
                    val atten = 1f/(1f+dist*0.1f+dist2*0.01f)
                    r+=lightColors[i][0]*atten; g+=lightColors[i][1]*atten; b+=lightColors[i][2]*atten
                }
            }
            // Bounce
            if (bounceCount > 0) { r*=1f+indirectIntensity*0.3f; g*=1f+indirectIntensity*0.3f; b*=1f+indirectIntensity*0.3f }
            lm.setPixel(x,y,r,g,b)
            bakeProgress = (y*lm.width+x).toFloat()/(lm.width*lm.height)
        }
        lm.bakeTimeMs = (System.nanoTime()-t0)/1_000_000f
        lm.baked = true
        isBaking = false
        bakeProgress = 1f
    }

    fun addRadianceProbe(x: Float, y: Float, z: Float, radius: Float = 10f): RadianceProbe {
        val p = RadianceProbe(x,y,z,1f,radius); radianceProbes.add(p); return p
    }

    fun updateProbes(ambientColor: FloatArray) {
        for (probe in radianceProbes) if (probe.dirty) probe.updateSH(ambientColor)
        for (probe in irradianceVolume.probes) if (probe.dirty) probe.updateSH(ambientColor)
    }

    fun sampleGI(worldX: Float, worldY: Float, worldZ: Float, normalX: Float, normalY: Float, normalZ: Float): FloatArray {
        return when (mode) {
            GIMode.Off -> floatArrayOf(0f,0f,0f)
            GIMode.Baked -> {
                // Sample lightmap - use world x/z as UV
                val lm = lightmaps.values.firstOrNull() ?: return floatArrayOf(0.1f,0.1f,0.1f)
                val u = ((worldX+50f)/100f*lm.width).toInt().coerceIn(0, lm.width-1)
                val v = ((worldZ+50f)/100f*lm.height).toInt().coerceIn(0, lm.height-1)
                lm.getPixel(u,v)
            }
            GIMode.Realtime, GIMode.Lumen, GIMode.Mixed -> {
                irradianceVolume.trilinearSample(worldX, worldY, worldZ)
            }
        }
    }

    fun calculateSSGI(depthBuffer: FloatArray, normalBuffer: FloatArray, width: Int, height: Int): FloatArray {
        if (!ssgiEnabled) return FloatArray(width*height*3)
        val result = FloatArray(width*height*3)
        // Simplified SSGI - blur depth differences
        for (y in 1 until height-1) for (x in 1 until width-1) {
            val idx = (y*width+x)
            val depth = depthBuffer.getOrElse(idx){1f}
            var gi=0f
            for (dy in -1..1) for (dx in -1..1) {
                val nIdx = (y+dy)*width+(x+dx)
                val nDepth = depthBuffer.getOrElse(nIdx){1f}
                val diff = abs(depth-nDepth)
                if (diff < ssgiThickness) gi+=1f
            }
            gi/=9f
            val outIdx = idx*3
            result[outIdx]=gi*ssgiIntensity*0.3f
            result[outIdx+1]=gi*ssgiIntensity*0.3f
            result[outIdx+2]=gi*ssgiIntensity*0.35f
        }
        return result
    }

    fun getStats(): String = "GI Mode:$mode Probes:${radianceProbes.size} Volume:${irradianceVolume.resX}x${irradianceVolume.resY}x${irradianceVolume.resZ} Lightmaps:${lightmaps.size} Baking:$isBaking ${(bakeProgress*100).toInt()}%"
}
