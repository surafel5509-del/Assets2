package com.sengine.engine.camera

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Position, zoom and orientation of a camera at a point in time. */
data class CameraPose(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var zoom: Float = 1f, var yaw: Float = 0f, var pitch: Float = 0f
) {
    fun copy(): CameraPose = CameraPose(x, y, z, zoom, yaw, pitch)
}

data class CameraBounds(
    var enabled: Boolean = false,
    var minX: Float = -100f, var maxX: Float = 100f,
    var minY: Float = -100f, var maxY: Float = 100f,
    var minZ: Float = -100f, var maxZ: Float = 100f,
    var viewWidth: Float = 0f, var viewHeight: Float = 0f,
    var viewDepth: Float = 0f
) {
    fun clampX(v: Float): Float {
        if (!enabled) return v
        val half = viewWidth * 0.5f
        val lo = minX + half; val hi = maxX - half
        return if (lo > hi) (minX + maxX) * 0.5f else v.coerceIn(lo, hi)
    }
    fun clampY(v: Float): Float {
        if (!enabled) return v
        val half = viewHeight * 0.5f
        val lo = minY + half; val hi = maxY - half
        return if (lo > hi) (minY + maxY) * 0.5f else v.coerceIn(lo, hi)
    }
    fun clampZ(v: Float): Float {
        if (!enabled) return v
        val half = viewDepth * 0.5f
        val lo = minZ + half; val hi = maxZ - half
        return if (lo > hi) (minZ + maxZ) * 0.5f else v.coerceIn(lo, hi)
    }
}

/**
 * Professional 2D/3D camera controller: priority, dead zone, velocity
 * look-ahead, world bounds, exponential damping, trauma-based screen shake,
 * zoom smoothing, blends and cinematic spline paths. Pure Kotlin — the engine
 * feeds it targets, the editor and tests can drive it headlessly.
 */
class CameraRig(
    var priority: Int = 0,
    var smoothing: Float = 5f,
    var seed: Int = 1
) {
    val pose = CameraPose()
    val bounds = CameraBounds()

    /** Dead-zone rectangle centred on the camera (world units). 0 disables. */
    var deadZoneW = 0f
    var deadZoneH = 0f
    /** How far ahead of the target velocity the camera looks, in seconds. */
    var lookAhead = 0f
    var lookAheadSmooth = 5f
    var maxLookAhead = 8f
    var minZoom = 0.2f
    var maxZoom = 8f
    var zoomSmoothing = 4f
    var trauma = 0f
    var traumaDecay = 1.6f
    var shakeScale = 1f
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set
    var shakeRot = 0f
        private set

    private var aheadX = 0f
    private var aheadY = 0f
    private var aheadZ = 0f
    private var zoomTarget = 1f
    private var clock = 0f

    fun snap(x: Float, y: Float, z: Float = 0f, zoom: Float = pose.zoom) {
        pose.x = x; pose.y = y; pose.z = z
        pose.zoom = zoom.coerceIn(minZoom, maxZoom)
        zoomTarget = pose.zoom
        aheadX = 0f; aheadY = 0f; aheadZ = 0f
        trauma = 0f
        shakeX = 0f; shakeY = 0f; shakeRot = 0f
    }

    fun addTrauma(amount: Float) { trauma = (trauma + amount.coerceAtLeast(0f)).coerceAtMost(1f) }

    fun zoomTo(z: Float) { zoomTarget = z.coerceIn(minZoom, maxZoom) }

    /** Frame-rate independent exponential damping. */
    fun damp(current: Float, target: Float, lambda: Float, dt: Float): Float =
        if (lambda <= 0f) target else target + (current - target) * exp(-lambda * dt)

    /**
     * Tracks a target. `vx/vy` are world units per second; `dt` is seconds.
     * Returns the (already mutated) [pose].
     */
    fun follow(tx: Float, ty: Float, tz: Float, vx: Float, vy: Float, dt: Float, vz: Float = 0f): CameraPose {
        clock += dt
        val laK = if (lookAheadSmooth <= 0f) 1f else (1f - exp(-lookAheadSmooth * dt))
        val desiredAx = (vx * lookAhead).coerceIn(-maxLookAhead, maxLookAhead)
        val desiredAy = (vy * lookAhead).coerceIn(-maxLookAhead, maxLookAhead)
        val desiredAz = (vz * lookAhead).coerceIn(-maxLookAhead, maxLookAhead)
        aheadX += (desiredAx - aheadX) * laK
        aheadY += (desiredAy - aheadY) * laK
        aheadZ += (desiredAz - aheadZ) * laK

        var dx = tx + aheadX
        var dy = ty + aheadY
        var dz = tz + aheadZ
        // dead zone: keep the camera still until the target leaves the box
        if (deadZoneW > 0f) {
            val half = deadZoneW * 0.5f
            if (abs(dx - pose.x) < half) dx = pose.x
            else dx -= (if (dx > pose.x) 1f else -1f) * half
        }
        if (deadZoneH > 0f) {
            val half = deadZoneH * 0.5f
            if (abs(dy - pose.y) < half) dy = pose.y
            else dy -= (if (dy > pose.y) 1f else -1f) * half
        }
        pose.x = damp(pose.x, dx, smoothing, dt)
        pose.y = damp(pose.y, dy, smoothing, dt)
        pose.z = damp(pose.z, dz, smoothing, dt)
        pose.x = bounds.clampX(pose.x)
        pose.y = bounds.clampY(pose.y)
        pose.z = bounds.clampZ(pose.z)
        pose.zoom = damp(pose.zoom, zoomTarget, zoomSmoothing, dt).coerceIn(minZoom, maxZoom)

        if (trauma > 0f) trauma = max(0f, trauma - traumaDecay * dt)
        val s = trauma * trauma * shakeScale
        shakeX = s * noise(clock * 17f, seed)
        shakeY = s * noise(clock * 19f + 31f, seed + 7)
        shakeRot = s * 2.5f * noise(clock * 13f + 71f, seed + 19)
        return pose
    }

    /** Deterministic value noise in [-1,1] — no RNG, replay-safe. */
    fun noise(t: Float, salt: Int): Float {
        val i = kotlin.math.floor(t).toInt()
        val f = t - i
        val a = hash(i, salt)
        val b = hash(i + 1, salt)
        val s = f * f * (3f - 2f * f)
        return (a + (b - a) * s) * 2f - 1f
    }

    private fun hash(i: Int, salt: Int): Float {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    companion object {
        fun blend(a: CameraPose, b: CameraPose, t: Float): CameraPose {
            val k = ease(t.coerceIn(0f, 1f))
            return CameraPose(
                a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k,
                a.zoom + (b.zoom - a.zoom) * k,
                a.yaw + (b.yaw - a.yaw) * k, a.pitch + (b.pitch - a.pitch) * k
            )
        }

        /** Smoothstep — zero velocity at both ends of a camera transition. */
        fun ease(t: Float): Float {
            val k = t.coerceIn(0f, 1f)
            return k * k * (3f - 2f * k)
        }
    }
}

/**
 * Arc-length parameterised Catmull–Rom spline, used for cinematic camera
 * dolly shots. Sampling with t ∈ [0,1] gives constant-speed motion.
 */
class CameraPath(points: List<FloatArray>, var loop: Boolean = false) {
    private val pts: List<FloatArray> = if (points.size >= 2) points.map { it.copyOf(3) } else
        listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f))
    private val cumulative: FloatArray
    val totalLength: Float

    init {
        val samples = 24
        val lengths = FloatArray((pts.size - 1) * samples + 1)
        var acc = 0f
        var prev = atRaw(0f)
        lengths[0] = 0f
        var idx = 1
        for (seg in 0 until pts.size - 1) for (s in 0 until samples) {
            val u = (s + 1f) / samples
            val p = atRaw(seg + u)
            val dx = p[0] - prev[0]; val dy = p[1] - prev[1]; val dz = p[2] - prev[2]
            acc += sqrt(dx * dx + dy * dy + dz * dz)
            lengths[idx++] = acc
            prev = p
        }
        cumulative = lengths
        totalLength = acc.coerceAtLeast(1e-6f)
    }

    fun sample(t: Float): FloatArray = atDistance(t.coerceIn(0f, 1f) * totalLength)

    fun atDistance(distance: Float): FloatArray {
        val d = distance.coerceIn(0f, totalLength)
        var lo = 0; var hi = cumulative.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (cumulative[mid] < d) lo = mid + 1 else hi = mid
        }
        val i = lo.coerceIn(1, cumulative.size - 1)
        val a = cumulative[i - 1]; val b = cumulative[i]
        val k = if (b - a > 1e-6f) (d - a) / (b - a) else 0f
        val segCount = pts.size - 1
        val stepsPerSeg = (cumulative.size - 1) / segCount
        val u = ((i - 1 + k) / stepsPerSeg).coerceIn(0f, segCount.toFloat())
        return atRaw(u)
    }

    private fun p(i: Int): FloatArray {
        val n = pts.size
        return when {
            loop -> pts[((i % n) + n) % n]
            else -> pts[i.coerceIn(0, n - 1)]
        }
    }

    private fun atRaw(u: Float): FloatArray {
        val maxSeg = pts.size - 1
        val seg = kotlin.math.floor(u).toInt().coerceIn(0, maxSeg - 1)
        val t = (u - seg).coerceIn(0f, 1f)
        val p0 = p(seg - 1); val p1 = p(seg); val p2 = p(seg + 1); val p3 = p(seg + 2)
        val out = FloatArray(3)
        for (c in 0..2) {
            val a = p0[c]; val b = p1[c]; val cc = p2[c]; val d = p3[c]
            out[c] = 0.5f * ((2f * b) + (-a + cc) * t +
                (2f * a - 5f * b + 4f * cc - d) * t * t +
                (-a + 3f * b - 3f * cc + d) * t * t * t)
        }
        return out
    }
}

/** Picks the active camera by priority and manages timed blends / cinematics. */
class CameraDirector {
    private val rigs = LinkedHashMap<Long, CameraRig>()

    var activeId: Long? = null
    var blendFromId: Long? = null
        private set
    var blendDuration = 0f
        private set
    var blendElapsed = 0f
        private set
    var cinematicPath: CameraPath? = null
    var cinematicTime = 0f
    var cinematicDuration = 0f

    fun rig(id: Long): CameraRig = rigs.getOrPut(id) { CameraRig() }
    fun remove(id: Long) { rigs.remove(id); if (activeId == id) activeId = null }
    fun clear() { rigs.clear(); activeId = null; blendFromId = null; cinematicPath = null }
    val size: Int get() = rigs.size

    /** Highest priority wins; ties keep the first registered (deterministic). */
    fun pick(available: List<Long>): Long? {
        var best: Long? = null
        var bestPriority = Int.MIN_VALUE
        for (id in available) {
            val p = rigs[id]?.priority ?: 0
            if (p > bestPriority) { bestPriority = p; best = id }
        }
        return best
    }

    fun switchTo(id: Long, duration: Float = 0f) {
        if (activeId == id) return
        blendFromId = activeId
        activeId = id
        blendDuration = duration.coerceAtLeast(0f)
        blendElapsed = 0f
    }

    fun playPath(path: CameraPath, duration: Float) {
        cinematicPath = path
        cinematicDuration = duration.coerceAtLeast(0.01f)
        cinematicTime = 0f
    }

    fun stopPath() { cinematicPath = null }

    val isBlending: Boolean get() = blendFromId != null && blendElapsed < blendDuration
    val blendProgress: Float get() = if (blendDuration <= 0f) 1f else (blendElapsed / blendDuration).coerceIn(0f, 1f)
    val isCinematic: Boolean get() = cinematicPath != null

    fun update(dt: Float): Float? {
        if (blendFromId != null) {
            blendElapsed += dt
            if (blendElapsed >= blendDuration) blendFromId = null
        }
        val path = cinematicPath
        if (path != null) {
            cinematicTime += dt
            if (cinematicTime >= cinematicDuration) {
                cinematicPath = null
                return 1f
            }
            return (cinematicTime / cinematicDuration).coerceIn(0f, 1f)
        }
        return null
    }

    /** Pose to render this frame (blends the previous rig into the active one). */
    fun framePose(activePose: CameraPose): CameraPose {
        if (!isBlending) return activePose
        val from = blendFromId?.let { rigs[it] } ?: return activePose
        return CameraRig.blend(from.pose, activePose, blendProgress)
    }
}
