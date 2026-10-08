package com.sengine.project

import android.content.Context
import com.sengine.engine.core.AssetKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The shipped game library.
 *
 * The four games live in the repository under `games/<id>/` as plain text --
 * `game.json`, `scenes/*.scene.json` and `scripts/*.js` -- and are staged into
 * the APK's assets by the `stageGameLibrary` Gradle task.  They are *not*
 * templates: a template is a code-only starting point, whereas these are
 * complete games whose art is imported from the offline Asset Store on demand.
 *
 * Splitting them this way is deliberate.  Keeping the games as data means
 * `tools/validate_games.py` can check every asset reference against the store
 * manifest in CI without an Android toolchain, and it means adding a game never
 * touches Kotlin.
 *
 * Materialising a game therefore has two halves:
 *   1. copy the scene and script text into a new project (immediate, offline),
 *   2. import the declared assets out of their store packs, which may need to
 *      extract a ZIP first -- so it reports progress and can fail per-entry.
 *
 * A game whose assets have not been imported yet still opens in the editor; the
 * sprites simply fall back to placeholder quads.  That keeps the editor usable
 * when a pack has not been extracted.
 */
object GameLibrary {

    private const val ASSET_ROOT = "games"

    class Game(
        val id: String,
        val title: String,
        val genre: String,
        val orientation: Int,
        val startScene: String,
        val scenes: List<String>,
        val scripts: List<String>,
        val assetCount: Int,
        val packIds: List<String>,
        val raw: JSONObject,
    ) {
        /** Human-readable summary for the project picker. */
        val subtitle: String get() = "$genre  •  $assetCount assets from ${packIds.size} packs"
    }

    /** What happened when a game was written into a project. */
    class Result(
        val project: Project?,
        val scenesWritten: Int,
        val scriptsWritten: Int,
        val assetsImported: Int,
        val clipsGenerated: Int,
        val errors: List<String>,
    ) {
        val ok get() = errors.isEmpty() && project != null
    }

    private var cache: List<Game>? = null

    /** All shipped games, in the order they appear in the manifest index. */
    val entries: List<Game>
        get() = cache ?: emptyList()

    /**
     * Reads the library index.  Cheap enough to call on the UI thread; the index
     * is one small JSON file, not the asset payloads.
     */
    fun load(ctx: Context): List<Game> {
        cache?.let { return it }
        val out = ArrayList<Game>()
        try {
            val index = readAssetText(ctx, "$ASSET_ROOT/index.json")
            if (index != null) {
                JSONArray(index).let { arr ->
                    for (i in 0 until arr.length()) {
                        val id = arr.optString(i)
                        readGame(ctx, id)?.let { out.add(it) }
                    }
                }
            } else {
                // Fall back to probing the known ids so a stale index cannot hide
                // a game that is actually present.
                for (id in KNOWN_IDS) readGame(ctx, id)?.let { out.add(it) }
            }
        } catch (e: Exception) {
            android.util.Log.w("GameLibrary", "index load failed: ${e.message}")
        }
        cache = out
        return out
    }

    fun byId(id: String): Game? = entries.firstOrNull { it.id == id }

    private fun readGame(ctx: Context, id: String): Game? {
        val text = readAssetText(ctx, "$ASSET_ROOT/$id/game.json") ?: return null
        return try {
            val o = JSONObject(text)
            Game(
                id = o.optString("id", id),
                title = o.optString("title", id),
                genre = o.optString("genre", ""),
                orientation = o.optInt("orientation", 0),
                startScene = o.optString("startScene", "Main"),
                scenes = o.optJSONArray("scenes").asStringList(),
                scripts = o.optJSONArray("scripts").asStringList(),
                assetCount = o.optJSONArray("assets")?.length() ?: 0,
                packIds = o.optJSONArray("assets").stringSet { it.optString("pack") },
                raw = o,
            )
        } catch (e: Exception) {
            android.util.Log.w("GameLibrary", "bad game.json for $id: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------ create

    /**
     * Writes a game into a new project under [ProjectManager]'s root.
     *
     * `importAssets` is separable because it is the slow half: extracting a pack
     * touches the filesystem and can take a second or two.  The editor can create
     * the project instantly and import in the background.
     */
    fun materialize(
        ctx: Context,
        game: Game,
        importAssets: Boolean = true,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): Result {
        val dir = File(ProjectManager.root(ctx), game.title)
        val project = Project(dir)
        val errors = ArrayList<String>()

        project.dir.mkdirs(); project.assetsDir.mkdirs(); project.scenesDir.mkdirs()
        project.startScene = game.startScene
        project.orientation = game.orientation

        var scenes = 0
        var scripts = 0

        for (name in game.scenes) {
            val text = readAssetText(ctx, "$ASSET_ROOT/${game.id}/scenes/$name.scene.json")
            if (text == null) { errors.add("missing scene: $name"); continue }
            project.sceneFile(name).writeText(text)
            scenes++
        }
        if (scenes == 0) errors.add("no scenes could be written")

        for (name in game.scripts) {
            val text = readAssetText(ctx, "$ASSET_ROOT/${game.id}/scripts/$name")
            if (text == null) { errors.add("missing script: $name"); continue }
            project.writeAsset(name, text)
            scripts++
        }

        project.saveMeta()

        var imported = 0
        var clips = 0
        if (importAssets) {
            val r = importDeclaredAssets(ctx, project, game, onProgress)
            imported = r.first; clips = r.second; errors.addAll(r.third)
        }

        return Result(project, scenes, scripts, imported, clips, errors)
    }

    /**
     * Imports every asset declared in `game.json`, grouped by pack so each pack
     * is extracted at most once.
     *
     * Assets are imported under their declared `as` name rather than their store
     * path: the scenes reference the short names, and several packs contain files
     * with colliding basenames.
     */
    private fun importDeclaredAssets(
        ctx: Context,
        project: Project,
        game: Game,
        onProgress: ((Int, Int) -> Unit)?,
    ): Triple<Int, Int, List<String>> {
        val arr = game.raw.optJSONArray("assets")
            ?: return Triple(0, 0, emptyList())

        val errors = ArrayList<String>()
        var imported = 0
        var clips = 0

        val store = AssetStore
        val manifest = store.load(ctx)
        if (manifest.packs.isEmpty()) {
            return Triple(0, 0, listOf("asset store manifest is empty"))
        }

        // packId -> list of (entry, targetName)
        val byPack = LinkedHashMap<String, MutableList<Pair<AssetStore.Entry, String>>>()
        // Named clips declared per asset.  importInto only ever emits a single
        // whole-sheet clip, so these are written separately below.
        val declaredClips = ArrayList<Triple<String, JSONObject, JSONObject>>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val packId = a.optString("pack")
            val path = a.optString("path")
            val target = a.optString("as").ifBlank { path.substringAfterLast('/') }
            val pack = manifest.packs.firstOrNull { it.id == packId }
            if (pack == null) { errors.add("unknown pack '$packId' for '$target'"); continue }
            val entry = pack.files.firstOrNull { it.path == path }
            if (entry == null) { errors.add("'$path' not found in pack '$packId'"); continue }
            byPack.getOrPut(packId) { ArrayList() }.add(entry to target)

            // `anim` is a store-detected animation grid; `tileset` is a hand-declared
            // tile atlas grid, which the store does not report as a sheet.  Either
            // can carry named clips.
            val grid = a.optJSONObject("anim") ?: a.optJSONObject("tileset")
            val clipMap = a.optJSONObject("clips")
            if (grid != null && clipMap != null) declaredClips += Triple(target, grid, clipMap)
        }

        val total = byPack.values.sumOf { it.size }
        var done = 0
        for ((packId, wanted) in byPack) {
            val pack = manifest.packs.first { it.id == packId }
            if (!pack.distributable) {
                // A pack the engine may not redistribute must never be bundled,
                // so it can only be imported if the user supplied it themselves.
                errors.add("pack '$packId' is not redistributable; import it manually")
                done += wanted.size
                continue
            }
            val r = store.importInto(ctx, project, pack, wanted.map { it.first }) { a, b ->
                onProgress?.invoke(done + a, total)
            }
            imported += r.imported
            clips += r.clips
            errors.addAll(r.errors)
            done += wanted.size
            onProgress?.invoke(done, total)

            // Rename to the declared short names.  importInto keeps store
            // basenames, which the scene files do not reference.
            for ((entry, target) in wanted) {
                val landed = project.assetFile(entry.name)
                if (landed.exists() && entry.name != target) {
                    val dest = project.assetFile(target)
                    if (!dest.exists()) landed.renameTo(dest)
                }
            }
        }
        // ------------------------------------------------------------ clips
        // A declared clip becomes "<name>.anim" -- the name AnimationSystem.clip()
        // looks up.  Without this step self.play("walk_down") resolves to nothing
        // and the sprite silently draws its whole sheet.
        for ((texture, grid, clipMap) in declaredClips) {
            val columns = grid.optInt("columns", 1).coerceAtLeast(1)
            val rows = grid.optInt("rows", 1).coerceAtLeast(1)
            for (key in clipMap.keys()) {
                val c = clipMap.optJSONObject(key) ?: continue
                val frames = c.optJSONArray("frames")
                if (frames == null || frames.length() == 0) continue
                val out = JSONObject()
                // A clip may pin its own texture, or declare an empty one to mean
                // "whatever the SpriteRenderer is showing".  The blank form is what
                // lets a single "idle" clip serve every skin: AnimationClip sets
                // animTexture to null for it and SceneRenderer falls back to
                // sr.texture, which setTexture() has already pointed at the skin.
                out.put("texture", if (c.has("texture")) c.optString("texture") else texture)
                out.put("columns", columns)
                out.put("rows", rows)
                out.put("fps", c.optDouble("fps", 8.0))
                out.put("loop", c.optBoolean("loop", true))
                val fa = JSONArray()
                for (f in 0 until frames.length()) fa.put(frames.getInt(f))
                out.put("frames", fa)
                project.writeAsset("$key.anim", out.toString(2))
                clips++
            }
        }

        return Triple(imported, clips, errors)
    }

    /** Deletes a materialised game project. */
    fun remove(ctx: Context, game: Game): Boolean {
        val dir = File(ProjectManager.root(ctx), game.title)
        return dir.exists() && dir.deleteRecursively()
    }

    // ------------------------------------------------------------------ helpers

    private fun readAssetText(ctx: Context, path: String): String? = try {
        ctx.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }

    private fun JSONArray?.asStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }

    private fun JSONArray?.stringSet(pick: (JSONObject) -> String): List<String> {
        if (this == null) return emptyList()
        val out = LinkedHashSet<String>()
        for (i in 0 until length()) optJSONObject(i)?.let { out.add(pick(it)) }
        out.remove("")
        return out.toList()
    }

    /** Probed when `index.json` is absent, so the library still loads. */
    private val KNOWN_IDS = listOf(
        "01_topdown_rpg",
        "02_fighting_2d",
        "03_action_3d",
        "04_runner_3d",
    )
}
