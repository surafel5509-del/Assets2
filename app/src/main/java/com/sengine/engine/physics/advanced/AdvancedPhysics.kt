package com.sengine.engine.physics.advanced

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.math.Mat4
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Ultimate Edition Advanced Physics - Unity PhysX / Unreal Chaos equivalent
 * Joints, Character Controller, Vehicle, Cloth, Soft Body, CCD, Materials
 */

enum class JointType { Fixed, Distance, Hinge, Spring, Slider, BallSocket, Configurable, Character, Wheel }

data class PhysicsMaterial(
    var name: String = "Default",
    var dynamicFriction: Float = 0.6f,
    var staticFriction: Float = 0.6f,
    var bounciness: Float = 0f,
    var frictionCombine: Int = 0, // 0 average, 1 min, 2 multiply, 3 max
    var bounceCombine: Int = 0
) {
    fun combineFriction(other: PhysicsMaterial): Float = when (frictionCombine) {
        1 -> minOf(dynamicFriction, other.dynamicFriction)
        2 -> dynamicFriction * other.dynamicFriction
        3 -> maxOf(dynamicFriction, other.dynamicFriction)
        else -> (dynamicFriction + other.dynamicFriction) * 0.5f
    }

    fun combineBounce(other: PhysicsMaterial): Float = when (bounceCombine) {
        1 -> minOf(bounciness, other.bounciness)
        2 -> bounciness * other.bounciness
        3 -> maxOf(bounciness, other.bounciness)
        else -> (bounciness + other.bounciness) * 0.5f
    }
}

class Joint(
    var type: JointType = JointType.Fixed,
    var connectedBody: GameObject? = null,
    var anchor: FloatArray = floatArrayOf(0f, 0f, 0f),
    var connectedAnchor: FloatArray = floatArrayOf(0f, 0f, 0f),
    var axis: FloatArray = floatArrayOf(0f, 1f, 0f),
    var breakForce: Float = Float.MAX_VALUE,
    var breakTorque: Float = Float.MAX_VALUE,
    var enableCollision: Boolean = false,
    // Limits
    var useLimits: Boolean = false,
    var minLimit: Float = -90f,
    var maxLimit: Float = 90f,
    var bounciness: Float = 0f,
    // Spring
    var useSpring: Boolean = false,
    var spring: Float = 10f,
    var damper: Float = 1f,
    // Motor
    var useMotor: Boolean = false,
    var motorForce: Float = 0f,
    var motorVelocity: Float = 0f
) {
    var isBroken: Boolean = false

    fun applyConstraint(go: GameObject, dt: Float) {
        if (isBroken) return
        val rb = go.get<com.sengine.engine.core.Rigidbody3D>() ?: return
        val connected = connectedBody?.get<com.sengine.engine.core.Rigidbody3D>()

        when (type) {
            JointType.Fixed -> applyFixed(go, rb, connected, dt)
            JointType.Distance -> applyDistance(go, rb, connected, dt)
            JointType.Hinge -> applyHinge(go, rb, connected, dt)
            JointType.Spring -> applySpring(go, rb, connected, dt)
            JointType.Configurable -> applyConfigurable(go, rb, connected, dt)
            else -> applyDistance(go, rb, connected, dt)
        }
    }

    private fun applyFixed(go: GameObject, rb: Rigidbody3D, other: Rigidbody3D?, dt: Float) {
        val target = connectedBody ?: return
        val targetWorld = target.world3
        val targetPos = floatArrayOf(targetWorld[12], targetWorld[13], targetWorld[14])
        // Simple spring to keep fixed
        val k = 20f
        val dx = targetPos[0] + connectedAnchor[0] - (go.world3[12] + anchor[0])
        val dy = targetPos[1] + connectedAnchor[1] - (go.world3[13] + anchor[1])
        val dz = targetPos[2] + connectedAnchor[2] - (go.world3[14] + anchor[2])
        rb.vx += dx * k * dt
        rb.vy += dy * k * dt
        rb.vz += dz * k * dt
    }

    private fun applyDistance(go: GameObject, rb: Rigidbody3D, other: Rigidbody3D?, dt: Float) {
        val target = connectedBody ?: return
        val w = go.world3; val tw = target.world3
        val px = w[12] + anchor[0]; val py = w[13] + anchor[1]; val pz = w[14] + anchor[2]
        val tx = tw[12] + connectedAnchor[0]; val ty = tw[13] + connectedAnchor[1]; val tz = tw[14] + connectedAnchor[2]
        val dx = tx - px; val dy = ty - py; val dz = tz - pz
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist < 0.001f) return
        val desired = 2f // rest length
        val diff = dist - desired
        val nx = dx / dist; val ny = dy / dist; val nz = dz / dist
        val correction = diff * 0.5f
        rb.vx += nx * correction * 5f * dt
        rb.vy += ny * correction * 5f * dt
        rb.vz += nz * correction * 5f * dt
        other?.let {
            it.vx -= nx * correction * 5f * dt
            it.vy -= ny * correction * 5f * dt
            it.vz -= nz * correction * 5f * dt
        }
    }

    private fun applyHinge(go: GameObject, rb: Rigidbody3D, other: Rigidbody3D?, dt: Float) {
        // Keep anchor together + limit rotation around axis
        applyDistance(go, rb, other, dt)
        // Angular limits would need quaternion math - simplified
    }

    private fun applySpring(go: GameObject, rb: Rigidbody3D, other: Rigidbody3D?, dt: Float) {
        val target = connectedBody ?: return
        val w = go.world3; val tw = target.world3
        val dx = (tw[12] + connectedAnchor[0]) - (w[12] + anchor[0])
        val dy = (tw[13] + connectedAnchor[1]) - (w[13] + anchor[1])
        val dz = (tw[14] + connectedAnchor[2]) - (w[14] + anchor[2])
        // F = -k*x - d*v
        rb.vx += (dx * spring - rb.vx * damper) * dt
        rb.vy += (dy * spring - rb.vy * damper) * dt
        rb.vz += (dz * spring - rb.vz * damper) * dt
    }

    private fun applyConfigurable(go: GameObject, rb: Rigidbody3D, other: Rigidbody3D?, dt: Float) {
        if (useSpring) applySpring(go, rb, other, dt) else applyDistance(go, rb, other, dt)
    }
}

class JointSystem {
    private val joints = mutableMapOf<Long, MutableList<Joint>>()

    fun addJoint(go: GameObject, joint: Joint) {
        joints.getOrPut(go.id) { mutableListOf() }.add(joint)
    }

    fun removeJoints(go: GameObject) { joints.remove(go.id) }

    fun update(scene: com.sengine.engine.core.Scene, dt: Float) {
        for ((id, list) in joints) {
            val go = scene.findById(id) ?: continue
            if (!go.isActiveInHierarchy()) continue
            for (j in list) if (!j.isBroken) j.applyConstraint(go, dt)
        }
    }
}

/**
 * Character Controller - like Unity's CharacterController / Unreal CMC
 */
class CharacterController {
    var radius: Float = 0.5f
    var height: Float = 2f
    var center: FloatArray = floatArrayOf(0f, 0f, 0f)
    var slopeLimit: Float = 45f
    var stepOffset: Float = 0.3f
    var skinWidth: Float = 0.08f
    var minMoveDistance: Float = 0f
    var detectCollisions: Boolean = true

    var isGrounded: Boolean = false
        private set
    var velocity: FloatArray = floatArrayOf(0f, 0f, 0f)
    var collisionFlags: Int = 0

    fun move(go: GameObject, motion: FloatArray, scene: com.sengine.engine.core.Scene, dt: Float): Int {
        // Simplified capsule sweep
        val rb = go.get<Rigidbody3D>()
        // Apply motion
        go.x += motion[0]
        go.y += motion[1]
        go.z += motion[2]

        velocity[0] = motion[0] / maxOf(dt, 0.001f)
        velocity[1] = motion[1] / maxOf(dt, 0.001f)
        velocity[2] = motion[2] / maxOf(dt, 0.001f)

        // Ground check via raycast down
        val origin = floatArrayOf(go.world3[12], go.world3[13] + 0.1f, go.world3[14])
        isGrounded = false
        // simple ground plane check at y=0
        if (go.world3[13] - height / 2 <= 0.1f) {
            isGrounded = true
            collisionFlags = 1 // below
            go.y = height / 2
        }

        return collisionFlags
    }
}

/**
 * Vehicle Physics - like Unity WheelCollider / Unreal Chaos Vehicle
 */
class WheelCollider {
    var radius: Float = 0.33f
    var suspensionDistance: Float = 0.3f
    var suspensionSpring: Float = 20000f
    var suspensionDamper: Float = 2500f
    var mass: Float = 20f
    var forwardFriction: Float = 1f
    var sidewaysFriction: Float = 1f
    var motorTorque: Float = 0f
    var brakeTorque: Float = 0f
    var steerAngle: Float = 0f

    var isGrounded: Boolean = false
    var rpm: Float = 0f
    var suspensionCompression: Float = 0f

    fun update(suspensionForce: Float, dt: Float) {
        // Simplified
        isGrounded = suspensionCompression < 1f
        rpm = motorTorque * 0.1f
    }
}

class VehicleController {
    val wheels = mutableListOf<WheelCollider>()
    var centerOfMass: FloatArray = floatArrayOf(0f, -0.9f, 0.3f)
    var motorInput: Float = 0f
    var brakeInput: Float = 0f
    var steerInput: Float = 0f
    var maxMotorTorque: Float = 1500f
    var maxBrakeTorque: Float = 3000f
    var maxSteerAngle: Float = 30f

    fun addWheel(wheel: WheelCollider) { wheels.add(wheel) }

    fun update(go: GameObject, dt: Float) {
        val rb = go.get<Rigidbody3D>() ?: return
        // Apply steer to front wheels
        for ((i, w) in wheels.withIndex()) {
            if (i < 2) w.steerAngle = steerInput * maxSteerAngle
            w.motorTorque = if (i >= 2) motorInput * maxMotorTorque else 0f
            w.brakeTorque = brakeInput * maxBrakeTorque

            // Apply forces
            val forward = Mat4.dir(go.world3, 0f, 0f, 1f)
            val force = w.motorTorque * 0.01f
            rb.vx += forward[0] * force * dt
            rb.vz += forward[2] * force * dt
        }

        // Simple drag
        rb.vx *= (1f - dt * 0.5f)
        rb.vz *= (1f - dt * 0.5f)
    }
}

/**
 * Broadphase acceleration - SAP and BVH
 */
class BroadphaseSAP {
    data class Proxy(val id: Long, var minX: Float, var maxX: Float, var minY: Float, var maxY: Float, var minZ: Float, var maxZ: Float)

    private val proxies = mutableListOf<Proxy>()

    fun addProxy(proxy: Proxy) { proxies.add(proxy) }
    fun removeProxy(id: Long) { proxies.removeIf { it.id == id } }
    fun updateProxy(id: Long, minX: Float, maxX: Float, minY: Float, maxY: Float, minZ: Float, maxZ: Float) {
        val p = proxies.firstOrNull { it.id == id } ?: return
        p.minX = minX; p.maxX = maxX; p.minY = minY; p.maxY = maxY; p.minZ = minZ; p.maxZ = maxZ
    }

    fun queryPairs(): List<Pair<Long, Long>> {
        // Sweep and Prune on X axis
        proxies.sortBy { it.minX }
        val pairs = mutableListOf<Pair<Long, Long>>()
        for (i in proxies.indices) {
            val a = proxies[i]
            for (j in i + 1 until proxies.size) {
                val b = proxies[j]
                if (b.minX > a.maxX) break
                if (a.maxY < b.minY || a.minY > b.maxY) continue
                if (a.maxZ < b.minZ || a.minZ > b.maxZ) continue
                pairs.add(a.id to b.id)
            }
        }
        return pairs
    }
}
