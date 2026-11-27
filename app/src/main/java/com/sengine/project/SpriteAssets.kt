package com.sengine.project

import com.sengine.engine.anim.AnimationClip
import com.sengine.engine.render.PngCodec
import com.sengine.engine.render.TextureProfile
import com.sengine.engine.sprite.SpriteDocument
import org.json.JSONArray
import org.json.JSONObject

data class SpriteExport(
    val sprite: String,
    val png: String,
    val atlas: String,
    val animation: String,
    val textureProfile: String,
    val columns: Int,
    val rows: Int,
    val frameCount: Int,
)

/** Exports an editable sprite document as a complete runtime asset set. */
object SpriteAssets {
    fun export(project: Project, doc: SpriteDocument, name: String): SpriteExport {
        val error = doc.validate()
        require(error == null) { error ?: "invalid sprite" }
        val stem = Project.safeName(name.substringBeforeLast('.')).ifBlank { "Sprite" }
        val spriteName = if (name.endsWith(".sprite", ignoreCase = true) && Project.isSafeName(name)) name
        else project.uniqueAssetName("$stem.sprite")

        // Lay frames out in a grid so very tall/wide sprites do not create an
        // unbounded one-row sheet. 4096 px is a conservative mobile GL limit.
        val columns = minOf(doc.frameCount, (4096 / doc.width).coerceAtLeast(1))
        val rows = (doc.frameCount + columns - 1) / columns
        val sheetW = doc.width * columns
        val sheetH = doc.height * rows
        val sheet = IntArray(sheetW * sheetH)
        for (f in 0 until doc.frameCount) {
            val frame = doc.composite(f)
            val ox = (f % columns) * doc.width
            val oy = (f / columns) * doc.height
            for (y in 0 until doc.height) {
                System.arraycopy(frame, y * doc.width, sheet, (oy + y) * sheetW + ox, doc.width)
            }
        }

        val pngName = project.uniqueAssetName("$stem.png")
        project.writeBinaryAsset(pngName, PngCodec.encode(sheetW, sheetH, sheet))

        val frames = JSONObject()
        for (f in 0 until doc.frameCount) {
            val col = f % columns
            val row = f / columns
            val u0 = col.toFloat() / columns
            val u1 = (col + 1f) / columns
            val v0 = (row + 1f) / rows
            val v1 = row.toFloat() / rows
            frames.put("frame_%02d".format(f), JSONArray().put(u0.toDouble()).put(v0.toDouble()).put(u1.toDouble()).put(v1.toDouble()))
        }
        val atlas = JSONObject()
            .put("texture", pngName)
            .put("columns", columns)
            .put("rows", rows)
            .put("frames", frames)
        val atlasName = project.uniqueAssetName("$stem.atlas")
        project.writeAsset(atlasName, atlas.toString(2))

        val clip = AnimationClip(
            pngName, columns, rows,
            (0 until doc.frameCount).toMutableList(), doc.fps.toFloat(), true,
        )
        val animName = project.uniqueAssetName("$stem.anim")
        project.writeAsset(animName, clip.toJson().toString(2))

        val profile = TextureProfile.DEFAULT.copy(
            minFilter = TextureProfile.FILTER_NEAREST,
            magFilter = TextureProfile.FILTER_NEAREST,
            mipmaps = false,
            atlasColumns = columns,
            atlasRows = rows,
            spriteSheet = true,
        )
        val profileName = project.uniqueAssetName("$pngName.texture")
        project.writeAsset(profileName, profile.toJson().toString(2))
        project.writeAsset(spriteName, doc.toJson().toString(2))
        return SpriteExport(spriteName, pngName, atlasName, animName, profileName, columns, rows, doc.frameCount)
    }
}
