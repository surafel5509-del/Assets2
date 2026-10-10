package com.sengine.engine.render

/**
 * Built-in shader presets for the Shader Library. Each one is a complete
 * `.glsl` asset body: a single `vec4 effect(vec4 color, vec2 uv)` function.
 *
 * Presets use only the uniforms every shader stage provides, so they work on
 * sprites, meshes and camera post-processing alike:
 *   uTime        seconds since play started
 *   uParam       the component's "Shader Param" (intensity, 1 by default)
 *   uTex         the texture (post effects: the rendered frame)
 *   uResolution  the target size in pixels
 *
 * Sources contain no `$`, so they can be stored and embedded without escaping.
 */
object ShaderPresets {

    data class Preset(val name: String, val category: String, val description: String, val source: String) {
        /** Project asset path, e.g. `Shaders/hit_flash.glsl`. */
        val fileName: String get() = "Shaders/" + slug(name) + ".glsl"
    }

    val CATEGORIES = listOf("Basic", "Sprites", "Effects", "Post")

    val ALL: List<Preset> = listOf(
        Preset("Passthrough", "Basic", "Draws the object unchanged. A starting point.",
            """
vec4 effect(vec4 color, vec2 uv) {
    return color;
}
"""),
        Preset("Pulse Glow", "Basic", "Gently breathes the brightness over time.",
            """
vec4 effect(vec4 color, vec2 uv) {
    float pulse = 0.75 + 0.25 * sin(uTime * 3.0 * uParam);
    return vec4(color.rgb * pulse, color.a);
}
"""),
        Preset("Hit Flash", "Sprites", "Flashes white when uParam goes 0 to 1 (set from a script on damage).",
            """
vec4 effect(vec4 color, vec2 uv) {
    float k = clamp(uParam, 0.0, 1.0);
    return vec4(mix(color.rgb, vec3(1.0), k), color.a);
}
"""),
        Preset("Dissolve", "Sprites", "Burns the object away. uParam 0 is solid, 1 is gone.",
            """
vec4 effect(vec4 color, vec2 uv) {
    float n = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    float edge = smoothstep(0.0, 0.08, n - clamp(uParam, 0.0, 1.0));
    vec3 burn = mix(vec3(1.0, 0.6, 0.1), vec3(1.0, 0.1, 0.0), smoothstep(0.0, 0.08, n - clamp(uParam, 0.0, 1.0) - 0.04));
    vec3 rgb = mix(burn, color.rgb, edge);
    return vec4(rgb, color.a * edge);
}
"""),
        Preset("Damage Vignette", "Effects", "Red edges that grow with uParam. Good for low health.",
            """
vec4 effect(vec4 color, vec2 uv) {
    float d = distance(uv, vec2(0.5)) * 1.4;
    float v = smoothstep(0.35, 0.9, d) * clamp(uParam, 0.0, 1.0);
    return vec4(mix(color.rgb, vec3(0.8, 0.0, 0.05), v), color.a);
}
"""),
        Preset("Hologram", "Effects", "Cyan scanlines with a faint flicker.",
            """
vec4 effect(vec4 color, vec2 uv) {
    float lines = 0.85 + 0.15 * sin(uv.y * uResolution.y * 1.5);
    float flicker = 0.95 + 0.05 * sin(uTime * 47.0);
    float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 tint = vec3(0.35, 0.9, 1.0) * lum;
    return vec4(tint * lines * flicker * (0.6 + 0.4 * uParam), color.a * 0.9);
}
"""),
        Preset("Heat Haze", "Effects", "Wobbles the picture like rising heat.",
            """
vec4 effect(vec4 color, vec2 uv) {
    vec2 off = vec2(sin(uv.y * 40.0 + uTime * 3.0), 0.0) * 0.004 * uParam;
    return texture2D(uTex, uv + off);
}
"""),
        Preset("Ripple", "Post", "Concentric ripples radiating from the centre.",
            """
vec4 effect(vec4 color, vec2 uv) {
    vec2 q = uv - vec2(0.5);
    float d = length(q);
    vec2 off = normalize(q + vec2(0.0001)) * sin(d * 60.0 - uTime * 6.0) * 0.006 * uParam;
    return texture2D(uTex, uv + off);
}
"""),
        Preset("Night Vision", "Post", "Green phosphor look with noise grain.",
            """
vec4 effect(vec4 color, vec2 uv) {
    vec3 src = texture2D(uTex, uv).rgb;
    float lum = dot(src, vec3(0.299, 0.587, 0.114));
    float grain = fract(sin(dot(uv + uTime, vec2(12.9898, 78.233))) * 43758.5453);
    vec3 green = vec3(0.1, 1.0, 0.25) * lum * (0.9 + 0.2 * grain);
    return vec4(mix(src, green, clamp(uParam, 0.0, 1.0)), 1.0);
}
"""),
        Preset("Toon Posterize", "Post", "Flattens colours into bands, like cel shading.",
            """
vec4 effect(vec4 color, vec2 uv) {
    vec3 src = texture2D(uTex, uv).rgb;
    float levels = max(2.0, 4.0 + 4.0 * (1.0 - clamp(uParam, 0.0, 1.0)));
    return vec4(floor(src * levels + 0.5) / levels, 1.0);
}
"""),
        Preset("Lava Glow", "Effects", "Animated orange-to-yellow heat ramp.",
            """
vec4 effect(vec4 color, vec2 uv) {
    float wave = 0.5 + 0.5 * sin(uv.x * 9.0 + uTime * 2.0) * cos(uv.y * 7.0 - uTime * 1.5);
    vec3 hot = mix(vec3(0.6, 0.05, 0.0), vec3(1.0, 0.85, 0.2), wave);
    return vec4(mix(color.rgb, hot, 0.6 * clamp(uParam, 0.0, 1.0)), color.a);
}
"""),
        Preset("Sepia Tone", "Post", "Warm old-photograph colours.",
            """
vec4 effect(vec4 color, vec2 uv) {
    vec3 src = texture2D(uTex, uv).rgb;
    vec3 s = vec3(dot(src, vec3(0.393, 0.769, 0.189)), dot(src, vec3(0.349, 0.686, 0.168)), dot(src, vec3(0.272, 0.534, 0.131)));
    return vec4(mix(src, s, clamp(uParam, 0.0, 1.0)), 1.0);
}
"""),
    )

    fun byName(name: String): Preset? = ALL.firstOrNull { it.name == name }

    fun inCategory(category: String): List<Preset> = ALL.filter { it.category == category }

    /** File-safe lower-case slug: "Hit Flash" becomes "hit_flash". */
    fun slug(name: String): String =
        name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_')
            .replace(Regex("_+"), "_")
}
