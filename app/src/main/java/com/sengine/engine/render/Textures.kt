package com.sengine.engine.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import com.sengine.project.Project
import java.io.File

class Tex(val id: Int, val w: Int, val h: Int)

/** GL texture caches. Must only be used on the GL thread. */
class TextureCache(private val project: Project) {
    private val images = HashMap<String, Tex?>()
    private val stamps = HashMap<String, Long>()
    private val texts = LinkedHashMap<String, Tex>(64, 0.75f, true)
    private val fonts = HashMap<String, Typeface>()

    /** Discard cached ids after an EGL context loss (old ids belong to the lost context). */
    fun clear() { images.clear(); stamps.clear(); texts.clear(); fonts.clear() }

    /** Delete every live GL texture before the owning context is destroyed. */
    fun release() {
        for (tex in images.values) tex?.let { GLES20.glDeleteTextures(1, intArrayOf(it.id), 0) }
        for (tex in texts.values) GLES20.glDeleteTextures(1, intArrayOf(tex.id), 0)
        clear()
    }

    fun image(name: String): Tex? {
        if (name.isBlank()) return null
        val f: File = project.assetFile(name)
        val profile = TextureProfile.load(project, name)
        val stamp = f.lastModified() * 31L + TextureProfile.stamp(project, name)
        if (images.containsKey(name) && stamps[name] == stamp) return images[name]
        images[name]?.let { GLES20.glDeleteTextures(1, intArrayOf(it.id), 0) }
        val bmp = TextureDecoders.loadBitmap(f)
        val scaled = bmp?.let { scaledForUpload(it, profile.maxSize) }
        val tex = scaled?.let { upload(it, profile) }
        if (scaled != null && scaled !== bmp) recycle(scaled)
        if (bmp != null) recycle(bmp)
        images[name] = tex
        stamps[name] = stamp
        return tex
    }

    fun text(text: String, bold: Boolean, align: Int, fontName: String = ""): Tex {
        val key = "$bold|$align|$fontName|$text"
        texts[key]?.let { return it }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textSize = 72f
        paint.typeface = loadTypeface(bold, fontName)
        val lines = text.split('\n')
        val fm = paint.fontMetrics
        val lineH = (fm.descent - fm.ascent)
        val widths = lines.map { paint.measureText(it) }
        val w = (widths.maxOrNull() ?: 1f).coerceAtLeast(1f).toInt() + 8
        val h = (lineH * lines.size).toInt().coerceAtLeast(1) + 4
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        for ((i, line) in lines.withIndex()) {
            val x = when (align) {
                0 -> 4f
                2 -> w - 4f - widths[i]
                else -> (w - widths[i]) / 2f
            }
            c.drawText(line, x, 2f - fm.ascent + i * lineH, paint)
        }
        val tex = upload(bmp, TextureProfile.DEFAULT)
        bmp.recycle()
        texts[key] = tex
        if (texts.size > 128) {
            val eldest = texts.entries.iterator().next()
            GLES20.glDeleteTextures(1, intArrayOf(eldest.value.id), 0)
            texts.remove(eldest.key)
        }
        return tex
    }

    /** Custom .ttf/.otf assets override the default bold/regular face; failures fall back safely. */
    private fun loadTypeface(bold: Boolean, fontName: String): Typeface {
        if (fontName.isBlank()) return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val f = project.assetFile(fontName)
        if (!f.isFile) return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val key = "$fontName|${f.lastModified()}"
        fonts[key]?.let { return it }
        return try {
            Typeface.createFromFile(f).also { fonts[key] = it }
        } catch (_: Throwable) {
            if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    /** Uploads with Phase 3 wrap/filter/mipmap settings from a .texture profile. */
    private fun upload(bmp: Bitmap, profile: TextureProfile): Tex {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        val minFilter = when {
            profile.minFilter == TextureProfile.FILTER_NEAREST -> GLES20.GL_NEAREST
            profile.mipmaps -> GLES20.GL_LINEAR_MIPMAP_LINEAR
            else -> GLES20.GL_LINEAR
        }
        val magFilter = if (profile.magFilter == TextureProfile.FILTER_NEAREST) GLES20.GL_NEAREST else GLES20.GL_LINEAR
        fun wrap(mode: Int) = when (mode) {
            TextureProfile.WRAP_REPEAT -> GLES20.GL_REPEAT
            TextureProfile.WRAP_MIRROR -> GLES20.GL_MIRRORED_REPEAT
            else -> GLES20.GL_CLAMP_TO_EDGE
        }
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, minFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, magFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap(profile.wrapS))
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap(profile.wrapT))
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        if (profile.mipmaps) GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return Tex(ids[0], bmp.width, bmp.height)
    }

    private fun scaledForUpload(bmp: Bitmap, maxSize: Int): Bitmap {
        val largest = maxOf(bmp.width, bmp.height)
        if (largest <= maxSize) return bmp
        return try {
            val s = maxSize.toFloat() / largest
            Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), false)
        } catch (_: Throwable) { bmp }
    }

    private fun recycle(bmp: Bitmap) {
        try { bmp.recycle() } catch (_: Throwable) {}
    }
}
