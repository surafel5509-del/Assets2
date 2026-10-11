package com.sengine.engine.profiling

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Most Powerful Profiler V2 & Full Control Systems
 * Advanced Profiler, Memory Control, Graphics Control, Full Engine Control
 */
enum class ProfilerCategory { CPU, GPU, Rendering, Physics, Audio, Memory, Networking, Scripts, Animation, VFX, AI, Loading }
enum class ProfilerView { Timeline, Hierarchy, Raw, Chart }

data class ProfilerSampleV2(
    var name: String = "Sample",
    var category: ProfilerCategory = ProfilerCategory.CPU,
    var startTime: Long = 0L,
    var endTime: Long = 0L,
    var durationNs: Long = 0L,
    var depth: Int = 0,
    var threadId: Long = 0L,
    var frame: Long = 0L,
    var gcAllocBytes: Long = 0L
) {
    fun durationMs(): Float = durationNs/1_000_000f
    fun durationUs(): Float = durationNs/1_000f
}

data class ProfilerFrameV2(
    var frameIndex: Long = 0L,
    var startTime: Long = 0L,
    var endTime: Long = 0L,
    var samples: MutableList<ProfilerSampleV2> = mutableListOf(),
    var totalTimeMs: Float = 0f,
    var fps: Float = 0f,
    var memoryMB: Float = 0f,
    var drawCalls: Int = 0,
    var triangles: Int = 0,
    var batches: Int = 0
) {
    fun getSamplesByCategory(cat: ProfilerCategory): List<ProfilerSampleV2> = samples.filter { it.category == cat }
    fun getTopSamples(count: Int = 10): List<ProfilerSampleV2> = samples.sortedByDescending { it.durationNs }.take(count)
}

class ProfilerV2System {
    var frames = ArrayDeque<ProfilerFrameV2>()
    var currentFrame = ProfilerFrameV2()
    var sampleStack = ArrayDeque<Pair<String, Long>>()
    var maxFrames: Int = 300
    var enabled: Boolean = true
    var recordGPU: Boolean = true
    var recordMemory: Boolean = true
    var frameCount: Long = 0L
    var fps: Float = 0f
    var averageFrameTimeMs: Float = 0f
    var minFrameTimeMs: Float = Float.MAX_VALUE
    var maxFrameTimeMs: Float = 0f
    var totalTimeMs: Float = 0f

    fun beginSample(name: String, category: ProfilerCategory = ProfilerCategory.CPU) {
        if (!enabled) return
        sampleStack.addLast(Pair(name, System.nanoTime()))
    }

    fun endSample(name: String) {
        if (!enabled || sampleStack.isEmpty()) return
        // Find matching begin
        var foundIdx = -1
        for (i in sampleStack.size-1 downTo 0) if (sampleStack[i].first == name) { foundIdx=i; break }
        if (foundIdx == -1) return
        val startTime = sampleStack[foundIdx].second
        val endTime = System.nanoTime()
        val duration = endTime-startTime
        // Remove from stack
        val toRemove = sampleStack.size-foundIdx
        repeat(toRemove) { sampleStack.removeLast() }

        val sample = ProfilerSampleV2(name=name, startTime=startTime, endTime=endTime, durationNs=duration, depth=foundIdx, threadId=Thread.currentThread().id, frame=frameCount)
        currentFrame.samples.add(sample)
    }

    fun endFrame(dt: Float) {
        if (!enabled) return
        currentFrame.frameIndex=frameCount++
        currentFrame.totalTimeMs=dt*1000f
        currentFrame.fps=if (dt > 0f) 1f/dt else 0f
        fps=currentFrame.fps
        totalTimeMs+=currentFrame.totalTimeMs
        averageFrameTimeMs = if (frames.isEmpty()) currentFrame.totalTimeMs else (averageFrameTimeMs*frames.size+currentFrame.totalTimeMs)/(frames.size+1)
        minFrameTimeMs=min(minFrameTimeMs, currentFrame.totalTimeMs)
        maxFrameTimeMs=max(maxFrameTimeMs, currentFrame.totalTimeMs)

        frames.addLast(currentFrame)
        if (frames.size > maxFrames) frames.removeFirst()
        currentFrame=ProfilerFrameV2()
    }

    fun getAverageByCategory(category: ProfilerCategory): Float {
        if (frames.isEmpty()) return 0f
        var total=0f; var count=0
        for (frame in frames) for (sample in frame.samples.filter { it.category == category }) { total+=sample.durationMs(); count++ }
        return if (count > 0) total/count else 0f
    }

    fun getTopCategories(): List<Pair<ProfilerCategory, Float>> {
        return ProfilerCategory.values().map { cat -> Pair(cat, getAverageByCategory(cat)) }.sortedByDescending { it.second }
    }

    fun dump(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Profiler V2 ===")
        sb.appendLine("Frames:${frames.size} Avg:${"%.2f".format(averageFrameTimeMs)}ms Min:${"%.2f".format(minFrameTimeMs)}ms Max:${"%.2f".format(maxFrameTimeMs)}ms FPS:${"%.1f".format(fps)}")
        sb.appendLine("Top Categories:")
        for ((cat, time) in getTopCategories().take(5)) sb.appendLine("  $cat: ${"%.2f".format(time)}ms")
        if (frames.isNotEmpty()) {
            sb.appendLine("Last Frame Top Samples:")
            for (sample in frames.last().getTopSamples(10)) sb.appendLine("  ${sample.name}: ${"%.2f".format(sample.durationMs())}ms [${sample.category}]")
        }
        return sb.toString()
    }

    fun getStats(): String = "ProfilerV2 Frames:${frames.size} Avg:${"%.1f".format(averageFrameTimeMs)}ms FPS:${"%.0f".format(fps)} Min:${"%.1f".format(minFrameTimeMs)} Max:${"%.1f".format(maxFrameTimeMs)}"
}

class MemoryControlSystem {
    var totalAllocatedMB: Float = 0f
    var totalReservedMB: Float = 0f
    var gcAllocatedMB: Float = 0f
    var textureMemoryMB: Float = 0f
    var meshMemoryMB: Float = 0f
    var audioMemoryMB: Float = 0f
    var maxMemoryMB: Float = 512f
    var gcEnabled: Boolean = true
    var gcInterval: Float = 5f
    var gcTimer: Float = 0f
    var pools = mutableMapOf<String, Int>()

    fun allocate(category: String, bytes: Long) {
        val mb = bytes/1024f/1024f
        totalAllocatedMB+=mb
        when (category) {
            "Texture" -> textureMemoryMB+=mb
            "Mesh" -> meshMemoryMB+=mb
            "Audio" -> audioMemoryMB+=mb
        }
        if (totalAllocatedMB > maxMemoryMB) {
            // Trigger GC
            collectGarbage()
        }
    }

    fun deallocate(category: String, bytes: Long) {
        val mb = bytes/1024f/1024f
        totalAllocatedMB=(totalAllocatedMB-mb).coerceAtLeast(0f)
        when (category) {
            "Texture" -> textureMemoryMB=(textureMemoryMB-mb).coerceAtLeast(0f)
            "Mesh" -> meshMemoryMB=(meshMemoryMB-mb).coerceAtLeast(0f)
            "Audio" -> audioMemoryMB=(audioMemoryMB-mb).coerceAtLeast(0f)
        }
    }

    fun collectGarbage() {
        System.gc()
        gcAllocatedMB=0f
    }

    fun createPool(name: String, size: Int) { pools[name]=size }

    fun update(dt: Float) {
        if (!gcEnabled) return
        gcTimer+=dt
        if (gcTimer >= gcInterval) { gcTimer=0f; collectGarbage() }
        // Update from runtime
        val runtime = Runtime.getRuntime()
        totalAllocatedMB = (runtime.totalMemory()-runtime.freeMemory())/1024f/1024f
        totalReservedMB = runtime.totalMemory()/1024f/1024f
    }

    fun getStats(): String = "Memory Alloc:${"%.1f".format(totalAllocatedMB)}/${maxMemoryMB}MB Tex:${"%.1f".format(textureMemoryMB)} Mesh:${"%.1f".format(meshMemoryMB)} Audio:${"%.1f".format(audioMemoryMB)} Pools:${pools.size}"
}

class GraphicsControlSystem {
    var renderScale: Float = 1f
    var resolutionWidth: Int = 1920
    var resolutionHeight: Int = 1080
    var fullscreen: Boolean = true
    var vSync: Int = 1
    var targetFrameRate: Int = 60
    var hdr: Boolean = true
    var msaa: Int = 4
    var anisotropicFiltering: Int = 2
    var shadowQuality: Int = 3
    var shadowDistance: Float = 100f
    var shadowCascades: Int = 4
    var lodBias: Float = 1.5f
    var maxLOD: Int = 0
    var particleBudget: Int = 10000
    var textureQuality: Int = 0
    var enablePostProcessing: Boolean = true
    var enableShadows: Boolean = true
    var enableFog: Boolean = true
    var enableSSR: Boolean = true
    var enableSSAO: Boolean = true
    var enableBloom: Boolean = true
    var enableMotionBlur: Boolean = true
    var enableRayTracing: Boolean = true
    var enableNanite: Boolean = true
    var enableLumen: Boolean = true
    var enableVirtualTextures: Boolean = true
    var graphicsJobs: Boolean = true
    var computeShaders: Boolean = true
    var gpuInstancing: Boolean = true
    var srpBatcher: Boolean = true
    var occlusionCulling: Boolean = true
    var allowHDRDisplay: Boolean = true

    fun setQualityLevel(level: Int) {
        when (level) {
            0 -> { // Very Low
                renderScale=0.5f; msaa=0; shadowQuality=0; shadowDistance=20f; lodBias=0.7f; particleBudget=2000; textureQuality=2
                enablePostProcessing=false; enableShadows=false; enableSSR=false; enableSSAO=false; enableBloom=false; enableRayTracing=false; enableNanite=false
            }
            1 -> { // Low
                renderScale=0.7f; msaa=0; shadowQuality=1; shadowDistance=40f; lodBias=1f; particleBudget=4000; textureQuality=1
                enablePostProcessing=true; enableShadows=true; enableSSR=false; enableSSAO=false; enableRayTracing=false
            }
            2 -> { // Medium
                renderScale=0.85f; msaa=2; shadowQuality=2; shadowDistance=70f; lodBias=1.2f; particleBudget=6000; textureQuality=0
            }
            3 -> { // High
                renderScale=1f; msaa=4; shadowQuality=3; shadowDistance=100f; lodBias=1.5f; particleBudget=10000; textureQuality=0
                enablePostProcessing=true; enableShadows=true; enableSSR=true; enableSSAO=true; enableBloom=true; enableRayTracing=true; enableNanite=true
            }
            4 -> { // Ultra
                renderScale=1.25f; msaa=8; shadowQuality=4; shadowDistance=250f; lodBias=2.5f; particleBudget=50000; textureQuality=0
                enablePostProcessing=true; enableShadows=true; enableSSR=true; enableSSAO=true; enableBloom=true; enableRayTracing=true; enableNanite=true; enableLumen=true; enableVirtualTextures=true
            }
        }
    }

    fun apply() {
        // Apply graphics settings to engine
    }

    fun getStats(): String = "Graphics Scale:$renderScale Res:${resolutionWidth}x$resolutionHeight MSAA:$msaa Shadows:$shadowQuality PostFX:$enablePostProcessing RT:$enableRayTracing Nanite:$enableNanite Lumen:$enableLumen"
}
