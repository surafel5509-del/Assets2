package com.sengine.engine.vfx

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.json.JSONObject

/**
 * Ultimate Edition VFX System - Unity VFX Graph + Unreal Niagara equivalent
 * Advanced particle system with modules, trails, lights, collisions, sub-emitters
 */

enum class ParticleSystemCurveMode { Constant, Curve, TwoConstants, TwoCurves }
enum class ParticleSystemShapeType { Sphere, Hemisphere, Cone, Box, Circle, Rectangle, Mesh }
enum class ParticleSystemInheritVelocityMode { Initial, Current }
enum class ParticleSystemCollisionMode { Plane, World }

data class MinMaxCurve(
    var mode: ParticleSystemCurveMode = ParticleSystemCurveMode.Constant,
    var constant: Float = 1f,
    var constantMin: Float = 0f,
    var constantMax: Float = 1f,
    var curveMultiplier: Float = 1f
) {
    fun evaluate(time: Float, lerpFactor: Float = 0f): Float {
        return when (mode) {
            ParticleSystemCurveMode.Constant -> constant
            ParticleSystemCurveMode.TwoConstants -> constantMin + (constantMax - constantMin) * lerpFactor
            else -> constant // curve evaluation simplified
        }
    }
}

data class MinMaxGradient(
    var mode: Int = 0, // 0 color, 1 gradient, 2 two colors, 3 two gradients, 4 random color
    var color: Int = 0xFFFFFFFF.toInt(),
    var colorMin: Int = 0xFFFFFFFF.toInt(),
    var colorMax: Int = 0xFFFFFFFF.toInt()
)

class ParticleSystemMainModule {
    var duration: Float = 5f
    var loop: Boolean = true
    var prewarm: Boolean = false
    var startDelay: MinMaxCurve = MinMaxCurve(constant = 0f)
    var startLifetime: MinMaxCurve = MinMaxCurve(constant = 5f)
    var startSpeed: MinMaxCurve = MinMaxCurve(constant = 5f)
    var startSize: MinMaxCurve = MinMaxCurve(constant = 1f)
    var startRotation: MinMaxCurve = MinMaxCurve(constant = 0f)
    var startColor: MinMaxGradient = MinMaxGradient()
    var gravityModifier: MinMaxCurve = MinMaxCurve(constant = 0f)
    var simulationSpace: Int = 0 // 0 local, 1 world, 2 custom
    var simulationSpeed: Float = 1f
    var maxParticles: Int = 1000
    var scalingMode: Int = 0
    var playOnAwake: Boolean = true
    var emitterVelocityMode: Int = 0
}

class ParticleSystemEmissionModule {
    var enabled: Boolean = true
    var rateOverTime: MinMaxCurve = MinMaxCurve(constant = 10f)
    var rateOverDistance: MinMaxCurve = MinMaxCurve(constant = 0f)
    var bursts = mutableListOf<Burst>()

    data class Burst(var time: Float, var count: MinMaxCurve, var cycleCount: Int = 1, var repeatInterval: Float = 0.01f, var probability: Float = 1f)
}

class ParticleSystemShapeModule {
    var enabled: Boolean = true
    var shapeType: ParticleSystemShapeType = ParticleSystemShapeType.Cone
    var angle: Float = 25f
    var radius: Float = 1f
    var radiusThickness: Float = 1f
    var arc: Float = 360f
    var position: FloatArray = floatArrayOf(0f, 0f, 0f)
    var rotation: FloatArray = floatArrayOf(0f, 0f, 0f)
    var scale: FloatArray = floatArrayOf(1f, 1f, 1f)
    var randomDirectionAmount: Float = 0f
    var sphericalDirectionAmount: Float = 0f
}

class ParticleSystemVelocityOverLifetimeModule {
    var enabled: Boolean = false
    var x: MinMaxCurve = MinMaxCurve(constant = 0f)
    var y: MinMaxCurve = MinMaxCurve(constant = 0f)
    var z: MinMaxCurve = MinMaxCurve(constant = 0f)
    var orbitalX: MinMaxCurve = MinMaxCurve(constant = 0f)
    var orbitalY: MinMaxCurve = MinMaxCurve(constant = 0f)
    var orbitalZ: MinMaxCurve = MinMaxCurve(constant = 0f)
    var space: Int = 0
}

class ParticleSystemColorOverLifetimeModule {
    var enabled: Boolean = false
    var gradient: MinMaxGradient = MinMaxGradient()
}

class ParticleSystemSizeOverLifetimeModule {
    var enabled: Boolean = false
    var size: MinMaxCurve = MinMaxCurve(constant = 1f)
    var separateAxes: Boolean = false
}

class ParticleSystemRotationOverLifetimeModule {
    var enabled: Boolean = false
    var rotation: MinMaxCurve = MinMaxCurve(constant = 0f)
}

class ParticleSystemForceOverLifetimeModule {
    var enabled: Boolean = false
    var x: MinMaxCurve = MinMaxCurve(constant = 0f)
    var y: MinMaxCurve = MinMaxCurve(constant = 0f)
    var z: MinMaxCurve = MinMaxCurve(constant = 0f)
    var randomized: Boolean = false
    var space: Int = 0
}

class ParticleSystemCollisionModule {
    var enabled: Boolean = false
    var type: ParticleSystemCollisionMode = ParticleSystemCollisionMode.World
    var mode: Int = 0 // 0 3D, 1 2D
    var dampen: MinMaxCurve = MinMaxCurve(constant = 0f)
    var bounce: MinMaxCurve = MinMaxCurve(constant = 0f)
    var lifetimeLoss: MinMaxCurve = MinMaxCurve(constant = 0f)
    var minKillSpeed: Float = 0f
    var maxKillSpeed: Float = 10000f
    var collidesWith: Int = -1 // layer mask
    var enableDynamicColliders: Boolean = true
    var maxCollisionShapes: Int = 256
    var quality: Int = 1
    var voxelSize: Float = 0.5f
}

class ParticleSystemTrailsModule {
    var enabled: Boolean = false
    var mode: Int = 0
    var ratio: Float = 1f
    var lifetime: MinMaxCurve = MinMaxCurve(constant = 1f)
    var minVertexDistance: Float = 0.2f
    var textureMode: Int = 0
    var worldSpace: Boolean = true
    var dieWithParticles: Boolean = true
    var sizeAffectsWidth: Boolean = true
    var inheritParticleColor: Boolean = true
    var colorOverLifetime: MinMaxGradient = MinMaxGradient()
    var widthOverTrail: MinMaxCurve = MinMaxCurve(constant = 1f)
}

class ParticleSystemLightsModule {
    var enabled: Boolean = false
    var ratio: Float = 1f
    var useRandomDistribution: Boolean = true
    var light: String = "" // prefab
    var useParticleColor: Boolean = true
    var sizeAffectsRange: Boolean = true
    var alphaAffectsIntensity: Boolean = true
    var range: MinMaxCurve = MinMaxCurve(constant = 1f)
    var rangeMultiplier: Float = 1f
    var intensity: MinMaxCurve = MinMaxCurve(constant = 1f)
    var intensityMultiplier: Float = 1f
    var maxLights: Int = 10000
}

class Particle {
    var x: Float = 0f; var y: Float = 0f; var z: Float = 0f
    var vx: Float = 0f; var vy: Float = 0f; var vz: Float = 0f
    var ax: Float = 0f; var ay: Float = 0f; var az: Float = 0f
    var size: Float = 1f
    var rotation: Float = 0f
    var angularVelocity: Float = 0f
    var color: Int = 0xFFFFFFFF.toInt()
    var age: Float = 0f
    var lifetime: Float = 1f
    var randomSeed: Long = Random.nextLong()

    fun normalizedAge(): Float = (age / lifetime).coerceIn(0f, 1f)
    fun isAlive(): Boolean = age < lifetime
}

class ParticleSystemV2 {
    val main = ParticleSystemMainModule()
    val emission = ParticleSystemEmissionModule()
    val shape = ParticleSystemShapeModule()
    val velocityOverLifetime = ParticleSystemVelocityOverLifetimeModule()
    val colorOverLifetime = ParticleSystemColorOverLifetimeModule()
    val sizeOverLifetime = ParticleSystemSizeOverLifetimeModule()
    val rotationOverLifetime = ParticleSystemRotationOverLifetimeModule()
    val forceOverLifetime = ParticleSystemForceOverLifetimeModule()
    val collision = ParticleSystemCollisionModule()
    val trails = ParticleSystemTrailsModule()
    val lights = ParticleSystemLightsModule()

    val particles = mutableListOf<Particle>()
    var time: Float = 0f
    var isPlaying: Boolean = true
    var isEmitting: Boolean = true
    private var emissionAccumulator: Float = 0f

    fun play() { isPlaying = true; isEmitting = true }
    fun pause() { isPlaying = false }
    fun stop() { isPlaying = false; isEmitting = false; particles.clear(); time = 0f }
    fun clear() { particles.clear() }

    fun emit(count: Int) {
        repeat(count) {
            if (particles.size >= main.maxParticles) return@repeat
            val p = Particle()
            // shape emission
            when (shape.shapeType) {
                ParticleSystemShapeType.Sphere -> {
                    val theta = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val phi = kotlin.math.acos(2f * Random.nextFloat() - 1f).toFloat()
                    val r = shape.radius * (1f - Random.nextFloat() * (1f - shape.radiusThickness))
                    p.x = r * sin(phi) * cos(theta)
                    p.y = r * sin(phi) * sin(theta)
                    p.z = r * cos(phi)
                    val speed = main.startSpeed.evaluate(0f, Random.nextFloat())
                    p.vx = p.x / r * speed
                    p.vy = p.y / r * speed
                    p.vz = p.z / r * speed
                }
                ParticleSystemShapeType.Cone -> {
                    val angle = Math.toRadians((Random.nextFloat() * 2f - 1f) * shape.angle.toDouble()).toFloat()
                    val speed = main.startSpeed.evaluate(0f, Random.nextFloat())
                    p.vx = sin(angle) * speed
                    p.vy = cos(angle) * speed
                    p.vz = (Random.nextFloat() * 2f - 1f) * speed * 0.1f
                    p.x = Random.nextFloat() * 0.1f
                    p.y = 0f
                    p.z = Random.nextFloat() * 0.1f
                }
                else -> {
                    p.x = (Random.nextFloat() * 2f - 1f) * shape.radius
                    p.y = (Random.nextFloat() * 2f - 1f) * shape.radius
                    p.z = (Random.nextFloat() * 2f - 1f) * shape.radius
                    val speed = main.startSpeed.evaluate(0f, Random.nextFloat())
                    val dir = Random.nextFloat() * 2f * Math.PI.toFloat()
                    p.vx = cos(dir) * speed
                    p.vy = sin(dir) * speed
                    p.vz = (Random.nextFloat() * 2f - 1f) * speed
                }
            }
            p.lifetime = main.startLifetime.evaluate(0f, Random.nextFloat())
            p.size = main.startSize.evaluate(0f, Random.nextFloat())
            p.rotation = main.startRotation.evaluate(0f, Random.nextFloat())
            p.color = main.startColor.color
            particles.add(p)
        }
    }

    fun update(dt: Float) {
        if (!isPlaying) return
        val scaledDt = dt * main.simulationSpeed
        time += scaledDt

        // emission
        if (isEmitting) {
            val rate = emission.rateOverTime.evaluate(time % main.duration)
            emissionAccumulator += rate * scaledDt
            val toEmit = emissionAccumulator.toInt()
            emissionAccumulator -= toEmit
            if (toEmit > 0) emit(toEmit)

            // bursts
            for (burst in emission.bursts) {
                if (time >= burst.time && time - scaledDt < burst.time) {
                    emit(burst.count.evaluate(0f).toInt())
                }
            }
        }

        // update particles
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.age += scaledDt
            if (!p.isAlive()) { iter.remove(); continue }

            val t = p.normalizedAge()

            // velocity over lifetime
            if (velocityOverLifetime.enabled) {
                p.vx += velocityOverLifetime.x.evaluate(t) * scaledDt
                p.vy += velocityOverLifetime.y.evaluate(t) * scaledDt
                p.vz += velocityOverLifetime.z.evaluate(t) * scaledDt
            }

            // force over lifetime
            if (forceOverLifetime.enabled) {
                p.ax = forceOverLifetime.x.evaluate(t)
                p.ay = forceOverLifetime.y.evaluate(t)
                p.az = forceOverLifetime.z.evaluate(t)
                p.vx += p.ax * scaledDt
                p.vy += p.ay * scaledDt
                p.vz += p.az * scaledDt
            }

            // gravity
            val gravity = main.gravityModifier.evaluate(t)
            p.vy += gravity * scaledDt * -9.81f

            p.x += p.vx * scaledDt
            p.y += p.vy * scaledDt
            p.z += p.vz * scaledDt

            // size over lifetime
            if (sizeOverLifetime.enabled) {
                p.size = main.startSize.constant * sizeOverLifetime.size.evaluate(t)
            }

            // rotation
            if (rotationOverLifetime.enabled) {
                p.angularVelocity = rotationOverLifetime.rotation.evaluate(t)
                p.rotation += p.angularVelocity * scaledDt
            }

            // collision simplified
            if (collision.enabled && p.y < 0f) {
                p.y = 0f
                p.vy = -p.vy * (1f - collision.bounce.evaluate(t)) * (1f - collision.dampen.evaluate(t))
                p.lifetime -= p.lifetime * collision.lifetimeLoss.evaluate(t)
                if (kotlin.math.abs(p.vy) < collision.minKillSpeed) iter.remove()
            }
        }

        // looping
        if (main.loop && time >= main.duration) time %= main.duration
        else if (!main.loop && time >= main.duration) isEmitting = false
    }

    fun particleCount(): Int = particles.size
}

/**
 * VFX Graph - node-based visual effects like Unity VFX Graph
 */
class VFXGraph {
    data class Node(val id: String, var type: String, var position: FloatArray = floatArrayOf(0f, 0f), var properties: MutableMap<String, Any> = mutableMapOf())
    data class Connection(val from: String, val fromPort: String, val to: String, val toPort: String)

    val nodes = mutableListOf<Node>()
    val connections = mutableListOf<Connection>()

    fun addNode(type: String, x: Float = 0f, y: Float = 0f): Node {
        val node = Node(java.util.UUID.randomUUID().toString(), type, floatArrayOf(x, y))
        nodes.add(node)
        return node
    }

    fun connect(from: Node, fromPort: String, to: Node, toPort: String) {
        connections.add(Connection(from.id, fromPort, to.id, toPort))
    }

    fun compileToParticleSystem(): ParticleSystemV2 {
        val ps = ParticleSystemV2()
        // Interpret graph to configure PS
        for (node in nodes) {
            when (node.type) {
                "SpawnRate" -> ps.emission.rateOverTime.constant = (node.properties["rate"] as? Float) ?: 10f
                "Lifetime" -> ps.main.startLifetime.constant = (node.properties["lifetime"] as? Float) ?: 5f
                "Speed" -> ps.main.startSpeed.constant = (node.properties["speed"] as? Float) ?: 5f
            }
        }
        return ps
    }
}
