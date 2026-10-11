package com.sengine.engine.core

/**
 * Advanced Time management like Unity's Time class - Ultimate Edition
 */
class TimeManager {
    var timeScale: Float = 1f
    var fixedDeltaTime: Float = 1f / 60f
    var maximumDeltaTime: Float = 0.1f
    var time: Double = 0.0
        private set
    var unscaledTime: Double = 0.0
        private set
    var deltaTime: Float = 0f
        private set
    var unscaledDeltaTime: Float = 0f
        private set
    var fixedTime: Double = 0.0
        private set
    var frameCount: Long = 0
        private set
    var fps: Float = 0f
        private set
    var smoothDeltaTime: Float = 0f
        private set

    private var fpsAcc = 0f
    private var fpsFrames = 0
    private var deltaHistory = FloatArray(30)
    private var historyIndex = 0

    fun update(rawDt: Float) {
        unscaledDeltaTime = rawDt.coerceIn(0f, maximumDeltaTime)
        deltaTime = (unscaledDeltaTime * timeScale).coerceIn(0f, maximumDeltaTime)
        time += deltaTime
        unscaledTime += unscaledDeltaTime
        frameCount++

        // smooth delta
        deltaHistory[historyIndex] = deltaTime
        historyIndex = (historyIndex + 1) % deltaHistory.size
        smoothDeltaTime = deltaHistory.average().toFloat()

        fpsAcc += unscaledDeltaTime
        fpsFrames++
        if (fpsAcc >= 0.5f) {
            fps = fpsFrames / fpsAcc
            fpsAcc = 0f
            fpsFrames = 0
        }
    }

    fun fixedUpdate() {
        fixedTime += fixedDeltaTime * timeScale
    }

    fun reset() {
        time = 0.0
        unscaledTime = 0.0
        fixedTime = 0.0
        frameCount = 0
        fps = 0f
        fpsAcc = 0f
        fpsFrames = 0
        deltaHistory.fill(fixedDeltaTime)
    }
}

/**
 * Application settings like Unity's Application class
 */
object Application {
    var targetFrameRate: Int = 60
    var runInBackground: Boolean = true
    var isPlaying: Boolean = false
    var isEditor: Boolean = true
    var platform: String = "Android"
    var version: String = EngineVersion.string()
    var companyName: String = "S Engine Studio"
    var productName: String = "Ultimate Game"
    var dataPath: String = ""
    var persistentDataPath: String = ""
    var isMobilePlatform: Boolean = true
    var isFocused: Boolean = true

    fun quit() { isPlaying = false }
}
