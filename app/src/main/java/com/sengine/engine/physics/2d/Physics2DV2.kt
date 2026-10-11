package com.sengine.engine.physics.`2d`

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Unity 2D Physics System V2
 * Full 2D Physics: Colliders, Joints, Materials, Rigidbody2D, Composite
 */
enum class Rigidbody2DType { Dynamic, Kinematic, Static }
enum class Collider2DType { Box, Circle, Polygon, Edge, Capsule, Composite }
enum class Joint2DType { Distance, Hinge, Spring, Slider, Wheel, Fixed, Friction, Relative, Target }
enum class CollisionDetectionMode2D { Discrete, Continuous }
enum class RigidbodySleepMode2D { NeverSleep, StartAwake, StartAsleep }

data class PhysicsMaterial2D(
    var name: String = "Material2D",
    var friction: Float = 0.4f,
    var bounciness: Float = 0f
)

data class Collider2DV2(
    var id: String = "collider_0",
    var type: Collider2DType = Collider2DType.Box,
    var x: Float = 0f, var y: Float = 0f,
    var width: Float = 1f, var height: Float = 1f,
    var radius: Float = 0.5f,
    var points: MutableList<FloatArray> = mutableListOf(), // for polygon/edge
    var offsetX: Float = 0f, var offsetY: Float = 0f,
    var isTrigger: Boolean = false,
    var usedByComposite: Boolean = false,
    var usedByEffector: Boolean = false,
    var density: Float = 1f,
    var friction: Float = 0.4f,
    var bounciness: Float = 0f,
    var compositeOperation: Int = 0, // 0 merge, 1 difference
    var geometryType: Int = 0, // 0 outlines, 1 polygons
    var edgeRadius: Float = 0f
) {
    fun getBounds(): FloatArray = when (type) {
        Collider2DType.Box -> floatArrayOf(x-width*0.5f+offsetX, y-height*0.5f+offsetY, x+width*0.5f+offsetX, y+height*0.5f+offsetY)
        Collider2DType.Circle -> floatArrayOf(x-radius+offsetX, y-radius+offsetY, x+radius+offsetX, y+radius+offsetY)
        else -> floatArrayOf(x-width*0.5f, y-height*0.5f, x+width*0.5f, y+height*0.5f)
    }
    fun containsPoint(px: Float, py: Float): Boolean {
        return when (type) {
            Collider2DType.Box -> {
                val b = getBounds(); px >= b[0] && px <= b[2] && py >= b[1] && py <= b[3]
            }
            Collider2DType.Circle -> {
                val dx = px-(x+offsetX); val dy = py-(y+offsetY); sqrt(dx*dx+dy*dy) <= radius
            }
            Collider2DType.Polygon -> pointInPolygon(px, py)
            else -> false
        }
    }
    private fun pointInPolygon(px: Float, py: Float): Boolean {
        if (points.size < 3) return false
        var inside = false
        var j = points.size-1
        for (i in points.indices) {
            val xi = points[i][0]+x+offsetX; val yi = points[i][1]+y+offsetY
            val xj = points[j][0]+x+offsetX; val yj = points[j][1]+y+offsetY
            if ((yi > py) != (yj > py) && px < (xj-xi)*(py-yi)/(yj-yi)+xi) inside = !inside
            j=i
        }
        return inside
    }
}

data class Rigidbody2DV2(
    var id: Long = 0L,
    var type: Rigidbody2DType = Rigidbody2DType.Dynamic,
    var x: Float = 0f, var y: Float = 0f,
    var rotation: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f,
    var angularVelocity: Float = 0f,
    var mass: Float = 1f,
    var drag: Float = 0f,
    var angularDrag: Float = 0.05f,
    var gravityScale: Float = 1f,
    var freezeRotation: Boolean = false,
    var isKinematic: Boolean = false,
    var useAutoMass: Boolean = false,
    var linearDamping: Float = 0f,
    var angularDamping: Float = 0.05f,
    var sleepMode: RigidbodySleepMode2D = RigidbodySleepMode2D.StartAwake,
    var isSleeping: Boolean = false,
    var collisionDetection: CollisionDetectionMode2D = CollisionDetectionMode2D.Discrete,
    var interpolated: Boolean = false,
    var constraints: Int = 0 // bitmask freeze position/rotation
) {
    fun addForce(fx: Float, fy: Float) { if (type == Rigidbody2DType.Dynamic) { vx+=fx/mass; vy+=fy/mass; isSleeping=false } }
    fun addTorque(torque: Float) { if (!freezeRotation) angularVelocity+=torque/mass }
    fun movePosition(nx: Float, ny: Float) { x=nx; y=ny }
    fun moveRotation(angle: Float) { rotation=angle }
    fun sleep() { isSleeping=true; vx=0f; vy=0f; angularVelocity=0f }
    fun wakeUp() { isSleeping=false }
}

data class Joint2DV2(
    var id: String = "joint_0",
    var type: Joint2DType = Joint2DType.Distance,
    var bodyA: Long = -1L, var bodyB: Long = -1L,
    var anchorAX: Float = 0f, var anchorAY: Float = 0f,
    var anchorBX: Float = 0f, var anchorBY: Float = 0f,
    var connectedAnchorX: Float = 0f, var connectedAnchorY: Float = 0f,
    var distance: Float = 1f,
    var frequency: Float = 0f, var dampingRatio: Float = 0f,
    var maxForce: Float = 1000f,
    var maxTorque: Float = 1000f,
    var breakForce: Float = Float.MAX_VALUE,
    var breakTorque: Float = Float.MAX_VALUE,
    var enableCollision: Boolean = false,
    var autoConfigureDistance: Boolean = true,
    var autoConfigureConnectedAnchor: Boolean = true,
    var useLimits: Boolean = false,
    var lowerAngle: Float = -45f, var upperAngle: Float = 45f,
    var useMotor: Boolean = false,
    var motorSpeed: Float = 0f, var motorMaxForce: Float = 1000f,
    var isBroken: Boolean = false
) {
    fun applyConstraint(bodyA: Rigidbody2DV2, bodyB: Rigidbody2DV2, dt: Float): Boolean {
        if (isBroken) return false
        when (type) {
            Joint2DType.Distance -> {
                val dx = (bodyB.x+anchorBX)-(bodyA.x+anchorAX); val dy = (bodyB.y+anchorAY)-(bodyA.y+anchorAY)
                val dist = sqrt(dx*dx+dy*dy).coerceAtLeast(0.0001f)
                val diff = dist-distance
                if (abs(diff) > 0.01f) {
                    val force = diff*frequency.coerceAtLeast(1f)
                    val fx = dx/dist*force*dt; val fy = dy/dist*force*dt
                    bodyA.vx+=fx/bodyA.mass; bodyA.vy+=fy/bodyA.mass
                    bodyB.vx-=fx/bodyB.mass; bodyB.vy-=fy/bodyB.mass
                }
            }
            Joint2DType.Spring -> {
                val dx = (bodyB.x+connectedAnchorX)-(bodyA.x+anchorAX); val dy = (bodyB.y+connectedAnchorY)-(bodyA.y+anchorAY)
                val dist = sqrt(dx*dx+dy*dy).coerceAtLeast(0.0001f)
                val force = (dist-distance)*frequency
                val fx = dx/dist*force*dt; val fy = dy/dist*force*dt
                bodyA.vx+=fx/bodyA.mass; bodyA.vy+=fy/bodyA.mass
                bodyB.vx-=fx/bodyB.mass; bodyB.vy-=fy/bodyB.mass
            }
            Joint2DType.Hinge -> {
                if (useLimits) {
                    val angle = bodyB.rotation-bodyA.rotation
                    if (angle < lowerAngle || angle > upperAngle) {
                        bodyB.angularVelocity*=0.9f
                    }
                }
                if (useMotor) bodyB.angularVelocity+=motorSpeed*dt
            }
            else -> {}
        }
        // Check break
        val force = sqrt(bodyA.vx*bodyA.vx+bodyA.vy*bodyA.vy)*bodyA.mass
        if (force > breakForce) { isBroken=true; return false }
        return true
    }
}

class Physics2DV2 {
    var bodies = mutableMapOf<Long, Rigidbody2DV2>()
    var colliders = mutableMapOf<String, Collider2DV2>()
    var joints = mutableMapOf<String, Joint2DV2>()
    var materials = mutableMapOf<String, PhysicsMaterial2D>()
    var gravityX: Float = 0f
    var gravityY: Float = -9.81f
    var velocityIterations: Int = 8
    var positionIterations: Int = 3
    var autoSimulation: Boolean = true
    var queriesHitTriggers: Boolean = true
    var queriesStartInColliders: Boolean = true
    var changeStopCallback: Boolean = false
    var nextColliderId: Int = 0
    var nextJointId: Int = 0

    fun createBody(id: Long, x: Float, y: Float, type: Rigidbody2DType = Rigidbody2DType.Dynamic): Rigidbody2DV2 {
        val body = Rigidbody2DV2(id=id, x=x, y=y, type=type); bodies[id]=body; return body
    }

    fun createBoxCollider(x: Float, y: Float, width: Float, height: Float, isTrigger: Boolean = false): Collider2DV2 {
        val id = "collider_${nextColliderId++}"
        val col = Collider2DV2(id=id, type=Collider2DType.Box, x=x, y=y, width=width, height=height, isTrigger=isTrigger)
        colliders[id]=col; return col
    }

    fun createCircleCollider(x: Float, y: Float, radius: Float, isTrigger: Boolean = false): Collider2DV2 {
        val id = "collider_${nextColliderId++}"
        val col = Collider2DV2(id=id, type=Collider2DType.Circle, x=x, y=y, radius=radius, isTrigger=isTrigger)
        colliders[id]=col; return col
    }

    fun createPolygonCollider(x: Float, y: Float, points: List<FloatArray>): Collider2DV2 {
        val id = "collider_${nextColliderId++}"
        val col = Collider2DV2(id=id, type=Collider2DType.Polygon, x=x, y=y, points=points.toMutableList())
        colliders[id]=col; return col
    }

    fun createJoint(type: Joint2DType, bodyA: Long, bodyB: Long): Joint2DV2 {
        val id = "joint_${nextJointId++}"
        val joint = Joint2DV2(id=id, type=type, bodyA=bodyA, bodyB=bodyB)
        joints[id]=joint; return joint
    }

    fun createDistanceJoint(bodyA: Long, bodyB: Long, distance: Float): Joint2DV2 {
        val j = createJoint(Joint2DType.Distance, bodyA, bodyB); j.distance=distance; return j
    }

    fun createHingeJoint(bodyA: Long, bodyB: Long): Joint2DV2 = createJoint(Joint2DType.Hinge, bodyA, bodyB)

    fun createSpringJoint(bodyA: Long, bodyB: Long, frequency: Float = 5f, damping: Float = 0.7f): Joint2DV2 {
        val j = createJoint(Joint2DType.Spring, bodyA, bodyB); j.frequency=frequency; j.dampingRatio=damping; return j
    }

    fun overlapPoint(x: Float, y: Float): List<Collider2DV2> = colliders.values.filter { it.containsPoint(x,y) }

    fun overlapBox(x: Float, y: Float, width: Float, height: Float): List<Collider2DV2> {
        return colliders.values.filter { col ->
            val b = col.getBounds()
            x-width*0.5f < b[2] && x+width*0.5f > b[0] && y-height*0.5f < b[3] && y+height*0.5f > b[1]
        }
    }

    fun raycast(ox: Float, oy: Float, dx: Float, dy: Float, distance: Float): Pair<Collider2DV2, Float>? {
        var closest: Pair<Collider2DV2, Float>? = null
        var minDist = Float.MAX_VALUE
        for (col in colliders.values) {
            if (col.isTrigger && !queriesHitTriggers) continue
            // Simple ray-box
            val b = col.getBounds()
            val t = rayBoxIntersect(ox, oy, dx, dy, b)
            if (t != null && t < minDist && t <= distance) { minDist=t; closest=Pair(col, t) }
        }
        return closest
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

    fun step(dt: Float) {
        if (!autoSimulation) return
        // Integrate bodies
        for (body in bodies.values) {
            if (body.type != Rigidbody2DType.Dynamic || body.isSleeping) continue
            body.vx+=gravityX*body.gravityScale*dt
            body.vy+=gravityY*body.gravityScale*dt
            body.vx*=1f-body.drag*dt; body.vy*=1f-body.drag*dt
            body.x+=body.vx*dt; body.y+=body.vy*dt
            if (!body.freezeRotation) {
                body.angularVelocity*=1f-body.angularDrag*dt
                body.rotation+=body.angularVelocity*dt
            }
        }
        // Joints
        for (joint in joints.values) {
            if (joint.isBroken) continue
            val a = bodies[joint.bodyA]; val b = bodies[joint.bodyB]
            if (a != null && b != null) joint.applyConstraint(a, b, dt)
        }
        // Simple collision - separate overlapping
        val bodyList = bodies.values.filter { it.type == Rigidbody2DType.Dynamic }.toList()
        for (i in bodyList.indices) for (j in i+1 until bodyList.size) {
            val a = bodyList[i]; val b = bodyList[j]
            val dx = b.x-a.x; val dy = b.y-a.y
            val dist = sqrt(dx*dx+dy*dy)
            if (dist < 1f && dist > 0.001f) {
                val sep = (1f-dist)*0.5f
                a.x-=dx/dist*sep; a.y-=dy/dist*sep
                b.x+=dx/dist*sep; b.y+=dy/dist*sep
            }
        }
    }

    fun getStats(): String = "2D Physics Bodies:${bodies.size} Colliders:${colliders.size} Joints:${joints.size} Gravity:($gravityX,$gravityY)"
}
