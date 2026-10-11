package com.sengine.engine.profiling

/**
 * Ultimate Edition Profiler - Unity Profiler + Unreal Insights equivalent
 */

data class ProfilerMarker(val name: String) {
    var totalTime: Long = 0
    var count: Long = 0
    var minTime: Long = Long.MAX_VALUE
    var maxTime: Long = 0
    var lastTime: Long = 0

    fun begin(): Long = System.nanoTime()

    fun end(start: Long) {
        val elapsed = System.nanoTime() - start
        lastTime = elapsed
        totalTime += elapsed
        count++
        if (elapsed < minTime) minTime = elapsed
        if (elapsed > maxTime) maxTime = elapsed
    }

    fun averageMs(): Float = if (count > 0) totalTime / 1e6f / count else 0f
    fun lastMs(): Float = lastTime / 1e6f
}

class ProfilerFrame {
    val markers = mutableMapOf<String, ProfilerMarker>()
    var frameTime: Long = 0
    var frameIndex: Long = 0

    fun getOrCreateMarker(name: String): ProfilerMarker = markers.getOrPut(name) { ProfilerMarker(name) }
}

class ProfilerSystem {
    private val frames = ArrayDeque<ProfilerFrame>()
    private var currentFrame = ProfilerFrame()
    private val markers = mutableMapOf<String, ProfilerMarker>()
    private val stack = ArrayDeque<Pair<String, Long>>()
    var enabled: Boolean = true
    var maxFrames: Int = 300

    var frameCount: Long = 0
    var fps: Float = 0f
    private var fpsTime = 0.0
    private var fpsFrames = 0

    fun beginSample(name: String) {
        if (!enabled) return
        stack.addLast(name to System.nanoTime())
    }

    fun endSample(name: String) {
        if (!enabled) return
        val entry = stack.removeLastOrNull() ?: return
        if (entry.first != name) {
            // mismatched, push back and try to find
            stack.addLast(entry)
            val idx = stack.indexOfLast { it.first == name }
            if (idx >= 0) {
                val found = stack.removeAt(idx)
                record(found.first, System.nanoTime() - found.second)
            }
            return
        }
        record(name, System.nanoTime() - entry.second)
    }

    private fun record(name: String, elapsed: Long) {
        val marker = markers.getOrPut(name) { ProfilerMarker(name) }
        marker.lastTime = elapsed
        marker.totalTime += elapsed
        marker.count++
        if (elapsed < marker.minTime) marker.minTime = elapsed
        if (elapsed > marker.maxTime) marker.maxTime = elapsed

        currentFrame.getOrCreateMarker(name).apply {
            lastTime = elapsed
            totalTime += elapsed
            count++
            if (elapsed < minTime) minTime = elapsed
            if (elapsed > maxTime) maxTime = elapsed
        }
    }

    fun endFrame(dt: Float) {
        if (!enabled) return
        currentFrame.frameTime = (dt * 1e9).toLong()
        currentFrame.frameIndex = frameCount++
        frames.addLast(currentFrame)
        if (frames.size > maxFrames) frames.removeFirst()
        currentFrame = ProfilerFrame()

        fpsTime += dt
        fpsFrames++
        if (fpsTime >= 0.5) {
            fps = fpsFrames / fpsTime.toFloat()
            fpsTime = 0.0
            fpsFrames = 0
        }
    }

    fun getMarkers(): Map<String, ProfilerMarker> = markers
    fun getFrames(): List<ProfilerFrame> = frames.toList()
    fun getAverageFrameTimeMs(): Float = frames.map { it.frameTime / 1e6f }.average().toFloat()
    fun getMarkerAverageMs(name: String): Float = markers[name]?.averageMs() ?: 0f

    fun reset() {
        markers.clear()
        frames.clear()
        stack.clear()
        frameCount = 0
        fps = 0f
    }

    fun dump(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Profiler Dump ===")
        sb.appendLine("Frames: ${frames.size}, FPS: $fps")
        sb.appendLine("Average Frame: ${getAverageFrameTimeMs()} ms")
        for ((name, marker) in markers.entries.sortedByDescending { it.value.averageMs() }) {
            sb.appendLine("$name: avg=${marker.averageMs()}ms last=${marker.lastMs()}ms count=${marker.count} min=${marker.minTime / 1e6f}ms max=${marker.maxTime / 1e6f}ms")
        }
        return sb.toString()
    }
}

class MemoryProfiler {
    data class MemorySnapshot(
        var totalAllocated: Long = 0,
        var totalReserved: Long = 0,
        var gcAllocated: Long = 0,
        var textureMemory: Long = 0,
        var meshMemory: Long = 0,
        var audioMemory: Long = 0
    )

    fun capture(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            totalAllocated = runtime.totalMemory() - runtime.freeMemory(),
            totalReserved = runtime.totalMemory(),
            gcAllocated = runtime.totalMemory(),
            textureMemory = 0,
            meshMemory = 0,
            audioMemory = 0
        )
    }
}

class FrameDebugger {
    data class DrawCall(
        var meshName: String = "",
        var materialName: String = "",
        var shaderName: String = "",
        var triangles: Int = 0,
        var instances: Int = 1,
        var batchType: String = "Draw" // Draw, Batched, Instanced, SRP Batch
    )

    private val drawCalls = mutableListOf<DrawCall>()
    var enabled: Boolean = false

    fun beginFrame() { if (enabled) drawCalls.clear() }
    fun addDrawCall(call: DrawCall) { if (enabled) drawCalls.add(call) }
    fun getDrawCalls(): List<DrawCall> = drawCalls.toList()
    fun totalTriangles(): Int = drawCalls.sumOf { it.triangles * it.instances }
    fun totalDrawCalls(): Int = drawCalls.size
    fun totalBatches(): Int = drawCalls.count { it.batchType == "Batched" }
    fun totalInstanced(): Int = drawCalls.count { it.batchType == "Instanced" }
}
