package com.sengine.engine.core

object ComponentRegistry {
    val types: LinkedHashMap<String, () -> Component> = linkedMapOf(
        "SpriteRenderer" to { SpriteRenderer() },
        "TextRenderer" to { TextRenderer() },
        "Camera" to { Camera2D() },
        "Rigidbody2D" to { Rigidbody2D() },
        "Collider2D" to { Collider2D() },
        "Script" to { ScriptComponent() },
        "ParticleEmitter" to { ParticleEmitter() },
        "AudioSource" to { AudioSource() },
        "Animator" to { Animator() },
        "MeshRenderer" to { MeshRenderer() },
        "Camera3D" to { Camera3D() },
        "Light" to { Light() },
        "Rigidbody3D" to { Rigidbody3D() },
        "Collider3D" to { Collider3D() },
        "Terrain" to { Terrain() },
        "Steering" to { Steering() },
        "Joint2D" to { Joint2D() },
        "Joint3D" to { Joint3D() },
    )

    val categories: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Rendering 2D" to listOf("SpriteRenderer", "TextRenderer", "Animator", "ParticleEmitter", "Camera"),
        "Rendering 3D" to listOf("MeshRenderer", "Camera3D", "Light", "Terrain"),
        "Physics 2D" to listOf("Rigidbody2D", "Collider2D", "Joint2D"),
        "Physics 3D" to listOf("Rigidbody3D", "Collider3D", "Joint3D"),
        "AI" to listOf("Steering"),
        "Scripting & Audio" to listOf("Script", "AudioSource"),
    )

    val POST_FX = listOf("None", "Grayscale", "Sepia", "Vignette", "CRT", "Pixelate", "Bloom", "Invert", "Chromatic", "Custom Shader")

    fun create(type: String): Component? = types[type]?.invoke()
}

class SpriteRenderer : Component() {
    override val type = "SpriteRenderer"
    var shape = 0 // 0 Square, 1 Circle, 2 Triangle
    var color = 0xFFFFFFFF.toInt()
    var texture = ""
    var flipX = false
    var flipY = false
    var shader = ""
    var shaderParam = 1f
    var screenSpace = false

    // runtime (set by Animator)
    var animTexture: String? = null
    val uv = floatArrayOf(0f, 1f, 1f, 0f) // u0, vBottom, u1, vTop

    override fun props() = listOf(
        Prop.Choice("Shape", SHAPES, { shape }, { shape = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.B("Flip X", { flipX }, { flipX = it }),
        Prop.B("Flip Y", { flipY }, { flipY = it }),
        Prop.Asset("Shader", AssetKind.SHADER, { shader }, { shader = it }),
        Prop.F("Shader Param", { shaderParam }, { shaderParam = it }),
        Prop.B("Screen Space UI", { screenSpace }, { screenSpace = it }),
    )

    fun resetUv() { uv[0] = 0f; uv[1] = 1f; uv[2] = 1f; uv[3] = 0f; animTexture = null }

    companion object {
        val SHAPES = listOf("Square", "Circle", "Triangle")
    }
}

class TextRenderer : Component() {
    override val type = "TextRenderer"
    var text = "Hello S Engine"
    var size = 0.5f
    var color = 0xFFFFFFFF.toInt()
    var align = 1 // 0 left, 1 center, 2 right
    var bold = false
    var screenSpace = false

    override fun props() = listOf(
        Prop.S("Text", { text }, { text = it }, multiline = true),
        Prop.B("Screen Space UI", { screenSpace }, { screenSpace = it }),
        Prop.F("Size", { size }, { size = it.coerceAtLeast(0.01f) }, 0.05f),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Choice("Align", listOf("Left", "Center", "Right"), { align }, { align = it }),
        Prop.B("Bold", { bold }, { bold = it }),
    )
}

class Camera2D : Component() {
    override val type = "Camera"
    var size = 5f
    var background = 0xFF1B2533.toInt()
    var follow = ""
    var smoothing = 5f
    var postFx = 0
    var postIntensity = 1f
    var postShader = ""
    var shake = 0f // runtime
    // v3 camera rig
    var priority = 0
    var deadZoneW = 0f
    var deadZoneH = 0f
    var lookAhead = 0f
    var boundsEnabled = false
    var boundMinX = -30f
    var boundMaxX = 30f
    var boundMinY = -20f
    var boundMaxY = 20f
    var minimumZoom = 0.4f
    var maximumZoom = 4f
    var traumaDecay = 1.6f
    var shakeScale = 1f

    override fun props() = listOf(
        Prop.F("Size", { size }, { size = it.coerceAtLeast(0.1f) }),
        Prop.Color("Background", { background }, { background = it }),
        Prop.S("Follow Target", { follow }, { follow = it }),
        Prop.F("Follow Smoothing", { smoothing }, { smoothing = it.coerceAtLeast(0f) }),
        Prop.Choice("Post FX", ComponentRegistry.POST_FX, { postFx }, { postFx = it }),
        Prop.F("FX Intensity", { postIntensity }, { postIntensity = it.coerceIn(0f, 4f) }, 0.05f),
        Prop.Asset("FX Shader", AssetKind.SHADER, { postShader }, { postShader = it }),
        Prop.I("Priority", { priority }, { priority = it }),
        Prop.F("Dead Zone W", { deadZoneW }, { deadZoneW = it.coerceAtLeast(0f) }, 0.1f),
        Prop.F("Dead Zone H", { deadZoneH }, { deadZoneH = it.coerceAtLeast(0f) }, 0.1f),
        Prop.F("Look Ahead", { lookAhead }, { lookAhead = it.coerceIn(0f, 2f) }, 0.05f),
        Prop.B("Clamp To Bounds", { boundsEnabled }, { boundsEnabled = it }),
        Prop.F("Bound Min X", { boundMinX }, { boundMinX = it }),
        Prop.F("Bound Max X", { boundMaxX }, { boundMaxX = it }),
        Prop.F("Bound Min Y", { boundMinY }, { boundMinY = it }),
        Prop.F("Bound Max Y", { boundMaxY }, { boundMaxY = it }),
        Prop.F("Min Zoom", { minimumZoom }, { minimumZoom = it.coerceIn(0.05f, 1f) }, 0.05f),
        Prop.F("Max Zoom", { maximumZoom }, { maximumZoom = it.coerceIn(1f, 20f) }),
        Prop.F("Trauma Decay", { traumaDecay }, { traumaDecay = it.coerceIn(0.1f, 10f) }, 0.1f),
        Prop.F("Shake Scale", { shakeScale }, { shakeScale = it.coerceIn(0f, 4f) }, 0.1f),
    )

    override fun resetRuntime() { shake = 0f }
}

class Rigidbody2D : Component() {
    override val type = "Rigidbody2D"
    var bodyType = 0 // 0 Dynamic, 1 Kinematic, 2 Static
    var mass = 1f
    var gravityScale = 1f
    var drag = 0f
    var bounciness = 0f
    var friction = 0.4f
    var startVx = 0f
    var startVy = 0f
    /** 0 = unlimited; otherwise the integration clamps |v| to this speed. */
    var maxSpeed = 0f
    /** Continuous collision detection: swept ray stops fast bodies from tunneling through thin walls. */
    var ccd = false

    // runtime
    var vx = 0f
    var vy = 0f
    var grounded = false

    override fun props() = listOf(
        Prop.Choice("Body Type", listOf("Dynamic", "Kinematic", "Static"), { bodyType }, { bodyType = it }),
        Prop.F("Mass", { mass }, { mass = it.coerceAtLeast(0.001f) }),
        Prop.F("Gravity Scale", { gravityScale }, { gravityScale = it }),
        Prop.F("Linear Drag", { drag }, { drag = it.coerceAtLeast(0f) }),
        Prop.F("Bounciness", { bounciness }, { bounciness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Friction", { friction }, { friction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Velocity X", { startVx }, { startVx = it }),
        Prop.F("Velocity Y", { startVy }, { startVy = it }),
        Prop.F("Max Speed", { maxSpeed }, { maxSpeed = it.coerceAtLeast(0f) }),
        Prop.B("Continuous Collision", { ccd }, { ccd = it }),
    )

    override fun resetRuntime() {
        vx = startVx; vy = startVy; grounded = false
    }
}

class Collider2D : Component() {
    override val type = "Collider2D"
    var shape = 0 // 0 Box, 1 Circle
    var width = 1f
    var height = 1f
    var radius = 0.5f
    var offsetX = 0f
    var offsetY = 0f
    var isTrigger = false
    /** Collision layer (bit index 0..15). */
    var layer = 0
    /** Bitmask of layers this collider can touch (default: everything). */
    var mask = 0xFFFF

    override fun props() = listOf(
        Prop.Choice("Shape", listOf("Box", "Circle"), { shape }, { shape = it }),
        Prop.F("Width", { width }, { width = it.coerceAtLeast(0.01f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.01f) }),
        Prop.F("Radius", { radius }, { radius = it.coerceAtLeast(0.01f) }),
        Prop.F("Offset X", { offsetX }, { offsetX = it }),
        Prop.F("Offset Y", { offsetY }, { offsetY = it }),
        Prop.B("Is Trigger", { isTrigger }, { isTrigger = it }),
        Prop.I("Layer", { layer }, { layer = it.coerceIn(0, 15) }),
        Prop.I("Mask", { mask }, { mask = it and 0xFFFF }),
    )
}

class ScriptComponent : Component() {
    override val type = "Script"
    var script = ""
    var params = ""

    override fun props() = listOf(
        Prop.Asset("Script", AssetKind.SCRIPT, { script }, { script = it }),
        Prop.S("Params", { params }, { params = it }),
    )
}

class ParticleEmitter : Component() {
    override val type = "ParticleEmitter"
    var emitting = true
    var rate = 30f
    var lifetime = 1.2f
    var speed = 3f
    var direction = 90f
    var spread = 30f
    var startSize = 0.25f
    var endSize = 0.02f
    var startColor = 0xFFFFC940.toInt()
    var endColor = 0x00FF3D00
    var gravity = 0f
    var maxParticles = 300
    var additive = false
    var texture = ""

    // runtime
    val particles = ArrayList<Particle>()
    var accumulator = 0f
    var pendingBurst = 0

    class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float, var life: Float)

    override fun props() = listOf(
        Prop.B("Emitting", { emitting }, { emitting = it }),
        Prop.F("Rate", { rate }, { rate = it.coerceAtLeast(0f) }, 1f),
        Prop.F("Lifetime", { lifetime }, { lifetime = it.coerceAtLeast(0.01f) }),
        Prop.F("Speed", { speed }, { speed = it }),
        Prop.F("Direction", { direction }, { direction = it }, 1f),
        Prop.F("Spread", { spread }, { spread = it.coerceIn(0f, 360f) }, 1f),
        Prop.F("Start Size", { startSize }, { startSize = it.coerceAtLeast(0f) }, 0.01f),
        Prop.F("End Size", { endSize }, { endSize = it.coerceAtLeast(0f) }, 0.01f),
        Prop.Color("Start Color", { startColor }, { startColor = it }),
        Prop.Color("End Color", { endColor }, { endColor = it }),
        Prop.F("Gravity", { gravity }, { gravity = it }),
        Prop.I("Max Particles", { maxParticles }, { maxParticles = it.coerceIn(1, 5000) }),
        Prop.B("Additive Blend", { additive }, { additive = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
    )

    override fun resetRuntime() {
        particles.clear(); accumulator = 0f; pendingBurst = 0
    }
}

class AudioSource : Component() {
    override val type = "AudioSource"
    var clip = ""
    var playOnStart = true
    var loop = false
    var volume = 1f

    override fun props() = listOf(
        Prop.Asset("Clip", AssetKind.SOUND, { clip }, { clip = it }),
        Prop.B("Play On Start", { playOnStart }, { playOnStart = it }),
        Prop.B("Loop", { loop }, { loop = it }),
        Prop.F("Volume", { volume }, { volume = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

/** Plays sprite-sheet animation clips (.anim assets) on the SpriteRenderer of the same object. */
class Animator : Component() {
    override val type = "Animator"
    var clip = ""
    var playOnStart = true
    var speed = 1f

    // runtime
    var current = ""
    var time = 0f
    var playing = false
    var frame = 0
    var finished = false

    override fun props() = listOf(
        Prop.Asset("Clip", AssetKind.ANIMATION, { clip }, { clip = it }),
        Prop.B("Play On Start", { playOnStart }, { playOnStart = it }),
        Prop.F("Speed", { speed }, { speed = it }),
    )

    override fun resetRuntime() {
        current = clip; time = 0f; playing = playOnStart; frame = 0; finished = false
    }
}

class MeshRenderer : Component() {
    override val type = "MeshRenderer"
    var mesh = 0
    var model = ""
    var color = 0xFFD0D4DC.toInt()
    var texture = ""
    var tiling = 1f
    var specular = 0.35f
    var shininess = 32f
    var emission = 0f
    var unlit = false
    var shader = ""
    var shaderParam = 1f

    override fun props() = listOf(
        Prop.Choice("Mesh", MESHES, { mesh }, { mesh = it }),
        Prop.Asset("Model (.obj)", AssetKind.MODEL, { model }, { model = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.F("Tiling", { tiling }, { tiling = it.coerceAtLeast(0.01f) }),
        Prop.F("Specular", { specular }, { specular = it.coerceIn(0f, 2f) }, 0.05f),
        Prop.F("Shininess", { shininess }, { shininess = it.coerceIn(1f, 256f) }, 1f),
        Prop.F("Emission", { emission }, { emission = it.coerceIn(0f, 4f) }, 0.05f),
        Prop.B("Unlit", { unlit }, { unlit = it }),
        Prop.Asset("Shader", AssetKind.SHADER, { shader }, { shader = it }),
        Prop.F("Shader Param", { shaderParam }, { shaderParam = it }),
    )

    companion object {
        val MESHES = listOf("Cube", "Sphere", "Plane", "Cylinder", "Cone", "Torus", "Capsule", "Pyramid", "Custom Model")
    }
}

class Camera3D : Component() {
    override val type = "Camera3D"
    var fov = 60f
    var near = 0.1f
    var far = 500f
    var skyTop = 0xFF3B7BD4.toInt()
    var skyHorizon = 0xFFBFD8F0.toInt()
    var follow = ""
    var offsetX = 0f
    var offsetY = 4f
    var offsetZ = 8f
    var lookAtTarget = true
    var smoothing = 6f
    var postFx = 0
    var postIntensity = 1f
    var postShader = ""
    var shake = 0f
    // v3 camera rig
    var priority = 0
    var deadZoneW = 0f
    var lookAhead = 0f
    var boundsEnabled = false
    var boundMinX = -150f
    var boundMaxX = 150f
    var boundMinZ = -150f
    var boundMaxZ = 150f
    var traumaDecay = 1.6f
    var shakeScale = 1f

    override fun props() = listOf(
        Prop.F("Field of View", { fov }, { fov = it.coerceIn(10f, 150f) }, 1f),
        Prop.F("Near", { near }, { near = it.coerceAtLeast(0.01f) }, 0.01f),
        Prop.F("Far", { far }, { far = it.coerceAtLeast(1f) }, 1f),
        Prop.Color("Sky Top", { skyTop }, { skyTop = it }),
        Prop.Color("Sky Horizon", { skyHorizon }, { skyHorizon = it }),
        Prop.S("Follow Target", { follow }, { follow = it }),
        Prop.F("Offset X", { offsetX }, { offsetX = it }),
        Prop.F("Offset Y", { offsetY }, { offsetY = it }),
        Prop.F("Offset Z", { offsetZ }, { offsetZ = it }),
        Prop.B("Look At Target", { lookAtTarget }, { lookAtTarget = it }),
        Prop.F("Follow Smoothing", { smoothing }, { smoothing = it.coerceAtLeast(0f) }),
        Prop.Choice("Post FX", ComponentRegistry.POST_FX, { postFx }, { postFx = it }),
        Prop.F("FX Intensity", { postIntensity }, { postIntensity = it.coerceIn(0f, 4f) }, 0.05f),
        Prop.Asset("FX Shader", AssetKind.SHADER, { postShader }, { postShader = it }),
        Prop.I("Priority", { priority }, { priority = it }),
        Prop.F("Dead Zone", { deadZoneW }, { deadZoneW = it.coerceAtLeast(0f) }, 0.1f),
        Prop.F("Look Ahead", { lookAhead }, { lookAhead = it.coerceIn(0f, 2f) }, 0.05f),
        Prop.B("Clamp To Bounds", { boundsEnabled }, { boundsEnabled = it }),
        Prop.F("Bound Min X", { boundMinX }, { boundMinX = it }),
        Prop.F("Bound Max X", { boundMaxX }, { boundMaxX = it }),
        Prop.F("Bound Min Z", { boundMinZ }, { boundMinZ = it }),
        Prop.F("Bound Max Z", { boundMaxZ }, { boundMaxZ = it }),
        Prop.F("Trauma Decay", { traumaDecay }, { traumaDecay = it.coerceIn(0.1f, 10f) }, 0.1f),
        Prop.F("Shake Scale", { shakeScale }, { shakeScale = it.coerceIn(0f, 4f) }, 0.1f),
    )

    override fun resetRuntime() { shake = 0f }
}

class Light : Component() {
    override val type = "Light"
    var kind = 0 // 0 Directional, 1 Point
    var color = 0xFFFFF4E0.toInt()
    var intensity = 1f
    var range = 10f

    override fun props() = listOf(
        Prop.Choice("Type", listOf("Directional", "Point"), { kind }, { kind = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.F("Intensity", { intensity }, { intensity = it.coerceIn(0f, 10f) }, 0.05f),
        Prop.F("Range", { range }, { range = it.coerceAtLeast(0.1f) }),
    )
}

class Rigidbody3D : Component() {
    override val type = "Rigidbody3D"
    var bodyType = 0 // 0 Dynamic, 1 Kinematic, 2 Static
    var mass = 1f
    var gravityScale = 1f
    var drag = 0.05f
    var bounciness = 0f
    var friction = 0.5f
    var startVx = 0f
    var startVy = 0f
    var startVz = 0f

    var vx = 0f
    var vy = 0f
    var vz = 0f
    var grounded = false
    /** 0 = unlimited; otherwise the integration clamps |v| to this speed. */
    var maxSpeed = 0f
    /** Continuous collision detection against fast-moving bodies. */
    var ccd = false
    /** Surface normal of the ground currently touching this body (0,1,0 when airborne). */
    var groundNx = 0f
    var groundNy = 1f
    var groundNz = 0f

    override fun props() = listOf(
        Prop.Choice("Body Type", listOf("Dynamic", "Kinematic", "Static"), { bodyType }, { bodyType = it }),
        Prop.F("Mass", { mass }, { mass = it.coerceAtLeast(0.001f) }),
        Prop.F("Gravity Scale", { gravityScale }, { gravityScale = it }),
        Prop.F("Linear Drag", { drag }, { drag = it.coerceAtLeast(0f) }),
        Prop.F("Bounciness", { bounciness }, { bounciness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Friction", { friction }, { friction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Velocity X", { startVx }, { startVx = it }),
        Prop.F("Velocity Y", { startVy }, { startVy = it }),
        Prop.F("Velocity Z", { startVz }, { startVz = it }),
        Prop.F("Max Speed", { maxSpeed }, { maxSpeed = it.coerceAtLeast(0f) }),
        Prop.B("Continuous Collision", { ccd }, { ccd = it }),
    )

    override fun resetRuntime() {
        vx = startVx; vy = startVy; vz = startVz; grounded = false
        groundNx = 0f; groundNy = 1f; groundNz = 0f
    }
}

class Collider3D : Component() {
    override val type = "Collider3D"
    var shape = 0 // 0 Box, 1 Sphere
    var sizeX = 1f
    var sizeY = 1f
    var sizeZ = 1f
    var radius = 0.5f
    var centerX = 0f
    var centerY = 0f
    var centerZ = 0f
    var isTrigger = false
    /** Collision layer (bit index 0..15). */
    var layer = 0
    /** Bitmask of layers this collider can touch (default: everything). */
    var mask = 0xFFFF

    override fun props() = listOf(
        Prop.Choice("Shape", listOf("Box", "Sphere"), { shape }, { shape = it }),
        Prop.F("Size X", { sizeX }, { sizeX = it.coerceAtLeast(0.01f) }),
        Prop.F("Size Y", { sizeY }, { sizeY = it.coerceAtLeast(0.01f) }),
        Prop.F("Size Z", { sizeZ }, { sizeZ = it.coerceAtLeast(0.01f) }),
        Prop.F("Radius", { radius }, { radius = it.coerceAtLeast(0.01f) }),
        Prop.F("Center X", { centerX }, { centerX = it }),
        Prop.F("Center Y", { centerY }, { centerY = it }),
        Prop.F("Center Z", { centerZ }, { centerZ = it }),
        Prop.B("Is Trigger", { isTrigger }, { isTrigger = it }),
        Prop.I("Layer", { layer }, { layer = it.coerceIn(0, 15) }),
        Prop.I("Mask", { mask }, { mask = it and 0xFFFF }),
    )
}

/**
 * Procedural heightmap terrain. Generates a deterministic FBM noise mesh and
 * doubles as the height source for 3D physics (ground support, slope walking
 * and ray casts). Assumes translation + scale only (no rotation).
 */
class Terrain : Component() {
    override val type = "Terrain"
    var size = 120f
    var resolution = 64
    var amplitude = 7f
    var frequency = 0.045f
    var octaves = 4
    var seed = 1337
    var collide = true
    var color = 0xFF7FA650.toInt()
    var texture = ""
    var tiling = 24f
    var specular = 0.12f
    var emission = 0f

    override fun props() = listOf(
        Prop.F("Size", { size }, { size = it.coerceIn(4f, 4000f) }, 1f),
        Prop.I("Resolution", { resolution }, { resolution = it.coerceIn(4, 256) }),
        Prop.F("Amplitude", { amplitude }, { amplitude = it.coerceIn(0f, 400f) }, 0.5f),
        Prop.F("Frequency", { frequency }, { frequency = it.coerceIn(0.0005f, 2f) }, 0.005f),
        Prop.I("Octaves", { octaves }, { octaves = it.coerceIn(1, 6) }),
        Prop.I("Seed", { seed }, { seed = it }),
        Prop.B("Collide", { collide }, { collide = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.F("Tiling", { tiling }, { tiling = it.coerceAtLeast(0.01f) }),
        Prop.F("Specular", { specular }, { specular = it.coerceIn(0f, 2f) }, 0.05f),
        Prop.F("Emission", { emission }, { emission = it.coerceIn(0f, 4f) }, 0.05f),
    )

    /** Height in local space at local (lx, lz). Deterministic from the props. */
    fun heightLocal(lx: Float, lz: Float): Float {
        if (amplitude == 0f) return 0f
        var sum = 0f
        var norm = 0f
        var amp = 1f
        var freq = frequency
        val oct = octaves.coerceIn(1, 6)
        for (i in 0 until oct) {
            sum += amp * valueNoise(lx * freq + seed * 0.37f, lz * freq - seed * 0.61f, seed + i * 101)
            norm += amp
            amp *= 0.5f
            freq *= 2f
        }
        return (sum / norm - 0.5f) * 2f * amplitude
    }

    /** World-space height at (wx, wz), or null when outside the terrain bounds. */
    fun heightAtWorld(wx: Float, wz: Float): Float? {
        if (!enabled) return null
        val w = gameObject.computeWorld3()
        val sx = com.sengine.engine.math.Mat4.scaleOf(w, 0)
        val sy = com.sengine.engine.math.Mat4.scaleOf(w, 1)
        val sz = com.sengine.engine.math.Mat4.scaleOf(w, 2)
        val lx = (wx - w[12]) / (if (Math.abs(sx) < 1e-6f) 1f else sx)
        val lz = (wz - w[14]) / (if (Math.abs(sz) < 1e-6f) 1f else sz)
        val half = size * 0.5f
        if (lx < -half || lx > half || lz < -half || lz > half) return null
        return w[13] + heightLocal(lx, lz) * sy
    }

    /** World-space bounds of the terrain footprint (for physics broad phase). */
    fun containsWorld(wx: Float, wz: Float): Boolean {
        val w = gameObject.computeWorld3()
        val sx = Math.abs(com.sengine.engine.math.Mat4.scaleOf(w, 0))
        val sz = Math.abs(com.sengine.engine.math.Mat4.scaleOf(w, 2))
        val half = size * 0.5f
        return Math.abs(wx - w[12]) <= half * (if (sx < 1e-6f) 1f else sx) &&
               Math.abs(wz - w[14]) <= half * (if (sz < 1e-6f) 1f else sz)
    }

    private fun valueNoise(x: Float, z: Float, salt: Int): Float {
        val x0 = Math.floor(x.toDouble()).toInt()
        val z0 = Math.floor(z.toDouble()).toInt()
        val fx = x - x0
        val fz = z - z0
        val sx = fx * fx * (3f - 2f * fx)
        val sz = fz * fz * (3f - 2f * fz)
        val a = hash(x0, z0, salt)
        val b = hash(x0 + 1, z0, salt)
        val c = hash(x0, z0 + 1, salt)
        val d = hash(x0 + 1, z0 + 1, salt)
        val top = a + (b - a) * sx
        val bot = c + (d - c) * sx
        return top + (bot - top) * sz
    }

    private fun hash(ix: Int, iz: Int, salt: Int): Float {
        var h = ix * 374761393 + iz * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }
}

/**
 * Steering behaviours (seek / flee / pursue / wander / patrol) that drive the
 * object's rigidbody each frame. Works in 2D (XY) and 3D (XZ).
 */
class Steering : Component() {
    override val type = "Steering"
    var mode = 0 // 0 Seek, 1 Flee, 2 Pursue, 3 Wander, 4 Patrol
    var target = ""
    var speed = 4f
    var acceleration = 16f
    var slowingRadius = 1.5f
    var wanderStrength = 1f
    var wanderTurn = 140f
    var patrolDistance = 4f
    var faceMovement = true

    // runtime
    var arrived = false
    var wanderAngle = 0f
    var startX = 0f
    var startZ = 0f
    var patrolDir = 1
    var initialized = false

    override fun props() = listOf(
        Prop.Choice("Mode", MODES, { mode }, { mode = it }),
        Prop.S("Target", { target }, { target = it }),
        Prop.F("Speed", { speed }, { speed = it.coerceAtLeast(0f) }),
        Prop.F("Acceleration", { acceleration }, { acceleration = it.coerceAtLeast(0f) }),
        Prop.F("Slowing Radius", { slowingRadius }, { slowingRadius = it.coerceAtLeast(0f) }),
        Prop.F("Wander Strength", { wanderStrength }, { wanderStrength = it.coerceAtLeast(0f) }),
        Prop.F("Wander Turn", { wanderTurn }, { wanderTurn = it.coerceAtLeast(0f) }),
        Prop.F("Patrol Distance", { patrolDistance }, { patrolDistance = it.coerceAtLeast(0f) }),
        Prop.B("Face Movement", { faceMovement }, { faceMovement = it }),
    )

    override fun resetRuntime() {
        arrived = false; wanderAngle = 0f; patrolDir = 1; initialized = false
    }

    companion object {
        val MODES = listOf("Seek", "Flee", "Pursue", "Wander", "Patrol")
    }
}

/**
 * Constraint between this object and [target], solved every physics step.
 * mode: 0 Distance, 1 Spring, 2 Rope (pull only), 3 Weld.
 */
class Joint2D : Component() {
    override val type = "Joint2D"
    var mode = 0
    var target = ""
    var restLength = 0f // 0 = use the distance at play start
    var stiffness = 0.85f
    var damping = 0.08f
    var breakForce = 0f // 0 = never breaks

    override fun props() = listOf(
        Prop.Choice("Mode", MODES, { mode }, { mode = it }),
        Prop.S("Target", { target }, { target = it }),
        Prop.F("Rest Length", { restLength }, { restLength = it.coerceAtLeast(0f) }, 0.1f),
        Prop.F("Stiffness", { stiffness }, { stiffness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Damping", { damping }, { damping = it.coerceIn(0f, 1f) }, 0.01f),
        Prop.F("Break Force", { breakForce }, { breakForce = it.coerceAtLeast(0f) }),
    )

    companion object { val MODES = listOf("Distance", "Spring", "Rope", "Weld") }
}

/** 3D counterpart of [Joint2D] (XYZ distance / spring / rope / weld). */
class Joint3D : Component() {
    override val type = "Joint3D"
    var mode = 0
    var target = ""
    var restLength = 0f
    var stiffness = 0.85f
    var damping = 0.08f
    var breakForce = 0f

    override fun props() = listOf(
        Prop.Choice("Mode", Joint2D.MODES, { mode }, { mode = it }),
        Prop.S("Target", { target }, { target = it }),
        Prop.F("Rest Length", { restLength }, { restLength = it.coerceAtLeast(0f) }, 0.1f),
        Prop.F("Stiffness", { stiffness }, { stiffness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Damping", { damping }, { damping = it.coerceIn(0f, 1f) }, 0.01f),
        Prop.F("Break Force", { breakForce }, { breakForce = it.coerceAtLeast(0f) }),
    )
}
