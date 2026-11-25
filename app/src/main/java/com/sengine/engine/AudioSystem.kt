package com.sengine.engine

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import com.sengine.engine.core.AssetKind
import com.sengine.project.Project
import kotlin.math.sqrt

/**
 * Positional (3D) audio math — pure functions, fully unit-testable on the JVM.
 *
 * Gains are computed from the listener position/orientation and the sound's
 * world position: inverse-ish linear distance attenuation between minDist and
 * maxDist, plus stereo pan from the lateral offset along the listener's right
 * vector (classic equal-power-free "constant power" style pan, clamped).
 */
object Audio3DMath {
    /**
     * Returns [leftGain, rightGain] in 0..1 for a sound at (sx,sy,sz)
     * heard by a listener at (lx,ly,lz) facing with right vector (rX,rY,rZ).
     */
    fun gains(
        lx: Float, ly: Float, lz: Float,
        rX: Float, rY: Float, rZ: Float,
        sx: Float, sy: Float, sz: Float,
        minDist: Float, maxDist: Float
    ): FloatArray {
        val dx = sx - lx
        val dy = sy - ly
        val dz = sz - lz
        val d = sqrt(dx * dx + dy * dy + dz * dz)
        val min = minDist.coerceAtLeast(0.01f)
        val max = maxDist.coerceAtLeast(min + 0.01f)
        val atten = when {
            d <= min -> 1f
            d >= max -> 0f
            else -> 1f - (d - min) / (max - min)
        }
        if (atten <= 0f) return floatArrayOf(0f, 0f)
        // pan: normalized lateral position relative to the listener's right axis
        val lateral = dx * rX + dy * rY + dz * rZ
        val pan = if (d > 1e-4f) (lateral / d).coerceIn(-1f, 1f) else 0f
        val left = atten * (if (pan > 0f) 1f - pan else 1f)
        val right = atten * (if (pan < 0f) 1f + pan else 1f)
        return floatArrayOf(left, right)
    }
}

/**
 * Sound playback: 2D SoundPool one-shots/loops plus a **3D positional layer**
 * (distance attenuation + stereo panning updated every frame against the
 * listener) and a **music channel** backed by MediaPlayer.
 *
 * All Android calls are guarded: on platforms where the pool fails to build
 * (e.g. headless tests) every method degrades to a safe no-op while the pure
 * [Audio3DMath] helpers remain fully testable.
 */
class AudioSystem(private val project: Project) {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val streams = ArrayList<Int>()
    private var tone: ToneGenerator? = null

    // ---- 3D positional state
    private class Sound3(
        val name: String, val x: Float, val y: Float, val z: Float,
        val volume: Float, val minDist: Float, val maxDist: Float
    )
    private val sounds3 = HashMap<Int, Sound3>()
    var listenerX = 0f
    var listenerY = 0f
    var listenerZ = 0f
    var listenerRightX = 1f
    var listenerRightY = 0f
    var listenerRightZ = 0f
    /** Global gains (0..1) applied like Unity's Audio mixer sliders. */
    var sfxVolume = 1f
        set(v) {
            field = v.coerceIn(0f, 1f)
            val p = pool ?: return
            for (s in streams) {
                if (s in sounds3) continue // 3D streams are refreshed by update3d()
                try { p.setVolume(s, field, field) } catch (_: Throwable) {}
            }
        }
    var musicVolume = 0.9f
        set(v) {
            field = v.coerceIn(0f, 1f)
            try { music?.setVolume(field, field) } catch (_: Throwable) {}
        }
    /** Default 3D falloff range in world units. */
    var minDist3 = 3f
    var maxDist3 = 70f

    // ---- music
    private var music: android.media.MediaPlayer? = null
    private var musicName = ""

    fun start() {
        stop()
        try { createPool() } catch (_: Throwable) { pool = null }
    }

    private fun createPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attrs).build()
        pool = p
        for (name in project.listAssets(AssetKind.SOUND)) {
            try {
                ids[name] = p.load(project.assetFile(name).absolutePath, 1)
            } catch (_: Exception) {
            }
        }
    }

    fun play(name: String, volume: Float = 1f, loop: Boolean = false): Int {
        val p = pool ?: return 0
        val id = ids[name] ?: return 0
        val v = volume * sfxVolume
        val s = p.play(id, v, v, 1, if (loop) -1 else 0, 1f)
        if (s != 0) streams.add(s)
        return s
    }

    fun beep(durationMs: Int = 80) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- 3D audio
    /** Updates the listener pose (call once per frame from the engine). */
    fun setListener(x: Float, y: Float, z: Float, rightX: Float, rightY: Float, rightZ: Float) {
        listenerX = x; listenerY = y; listenerZ = z
        val l = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ).coerceAtLeast(1e-5f)
        listenerRightX = rightX / l; listenerRightY = rightY / l; listenerRightZ = rightZ / l
    }

    /**
     * Plays a sound at a world position. Returns a stream id (0 when audio is
     * unavailable) usable with [stop3d]. Loops keep panning/attenuating live
     * via [update3d]; one-shots are panned at spawn.
     */
    fun play3d(
        name: String, x: Float, y: Float, z: Float,
        volume: Float = 1f, loop: Boolean = false,
        minDist: Float = minDist3, maxDist: Float = maxDist3
    ): Int {
        val p = pool ?: return 0
        val id = ids[name] ?: return 0
        val g = Audio3DMath.gains(listenerX, listenerY, listenerZ, listenerRightX, listenerRightY, listenerRightZ, x, y, z, minDist, maxDist)
        val l = g[0] * volume * sfxVolume
        val r = g[1] * volume * sfxVolume
        val s = p.play(id, l, r, 1, if (loop) -1 else 0, 1f)
        if (s != 0) {
            sounds3[s] = Sound3(name, x, y, z, volume, minDist, maxDist)
            streams.add(s)
        }
        return s
    }

    /** Stops a specific 3D (or 2D) stream started with [play]/[play3d]. */
    fun stop3d(streamId: Int) {
        try { pool?.stop(streamId) } catch (_: Throwable) {}
        sounds3.remove(streamId)
        streams.remove(streamId)
    }

    /** Re-pans/attenuates every tracked 3D sound against the current listener. */
    fun update3d() {
        val p = pool ?: return
        if (sounds3.isEmpty()) return
        for ((sid, st) in sounds3) {
            val g = Audio3DMath.gains(listenerX, listenerY, listenerZ, listenerRightX, listenerRightY, listenerRightZ, st.x, st.y, st.z, st.minDist, st.maxDist)
            try { p.setVolume(sid, g[0] * st.volume * sfxVolume, g[1] * st.volume * sfxVolume) } catch (_: Throwable) {}
        }
    }

    /** Moves an already-playing 3D sound (e.g. an engine hum that follows its source). */
    fun move3d(streamId: Int, x: Float, y: Float, z: Float) {
        sounds3[streamId]?.let { it2 ->
            // Sound3 is val-heavy; replace entry
            val copy = Sound3(it2.name, x, y, z, it2.volume, it2.minDist, it2.maxDist)
            sounds3[streamId] = copy
        }
    }

    // ---------------------------------------------------------------- music
    /** Starts background music (looping by default). Safe no-op when playback fails. */
    fun playMusic(name: String, loop: Boolean = true, volume: Float = -1f) {
        try {
            stopMusic()
            val file = project.assetFile(name)
            if (!file.exists()) return
            val mp = android.media.MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.isLooping = loop
            val v = (if (volume < 0f) musicVolume else volume)
            mp.setVolume(v, v)
            mp.prepare()
            mp.start()
            music = mp
            musicName = name
        } catch (_: Throwable) {
            music = null
        }
    }

    fun pauseMusic() {
        try { music?.let { if (it.isPlaying) it.pause() } } catch (_: Throwable) {}
    }

    fun resumeMusic() {
        try { music?.let { if (!it.isPlaying) it.start() } } catch (_: Throwable) {}
    }

    fun stopMusic() {
        try {
            music?.stop()
            music?.release()
        } catch (_: Throwable) {}
        music = null
        musicName = ""
    }

    fun stopAll() {
        val p = pool ?: return
        try { for (s in streams) p.stop(s) } catch (_: Throwable) {}
        streams.clear()
        sounds3.clear()
    }

    fun stop() {
        stopMusic()
        stopAll()
        try { pool?.release() } catch (_: Throwable) {}
        pool = null
        ids.clear()
        tone?.release()
        tone = null
    }
}
