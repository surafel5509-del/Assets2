package com.sengine.engine.core

/**
 * File classifications used by asset pickers, the asset browser, import menus
 * and the data-asset validator. Phase 3 adds model, animation, archive, sprite
 * and texture-metadata extensions while preserving the Phase 2 ordinals.
 */
enum class AssetKind(
    val extensions: List<String>,
    val glyph: String,
    val binary: Boolean = false,
) {
    TEXTURE(listOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "tga", "ppm", "pgm", "dds", "ktx", "ktx2", "hdr", "exr"), "IMG", true),
    SCRIPT(listOf("js", "bp"), "JS"),
    SOUND(listOf("wav", "ogg", "mp3", "m4a"), "♪", true),
    SHADER(listOf("glsl"), "GLSL"),
    ANIMATION(listOf("anim"), "▶▶"),
    MODEL(listOf("obj", "mtl", "fbx", "fbxa", "blend", "c4d", "3ds", "max", "ma", "mb", "dae", "gltf", "glb", "stl", "ply", "off", "usd", "usdz", "vox", "x", "ase", "ms3d", "md2", "md3", "md5mesh", "smd", "lwo", "lws"), "3D", true),
    PREFAB(listOf("prefab"), "PF"),
    MATERIAL(listOf("mat"), "MAT"),
    ATLAS(listOf("atlas"), "UV"),
    TILEMAP(listOf("tilemap"), "TILE"),
    SCENE(listOf("scene"), "SCN"),
    FONT(listOf("ttf", "otf"), "Aa", true),
    TEXT(listOf("txt", "json", "csv", "md", "xml"), "TXT"),
    // Phase 3 additions. New kinds are appended so existing sort order is stable.
    ANIMATION3D(listOf("anim3d", "bvh", "glanim", "motion", "clip", "skel", "skin", "fbxanim"), "ANM"),
    ARCHIVE(listOf("zip", "rar", "esr"), "ZIP", true),
    SPRITE(listOf("sprite"), "SPR"),
    TEXTURE_META(listOf("texture", "texmeta"), "TEX"),
    MODELPACK(listOf("modelpack"), "PACK"),
    BINARY(listOf("bin", "dat", "pak"), "BIN", true),
    // v3.2.0 sprite sheet workflow. New kinds are appended so existing sort order is stable.
    SHEET(listOf("sheet", "spritesheet"), "SH");

    val defaultExtension: String get() = extensions.first()

    companion object {
        fun of(fileName: String): AssetKind? = matching(fileName).firstOrNull()

        /** All kinds matching an extension. Useful for archives and sidecar data. */
        fun matching(fileName: String): List<AssetKind> {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return values().filter { ext in it.extensions }
        }

        fun ofName(name: String): AssetKind? =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) }
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
