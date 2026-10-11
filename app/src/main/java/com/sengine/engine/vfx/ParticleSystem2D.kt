package com.sengine.engine.vfx

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - 2D Particle System & Shader Graph 2D
 * Unity 2D Particle System, 2D Shader Graph, Sprite Effects
 */
enum class Particle2DMode { Billboard, StretchedBillboard, HorizontalBillboard, VerticalBillboard, Mesh }
enum class Particle2DRenderMode { Billboard, Stretched, HorizontalBillboard, VerticalBillboard, Mesh }

data class Particle2D(
    var x: Float = 0f, var y: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f,
    var ax: Float = 0f, var ay: Float = 0f,
    var rotation: Float = 0f,
    var angularVelocity: Float = 0f,
    var size: Float = 1f,
    var color: Int = 0xFFFFFFFF.toInt(),
    var age: Float = 0f,
    var lifetime: Float = 1f,
    var isAlive: Boolean = true
) {
    fun update(dt: Float, gravity: Float) {
        if (!isAlive) return
        vx+=ax*dt; vy+=ay*dt+gravity*dt
        x+=vx*dt; y+=vy*dt
        rotation+=angularVelocity*dt
        age+=dt
        if (age >= lifetime) isAlive=false
    }
    fun normalizedAge(): Float = (age/lifetime).coerceIn(0f,1f)
}

data class ParticleSystem2DModuleMain(
    var duration: Float = 5f,
    var loop: Boolean = true,
    var startDelay: Float = 0f,
    var startLifetime: Float = 5f,
    var startSpeed: Float = 5f,
    var startSize: Float = 1f,
    var startRotation: Float = 0f,
    var startColor: Int = 0xFFFFFFFF.toInt(),
    var gravityModifier: Float = 0f,
    var maxParticles: Int = 1000,
    var simulationSpace: Int = 0, // 0 local, 1 world
    var playOnAwake: Boolean = true
)

data class ParticleSystem2DEmission(
    var rateOverTime: Float = 10f,
    var rateOverDistance: Float = 0f,
    var bursts: MutableList<Burst2D> = mutableListOf()
) {
    data class Burst2D(var time: Float, var count: Int, var cycleCount: Int = 1, var repeatInterval: Float = 0.01f)
}

data class ParticleSystem2DShape(
    var shape: Int = 0, // 0 point, 1 circle, 2 box, 3 edge
    var radius: Float = 1f,
    var angle: Float = 25f,
    var arc: Float = 360f,
    var width: Float = 1f, var height: Float = 1f
)

class ParticleSystem2D {
    var main = ParticleSystem2DModuleMain()
    var emission = ParticleSystem2DEmission()
    var shape = ParticleSystem2DShape()
    var particles = mutableListOf<Particle2D>()
    var time: Float = 0f
    var isPlaying: Boolean = true
    var isEmitting: Boolean = true
    var emissionAccumulator: Float = 0f
    var x: Float = 0f; var y: Float = 0f

    fun play() { isPlaying=true; isEmitting=true }
    fun pause() { isPlaying=false }
    fun stop() { isPlaying=false; isEmitting=false; particles.clear() }
    fun clear() { particles.clear() }

    fun emit(count: Int) {
        repeat(count) {
            if (particles.size >= main.maxParticles) return
            val angle = when (shape.shape) {
                1 -> Math.random().toFloat()*360f // circle
                else -> (Math.random().toFloat()-0.5f)*shape.angle
            }
            val rad = angle*PI.toFloat()/180f
            val speed = main.startSpeed * (0.8f+Math.random().toFloat()*0.4f)
            val p = Particle2D(
                x=x, y=y,
                vx=cos(rad)*speed, vy=sin(rad)*speed,
                rotation=main.startRotation+Math.random().toFloat()*360f,
                angularVelocity=(Math.random().toFloat()-0.5f)*90f,
                size=main.startSize*(0.8f+Math.random().toFloat()*0.4f),
                color=main.startColor,
                lifetime=main.startLifetime*(0.8f+Math.random().toFloat()*0.4f)
            )
            particles.add(p)
        }
    }

    fun update(dt: Float) {
        if (!isPlaying) return
        time+=dt
        if (main.loop && time > main.duration) time=0f

        // Emission
        if (isEmitting) {
            emissionAccumulator+=emission.rateOverTime*dt
            while (emissionAccumulator >= 1f) { emissionAccumulator-=1f; emit(1) }
        }

        // Update particles
        for (p in particles) p.update(dt, main.gravityModifier*-9.81f)
        particles.removeAll { !it.isAlive }
    }

    fun getStats(): String = "2D Particles:${particles.size}/${main.maxParticles} Time:${"%.1f".format(time)}/${main.duration}"
}

// 2D Shader Graph
enum class ShaderGraph2DNodeType { Master, Texture, Color, Float, Vector2, Add, Multiply, Lerp, Time, UV, Panner, SampleSprite, Blend, Mask, Distortion }

data class ShaderGraph2DNode(
    var id: String = "node_0",
    var type: ShaderGraph2DNodeType = ShaderGraph2DNodeType.Master,
    var x: Float = 0f, var y: Float = 0f,
    var properties: MutableMap<String, Any> = mutableMapOf(),
    var inputs: MutableMap<String, String> = mutableMapOf(), // port -> connected node id
    var outputs: MutableList<String> = mutableListOf()
)

class ShaderGraph2D(
    var name: String = "2D_Shader",
    var nodes: MutableMap<String, ShaderGraph2DNode> = mutableMapOf(),
    var connections: MutableList<Pair<String,String>> = mutableListOf()
) {
    fun addNode(id: String, type: ShaderGraph2DNodeType, x: Float = 0f, y: Float = 0f): ShaderGraph2DNode {
        val node = ShaderGraph2DNode(id=id, type=type, x=x, y=y); nodes[id]=node; return node
    }
    fun connect(fromId: String, toId: String) { connections.add(Pair(fromId, toId)); nodes[toId]?.inputs?.put("input", fromId) }

    fun compileToGLSL(): String {
        val sb = StringBuilder()
        sb.appendLine("// 2D Shader Graph: $name - Compiled GLSL")
        sb.appendLine("vec4 effect(vec4 color, vec2 uv) {")
        sb.appendLine("  float time = uTime;")
        sb.appendLine("  vec4 tex = texture2D(uTex, uv);")
        // Topological sort simplified
        for (node in nodes.values) {
            when (node.type) {
                ShaderGraph2DNodeType.Texture -> sb.appendLine("  vec4 ${node.id} = texture2D(uTex, uv);")
                ShaderGraph2DNodeType.Color -> sb.appendLine("  vec4 ${node.id} = vec4(1.0); // color")
                ShaderGraph2DNodeType.Panner -> sb.appendLine("  vec2 ${node.id} = uv + vec2(time*0.1, 0.0);")
                ShaderGraph2DNodeType.Add -> sb.appendLine("  vec4 ${node.id} = tex + vec4(0.1);")
                ShaderGraph2DNodeType.Multiply -> sb.appendLine("  vec4 ${node.id} = tex * color;")
                ShaderGraph2DNodeType.Distortion -> sb.appendLine("  vec2 ${node.id} = uv + vec2(sin(uv.y*10.0+time)*0.05, 0.0);")
                else -> {}
            }
        }
        sb.appendLine("  return tex * color;")
        sb.appendLine("}")
        return sb.toString()
    }

    fun getStats(): String = "2D ShaderGraph Nodes:${nodes.size} Connections:${connections.size}"
}
