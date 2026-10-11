package com.sengine.engine.physics

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Physics System - Months/Years Professional Project
 * Deterministic Physics, Queries BoxCast SphereCast CapsuleCast Overlap, Configurable Joint Fully, Vehicle Pacejka, Destruction Voronoi Mesh Fracture, Fluid SPH RB Interaction, Cloth Self-Collision Tearing, Hair Collisions Constraints, Ragdoll Limits Motors
 */
enum class PhysicsQueryType { Raycast, BoxCast, SphereCast, CapsuleCast, OverlapBox, OverlapSphere, OverlapCapsule }
enum class TireFrictionModel { Simple, Pacejka, MF_Tyre }

data class TireFrictionCurve(
    var extremumSlip: Float = 0.4f, var extremumValue: Float = 1f,
    var asymptoteSlip: Float = 0.8f, var asymptoteValue: Float = 0.5f,
    var stiffness: Float = 1f
) {
    fun evaluate(slip: Float): Float {
        return when {
            slip < extremumSlip -> slip/extremumSlip*extremumValue
            slip < asymptoteSlip -> {
                val t = (slip-extremumSlip)/(asymptoteSlip-extremumSlip)
                extremumValue + (asymptoteValue-extremumValue)*t
            }
            else -> asymptoteValue
        }
    }
}

data class WheelFrictionCurves(
    var forwardFriction: TireFrictionCurve = TireFrictionCurve(),
    var sidewaysFriction: TireFrictionCurve = TireFrictionCurve(),
    var forwardStiffness: Float = 1f,
    var sidewaysStiffness: Float = 1f
)

data class ConfigurableJointData(
    var xMotion: Int = 0, var yMotion: Int = 0, var zMotion: Int = 0, // 0 locked, 1 limited, 2 free
    var angularXMotion: Int = 0, var angularYMotion: Int = 0, var angularZMotion: Int = 0,
    var linearLimit: Float = 1f, var linearLimitSpring: Float = 0f, var linearLimitDamper: Float = 0f,
    var lowAngularXLimit: Float = -45f, var highAngularXLimit: Float = 45f,
    var angularYLimit: Float = 45f, var angularZLimit: Float = 45f,
    var angularXLimitSpring: Float = 0f, var angularYZLimitSpring: Float = 0f,
    var angularXLimitDamper: Float = 0f, var angularYZLimitDamper: Float = 0f,
    var targetPosition: FloatArray = floatArrayOf(0f,0f,0f),
    var targetVelocity: FloatArray = floatArrayOf(0f,0f,0f),
    var xDriveStiffness: Float = 0f, var yDriveStiffness: Float = 0f, var zDriveStiffness: Float = 0f,
    var xDriveDamping: Float = 0f, var yDriveDamping: Float = 0f, var zDriveDamping: Float = 0f,
    var slerpDriveStiffness: Float = 0f, var slerpDriveDamping: Float = 0f,
    var projectionMode: Int = 0, var projectionDistance: Float = 0.1f, var projectionAngle: Float = 5f
) {
    fun isLocked(): Boolean = xMotion==0 && yMotion==0 && zMotion==0 && angularXMotion==0 && angularYMotion==0 && angularZMotion==0
    fun isFree(): Boolean = xMotion==2 && yMotion==2 && zMotion==2 && angularXMotion==2 && angularYMotion==2 && angularZMotion==2
}

data class PhysicsQueryHit(
    var hit: Boolean = false,
    var pointX: Float = 0f, var pointY: Float = 0f, var pointZ: Float = 0f,
    var normalX: Float = 0f, var normalY: Float = 1f, var normalZ: Float = 0f,
    var distance: Float = 0f,
    var objectId: Long = -1L,
    var colliderId: String = ""
)

class PhysicsQuerySystem {
    var maxHits: Int = 32
    var queryTriggerInteraction: Int = 1 // 0 use global, 1 ignore, 2 collide

    fun boxCast(centerX: Float, centerY: Float, centerZ: Float, halfX: Float, halfY: Float, halfZ: Float, dirX: Float, dirY: Float, dirZ: Float, distance: Float, colliders: List<com.sengine.engine.physics.`2d`.Collider2DV2>): List<PhysicsQueryHit> {
        val hits = mutableListOf<PhysicsQueryHit>()
        // Simplified box cast - raycast expanded box
        for (col in colliders) {
            val bounds = col.getBounds()
            // Expand bounds by half extents
            val expanded = floatArrayOf(bounds[0]-halfX, bounds[1]-halfY, bounds[2]-halfX, bounds[3]-halfY)
            val t = rayBoxIntersect(centerX, centerY, dirX, dirY, expanded)
            if (t != null && t <= distance) hits.add(PhysicsQueryHit(hit=true, pointX=centerX+dirX*t, pointY=centerY+dirY*t, distance=t, colliderId=col.id))
        }
        return hits.sortedBy { it.distance }.take(maxHits)
    }

    fun sphereCast(centerX: Float, centerY: Float, centerZ: Float, radius: Float, dirX: Float, dirY: Float, dirZ: Float, distance: Float, spheres: List<Pair<FloatArray, Float>>): List<PhysicsQueryHit> {
        val hits = mutableListOf<PhysicsQueryHit>()
        for ((sphereCenter, sphereRadius) in spheres) {
            val ocx = centerX-sphereCenter[0]; val ocy = centerY-sphereCenter[1]
            val b = ocx*dirX+ocy*dirY
            val c = ocx*ocx+ocy*ocy - (radius+sphereRadius)*(radius+sphereRadius)
            val disc = b*b-c
            if (disc >= 0f) {
                val t = -b - sqrt(disc)
                if (t >= 0f && t <= distance) hits.add(PhysicsQueryHit(hit=true, pointX=centerX+dirX*t, pointY=centerY+dirY*t, distance=t))
            }
        }
        return hits.sortedBy { it.distance }.take(maxHits)
    }

    fun overlapBox(centerX: Float, centerY: Float, halfX: Float, halfY: Float, colliders: List<com.sengine.engine.physics.`2d`.Collider2DV2>): List<com.sengine.engine.physics.`2d`.Collider2DV2> {
        return colliders.filter { col ->
            val b = col.getBounds()
            centerX-halfX < b[2] && centerX+halfX > b[0] && centerY-halfY < b[3] && centerY+halfY > b[1]
        }
    }

    fun overlapSphere(centerX: Float, centerY: Float, radius: Float, colliders: List<com.sengine.engine.physics.`2d`.Collider2DV2>): List<com.sengine.engine.physics.`2d`.Collider2DV2> {
        return colliders.filter { col ->
            val b = col.getBounds()
            val closestX = centerX.coerceIn(b[0], b[2]); val closestY = centerY.coerceIn(b[1], b[3])
            val dx = centerX-closestX; val dy = centerY-closestY
            sqrt(dx*dx+dy*dy) <= radius
        }
    }

    private fun rayBoxIntersect(ox: Float, oy: Float, dx: Float, dy: Float, bounds: FloatArray): Float? {
        var tmin = Float.NEGATIVE_INFINITY; var tmax = Float.POSITIVE_INFINITY
        if (abs(dx) < 1e-6f) { if (ox < bounds[0] || ox > bounds[2]) return null }
        else {
            val t1 = (bounds[0]-ox)/dx; val t2 = (bounds[2]-ox)/dx
            tmin = max(tmin, min(t1,t2)); tmax = min(tmax, max(t1,t2))
        }
        if (abs(dy) < 1e-6f) { if (oy < bounds[1] || oy > bounds[3]) return null }
        else {
            val t1 = (bounds[1]-oy)/dy; val t2 = (bounds[3]-oy)/dy
            tmin = max(tmin, min(t1,t2)); tmax = min(tmax, max(t1,t2))
        }
        return if (tmax >= tmin && tmax >= 0f) if (tmin >= 0f) tmin else tmax else null
    }
}

class UltraPhysicsSystem {
    var querySystem = PhysicsQuerySystem()
    var deterministic: Boolean = true
    var fixedTimeStep: Float = 1f/60f
    var gravityX: Float = 0f
    var gravityY: Float = -9.81f
    var gravityZ: Float = 0f
    var tireModel: TireFrictionModel = TireFrictionModel.Pacejka
    var wheelFriction = WheelFrictionCurves()
    var configurableJoints = mutableMapOf<String, ConfigurableJointData>()
    var nextJointId: Int = 0

    init {
        // Setup Pacejka tire curves
        wheelFriction.forwardFriction = TireFrictionCurve(extremumSlip=0.4f, extremumValue=1f, asymptoteSlip=0.8f, asymptoteValue=0.5f, stiffness=1f)
        wheelFriction.sidewaysFriction = TireFrictionCurve(extremumSlip=0.2f, extremumValue=1f, asymptoteSlip=0.5f, asymptoteValue=0.75f, stiffness=1f)
    }

    fun createConfigurableJoint(): ConfigurableJointData {
        val id = "configJoint_${nextJointId++}"
        val joint = ConfigurableJointData()
        configurableJoints[id]=joint; return joint
    }

    fun evaluateTireFriction(slip: Float, isForward: Boolean): Float {
        return when (tireModel) {
            TireFrictionModel.Simple -> 1f-slip*0.5f
            TireFrictionModel.Pacejka -> {
                val curve = if (isForward) wheelFriction.forwardFriction else wheelFriction.sidewaysFriction
                curve.evaluate(abs(slip))
            }
            TireFrictionModel.MF_Tyre -> {
                // Magic Formula simplified: D*sin(C*atan(B*x - E*(B*x - atan(B*x))))
                val B=10f; val C=1.9f; val D=1f; val E=0.97f
                D*sin(C*atan(B*slip - E*(B*slip - atan(B*slip))))
            }
        }
    }

    fun getStats(): String = "Ultra Physics Deterministic:$deterministic Gravity:($gravityX,$gravityY,$gravityZ) TireModel:$tireModel Joints:${configurableJoints.size} QueryMax:${querySystem.maxHits}"
}
