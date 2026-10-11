package com.sengine.engine.anim.advanced

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Motion Matching & Pose Search System
 * UE5 Motion Warping, Motion Matching, Pose Search, Distance Matching
 */
enum class MotionMatchingMode { Simple, Advanced, Learned }
enum class WarpType { Translation, Rotation, Both }

data class PoseFeature(
    var position: FloatArray = floatArrayOf(0f,0f,0f),
    var velocity: FloatArray = floatArrayOf(0f,0f,0f),
    var facing: FloatArray = floatArrayOf(0f,0f,1f),
    var leftFootPos: FloatArray = floatArrayOf(0f,0f,0f),
    var rightFootPos: FloatArray = floatArrayOf(0f,0f,0f),
    var leftFootVel: FloatArray = floatArrayOf(0f,0f,0f),
    var rightFootVel: FloatArray = floatArrayOf(0f,0f,0f),
    var hipHeight: Float = 1f,
    var trajectory: Array<FloatArray> = Array(4) { floatArrayOf(0f,0f,0f) } // future trajectory
) {
    fun distanceTo(other: PoseFeature): Float {
        var dist = 0f
        fun dist3(a: FloatArray, b: FloatArray): Float {
            val dx=a[0]-b[0]; val dy=a[1]-b[1]; val dz=a[2]-b[2]; return dx*dx+dy*dy+dz*dz
        }
        dist+=dist3(position, other.position)*1f
        dist+=dist3(velocity, other.velocity)*0.5f
        dist+=dist3(facing, other.facing)*0.3f
        dist+=dist3(leftFootPos, other.leftFootPos)*0.8f
        dist+=dist3(rightFootPos, other.rightFootPos)*0.8f
        dist+=abs(hipHeight-other.hipHeight)*0.2f
        for (i in trajectory.indices) dist+=dist3(trajectory[i], other.trajectory[i])*0.5f
        return sqrt(dist)
    }
}

data class MotionClip(
    var name: String = "Clip",
    var duration: Float = 1f,
    var frameRate: Float = 30f,
    var poses: MutableList<PoseFeature> = mutableListOf(),
    var rootMotion: MutableList<FloatArray> = mutableListOf(),
    var tags: MutableList<String> = mutableListOf()
) {
    fun getPoseAt(time: Float): PoseFeature {
        if (poses.isEmpty()) return PoseFeature()
        val t = time.coerceIn(0f, duration)
        val frame = (t*frameRate).toInt().coerceIn(0, poses.size-1)
        return poses[frame]
    }
    fun sampleTrajectory(time: Float, futureTimes: FloatArray = floatArrayOf(0.2f,0.4f,0.6f,1f)): Array<FloatArray> {
        return Array(futureTimes.size) { i ->
            val futureTime = time+futureTimes[i]
            getPoseAt(futureTime).position
        }
    }
}

data class MotionWarpingTarget(
    var name: String = "WarpTarget",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var rotY: Float = 0f,
    var type: WarpType = WarpType.Both,
    var warpStartTime: Float = 0f,
    var warpEndTime: Float = 1f
)

class MotionMatchingSystem {
    var clips = mutableListOf<MotionClip>()
    var currentClip: MotionClip? = null
    var currentTime: Float = 0f
    var queryFeature = PoseFeature()
    var mode: MotionMatchingMode = MotionMatchingMode.Advanced
    var searchInterval: Float = 0.1f
    var searchTimer: Float = 0f
    var blendTime: Float = 0.2f
    var isMatching: Boolean = true
    var trajectoryPrediction: Array<FloatArray> = Array(4) { floatArrayOf(0f,0f,0f) }
    var warpingTargets = mutableListOf<MotionWarpingTarget>()
    var distanceMatching: Boolean = true
    var distanceRemaining: Float = 0f

    fun addClip(clip: MotionClip) { clips.add(clip) }

    fun createLocomotionClips() {
        // Walk
        val walk = MotionClip(name="Walk", duration=1f, frameRate=30f)
        for (i in 0 until 30) {
            val t = i/30f
            walk.poses.add(PoseFeature(
                position=floatArrayOf(sin(t*2*PI.toFloat())*0.5f, 0f, t*2f),
                velocity=floatArrayOf(0f,0f,2f),
                facing=floatArrayOf(0f,0f,1f),
                leftFootPos=floatArrayOf(-0.1f, sin(t*2*PI.toFloat())*0.1f, 0f),
                rightFootPos=floatArrayOf(0.1f, sin(t*2*PI.toFloat()+PI.toFloat())*0.1f, 0f),
                hipHeight=1f
            ))
            walk.rootMotion.add(floatArrayOf(0f,0f,t*2f))
        }
        walk.tags.add("Locomotion"); clips.add(walk)

        // Run
        val run = MotionClip(name="Run", duration=0.6f, frameRate=30f)
        for (i in 0 until 18) {
            val t = i/18f
            run.poses.add(PoseFeature(
                position=floatArrayOf(sin(t*4*PI.toFloat())*0.3f, 0f, t*5f),
                velocity=floatArrayOf(0f,0f,5f),
                facing=floatArrayOf(0f,0f,1f),
                leftFootPos=floatArrayOf(-0.15f, sin(t*4*PI.toFloat())*0.2f, 0f),
                rightFootPos=floatArrayOf(0.15f, sin(t*4*PI.toFloat()+PI.toFloat())*0.2f, 0f),
                hipHeight=1f
            ))
            run.rootMotion.add(floatArrayOf(0f,0f,t*5f))
        }
        run.tags.add("Locomotion"); clips.add(run)

        // Idle
        val idle = MotionClip(name="Idle", duration=2f, frameRate=30f)
        for (i in 0 until 60) {
            idle.poses.add(PoseFeature(
                position=floatArrayOf(0f,0f,0f),
                velocity=floatArrayOf(0f,0f,0f),
                facing=floatArrayOf(0f,0f,1f),
                leftFootPos=floatArrayOf(-0.1f,0f,0f),
                rightFootPos=floatArrayOf(0.1f,0f,0f),
                hipHeight=1f
            ))
            idle.rootMotion.add(floatArrayOf(0f,0f,0f))
        }
        idle.tags.add("Idle"); clips.add(idle)
    }

    fun updateQuery(desiredVelocity: FloatArray, desiredFacing: FloatArray, currentPos: FloatArray, currentVel: FloatArray) {
        queryFeature.velocity = desiredVelocity
        queryFeature.facing = desiredFacing
        queryFeature.position = currentPos
        // Predict trajectory
        for (i in trajectoryPrediction.indices) {
            val futureT = (i+1)*0.2f
            trajectoryPrediction[i] = floatArrayOf(
                currentPos[0]+desiredVelocity[0]*futureT,
                currentPos[1]+desiredVelocity[1]*futureT,
                currentPos[2]+desiredVelocity[2]*futureT
            )
        }
        queryFeature.trajectory = trajectoryPrediction
    }

    fun searchBestMatch(): Pair<MotionClip, Float>? {
        if (clips.isEmpty()) return null
        var bestDist = Float.MAX_VALUE
        var bestClip: MotionClip? = null
        var bestTime = 0f
        for (clip in clips) {
            for (i in clip.poses.indices step 3) { // sample every 3 frames for performance
                val pose = clip.poses[i]
                val dist = pose.distanceTo(queryFeature)
                if (dist < bestDist) { bestDist=dist; bestClip=clip; bestTime=i/clip.frameRate }
            }
        }
        return if (bestClip != null) Pair(bestClip, bestTime) else null
    }

    fun update(dt: Float): FloatArray {
        searchTimer+=dt
        if (isMatching && searchTimer >= searchInterval) {
            searchTimer=0f
            val match = searchBestMatch()
            if (match != null) {
                currentClip = match.first
                currentTime = match.second
            }
        }
        currentClip?.let {
            currentTime+=dt
            if (currentTime > it.duration) currentTime %= it.duration
            val pose = it.getPoseAt(currentTime)
            // Apply warping if any
            var warpedPos = pose.position
            for (target in warpingTargets) {
                if (currentTime >= target.warpStartTime && currentTime <= target.warpEndTime) {
                    val warpT = (currentTime-target.warpStartTime)/(target.warpEndTime-target.warpStartTime)
                    warpedPos = floatArrayOf(
                        warpedPos[0]+(target.x-warpedPos[0])*warpT,
                        warpedPos[1]+(target.y-warpedPos[1])*warpT,
                        warpedPos[2]+(target.z-warpedPos[2])*warpT
                    )
                }
            }
            return warpedPos
        }
        return floatArrayOf(0f,0f,0f)
    }

    fun addWarpingTarget(x: Float, y: Float, z: Float, rotY: Float, startTime: Float, endTime: Float): MotionWarpingTarget {
        val target = MotionWarpingTarget(x=x,y=y,z=z,rotY=rotY, warpStartTime=startTime, warpEndTime=endTime)
        warpingTargets.add(target); return target
    }

    fun getStats(): String = "MotionMatching Clips:${clips.size} Current:${currentClip?.name} Time:${"%.2f".format(currentTime)} Warps:${warpingTargets.size}"
}
