package com.sengine.engine.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Professional world-building components: Sun (with a real sun editor),
 * procedural Terrain ground, Grass, Water, a procedural Human character,
 * screen-space UI Panels and a true 3D particle emitter.
 *
 * Everything here is pure data + deterministic maths so it can be unit-tested
 * on the JVM; the GL rendering lives in SceneRenderer / Renderer3D.
 */

// ---------------------------------------------------------------------- Sun
/**
 * The scene sun. Attach to any object (usually called "Sun").
 * Editor fields requested by users: colour, sunshine (intensity),
 * temperature (Kelvin), size / halo and a sun shader style.
 */
class Sun : Component() {
    override val type = "Sun"
    var color = 0xFFFFF2D8.toInt()
    /** "Sunshine" — how strong the sunlight is (0 = night). */
    var sunshine = 1.25f
    /** Colour temperature in Kelvin: 1800 = candle, 5600 = noon, 9000 = blue shade. */
    var temperature = 5600f
    var elevation = 52f   // degrees above the horizon
    var azimuth = 35f     // degrees around Y
    var sunSize = 0.045f  // angular size of the visible disc
    var halo = 0.55f      // glow around the disc (0..1)
    var shader = 0        // SunShaders index
    var shaderAsset = ""
    var animateDay = false
    var daySpeed = 1f     // full day cycles per (100 * daySpeed) seconds

    /** Runtime: when [animateDay] is on the Engine advances this 0..1 day fraction. */
    var dayFraction = 0.32f

    override fun props() = listOf(
        Prop.Color("Sun Color", { color }, { color = it }),
        Prop.F("Sunshine", { sunshine }, { sunshine = it.coerceIn(0f, 6f) }, 0.05f),
        Prop.F("Temperature (K)", { temperature }, { temperature = it.coerceIn(1000f, 12000f) }, 50f),
        Prop.F("Elevation", { elevation }, { elevation = it.coerceIn(-5f, 90f) }, 1f),
        Prop.F("Azimuth", { azimuth }, { azimuth = it }, 1f),
        Prop.F("Sun Size", { sunSize }, { sunSize = it.coerceIn(0.005f, 0.25f) }, 0.005f),
        Prop.F("Halo", { halo }, { halo = it.coerceIn(0f, 2f) }, 0.05f),
        Prop.Choice("Sun Shader", SunShaders.NAMES, { shader }, { shader = it }),
        Prop.Asset("Custom Sun Shader", AssetKind.SHADER, { shaderAsset }, { shaderAsset = it }),
        Prop.B("Animate Day Cycle", { animateDay }, { animateDay = it }),
        Prop.F("Day Speed", { daySpeed }, { daySpeed = it.coerceIn(0f, 20f) }, 0.1f),
    )

    override fun resetRuntime() { dayFraction = ((elevation + 90f) / 180f).coerceIn(0f, 1f) }

    /** Direction the sunlight travels (from the sun towards the scene). */
    fun lightDirection(): FloatArray {
        val el = Math.toRadians(effectiveElevation().toDouble())
        val az = Math.toRadians(azimuth.toDouble())
        // position of the sun in the sky
        val sx = (cos(el) * sin(az)).toFloat()
        val sy = sin(el).toFloat()
        val sz = (cos(el) * cos(az)).toFloat()
        return floatArrayOf(-sx, -sy, -sz)
    }

    /** Unit vector pointing from the scene towards the sun (for the sky disc). */
    fun sunDirection(): FloatArray {
        val d = lightDirection()
        return floatArrayOf(-d[0], -d[1], -d[2])
    }

    fun effectiveElevation(): Float =
        if (animateDay) -90f + dayFraction * 180f else elevation

    /** Sun colour after the Kelvin temperature tint is applied. */
    fun effectiveColor(): Int {
        val tint = kelvinToRgb(temperature)
        fun ch(shift: Int, t: Float): Int =
            (((color shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        val a = (color ushr 24) and 0xFF
        return (a shl 24) or (ch(16, tint[0]) shl 16) or (ch(8, tint[1]) shl 8) or ch(0, tint[2])
    }

    /** 0 at/below the horizon, 1 at noon — used to dim light & sky at night. */
    fun dayFactor(): Float = (sin(Math.toRadians(effectiveElevation().toDouble())).toFloat()).coerceIn(0f, 1f)

    companion object {
        /** Approximate Kelvin -> RGB multiplier (Tanner Helland algorithm). */
        fun kelvinToRgb(kelvin: Float): FloatArray {
            val t = (kelvin.coerceIn(1000f, 12000f) / 100f).toDouble()
            val r = if (t <= 66) 1.0 else (1.292936 * (t - 60).pow(-0.1332047)).coerceIn(0.0, 1.0)
            val g = if (t <= 66) ((0.3900816 * kotlin.math.ln(t) - 0.6318414).coerceIn(0.0, 1.0))
                    else (1.1298909 * (t - 60).pow(-0.0755148)).coerceIn(0.0, 1.0)
            val b = when {
                t >= 66 -> 1.0
                t <= 19 -> 0.0
                else -> (0.5432068 * kotlin.math.ln(t - 10) - 1.196254).coerceIn(0.0, 1.0)
            }
            return floatArrayOf(r.toFloat(), g.toFloat(), b.toFloat())
        }
    }
}

object SunShaders {
    val NAMES = listOf("Realistic Disc", "Soft Glow", "Golden Flare", "Moon", "Flat Cartoon")
    const val REALISTIC = 0
    const val SOFT_GLOW = 1
    const val GOLDEN_FLARE = 2
    const val MOON = 3
    const val CARTOON = 4
}

// ------------------------------------------------------------------ Terrain
/** Procedural ground: deterministic value-noise heightfield with real collision height. */
class Terrain : Component() {
    override val type = "Terrain"
    var width = 40f
    var depth = 40f
    var segments = 48
    var height = 2.5f
    var seed = 7
    var color = 0xFF6FA85C.toInt()
    var texture = ""
    var tiling = 8f
    var textureTop = ""     // optional texture used on high / flat ground
    var flatShading = false

    override fun props() = listOf(
        Prop.F("Width", { width }, { width = it.coerceIn(2f, 500f) }, 1f),
        Prop.F("Depth", { depth }, { depth = it.coerceIn(2f, 500f) }, 1f),
        Prop.I("Segments", { segments }, { segments = it.coerceIn(4, 160) }),
        Prop.F("Hill Height", { height }, { height = it.coerceIn(0f, 60f) }, 0.1f),
        Prop.I("Seed", { seed }, { seed = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.Asset("Top Texture", AssetKind.TEXTURE, { textureTop }, { textureTop = it }),
        Prop.F("Tiling", { tiling }, { tiling = it.coerceAtLeast(0.1f) }, 0.5f),
        Prop.B("Flat Shading", { flatShading }, { flatShading = it }),
    )

    /** Height of the terrain surface at world [x],[z] for an object placed at [originX],[originY],[originZ]. */
    fun heightAt(x: Float, z: Float, originX: Float = 0f, originY: Float = 0f, originZ: Float = 0f): Float {
        val lx = x - originX
        val lz = z - originZ
        if (abs(lx) > width / 2f || abs(lz) > depth / 2f) return originY
        return originY + TerrainNoise.height(lx / width + 0.5f, lz / depth + 0.5f, seed) * height
    }
}

/** Deterministic fractal value noise in 0..1 (pure Kotlin, unit-testable). */
object TerrainNoise {
    fun hash(ix: Int, iz: Int, seed: Int): Float {
        var h = ix * 374761393 + iz * 668265263 + seed * 974634211
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    fun noise(u: Float, v: Float, seed: Int, freq: Int): Float {
        val x = u * freq
        val z = v * freq
        val ix = floor(x).toInt(); val iz = floor(z).toInt()
        val fx = smooth(x - ix); val fz = smooth(z - iz)
        val a = hash(ix, iz, seed); val b = hash(ix + 1, iz, seed)
        val c = hash(ix, iz + 1, seed); val d = hash(ix + 1, iz + 1, seed)
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz
    }

    /** Fractal height 0..1; edges are flattened so terrain meets the border cleanly. */
    fun height(u: Float, v: Float, seed: Int): Float {
        var amp = 0.55f; var freq = 3; var sum = 0f; var norm = 0f
        repeat(4) {
            sum += noise(u, v, seed + it * 101, freq) * amp
            norm += amp; amp *= 0.5f; freq *= 2
        }
        val edge = min(min(u, 1f - u), min(v, 1f - v)).coerceIn(0f, 0.18f) / 0.18f
        return (sum / norm) * smooth(edge)
    }
}

// -------------------------------------------------------------------- Grass
/** Wind-blown grass field rendered as thousands of merged blades in one draw call. */
class GrassField : Component() {
    override val type = "Grass"
    var blades = 900
    var areaX = 18f
    var areaZ = 18f
    var bladeHeight = 0.75f
    var bladeWidth = 0.09f
    var colorBottom = 0xFF2E6B21.toInt()
    var colorTop = 0xFF8FE06A.toInt()
    var windStrength = 0.28f
    var windSpeed = 1.6f
    var seed = 3
    var texture = ""

    override fun props() = listOf(
        Prop.I("Blades", { blades }, { blades = it.coerceIn(0, 20000) }),
        Prop.F("Area X", { areaX }, { areaX = it.coerceIn(0.5f, 300f) }, 0.5f),
        Prop.F("Area Z", { areaZ }, { areaZ = it.coerceIn(0.5f, 300f) }, 0.5f),
        Prop.F("Blade Height", { bladeHeight }, { bladeHeight = it.coerceIn(0.05f, 5f) }, 0.05f),
        Prop.F("Blade Width", { bladeWidth }, { bladeWidth = it.coerceIn(0.01f, 1f) }, 0.01f),
        Prop.Color("Color Bottom", { colorBottom }, { colorBottom = it }),
        Prop.Color("Color Top", { colorTop }, { colorTop = it }),
        Prop.F("Wind Strength", { windStrength }, { windStrength = it.coerceIn(0f, 2f) }, 0.05f),
        Prop.F("Wind Speed", { windSpeed }, { windSpeed = it.coerceIn(0f, 10f) }, 0.1f),
        Prop.I("Seed", { seed }, { seed = it }),
        Prop.Asset("Blade Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
    )
}

// -------------------------------------------------------------------- Water
/** Animated water surface: vertex waves, fresnel shine, foam edge and flow. */
class Water : Component() {
    override val type = "Water"
    var sizeX = 16f
    var sizeZ = 16f
    var color = 0xFF2E9BC6.toInt()
    var deepColor = 0xFF0B3D66.toInt()
    var opacity = 0.82f
    var waveHeight = 0.12f
    var waveSpeed = 1.4f
    var waveScale = 0.7f
    var specular = 0.9f
    var foam = 0.35f
    var flowX = 0.03f
    var flowZ = 0.01f

    override fun props() = listOf(
        Prop.F("Size X", { sizeX }, { sizeX = it.coerceIn(0.5f, 500f) }, 0.5f),
        Prop.F("Size Z", { sizeZ }, { sizeZ = it.coerceIn(0.5f, 500f) }, 0.5f),
        Prop.Color("Shallow Color", { color }, { color = it }),
        Prop.Color("Deep Color", { deepColor }, { deepColor = it }),
        Prop.F("Opacity", { opacity }, { opacity = it.coerceIn(0.05f, 1f) }, 0.02f),
        Prop.F("Wave Height", { waveHeight }, { waveHeight = it.coerceIn(0f, 2f) }, 0.02f),
        Prop.F("Wave Speed", { waveSpeed }, { waveSpeed = it.coerceIn(0f, 10f) }, 0.1f),
        Prop.F("Wave Scale", { waveScale }, { waveScale = it.coerceIn(0.05f, 5f) }, 0.05f),
        Prop.F("Specular", { specular }, { specular = it.coerceIn(0f, 3f) }, 0.05f),
        Prop.F("Foam", { foam }, { foam = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Flow X", { flowX }, { flowX = it }, 0.01f),
        Prop.F("Flow Z", { flowZ }, { flowZ = it }, 0.01f),
    )

    /** Animated surface height at local [x],[z] and [time] seconds (matches the vertex shader). */
    fun surfaceHeight(x: Float, z: Float, time: Float): Float =
        (sin(x * waveScale + time * waveSpeed) + cos(z * waveScale * 1.3f + time * waveSpeed * 0.8f)) * 0.5f * waveHeight
}

// -------------------------------------------------------------------- Human
/**
 * One ready-made human character, drawn procedurally (head, hair, torso,
 * swinging arms and legs) — add the component and you have a person.
 * Walk automatically when the object moves, or force it from scripts
 * with `self.setWalking(true)`.
 */
class Human : Component() {
    override val type = "Human"
    var height = 1.8f
    var skinColor = 0xFFE8B08A.toInt()
    var shirtColor = 0xFF3E7CB1.toInt()
    var pantsColor = 0xFF37474F.toInt()
    var hairColor = 0xFF3B2A1E.toInt()
    var shoesColor = 0xFF212121.toInt()
    var autoWalk = true
    var walking = false
    var walkSpeed = 7f
    var swing = 0.55f

    // runtime
    var walkPhase = 0f
    var moveSpeed = 0f

    override fun props() = listOf(
        Prop.F("Height", { height }, { height = it.coerceIn(0.3f, 4f) }, 0.05f),
        Prop.Color("Skin Color", { skinColor }, { skinColor = it }),
        Prop.Color("Shirt Color", { shirtColor }, { shirtColor = it }),
        Prop.Color("Pants Color", { pantsColor }, { pantsColor = it }),
        Prop.Color("Hair Color", { hairColor }, { hairColor = it }),
        Prop.Color("Shoes Color", { shoesColor }, { shoesColor = it }),
        Prop.B("Auto Walk (when moving)", { autoWalk }, { autoWalk = it }),
        Prop.B("Walking", { walking }, { walking = it }),
        Prop.F("Walk Speed", { walkSpeed }, { walkSpeed = it.coerceIn(0.1f, 30f) }, 0.25f),
        Prop.F("Arm/Leg Swing", { swing }, { swing = it.coerceIn(0f, 1.5f) }, 0.05f),
    )

    override fun resetRuntime() { walkPhase = 0f; moveSpeed = 0f }

    val isWalking: Boolean get() = walking || (autoWalk && moveSpeed > 0.15f)
}

// ------------------------------------------------------------------ UI Panel
/**
 * Screen-space UI panel (the "panel adding system"): add to any object and it
 * is drawn in the UI layer with a background, border and optional text.
 * Position/size come from the object's transform (position = centre, scale = size in UI units).
 */
class UIPanel : Component() {
    override val type = "UIPanel"
    var color = 0xCC16202E.toInt()
    var borderColor = 0xFF4FC3F7.toInt()
    var borderWidth = 0.06f
    var text = ""
    var textColor = 0xFFFFFFFF.toInt()
    var textSize = 0.42f

    override fun props() = listOf(
        Prop.Color("Panel Color", { color }, { color = it }),
        Prop.Color("Border Color", { borderColor }, { borderColor = it }),
        Prop.F("Border Width", { borderWidth }, { borderWidth = it.coerceIn(0f, 0.5f) }, 0.01f),
        Prop.S("Text", { text }, { text = it }, multiline = true),
        Prop.Color("Text Color", { textColor }, { textColor = it }),
        Prop.F("Text Size", { textSize }, { textSize = it.coerceIn(0.05f, 3f) }, 0.02f),
    )
}

// ------------------------------------------------------- 3D particle emitter
/** True 3D particles: sphere/box emitters, 3D velocity, gravity and billboard rendering. */
class ParticleEmitter3D : Component() {
    override val type = "ParticleEmitter3D"
    var preset = 0
    var emitting = true
    var rate = 40f
    var lifetime = 1.4f
    var speed = 3f
    var speedVariance = 0.4f
    var directionY = 1f       // base direction (normalized in the engine)
    var directionX = 0f
    var directionZ = 0f
    var spread = 35f          // degrees
    var shape = 0             // 0 Point, 1 Sphere, 2 Box
    var shapeSize = 0.5f
    var startSize = 0.28f
    var endSize = 0.02f
    var sizeVariance = 0.2f
    var startColor = 0xFFFFC940.toInt()
    var endColor = 0x00FF3D00
    var gravity = -2f
    var drag = 0f
    var maxParticles = 500
    var additive = true
    var texture = ""

    class Particle3(
        var x: Float, var y: Float, var z: Float,
        var vx: Float, var vy: Float, var vz: Float,
        var age: Float, var life: Float, var sizeScale: Float = 1f, var seed: Float = 0f,
    )

    val particles = ArrayList<Particle3>()
    var accumulator = 0f
    var pendingBurst = 0

    override fun props() = listOf(
        Prop.Choice("Preset", ParticlePresets.NAMES, { preset }, { preset = it; ParticlePresets.apply3D(this, it) }),
        Prop.B("Emitting", { emitting }, { emitting = it }),
        Prop.F("Rate", { rate }, { rate = it.coerceAtLeast(0f) }, 1f),
        Prop.F("Lifetime", { lifetime }, { lifetime = it.coerceAtLeast(0.01f) }),
        Prop.F("Speed", { speed }, { speed = it }),
        Prop.F("Speed Variance", { speedVariance }, { speedVariance = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Direction X", { directionX }, { directionX = it }, 0.1f),
        Prop.F("Direction Y", { directionY }, { directionY = it }, 0.1f),
        Prop.F("Direction Z", { directionZ }, { directionZ = it }, 0.1f),
        Prop.F("Spread (deg)", { spread }, { spread = it.coerceIn(0f, 180f) }, 1f),
        Prop.Choice("Emitter Shape", listOf("Point", "Sphere", "Box"), { shape }, { shape = it }),
        Prop.F("Shape Size", { shapeSize }, { shapeSize = it.coerceAtLeast(0f) }, 0.1f),
        Prop.F("Start Size", { startSize }, { startSize = it.coerceAtLeast(0f) }, 0.01f),
        Prop.F("End Size", { endSize }, { endSize = it.coerceAtLeast(0f) }, 0.01f),
        Prop.F("Size Variance", { sizeVariance }, { sizeVariance = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.Color("Start Color", { startColor }, { startColor = it }),
        Prop.Color("End Color", { endColor }, { endColor = it }),
        Prop.F("Gravity Y", { gravity }, { gravity = it }),
        Prop.F("Drag", { drag }, { drag = it.coerceAtLeast(0f) }, 0.05f),
        Prop.I("Max Particles", { maxParticles }, { maxParticles = it.coerceIn(1, 8000) }),
        Prop.B("Additive Blend", { additive }, { additive = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
    )

    override fun resetRuntime() { particles.clear(); accumulator = 0f; pendingBurst = 0 }
}

// ------------------------------------------------------------- particle presets
/** 14 ready-made particle looks that work on both the 2D and the 3D emitter. */
object ParticlePresets {
    class Preset(
        val name: String,
        val rate: Float, val lifetime: Float, val speed: Float, val spread: Float, val direction: Float,
        val startSize: Float, val endSize: Float,
        val startColor: Int, val endColor: Int,
        val gravity: Float, val drag: Float, val additive: Boolean,
    )

    val all: List<Preset> = listOf(
        Preset("Fire", 90f, 0.8f, 2.2f, 28f, 90f, 0.34f, 0.02f, 0xFFFFC940.toInt(), 0x00E64A19, -1.5f, 0.4f, true),
        Preset("Smoke", 26f, 2.6f, 1.1f, 24f, 90f, 0.3f, 0.85f, 0x66808080, 0x001A1A1A, -0.4f, 0.25f, false),
        Preset("Explosion", 0f, 0.7f, 7f, 360f, 90f, 0.4f, 0.03f, 0xFFFFEE58.toInt(), 0x00BF360C, 2f, 1.2f, true),
        Preset("Sparks", 70f, 0.55f, 6f, 55f, 90f, 0.13f, 0.01f, 0xFFFFFF8D.toInt(), 0x00FF6D00, 7f, 0.1f, true),
        Preset("Rain", 220f, 0.75f, 11f, 6f, -90f, 0.16f, 0.16f, 0xAA90CAF9.toInt(), 0x4466BBFF, 4f, 0f, false),
        Preset("Snow", 70f, 4.5f, 1.1f, 40f, -90f, 0.15f, 0.1f, 0xFFFFFFFF.toInt(), 0x88E3F2FD.toInt(), 0.35f, 0.15f, false),
        Preset("Magic Trail", 80f, 1.1f, 0.7f, 360f, 90f, 0.26f, 0.0f, 0xFFBA68C8.toInt(), 0x004A148C, -0.5f, 0.6f, true),
        Preset("Healing", 45f, 1.8f, 1.5f, 45f, 90f, 0.22f, 0.05f, 0xFFA5FF9E.toInt(), 0x0000C853, -1.2f, 0.2f, true),
        Preset("Confetti", 0f, 2.4f, 5f, 70f, 90f, 0.17f, 0.14f, 0xFFFF4081.toInt(), 0xFF40C4FF.toInt(), 5f, 0.7f, false),
        Preset("Dust", 30f, 2.2f, 0.55f, 360f, 90f, 0.24f, 0.5f, 0x55C9B18C, 0x008D6E63, -0.15f, 0.5f, false),
        Preset("Water Splash", 110f, 0.85f, 4.6f, 48f, 90f, 0.17f, 0.03f, 0xCCB3E5FC.toInt(), 0x000288D1, 8f, 0.05f, false),
        Preset("Falling Leaves", 24f, 5f, 0.9f, 70f, -90f, 0.26f, 0.2f, 0xFF9CCC65.toInt(), 0xFF558B2F.toInt(), 0.55f, 0.7f, false),
        Preset("Stars", 50f, 1.7f, 0.45f, 360f, 90f, 0.2f, 0.0f, 0xFFFFFF9D.toInt(), 0x00FF8F00, 0f, 0.25f, true),
        Preset("Bubbles", 40f, 2.8f, 1.3f, 30f, 90f, 0.15f, 0.3f, 0x99E1F5FE.toInt(), 0x224FC3F7, -1.8f, 0.1f, false),
    )

    val NAMES: List<String> = all.map { it.name }

    fun apply2D(e: ParticleEmitter, index: Int) {
        val p = all.getOrNull(index) ?: return
        e.rate = p.rate; e.lifetime = p.lifetime; e.speed = p.speed; e.spread = p.spread; e.direction = p.direction
        e.startSize = p.startSize; e.endSize = p.endSize; e.startColor = p.startColor; e.endColor = p.endColor
        e.gravity = if (p.direction < 0) -abs(p.gravity) else p.gravity
        // 2D emitter gravity pulls down when negative in world space; presets store "down positive" for rain-like looks
        e.gravity = when (p.name) {
            "Rain" -> -14f; "Snow" -> -0.9f; "Falling Leaves" -> -1.1f
            "Fire", "Healing", "Bubbles", "Smoke", "Magic Trail" -> abs(p.gravity)
            else -> -abs(p.gravity)
        }
        e.drag = p.drag; e.additive = p.additive
    }

    fun apply3D(e: ParticleEmitter3D, index: Int) {
        val p = all.getOrNull(index) ?: return
        e.rate = p.rate; e.lifetime = p.lifetime; e.speed = p.speed; e.spread = p.spread
        e.startSize = p.startSize; e.endSize = p.endSize; e.startColor = p.startColor; e.endColor = p.endColor
        e.drag = p.drag; e.additive = p.additive
        val falling = p.direction < 0
        e.directionX = 0f; e.directionZ = 0f
        e.directionY = if (falling) -1f else 1f
        e.gravity = when (p.name) {
            "Rain" -> -6f; "Snow" -> -0.6f; "Falling Leaves" -> -0.8f
            "Fire", "Healing", "Bubbles", "Smoke", "Magic Trail" -> abs(p.gravity) * 0.4f
            else -> -abs(p.gravity)
        }
    }

    /** Small helper so scripts can ask for a preset by name ("Fire", "Rain", ...). */
    fun indexOf(name: String): Int = all.indexOfFirst { it.name.equals(name, ignoreCase = true) }
}
