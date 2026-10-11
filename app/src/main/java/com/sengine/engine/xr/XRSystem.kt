package com.sengine.engine.xr

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - XR System (VR/AR/MR)
 * OpenXR, Hand Tracking, Eye Tracking, Passthrough, Spatial Anchors
 */
enum class XRMode { None, VR, AR, MR }
enum class XRHand { Left, Right }
enum class XRHandJoint { Palm, Wrist, ThumbMetacarpal, ThumbProximal, ThumbDistal, ThumbTip, IndexMetacarpal, IndexProximal, IndexIntermediate, IndexDistal, IndexTip, MiddleMetacarpal, MiddleProximal, MiddleIntermediate, MiddleDistal, MiddleTip, RingMetacarpal, RingProximal, RingIntermediate, RingDistal, RingTip, LittleMetacarpal, LittleProximal, LittleIntermediate, LittleDistal, LittleTip }

data class XRPose(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var qx: Float = 0f, var qy: Float = 0f, var qz: Float = 0f, var qw: Float = 1f,
    var isValid: Boolean = true
) {
    fun position(): FloatArray = floatArrayOf(x,y,z)
    fun rotation(): FloatArray = floatArrayOf(qx,qy,qz,qw)
}

data class XRHandTracking(
    var hand: XRHand = XRHand.Left,
    var isTracked: Boolean = false,
    var joints: MutableMap<XRHandJoint, XRPose> = mutableMapOf(),
    var pinchStrength: Float = 0f,
    var gripStrength: Float = 0f,
    var isPinching: Boolean = false,
    var isGripping: Boolean = false
) {
    fun getJoint(joint: XRHandJoint): XRPose? = joints[joint]
    fun getTipPosition(finger: String): FloatArray {
        val joint = when (finger) {
            "thumb" -> XRHandJoint.ThumbTip
            "index" -> XRHandJoint.IndexTip
            "middle" -> XRHandJoint.MiddleTip
            else -> XRHandJoint.IndexTip
        }
        return joints[joint]?.position() ?: floatArrayOf(0f,0f,0f)
    }
}

data class XREyeGaze(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var dirX: Float = 0f, var dirY: Float = 0f, var dirZ: Float = -1f,
    var isValid: Boolean = false,
    var confidence: Float = 0f
)

data class XRSpatialAnchor(
    var id: String = "anchor",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var qx: Float = 0f, var qy: Float = 0f, var qz: Float = 0f, var qw: Float = 1f,
    var isPersisted: Boolean = false
)

class XRSystem {
    var mode: XRMode = XRMode.None
    var enabled: Boolean = false
    var headPose = XRPose(x=0f, y=1.6f, z=0f)
    var leftHand = XRHandTracking(hand=XRHand.Left)
    var rightHand = XRHandTracking(hand=XRHand.Right)
    var eyeGaze = XREyeGaze()
    var leftEye = XRPose(x=-0.03f, y=1.6f, z=0f)
    var rightEye = XRPose(x=0.03f, y=1.6f, z=0f)
    var ipd: Float = 0.064f
    var fov: Float = 90f
    var refreshRate: Float = 90f
    var passthroughEnabled: Boolean = false
    var anchors = mutableMapOf<String, XRSpatialAnchor>()
    var isInitialized: Boolean = false
    var frameCount: Long = 0L

    fun initialize(mode: XRMode = XRMode.VR): Boolean {
        this.mode = mode; enabled = mode != XRMode.None
        isInitialized = enabled
        // Init hand joints
        for (joint in XRHandJoint.values()) {
            leftHand.joints[joint] = XRPose()
            rightHand.joints[joint] = XRPose()
        }
        return isInitialized
    }

    fun updateHeadPose(x: Float, y: Float, z: Float, qx: Float, qy: Float, qz: Float, qw: Float) {
        headPose = XRPose(x,y,z,qx,qy,qz,qw,true)
        leftEye = XRPose(x-ipd*0.5f, y, z, qx,qy,qz,qw,true)
        rightEye = XRPose(x+ipd*0.5f, y, z, qx,qy,qz,qw,true)
    }

    fun updateHandTracking(hand: XRHand, isTracked: Boolean, palmX: Float, palmY: Float, palmZ: Float) {
        val tracking = if (hand == XRHand.Left) leftHand else rightHand
        tracking.isTracked = isTracked
        tracking.joints[XRHandJoint.Palm] = XRPose(palmX, palmY, palmZ, 0f,0f,0f,1f, isTracked)
        // Simulate finger joints
        for (joint in XRHandJoint.values()) {
            if (joint == XRHandJoint.Palm) continue
            val offset = when (joint) {
                XRHandJoint.IndexTip -> floatArrayOf(0f,0f,0.1f)
                XRHandJoint.ThumbTip -> floatArrayOf(0.05f,0f,0.05f)
                else -> floatArrayOf(0f,0f,0.05f)
            }
            tracking.joints[joint] = XRPose(palmX+offset[0], palmY+offset[1], palmZ+offset[2], 0f,0f,0f,1f, isTracked)
        }
    }

    fun createAnchor(id: String, x: Float, y: Float, z: Float): XRSpatialAnchor {
        val anchor = XRSpatialAnchor(id=id, x=x, y=y, z=z); anchors[id]=anchor; return anchor
    }

    fun getAnchor(id: String): XRSpatialAnchor? = anchors[id]

    fun raycastFromHand(hand: XRHand, maxDistance: Float = 10f): FloatArray? {
        val tracking = if (hand == XRHand.Left) leftHand else rightHand
        if (!tracking.isTracked) return null
        val palm = tracking.joints[XRHandJoint.Palm] ?: return null
        val tip = tracking.joints[XRHandJoint.IndexTip] ?: return null
        val dirX = tip.x-palm.x; val dirY = tip.y-palm.y; val dirZ = tip.z-palm.z
        val len = sqrt(dirX*dirX+dirY*dirY+dirZ*dirZ).coerceAtLeast(0.0001f)
        return floatArrayOf(palm.x, palm.y, palm.z, dirX/len, dirY/len, dirZ/len, maxDistance)
    }

    fun getStats(): String = "XR Mode:$mode Initialized:$isInitialized Head:(${headPose.x},${headPose.y},${headPose.z}) Hands:${leftHand.isTracked}/${rightHand.isTracked} Anchors:${anchors.size} FPS:$refreshRate"
}
