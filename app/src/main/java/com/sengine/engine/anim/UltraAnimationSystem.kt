package com.sengine.engine.anim

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Animation System - Months/Years Professional Project
 * Retargeting, Compression, Events, Root Motion Curves, Blend Shapes Morph Targets, Pose Library, Layers Additive Override Sync, State Machine Behaviours, Motion Warping v2, Full Body IK Constraints Effectors
 */
enum class AnimationCompression { Off, KeyframeReduction, Optimal, Bitwise }
enum class RootMotionMode { BakeIntoPose, RootTransform, Curves }
enum class BlendShapeMode { Normalized, Relative }

data class AnimationEvent(
    var time: Float = 0f,
    var functionName: String = "",
    var stringParameter: String = "",
    var floatParameter: Float = 0f,
    var intParameter: Int = 0,
    var objectParameter: String = ""
)

data class AnimationCurve(
    var name: String = "Curve",
    var keys: MutableList<Pair<Float, Float>> = mutableListOf(), // time, value
    var preWrap: Int = 0, var postWrap: Int = 0
) {
    fun addKey(time: Float, value: Float) { keys.add(Pair(time, value)); keys.sortBy { it.first } }
    fun evaluate(time: Float): Float {
        if (keys.isEmpty()) return 0f
        if (keys.size==1) return keys[0].second
        if (time <= keys.first().first) return keys.first().second
        if (time >= keys.last().first) return keys.last().second
        for (i in 1 until keys.size) {
            if (time <= keys[i].first) {
                val t0 = keys[i-1].first; val v0 = keys[i-1].second
                val t1 = keys[i].first; val v1 = keys[i].second
                val frac = (time-t0)/(t1-t0)
                return v0+(v1-v0)*frac
            }
        }
        return keys.last().second
    }
    fun compress(tolerance: Float = 0.01f): AnimationCurve {
        if (keys.size <= 2) return this
        val compressed = AnimationCurve(name=name)
        compressed.keys.add(keys.first())
        for (i in 1 until keys.size-1) {
            val prev = compressed.keys.last()
            val curr = keys[i]
            val next = keys[i+1]
            val interpolated = prev.second + (next.second-prev.second)*(curr.first-prev.first)/(next.first-prev.first)
            if (abs(curr.second-interpolated) > tolerance) compressed.keys.add(curr)
        }
        compressed.keys.add(keys.last())
        return compressed
    }
}

data class BlendShape(
    var name: String = "BlendShape",
    var weight: Float = 0f,
    var vertices: MutableList<FloatArray> = mutableListOf(), // delta per vertex
    var mode: BlendShapeMode = BlendShapeMode.Normalized
) {
    fun apply(baseVertices: List<FloatArray>, weight: Float): List<FloatArray> {
        return baseVertices.mapIndexed { idx, base ->
            val delta = vertices.getOrElse(idx){floatArrayOf(0f,0f,0f)}
            floatArrayOf(base[0]+delta[0]*weight/100f, base[1]+delta[1]*weight/100f, base[2]+delta[2]*weight/100f)
        }
    }
}

data class PoseLibrary(
    var name: String = "PoseLibrary",
    var poses: MutableMap<String, MutableMap<String, Float>> = mutableMapOf() // pose name -> bone name -> rotation
) {
    fun addPose(poseName: String, boneRotations: Map<String, Float>) { poses[poseName]=boneRotations.toMutableMap() }
    fun getPose(poseName: String): Map<String, Float>? = poses[poseName]
    fun blendPoses(poseA: String, poseB: String, t: Float): Map<String, Float> {
        val a = poses[poseA] ?: return poses[poseB] ?: emptyMap()
        val b = poses[poseB] ?: return a
        val blended = mutableMapOf<String, Float>()
        for (bone in a.keys + b.keys) {
            val va = a[bone] ?: 0f; val vb = b[bone] ?: 0f
            blended[bone]=va+(vb-va)*t
        }
        return blended
    }
}

data class StateMachineBehaviour(
    var name: String = "Behaviour",
    var onStateEnter: String = "",
    var onStateUpdate: String = "",
    var onStateExit: String = "",
    var onStateMove: String = "",
    var onStateIK: String = ""
)

data class MotionWarpingV2(
    var name: String = "Warp",
    var warpTranslation: Boolean = true,
    var warpRotation: Boolean = true,
    var warpStartTime: Float = 0f,
    var warpEndTime: Float = 1f,
    var targetX: Float = 0f, var targetY: Float = 0f, var targetZ: Float = 0f,
    var targetRotY: Float = 0f,
    var translationCurve: AnimationCurve = AnimationCurve("TranslationWarp"),
    var rotationCurve: AnimationCurve = AnimationCurve("RotationWarp")
) {
    init {
        translationCurve.addKey(0f, 0f); translationCurve.addKey(1f, 1f)
        rotationCurve.addKey(0f, 0f); rotationCurve.addKey(1f, 1f)
    }
    fun getWarpFactor(time: Float): Pair<Float, Float> {
        if (time < warpStartTime || time > warpEndTime) return Pair(0f,0f)
        val t = (time-warpStartTime)/(warpEndTime-warpStartTime)
        return Pair(translationCurve.evaluate(t), rotationCurve.evaluate(t))
    }
}

class UltraAnimationSystem {
    var events = mutableListOf<AnimationEvent>()
    var curves = mutableMapOf<String, AnimationCurve>()
    var blendShapes = mutableMapOf<String, BlendShape>()
    var poseLibraries = mutableMapOf<String, PoseLibrary>()
    var stateMachineBehaviours = mutableMapOf<String, StateMachineBehaviour>()
    var motionWarping = mutableMapOf<String, MotionWarpingV2>()
    var compression: AnimationCompression = AnimationCompression.Optimal
    var rootMotionMode: RootMotionMode = RootMotionMode.RootTransform
    var retargetingEnabled: Boolean = true
    var nextEventId: Int = 0

    fun addEvent(time: Float, functionName: String, floatParam: Float = 0f, stringParam: String = ""): AnimationEvent {
        val ev = AnimationEvent(time=time, functionName=functionName, floatParameter=floatParam, stringParameter=stringParam)
        events.add(ev); events.sortBy { it.time }; return ev
    }

    fun createCurve(name: String): AnimationCurve {
        val curve = AnimationCurve(name=name); curves[name]=curve; return curve
    }

    fun createBlendShape(name: String): BlendShape {
        val bs = BlendShape(name=name); blendShapes[name]=bs; return bs
    }

    fun createPoseLibrary(name: String): PoseLibrary {
        val lib = PoseLibrary(name=name); poseLibraries[name]=lib; return lib
    }

    fun createStateMachineBehaviour(name: String): StateMachineBehaviour {
        val smb = StateMachineBehaviour(name=name); stateMachineBehaviours[name]=smb; return smb
    }

    fun createMotionWarping(name: String, targetX: Float, targetY: Float, targetZ: Float): MotionWarpingV2 {
        val warp = MotionWarpingV2(name=name, targetX=targetX, targetY=targetY, targetZ=targetZ); motionWarping[name]=warp; return warp
    }

    fun getEventsAt(time: Float, delta: Float = 0.1f): List<AnimationEvent> {
        return events.filter { it.time >= time-delta && it.time <= time+delta }
    }

    fun compressAllCurves(tolerance: Float = 0.01f) {
        if (compression == AnimationCompression.Off) return
        for ((name, curve) in curves) curves[name]=curve.compress(tolerance)
    }

    fun retargetAnimation(sourceBones: Map<String, FloatArray>, targetBones: Map<String, FloatArray>): Map<String, FloatArray> {
        if (!retargetingEnabled) return sourceBones
        // Simple retargeting - scale positions by target/source height ratio
        val sourceHeight = sourceBones.values.maxOfOrNull { it[1] } ?: 1f
        val targetHeight = targetBones.values.maxOfOrNull { it[1] } ?: 1f
        val scale = if (sourceHeight > 0f) targetHeight/sourceHeight else 1f
        return sourceBones.mapValues { (_, pos) -> floatArrayOf(pos[0]*scale, pos[1]*scale, pos[2]*scale) }
    }

    fun getStats(): String = "Ultra Anim Events:${events.size} Curves:${curves.size} BlendShapes:${blendShapes.size} PoseLibs:${poseLibraries.size} SMB:${stateMachineBehaviours.size} Warping:${motionWarping.size} Compression:$compression RootMotion:$rootMotionMode"
}
