package com.sengine.project

import com.sengine.engine.anim.AnimationClip
import com.sengine.engine.render.BitmapData
import com.sengine.engine.render.PngCodec
import com.sengine.engine.render.TextureDecoders
import com.sengine.engine.render.TextureProfile
import com.sengine.engine.sprite.SpritePacker
import com.sengine.engine.sprite.SpriteSheetDoc
import org.json.JSONArray
import org.json.JSONObject

data class SpriteSheetExport(
    val sheet: String,
    val png: String,
    val atlas: String,
    val animation: String,
    val textureProfile: String,
    val width: Int,
    val height: Int,
    val frameCount: Int,
)

/**
 * Exports a sprite-sheet document as a complete runtime asset set: a freshly
 * packed PNG sheet, a named `.atlas` (UVs + pixel rectangles), an `.anim` clip
 * that plays the named frames, a nearest-filtered `.texture` profile and the
 * updated `.sheet` document. Re-exporting the same sheet overwrites its
 * derived assets so references stay stable.
 */
object SpriteSheetAssets {
    fun export(
        project: Project,
        doc: SpriteSheetDoc,
        name: String,
        source: BitmapData? = null,
        padding: Int = 1,
        powerOfTwo: Boolean = true,
    ): SpriteSheetExport {
        val error = doc.validate()
        require(error == null) { error ?: "invalid sprite sheet" }
        require(doc.frames.isNotEmpty()) { "sprite sheet has no frames to export" }
        val src = source ?: loadSource(project, doc.texture)
            ?: throw IllegalArgumentException("cannot decode source texture ${doc.texture}")
        for (f in doc.frames) {
            require(f.x >= 0 && f.y >= 0 && f.x + f.w <= src.width && f.y + f.h <= src.height) {
                "frame ${f.name} is outside ${doc.texture}"
            }
        }

        val sizes = doc.frames.map { it.w to it.h }
        val pack = SpritePacker.pack(sizes, padding.coerceAtLeast(0), powerOfTwo)
        val sheet = IntArray(pack.width * pack.height)
        for (i in doc.frames.indices) {
            val f = doc.frames[i]
            val p = pack.placements[i]
            for (row in 0 until f.h) {
                System.arraycopy(
                    src.argb, (f.y + row) * src.width + f.x,
                    sheet, (p.y + row) * pack.width + p.x, f.w,
                )
            }
        }

        val stem = Project.safeName(name.substringBeforeLast('.')).ifBlank { "SpriteSheet" }
        val sheetName = if (name.endsWith(".sheet", ignoreCase = true) && Project.isSafeName(name)) {
            name
        } else project.uniqueAssetName("$stem.sheet")

        // Derived assets are keyed to the sheet stem and overwritten in place.
        val pngName = "$stem.png"
        project.writeBinaryAsset(pngName, PngCodec.encode(pack.width, pack.height, sheet))

        val frames = JSONObject()
        val rects = JSONObject()
        for (i in doc.frames.indices) {
            val f = doc.frames[i]
            val p = pack.placements[i]
            val u0 = p.x.toDouble() / pack.width
            val u1 = (p.x + p.w).toDouble() / pack.width
            val vBottom = (p.y + p.h).toDouble() / pack.height
            val vTop = p.y.toDouble() / pack.height
            frames.put(f.name, JSONArray().put(u0).put(vBottom).put(u1).put(vTop))
            rects.put(f.name, JSONArray().put(p.x).put(p.y).put(p.w).put(p.h))
        }
        val atlasName = "$stem.atlas"
        project.writeAsset(
            atlasName,
            JSONObject()
                .put("texture", pngName)
                .put("columns", 1)
                .put("rows", 1)
                .put("frames", frames)
                .put("rects", rects)
                .toString(2),
        )

        val clip = AnimationClip(
            texture = pngName,
            columns = 1,
            rows = 1,
            frames = mutableListOf(0),
            fps = doc.fps,
            loop = doc.loop,
            atlas = atlasName,
            frameNames = doc.frames.map { it.name }.toMutableList(),
        )
        val animName = "$stem.anim"
        project.writeAsset(animName, clip.toJson().toString(2))

        val profile = TextureProfile.DEFAULT.copy(
            minFilter = TextureProfile.FILTER_NEAREST,
            magFilter = TextureProfile.FILTER_NEAREST,
            mipmaps = false,
            atlasColumns = 1,
            atlasRows = 1,
            spriteSheet = true,
        )
        val profileName = "$pngName.texture"
        project.writeAsset(profileName, profile.toJson().toString(2))

        project.writeAsset(sheetName, doc.toJson().toString(2))
        return SpriteSheetExport(sheetName, pngName, atlasName, animName, profileName, pack.width, pack.height, doc.frames.size)
    }

    /** Headless decode of the source texture through the pure-JVM decoders. */
    private fun loadSource(project: Project, texture: String): BitmapData? {
        if (texture.isBlank()) return null
        val f = project.assetFile(texture)
        if (!f.isFile) return null
        return try {
            TextureDecoders.decodeBitmapData(f.name, f.readBytes())
        } catch (_: Exception) {
            null
        }
    }
}
