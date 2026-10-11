package com.sengine.engine.physics

import com.sengine.engine.core.Scene
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.math.Mat4
import kotlin.math.sqrt
import kotlin.math.max

/**
 * Ultimate Edition Physics Material & Advanced Collision System
 * PhysX / Chaos like materials, CCD, layers, triggers, complex collisions
 */

data class ContactPoint(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var normalX: Float = 0f,
    var normalY: Float = 1f,
    var normalZ: Float = 0f,
    var separation: Float = 0f,
    var impulse: Float = 0f
)

data class CollisionInfo(
    val goA: GameObject,
    val goB: GameObject,
    var contacts: List<ContactPoint> = emptyList(),
    var relativeVelocity: FloatArray = floatArrayOf(0f, 0f, 0f),
    var impulse: FloatArray = floatArrayOf(0f, 0f, 0f)
)

class PhysicsMaterialManager {
    private val materials = mutableMapOf<String, com.sengine.engine.physics.advanced.PhysicsMaterial>()

    init {
        materials["Default"] = com.sengine.engine.physics.advanced.PhysicsMaterial("Default", 0.6f, 0.6f, 0f)
        materials["Ice"] = com.sengine.engine.physics.advanced.PhysicsMaterial("Ice", 0.05f, 0.05f, 0f)
        materials["Rubber"] = com.sengine.engine.physics.advanced.PhysicsMaterial("Rubber", 0.8f, 0.8f, 0.8f)
        materials["Metal"] = com.sengine.engine.physics.advanced.PhysicsMaterial("Metal", 0.2f, 0.2f, 0.1f)
        materials["Wood"] = com.sengine.engine.physics.advanced.PhysicsMaterial("Wood", 0.4f, 0.5f, 0.2f)
        materials["Bouncy"] = com.sengine.engine.physics.advanced.PhysicsMaterial("Bouncy", 0.3f, 0.3f, 1f)
    }

    fun get(name: String): com.sengine.engine.physics.advanced.PhysicsMaterial = materials[name] ?: materials["Default"]!!
    fun register(material: com.sengine.engine.physics.advanced.PhysicsMaterial) { materials[material.name] = material }
    fun all(): List<com.sengine.engine.physics.advanced.PhysicsMaterial> = materials.values.toList()
}

class ContinuousCollisionDetection {
    data class SweepResult(var hit: Boolean = false, var time: Float = 1f, var point: FloatArray = floatArrayOf(0f, 0f, 0f), var normal: FloatArray = floatArrayOf(0f, 1f, 0f), var go: GameObject? = null)

    fun sweepSphere(scene: Scene, start: FloatArray, end: FloatArray, radius: Float, ignoreId: Long = -1): SweepResult {
        val result = SweepResult()
        val dir = floatArrayOf(end[0] - start[0], end[1] - start[1], end[2] - start[2])
        val dist = sqrt((dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]).toDouble()).toFloat()
        if (dist < 0.001f) return result

        val normDir = floatArrayOf(dir[0] / dist, dir[1] / dist, dir[2] / dist)

        var closestT = Float.MAX_VALUE
        var closestGo: GameObject? = null
        var closestPoint = floatArrayOf(0f, 0f, 0f)
        var closestNormal = floatArrayOf(0f, 1f, 0f)

        for (go in scene.objects) {
            if (!go.isActiveInHierarchy() || go.id == ignoreId) continue
            val col = go.get<Collider3D>() ?: continue
            val w = go.world3
            val cx = w[12] + col.centerX
            val cy = w[13] + col.centerY
            val cz = w[14] + col.centerZ

            if (col.shape == 1) { // sphere
                val r = col.radius + radius
                // Ray-sphere intersection
                val ocX = start[0] - cx; val ocY = start[1] - cy; val ocZ = start[2] - cz
                val b = ocX * normDir[0] + ocY * normDir[1] + ocZ * normDir[2]
                val c = ocX * ocX + ocY * ocY + ocZ * ocZ - r * r
                val disc = b * b - c
                if (disc >= 0f) {
                    val t = -b - sqrt(disc.toDouble()).toFloat()
                    if (t >= 0f && t <= dist && t < closestT) {
                        closestT = t
                        closestGo = go
                        closestPoint = floatArrayOf(start[0] + normDir[0] * t, start[1] + normDir[1] * t, start[2] + normDir[2] * t)
                        val nx = closestPoint[0] - cx; val ny = closestPoint[1] - cy; val nz = closestPoint[2] - cz
                        val len = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat().coerceAtLeast(0.001f)
                        closestNormal = floatArrayOf(nx / len, ny / len, nz / len)
                    }
                }
            } else { // box - simplified as sphere for CCD
                val hx = col.sizeX / 2 + radius; val hy = col.sizeY / 2 + radius; val hz = col.sizeZ / 2 + radius
                // AABB ray test
                val t = com.sengine.engine.physics.PhysicsWorld3D.rayAabb(start[0], start[1], start[2], normDir[0], normDir[1], normDir[2], cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz)
                if (t != null && t >= 0f && t <= dist && t < closestT) {
                    closestT = t
                    closestGo = go
                    closestPoint = floatArrayOf(start[0] + normDir[0] * t, start[1] + normDir[1] * t, start[2] + normDir[2] * t)
                    // approximate normal by closest face
                    closestNormal = floatArrayOf(0f, 1f, 0f)
                }
            }
        }

        if (closestGo != null) {
            result.hit = true
            result.time = closestT / dist
            result.point = closestPoint
            result.normal = closestNormal
            result.go = closestGo
        }
        return result
    }
}

class RagdollSystem {
    data class RagdollBone(var go: GameObject, var collider: Collider3D, var rigidbody: Rigidbody3D, var joint: com.sengine.engine.physics.advanced.Joint? = null)

    private val ragdolls = mutableMapOf<Long, List<RagdollBone>>()

    fun createRagdoll(root: GameObject, scene: Scene): List<RagdollBone> {
        val bones = mutableListOf<RagdollBone>()
        // Find all child objects and create physics for them
        fun processBone(go: GameObject, parent: GameObject?) {
            var col = go.get<Collider3D>()
            var rb = go.get<Rigidbody3D>()
            if (col == null) {
                col = Collider3D().also { it.shape = 1; it.radius = 0.3f }
                go.add(col)
            }
            if (rb == null) {
                rb = Rigidbody3D().also { it.mass = 1f }
                go.add(rb)
            }
            val bone = RagdollBone(go, col, rb)
            if (parent != null) {
                val joint = com.sengine.engine.physics.advanced.Joint(com.sengine.engine.physics.advanced.JointType.BallSocket, parent)
                bone.joint = joint
            }
            bones.add(bone)
            for (child in scene.childrenOf(go)) processBone(child, go)
        }
        processBone(root, null)
        ragdolls[root.id] = bones
        return bones
    }

    fun enableRagdoll(rootId: Long, enable: Boolean) {
        val bones = ragdolls[rootId] ?: return
        for (bone in bones) {
            bone.rigidbody.let { /* enable physics */ }
            bone.collider.let { /* enable collider */ }
        }
    }
}
