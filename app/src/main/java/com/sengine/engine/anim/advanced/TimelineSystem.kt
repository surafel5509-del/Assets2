package com.sengine.engine.anim.advanced

import org.json.JSONObject
import org.json.JSONArray

/**
 * Ultimate Edition Timeline & Cinemachine - Unity Timeline + Cinemachine + Unreal Sequencer equivalent
 */

enum class TrackType { Animation, Audio, Activation, Control, Signal, Cinemachine }

class TimelineClip(var name: String = "Clip") {
    var start: Float = 0f
    var duration: Float = 1f
    var clipIn: Float = 0f
    var timeScale: Float = 1f
    var extrapolationMode: Int = 0
    var postExtrapolationMode: Int = 0
    var displayName: String = name
    var asset: String = "" // clip asset path
    var properties = mutableMapOf<String, Any>()

    fun end(): Float = start + duration
    fun contains(time: Float): Boolean = time >= start && time < end()
}

class TimelineTrack(var name: String = "Track", var type: TrackType = TrackType.Animation) {
    var muted: Boolean = false
    var locked: Boolean = false
    var clips = mutableListOf<TimelineClip>()
    var childTracks = mutableListOf<TimelineTrack>()
    var parent: TimelineTrack? = null
    var color: Int = 0xFF808080.toInt()

    fun addClip(clip: TimelineClip) { clips.add(clip); clips.sortBy { it.start } }
    fun getClipAt(time: Float): TimelineClip? = clips.firstOrNull { it.contains(time) }
    fun duration(): Float = clips.maxOfOrNull { it.end() } ?: 0f
}

class TimelineAsset(var name: String = "Timeline") {
    var tracks = mutableListOf<TimelineTrack>()
    var duration: Float = 10f
    var frameRate: Float = 60f
    var durationMode: Int = 0 // 0 based on clips, 1 fixed length

    fun addTrack(track: TimelineTrack) { tracks.add(track); updateDuration() }
    fun updateDuration() {
        if (durationMode == 0) duration = tracks.maxOfOrNull { it.duration() } ?: 0f
    }

    fun evaluate(time: Float): Map<TimelineTrack, TimelineClip?> {
        val result = mutableMapOf<TimelineTrack, TimelineClip?>()
        for (track in tracks) {
            if (track.muted) continue
            result[track] = track.getClipAt(time)
            for (child in track.childTracks) result[child] = child.getClipAt(time)
        }
        return result
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("duration", duration.toDouble())
        o.put("frameRate", frameRate.toDouble())
        val tracksArr = JSONArray()
        for (track in tracks) {
            val to = JSONObject()
            to.put("name", track.name)
            to.put("type", track.type.name)
            to.put("muted", track.muted)
            val clipsArr = JSONArray()
            for (clip in track.clips) {
                val co = JSONObject()
                co.put("name", clip.name)
                co.put("start", clip.start.toDouble())
                co.put("duration", clip.duration.toDouble())
                co.put("asset", clip.asset)
                clipsArr.put(co)
            }
            to.put("clips", clipsArr)
            tracksArr.put(to)
        }
        o.put("tracks", tracksArr)
        return o
    }
}

class PlayableDirector {
    var timeline: TimelineAsset? = null
    var time: Float = 0f
    var isPlaying: Boolean = false
    var playOnAwake: Boolean = true
    var wrapMode: Int = 0 // 0 hold, 1 loop, 2 none
    var initialTime: Float = 0f
    var currentTime: Float = 0f

    fun play() { isPlaying = true; currentTime = initialTime }
    fun pause() { isPlaying = false }
    fun stop() { isPlaying = false; currentTime = initialTime }
    fun setTime(t: Float) { currentTime = t.coerceIn(0f, timeline?.duration ?: Float.MAX_VALUE) }

    fun update(dt: Float): Map<TimelineTrack, TimelineClip?>? {
        if (!isPlaying) return null
        val tl = timeline ?: return null
        currentTime += dt
        if (currentTime > tl.duration) {
            when (wrapMode) {
                0 -> { currentTime = tl.duration; isPlaying = false }
                1 -> currentTime %= tl.duration
                2 -> { currentTime = tl.duration; isPlaying = false; return null }
            }
        }
        return tl.evaluate(currentTime)
    }
}

/**
 * Cinemachine - procedural camera system like Unity Cinemachine / Unreal Camera Rig
 */
enum class CinemachineBlendStyle { Cut, EaseInOut, EaseIn, EaseOut, HardIn, HardOut, Linear }

class CinemachineVirtualCamera(var name: String = "CM vcam") {
    var priority: Int = 10
    var follow: String = "" // target name
    var lookAt: String = ""
    var lensFOV: Float = 60f
    var lensNear: Float = 0.1f
    var lensFar: Float = 5000f
    var lensDutch: Float = 0f

    // Body
    var bodyType: Int = 0 // 0 do nothing, 1 transposer, 2 dolly, 3 orbital, 4 framing transposer, 5 tracked dolly
    var followOffset: FloatArray = floatArrayOf(0f, 2f, -5f)
    var dampingX: Float = 1f
    var dampingY: Float = 1f
    var dampingZ: Float = 1f

    // Aim
    var aimType: Int = 0 // 0 do nothing, 1 composer, 2 group composer, 3 POV, 4 same as follow
    var compositionDeadZoneWidth: Float = 0.2f
    var compositionDeadZoneHeight: Float = 0.2f
    var compositionSoftZoneWidth: Float = 0.8f
    var compositionSoftZoneHeight: Float = 0.8f
    var compositionBiasX: Float = 0f
    var compositionBiasY: Float = 0f
    var trackedObjectOffset: FloatArray = floatArrayOf(0f, 1f, 0f)

    // Noise
    var noiseProfile: String = ""
    var noiseAmplitude: Float = 0f
    var noiseFrequency: Float = 1f

    fun getPosition(targetPos: FloatArray): FloatArray {
        // Simple transposer logic
        return floatArrayOf(
            targetPos[0] + followOffset[0],
            targetPos[1] + followOffset[1],
            targetPos[2] + followOffset[2]
        )
    }
}

class CinemachineDollyTrack {
    data class Waypoint(var x: Float, var y: Float, var z: Float, var roll: Float = 0f)

    val waypoints = mutableListOf<Waypoint>()
    var isLooped: Boolean = false
    var positionUnits: Int = 0 // 0 path units, 1 distance

    fun addWaypoint(x: Float, y: Float, z: Float) { waypoints.add(Waypoint(x, y, z)) }

    fun evaluate(position: Float): FloatArray {
        if (waypoints.isEmpty()) return floatArrayOf(0f, 0f, 0f)
        if (waypoints.size == 1) return floatArrayOf(waypoints[0].x, waypoints[0].y, waypoints[0].z)

        val t = position.coerceIn(0f, 1f) * (waypoints.size - 1)
        val idx = t.toInt().coerceIn(0, waypoints.size - 2)
        val frac = t - idx
        val a = waypoints[idx]; val b = waypoints[idx + 1]
        return floatArrayOf(
            a.x + (b.x - a.x) * frac,
            a.y + (b.y - a.y) * frac,
            a.z + (b.z - a.z) * frac
        )
    }
}

class CinemachineBrain {
    var activeVirtualCamera: CinemachineVirtualCamera? = null
    var defaultBlendTime: Float = 2f
    var defaultBlendStyle: CinemachineBlendStyle = CinemachineBlendStyle.EaseInOut
    var customBlends = mutableListOf<Pair<Pair<String, String>, CinemachineBlendStyle>>()

    private val cameras = mutableListOf<CinemachineVirtualCamera>()

    fun addCamera(vcam: CinemachineVirtualCamera) { cameras.add(vcam); cameras.sortByDescending { it.priority } }

    fun update(): CinemachineVirtualCamera? {
        activeVirtualCamera = cameras.firstOrNull { true } // highest priority active
        return activeVirtualCamera
    }

    fun getBlend(from: String, to: String): CinemachineBlendStyle {
        return customBlends.firstOrNull { it.first.first == from && it.first.second == to }?.second ?: defaultBlendStyle
    }
}
