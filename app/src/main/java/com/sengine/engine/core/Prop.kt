package com.sengine.engine.core

/**
 * File classifications used by asset pickers, the asset browser, import menus
 * and the data-asset validator. New Big Edition kinds are text JSON/CSV unless
 * [binary] is set.
 */
enum class AssetKind(
    val extensions: List<String>,
    val glyph: String,
    val binary: Boolean = false,
) {
    TEXTURE(listOf("png", "jpg", "jpeg", "webp", "bmp"), "IMG", true),
    SCRIPT(listOf("js", "bp"), "JS"),
    SOUND(listOf("wav", "ogg", "mp3", "m4a"), "♪", true),
    SHADER(listOf("glsl"), "GLSL"),
    ANIMATION(listOf("anim"), "▶▶"),
    MODEL(listOf("obj"), "3D"),
    PREFAB(listOf("prefab"), "PF"),
    MATERIAL(listOf("mat"), "MAT"),
    ATLAS(listOf("atlas"), "UV"),
    TILEMAP(listOf("tilemap"), "TILE"),
    SCENE(listOf("scene"), "SCN"),
    FONT(listOf("ttf", "otf"), "Aa", true),
    TEXT(listOf("txt", "json", "csv", "md", "xml"), "TXT");

    val defaultExtension: String get() = extensions.first()

    companion object {
        fun of(fileName: String): AssetKind? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return values().firstOrNull { ext in it.extensions }
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
