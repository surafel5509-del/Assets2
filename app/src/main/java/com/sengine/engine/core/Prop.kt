package com.sengine.engine.core

enum class AssetKind(val extensions: List<String>) {
    TEXTURE(listOf("png", "jpg", "jpeg", "webp", "bmp")),
    SCRIPT(listOf("js", "bp")),
    SOUND(listOf("wav", "ogg", "mp3", "m4a")),
    SHADER(listOf("glsl")),
    ANIMATION(listOf("anim")),
    MODEL(listOf("obj")),
    /** UI Builder screen layouts. Compound suffix, so it is matched on the whole name. */
    UI(listOf("ui.json")),
    /** On-screen control layouts. */
    CONTROLS(listOf("ctrl.json"));

    companion object {
        /**
         * The kind of an asset from its file name. Compound suffixes such as
         * ".ui.json" are matched on the whole name, and a path ("UI/hud.ui.json")
         * is accepted as well as a bare name.
         */
        fun of(fileName: String): AssetKind? {
            val lower = fileName.substringAfterLast('/').lowercase()
            for (k in values()) for (ext in k.extensions) if (lower.endsWith(".$ext")) return k
            return null
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
