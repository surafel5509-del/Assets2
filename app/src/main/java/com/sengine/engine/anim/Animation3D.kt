package com.sengine.engine.anim

import org.json.JSONObject
import kotlin.math.pow

/**
 * Track-based 3D / rigged animation. A clip transforms either named joints or
 * whole sub-object transforms; JSON `.anim3d`, glTF-style `.glanim`,
 * BioVision `.bvh` and FBX-ASCII take imports are all normalized to this model.
 */
data class AnimationTrack(
    val target: String,
    val times: List<Float>,
    val positions: List<FloatArray>,
    val rotations: List<FloatArray>,
    val scales: List<FloatArray>,
    val interpolations: List<String> = emptyList(),
) {
    fun validate(): String? = when {
        times.isEmpty() -> "empty times"
        positions.isEmpty() && rotations.isEmpty() && scales.isEmpty() -> "no channels"
        times.zipWithNext().any { it.second < it.first } -> "non-monotonic times"
        else -> null
    }

    fun duration(): Float = times.maxOrNull() ?: 0f

    fun sample(t: Float, loop: Boolean = true): Triple<FloatArray, FloatArray, FloatArray>? {
        if (times.isEmpty()) return null
        val dur = duration()
        var time = if (loop && dur > 0f) t % dur else t.coerceIn(0f, dur)
        var i = times.indexOfLast { it <= time }.coerceAtLeast(0)
        if (i >= times.size - 1) {
            i = times.size - 1
            return Triple(
                positions.getOrNull(i) ?: floatArrayOf(0f, 0f, 0f),
                rotations.getOrNull(i) ?: floatArrayOf(0f, 0f, 0f),
                scales.getOrNull(i) ?: floatArrayOf(1f, 1f, 1f),
            )
        }
        val t0 = times[i]; val t1 = times[i + 1]
        val a = if (t1 <= t0) 0f else ((time - t0) / (t1 - t0)).coerceIn(0f, 1f)
        val mode = interpolations.getOrElse(i) { "LINEAR" }
        val f = if (mode.equals("STEP", ignoreCase = true)) 0f else a
        fun lerp(list: List<FloatArray>, fallback: FloatArray): FloatArray {
            val a0 = list.getOrNull(i) ?: fallback
            val a1 = list.getOrNull(i + 1) ?: a0
            return FloatArray(3) { k -> a0.getOrElse(k) { fallback[k] } * (1f - f) + a1.getOrElse(k) { a0.getOrElse(k) { fallback[k] } } * f }
        }
        return Triple(
            lerp(positions, floatArrayOf(0f, 0f, 0f)),
            lerp(rotations, floatArrayOf(0f, 0f, 0f)),
            lerp(scales, floatArrayOf(1f, 1f, 1f)),
        )
    }
}

data class Animation3DClip(
    var name: String = "Animation",
    var fps: Float = 30f,
    var loop: Boolean = true,
    var skeleton: String = "",
    val tracks: MutableList<AnimationTrack> = mutableListOf(),
) {
    fun duration(): Float = tracks.maxOfOrNull { it.duration() } ?: 0f

    fun validate(): String? {
        if (name.isBlank()) return "missing name"
        if (fps <= 0f) return "invalid fps"
        if (tracks.isEmpty()) return "no tracks"
        return tracks.firstNotNullOfOrNull { it.validate() }
    }

    fun sample(t: Float, shouldLoop: Boolean = loop): Map<String, Triple<FloatArray, FloatArray, FloatArray>> =
        tracks.mapNotNull { tr -> tr.sample(t * fps, shouldLoop)?.let { tr.target to it } }.toMap()

    fun toJson(): JSONObject = JSONObject()
        .put("type", "animation3d").put("name", name)
        .put("fps", fps.toDouble()).put("loop", loop)
        .put("skeleton", skeleton)
        .put("tracks", org.json.JSONArray().apply {
            for (tr in tracks) put(JSONObject()
                .put("target", tr.target)
                .put("times", org.json.JSONArray(tr.times.map { it.toDouble() }))
                .put("positions", org.json.JSONArray(tr.positions.map { org.json.JSONArray(it.map { v -> v.toDouble() }) }))
                .put("rotations", org.json.JSONArray(tr.rotations.map { org.json.JSONArray(it.map { v -> v.toDouble() }) }))
                .put("scales", org.json.JSONArray(tr.scales.map { org.json.JSONArray(it.map { v -> v.toDouble() }) }))
                .put("interpolations", org.json.JSONArray(tr.interpolations)))
        })

    companion object {
        fun fromJson(o: JSONObject): Animation3DClip {
            val clip = Animation3DClip(
                o.optString("name", "Animation"),
                o.optDouble("fps", 30.0).toFloat(),
                o.optBoolean("loop", true),
                o.optString("skeleton", ""),
            )
            val arr = o.optJSONArray("tracks") ?: return clip
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                fun floats(key: String): List<FloatArray> {
                    val a = t.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).mapNotNull { j ->
                        val row = a.optJSONArray(j) ?: return@mapNotNull null
                        FloatArray(3) { k -> row.optDouble(k, 0.0).toFloat() }
                    }
                }
                val timesArray = t.optJSONArray("times")
                val times = if (timesArray != null) (0 until timesArray.length()).map { timesArray.optDouble(it, 0.0).toFloat() } else emptyList()
                val modes = t.optJSONArray("interpolations")
                val interpolations = if (modes != null) (0 until modes.length()).map { modes.optString(it, "LINEAR") } else emptyList()
                clip.tracks.add(AnimationTrack(
                    t.optString("target", "root"), times,
                    floats("positions"), floats("rotations"), floats("scales"), interpolations,
                ))
            }
            return clip
        }
    }
}

object Animation3DImporters {
    fun isAnimationFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("anim3d", "glanim", "motion", "clip", "skel", "skin", "bvh", "fbxanim")

    fun parse(name: String, text: String): Animation3DClip? {
        val e = name.substringAfterLast('.', "").lowercase()
        return when (e) {
            "bvh", "motion" -> parseBvh(text, name.substringBeforeLast('.'))
            "glanim", "clip", "skel", "skin", "fbxanim" -> fromData(text)
            "anim3d" -> fromData(text)
            else -> null
        }
    }

    private fun fromData(text: String): Animation3DClip? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return null
        return try {
            val o = JSONObject(trimmed)
            val c = if (o.has("tracks")) Animation3DClip.fromJson(o) else Animation3DClip.fromJson(o.optJSONObject("animation") ?: o)
            c.takeIf { it.validate() == null }
        } catch (_: Exception) { null }
    }

    private fun parseBvh(text: String, fallbackName: String): Animation3DClip? {
        val lines = text.lines().map { it.trim() }
        val joints = ArrayList<String>()
        for (l in lines) {
            if (l.startsWith("ROOT ", ignoreCase = true) || l.startsWith("JOINT ", ignoreCase = true)) {
                val n = l.split(Regex("\\s+")).getOrNull(1) ?: continue
                joints.add(n)
            }
            if (l.equals("MOTION", ignoreCase = true)) break
        }
        val framesIndex = lines.indexOfFirst { it.startsWith("Frames:", ignoreCase = true) }
        val frameTimeIndex = lines.indexOfFirst { it.startsWith("Frame Time:", ignoreCase = true) }
        val frames = lines.getOrNull(framesIndex)?.substringAfter(':')?.trim()?.toIntOrNull() ?: return null
        val frameTime = lines.getOrNull(frameTimeIndex)?.substringAfter(':')?.trim()?.toFloatOrNull() ?: return null
        val data = lines.drop(frameTimeIndex + 1).filter { it.isNotEmpty() }.take(frames)
        if (joints.isEmpty() || data.isEmpty()) return null
        val fps = (1f / frameTime.coerceAtLeast(1e-5f)).coerceIn(0.1f, 240f)
        val clip = Animation3DClip(fallbackName, fps, true, joints.joinToString(","))
        val channels = lines.take(frameTimeIndex).count { it.startsWith("CHANNELS ", ignoreCase = true) }
        val perJoint = if (channels > 0) channels else 6
        val root = joints.first()
        val times = (0 until data.size).map { it.toFloat() }
        val positions = ArrayList<FloatArray>()
        val rotations = ArrayList<FloatArray>()
        val scales = ArrayList<FloatArray>()
        var target = root
        for (line in data) {
            val p = line.split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
            if (p.size >= 6) {
                positions.add(floatArrayOf(p[0], p[1], p[2]))
                rotations.add(floatArrayOf(p[5], p[4], p[3]))
                scales.add(floatArrayOf(1f, 1f, 1f))
            }
        }
        clip.tracks.add(AnimationTrack(target, times, positions, rotations, scales))
        return clip.takeIf { it.validate() == null }
    }
}
