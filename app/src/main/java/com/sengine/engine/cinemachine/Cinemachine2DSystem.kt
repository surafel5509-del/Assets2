package com.sengine.engine.cinemachine

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Cinemachine 2D & Most Powerful Tools
 * Framing Transposer, Confiner, Pixel Perfect, 2D Camera Control
 */
enum class Cinemachine2DMode { FramingTransposer, Confiner, PixelPerfect, GroupFraming, OrbitalTransposer }
enum class CinemachineBlendStyle { Cut, EaseInOut, EaseIn, EaseOut, HardIn, HardOut, Linear }

data class CinemachineConfiner(
    var boundingShape: String = "PolygonCollider2D",
    var damping: Float = 0f,
    var confinerMode: Int = 0, // 0 confine screen edges, 1 confine camera
    var maxWindowSize: Float = 0f
) {
    fun confine(cameraX: Float, cameraY: Float, boundsMinX: Float, boundsMinY: Float, boundsMaxX: Float, boundsMaxY: Float): Pair<Float,Float> {
        var x = cameraX.coerceIn(boundsMinX, boundsMaxX)
        var y = cameraY.coerceIn(boundsMinY, boundsMaxY)
        return Pair(x,y)
    }
}

data class CinemachinePixelPerfect(
    var enabled: Boolean = true,
    var assetsPPU: Int = 100,
    var refResolutionX: Int = 1920,
    var refResolutionY: Int = 1080,
    var cropFrame: Int = 0, // 0 stretch, 1 crop
    var upscaleRT: Boolean = false,
    var pixelSnapping: Boolean = true
) {
    fun getPixelPerfectPosition(x: Float, y: Float): Pair<Float,Float> {
        if (!enabled) return Pair(x,y)
        val ppu = assetsPPU.toFloat()
        return Pair(floor(x*ppu)/ppu, floor(y*ppu)/ppu)
    }
    fun getOrthoSize(screenHeight: Int): Float = screenHeight.toFloat()/(assetsPPU*2f)
}

data class CinemachineFramingTransposer2D(
    var followTarget: String = "",
    var lookaheadTime: Float = 0f,
    var lookaheadSmoothing: Float = 0f,
    var lookaheadIgnoreY: Boolean = false,
    var xDamping: Float = 1f,
    var yDamping: Float = 1f,
    var zDamping: Float = 1f,
    var screenX: Float = 0.5f,
    var screenY: Float = 0.5f,
    var deadZoneWidth: Float = 0.1f,
    var deadZoneHeight: Float = 0.1f,
    var softZoneWidth: Float = 0.8f,
    var softZoneHeight: Float = 0.8f,
    var biasX: Float = 0f,
    var biasY: Float = 0f,
    var unbounded: Boolean = false
) {
    fun calculatePosition(targetX: Float, targetY: Float, currentX: Float, currentY: Float, screenWidth: Float, screenHeight: Float, dt: Float): Pair<Float,Float> {
        val deadLeft = screenWidth*(screenX-deadZoneWidth*0.5f)
        val deadRight = screenWidth*(screenX+deadZoneWidth*0.5f)
        val deadBottom = screenHeight*(screenY-deadZoneHeight*0.5f)
        val deadTop = screenHeight*(screenY+deadZoneHeight*0.5f)

        var newX = currentX; var newY = currentY
        val offsetX = targetX-currentX; val offsetY = targetY-currentY

        if (offsetX < deadLeft || offsetX > deadRight) {
            val k = if (xDamping <= 0f) 1f else (1f-exp(-xDamping*dt))
            newX += (targetX-currentX-screenWidth*biasX)*k
        }
        if (offsetY < deadBottom || offsetY > deadTop) {
            val k = if (yDamping <= 0f) 1f else (1f-exp(-yDamping*dt))
            newY += (targetY-currentY-screenHeight*biasY)*k
        }
        return Pair(newX, newY)
    }
}

data class CinemachineVirtualCamera2D(
    var name: String = "CM vcam 2D",
    var priority: Int = 10,
    var follow: String = "",
    var lookAt: String = "",
    var lensFOV: Float = 60f,
    var lensOrthoSize: Float = 5f,
    var lensNear: Float = 0.1f,
    var lensFar: Float = 5000f,
    var mode: Cinemachine2DMode = Cinemachine2DMode.FramingTransposer,
    var framingTransposer: CinemachineFramingTransposer2D = CinemachineFramingTransposer2D(),
    var confiner: CinemachineConfiner = CinemachineConfiner(),
    var pixelPerfect: CinemachinePixelPerfect = CinemachinePixelPerfect(),
    var isActive: Boolean = true,
    var x: Float = 0f, var y: Float = 0f
) {
    fun getPosition(): FloatArray = floatArrayOf(x, y, 0f)
}

class Cinemachine2DSystem {
    var cameras = mutableListOf<CinemachineVirtualCamera2D>()
    var activeCamera: CinemachineVirtualCamera2D? = null
    var defaultBlendTime: Float = 2f
    var defaultBlendStyle: CinemachineBlendStyle = CinemachineBlendStyle.EaseInOut
    var brainX: Float = 0f; var brainY: Float = 0f

    fun createCamera(name: String, follow: String = "", priority: Int = 10): CinemachineVirtualCamera2D {
        val cam = CinemachineVirtualCamera2D(name=name, follow=follow, priority=priority); cameras.add(cam); sortByPriority(); return cam
    }

    fun sortByPriority() { cameras.sortByDescending { it.priority } }

    fun update(dt: Float, targetPositions: Map<String, Pair<Float,Float>>) {
        // Find active camera (highest priority active)
        activeCamera = cameras.filter { it.isActive }.maxByOrNull { it.priority }
        val active = activeCamera ?: return

        val targetPos = targetPositions[active.follow] ?: Pair(0f,0f)
        when (active.mode) {
            Cinemachine2DMode.FramingTransposer -> {
                val newPos = active.framingTransposer.calculatePosition(targetPos.first, targetPos.second, active.x, active.y, 1920f, 1080f, dt)
                active.x = newPos.first; active.y = newPos.second
            }
            Cinemachine2DMode.PixelPerfect -> {
                val ppPos = active.pixelPerfect.getPixelPerfectPosition(targetPos.first, targetPos.second)
                active.x = ppPos.first; active.y = ppPos.second
            }
            else -> { active.x = targetPos.first; active.y = targetPos.second }
        }
        brainX = active.x; brainY = active.y
    }

    fun blendToCamera(name: String, blendTime: Float = defaultBlendTime) {
        val cam = cameras.find { it.name == name } ?: return
        // Blend logic - for simplicity just set active
        for (c in cameras) c.isActive = c.name == name
        activeCamera = cam
    }

    fun getActivePosition(): Pair<Float,Float> = Pair(brainX, brainY)

    fun getStats(): String = "Cinemachine2D Cameras:${cameras.size} Active:${activeCamera?.name} Pos:($brainX,$brainY) Blend:$defaultBlendStyle"
}
