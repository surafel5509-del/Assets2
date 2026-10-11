package com.sengine.engine.core

enum class AssetKind(val extensions: List<String>) {
    TEXTURE(listOf("png", "jpg", "jpeg", "webp", "bmp", "tga", "psd", "hdr", "exr", "cubemap")),
    SCRIPT(listOf("js", "bp", "cs", "lua", "py")),
    SOUND(listOf("wav", "ogg", "mp3", "m4a", "flac", "aiff")),
    SHADER(listOf("glsl", "hlsl", "shader", "shadergraph", "vfx")),
    ANIMATION(listOf("anim", "controller", "fbx", "animclip")),
    MODEL(listOf("obj", "fbx", "gltf", "glb", "dae", "blend")),
    MATERIAL(listOf("mat", "material", "matgraph")),
    FONT(listOf("ttf", "otf", "font", "tmp")),
    PREFAB(listOf("prefab", "prefab.json")),
    SCENE(listOf("scene", "scene.json", "unity")),
    VIDEO(listOf("mp4", "mov", "webm", "avi")),
    TERRAIN(listOf("terrain", "terrainlayer", "heightmap", "splatmap")),
    PHYSICS(listOf("physmat", "physicMaterial", "collider")),
    NAVMESH(listOf("navmesh", "navarea")),
    LIGHTING(listOf("lighting", "lightmap", "probe", "reflectionprobe")),
    POSTFX(listOf("postprocess", "volume", "lut")),
    VFX(listOf("vfx", "vfxgraph", "particle", "trail")),
    AUDIO_MIXER(listOf("mixer", "mixergroup", "snapshot")),
    INPUT(listOf("inputactions", "input", "control")),
    TIMELINE(listOf("timeline", "playable", "signal"));

    companion object {
        fun of(fileName: String): AssetKind? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return values().firstOrNull { ext in it.extensions }
        }
    }
}

/** Editable / serializable property descriptor used by the Inspector and by the serializer. */
sealed class Prop(val name: String) {
    class F(name: String, val get: () -> Float, val set: (Float) -> Unit, val step: Float = 0.1f) : Prop(name)
    class I(name: String, val get: () -> Int, val set: (Int) -> Unit) : Prop(name)
    class B(name: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : Prop(name)
    class S(name: String, val get: () -> String, val set: (String) -> Unit, val multiline: Boolean = false) : Prop(name)
    class Color(name: String, val get: () -> Int, val set: (Int) -> Unit) : Prop(name)
    class Choice(name: String, val options: List<String>, val get: () -> Int, val set: (Int) -> Unit) : Prop(name)
    class Asset(name: String, val kind: AssetKind, val get: () -> String, val set: (String) -> Unit) : Prop(name)
}
