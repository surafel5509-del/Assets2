package com.sengine.project

/**
 * The project asset folder hierarchy.
 *
 * Every project keeps its assets under `assets/`, organised in branches:
 *
 *   assets/
 *     Scripts/            .js behaviours
 *     Blueprints/         .bp visual-script graphs
 *     Audio/
 *       SFX/              short sounds (.wav .ogg .mp3 .m4a)
 *       Music/            looping tracks
 *     Sprites/            imported images and sprite-sheet sources
 *       Studio/           images exported from the Sprite Editor Studio
 *     Animations/         .anim sprite-sheet clips
 *     Shaders/            .glsl effect shaders (see render/ShaderPresets.kt)
 *     Models/             .obj meshes
 *     UI/                 .ui.json screen layouts (UI Builder)
 *     Controls/           .ctrl.json on-screen control layouts
 *     Data/               .json / .csv / .txt game data
 *
 * Asset references are *relative paths* ("Sprites/hero.png"). A file that was
 * imported before folders existed sits at the root ("hero.png") and remains
 * valid, so nothing that already references an asset by name breaks.
 *
 * This file is pure Kotlin (no Android types) so the placement rules can be
 * unit-tested on the host.
 */
object AssetFolders {

    /** One branch of the tree. [path] is relative to `assets/`. */
    data class Folder(val path: String, val label: String, val description: String)

    val SCRIPTS = Folder("Scripts", "Scripts", "JavaScript behaviours (.js)")
    val BLUEPRINTS = Folder("Blueprints", "Blueprints", "Visual-script graphs (.bp)")
    val SFX = Folder("Audio/SFX", "Sound Effects", "Short one-shot sounds")
    val MUSIC = Folder("Audio/Music", "Music", "Looping background tracks")
    val SPRITES = Folder("Sprites", "Sprites", "Imported images and sprite sheets")
    val STUDIO = Folder("Sprites/Studio", "Sprite Studio", "Images made in the Sprite Editor Studio")
    val ANIMATIONS = Folder("Animations", "Animations", "Sprite-sheet clips (.anim)")
    val SHADERS = Folder("Shaders", "Shaders", "Effect shaders (.glsl)")
    val MODELS = Folder("Models", "3D Models", "Meshes (.obj)")
    val UI = Folder("UI", "UI Screens", "Screen layouts from the UI Builder (.ui.json)")
    val CONTROLS = Folder("Controls", "Game Controls", "On-screen control layouts (.ctrl.json)")
    val DATA = Folder("Data", "Data", "Game data: JSON, CSV, text")

    /** Every folder, in the order the tree shows them. */
    val ALL: List<Folder> = listOf(
        SCRIPTS, BLUEPRINTS, SFX, MUSIC, SPRITES, STUDIO,
        ANIMATIONS, SHADERS, MODELS, UI, CONTROLS, DATA,
    )

    private val IMAGE = setOf("png", "jpg", "jpeg", "webp", "bmp")
    private val SOUND = setOf("wav", "ogg", "mp3", "m4a")
    private val MUSIC_HINTS = listOf("music", "theme", "bgm", "song", "track", "ambience", "ambient")

    /**
     * The folder a new file belongs in, decided by its name.
     * Returns a path relative to `assets/` (no trailing slash).
     */
    fun folderFor(fileName: String, studioOrigin: Boolean = false): String {
        val base = fileName.substringAfterLast('/').lowercase()
        return when {
            base.endsWith(".ui.json") -> UI.path
            base.endsWith(".ctrl.json") -> CONTROLS.path
            base.endsWith(".js") -> SCRIPTS.path
            base.endsWith(".bp") -> BLUEPRINTS.path
            base.endsWith(".anim") -> ANIMATIONS.path
            base.endsWith(".glsl") -> SHADERS.path
            base.endsWith(".sprite") -> STUDIO.path
            base.endsWith(".obj") -> MODELS.path
            base.endsWith(".json") || base.endsWith(".csv") || base.endsWith(".txt") -> DATA.path
            base.substringAfterLast('.') in SOUND -> {
                val stem = base.substringBeforeLast('.')
                if (MUSIC_HINTS.any { stem.contains(it) }) MUSIC.path else SFX.path
            }
            base.substringAfterLast('.') in IMAGE -> if (studioOrigin) STUDIO.path else SPRITES.path
            else -> DATA.path
        }
    }

    /** Joins a folder and a file name, avoiding doubled slashes. An empty folder means the root. */
    fun join(folder: String, fileName: String): String {
        val f = folder.trim('/')
        return if (f.isEmpty()) fileName else "$f/$fileName"
    }

    /** The folder part of a relative asset path ("" for root-level files). */
    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    /** The file-name part of a relative asset path. */
    fun nameOf(path: String): String = path.substringAfterLast('/')

    /**
     * True when [path] is a safe relative asset path: no absolute roots, no
     * `..` segments, no empty or backslash segments. Used to refuse zip-slip
     * style names and to keep every asset inside `assets/`.
     */
    fun isSafeRelative(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.contains('\\')) return false
        return path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
    }

    /** Every ancestor folder of [path], shallowest first ("A/B/c.png" -> ["A", "A/B"]). */
    fun ancestorsOf(path: String): List<String> {
        val parts = path.split('/').dropLast(1)
        val out = ArrayList<String>(parts.size)
        var acc = ""
        for (p in parts) {
            acc = if (acc.isEmpty()) p else "$acc/$p"
            out.add(acc)
        }
        return out
    }

    /**
     * A file name that does not collide with [exists]. "hero.png" becomes
     * "hero_1.png", then "hero_2.png", and so on, keeping the folder part.
     */
    fun unique(path: String, exists: (String) -> Boolean): String {
        if (!exists(path)) return path
        val folder = parentOf(path)
        val name = nameOf(path)
        val stem = name.removeSuffixCompound()
        val ext = name.substring(stem.length)
        var i = 1
        while (true) {
            val candidate = join(folder, "${stem}_$i$ext")
            if (!exists(candidate)) return candidate
            i++
        }
    }

    /**
     * The extension including compound suffixes: "hero.ui.json" -> ".ui.json",
     * "hero.png" -> ".png", "README" -> "".
     */
    private fun String.removeSuffixCompound(): String {
        for (suffix in listOf(".ui.json", ".ctrl.json")) if (endsWith(suffix)) return removeSuffix(suffix)
        val dot = lastIndexOf('.')
        return if (dot <= 0) this else substring(0, dot)
    }
}
