package com.sengine.engine.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sengine.project.Project
import org.json.JSONObject
import java.io.File
import kotlin.math.pow

/** Per-image import settings. Stored as a validated .texture JSON asset. */
data class TextureProfile(
    var wrapS: Int = 1,
    var wrapT: Int = 1,
    var minFilter: Int = 1,
    var magFilter: Int = 1,
    var mipmaps: Boolean = true,
    var srgb: Boolean = true,
    var premultiply: Boolean = false,
    var maxSize: Int = 2048,
    var alphaCutoff: Float = 0f,
    var compression: String = "auto",
    var atlasColumns: Int = 1,
    var atlasRows: Int = 1,
    var spriteSheet: Boolean = false,
) {
    companion object {
        const val WRAP_REPEAT = 0
        const val WRAP_CLAMP = 1
        const val WRAP_MIRROR = 2
        const val FILTER_NEAREST = 0
        const val FILTER_LINEAR = 1

        val DEFAULT = TextureProfile()

        fun fromJson(o: JSONObject): TextureProfile = TextureProfile(
            o.optInt("wrapS", 1).coerceIn(0, 2),
            o.optInt("wrapT", 1).coerceIn(0, 2),
            o.optInt("minFilter", 1).coerceIn(0, 1),
            o.optInt("magFilter", 1).coerceIn(0, 1),
            o.optBoolean("mipmaps", true),
            o.optBoolean("srgb", true),
            o.optBoolean("premultiply", false),
            o.optInt("maxSize", 2048).coerceIn(16, 8192),
            o.optDouble("alphaCutoff", 0.0).toFloat().coerceIn(0f, 1f),
            o.optString("compression", "auto"),
            o.optInt("atlasColumns", 1).coerceIn(1, 256),
            o.optInt("atlasRows", 1).coerceIn(1, 256),
            o.optBoolean("spriteSheet", false),
        )

        fun load(project: Project, textureName: String): TextureProfile {
            val stem = textureName.substringBeforeLast('.')
            for (candidate in candidates(textureName)) {
                val f = project.assetFile(candidate)
                if (!f.isFile) continue
                return try { fromJson(JSONObject(f.readText())) } catch (_: Exception) { DEFAULT }
            }
            return DEFAULT
        }

        /** Cache stamp includes the profile sidecar so settings edits reload the GL texture. */
        fun stamp(project: Project, textureName: String): Long {
            for (candidate in candidates(textureName)) {
                val f = project.assetFile(candidate)
                if (f.isFile) return f.lastModified()
            }
            return 0L
        }

        private fun candidates(textureName: String): List<String> {
            val stem = textureName.substringBeforeLast('.')
            return listOf("$stem.texture", "$textureName.texture", "$stem.texmeta")
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("wrapS", wrapS).put("wrapT", wrapT)
        .put("minFilter", minFilter).put("magFilter", magFilter)
        .put("mipmaps", mipmaps).put("srgb", srgb).put("premultiply", premultiply)
        .put("maxSize", maxSize).put("alphaCutoff", alphaCutoff.toDouble())
        .put("compression", compression).put("atlasColumns", atlasColumns)
        .put("atlasRows", atlasRows).put("spriteSheet", spriteSheet)

    fun validate(): String? = when {
        wrapS !in 0..2 || wrapT !in 0..2 -> "invalid wrap mode"
        minFilter !in 0..1 || magFilter !in 0..1 -> "invalid filter mode"
        maxSize !in 16..8192 -> "invalid maximum size"
        alphaCutoff !in 0f..1f -> "invalid alpha cutoff"
        atlasColumns !in 1..256 || atlasRows !in 1..256 -> "invalid atlas grid"
        else -> null
    }
}

data class TextureInfo(val width: Int, val height: Int, val format: String, val hasAlpha: Boolean, val compressed: Boolean)

/** CPU-decodable pixel data for texture formats Android's BitmapFactory cannot read. */
data class BitmapData(val width: Int, val height: Int, val argb: IntArray)

/** Pure-JVM ARGB helpers; android.graphics.Color throws "Stub!" in offline JVM tests. */
private object Argb {
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = ((a and 255) shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)
    fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)
    fun red(c: Int): Int = (c shr 16) and 255
    fun green(c: Int): Int = (c shr 8) and 255
    fun blue(c: Int): Int = c and 255
    const val TRANSPARENT = 0
}

object TextureDecoders {
    private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    fun probe(name: String, bytes: ByteArray): TextureInfo? {
        val e = name.substringAfterLast('.', "").lowercase()
        if (bytes.size >= 26 && bytes.take(8).toByteArray().contentEquals(PNG)) {
            val w = beInt(bytes, 16); val h = beInt(bytes, 20)
            val colorType = bytes[25].toInt() and 255
            return TextureInfo(w, h, "PNG", colorType == 4 || colorType == 6, false)
        }
        if (bytes.size >= 4 && (bytes[0].toInt() and 255) == 0xFF && (bytes[1].toInt() and 255) == 0xD8) {
            val jpeg = probeJpeg(bytes)
            if (jpeg != null) return TextureInfo(jpeg[0], jpeg[1], "JPEG", false, false)
        }
        if (bytes.size >= 18 && e == "tga") {
            val w = le16(bytes, 12); val h = le16(bytes, 13)
            val depth = bytes[16].toInt() and 255
            return TextureInfo(w, h, "TGA", depth == 32, false)
        }
        if (bytes.size >= 128 && String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "DDS ") {
            val w = le32(bytes, 16).toInt(); val h = le32(bytes, 12).toInt()
            val fourCC = String(bytes.copyOfRange(84, 88), Charsets.US_ASCII)
            return TextureInfo(w, h, "DDS $fourCC", fourCC != "DX10", fourCC.startsWith("DXT"))
        }
        if (bytes.size >= 64 && isKtxMagic(bytes)) {
            val little = le32(bytes, 12) == 0x04030201L
            val w = readInt(bytes, 36, little).toInt(); val h = readInt(bytes, 40, little).toInt()
            val compressed = readInt(bytes, 16, little) == 0L
            return TextureInfo(w, h, "KTX", true, compressed)
        }
        if (bytes.size >= 2 && (e == "ppm" || e == "pgm")) {
            val r = PnmReader(bytes)
            val magic = r.token()
            val w = r.token().toIntOrNull() ?: return null
            val h = r.token().toIntOrNull() ?: return null
            return TextureInfo(w, h, magic.uppercase(), false, false)
        }
        if (bytes.size >= 10 && String(bytes.copyOfRange(0, 10), Charsets.US_ASCII).startsWith("#?RADIANCE")) {
            return TextureInfo(0, 0, "HDR", false, false)
        }
        val upper = e.uppercase().ifBlank { "BIN" }
        return TextureInfo(0, 0, upper, false, false)
    }

    fun decodeBitmapData(name: String, bytes: ByteArray): BitmapData? {
        val e = name.substringAfterLast('.', "").lowercase()
        return when (e) {
            "png" -> PngCodec.decode(bytes)
            "tga" -> decodeTga(bytes)
            "ppm", "pgm" -> decodePnm(bytes)
            "dds" -> decodeDds(bytes)
            "ktx" -> decodeKtx(bytes)
            "hdr" -> decodeHdr(bytes)
            else -> null
        }
    }

    fun toBitmap(data: BitmapData): Bitmap? = try {
        Bitmap.createBitmap(data.width, data.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(data.argb, 0, data.width, 0, 0, data.width, data.height)
        }
    } catch (_: Throwable) { null }

    /** Loads an asset through BitmapFactory, then through the Phase 3 CPU decoders. */
    fun loadBitmap(file: File): Bitmap? {
        if (!file.isFile) return null
        val bytes = try { file.readBytes() } catch (_: Exception) { return null }
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) } catch (_: Throwable) { null }
        if (bmp != null) return bmp
        return decodeBitmapData(file.name, bytes)?.let { toBitmap(it) }
    }

    // ------------------------------------------------------------------ helpers
    private fun le16(b: ByteArray, p: Int): Int = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun le32(b: ByteArray, p: Int): Long =
        (b[p].toInt() and 255).toLong() or ((b[p + 1].toInt() and 255).toLong() shl 8) or
            ((b[p + 2].toInt() and 255).toLong() shl 16) or ((b[p + 3].toInt() and 255).toLong() shl 24)
    private fun beInt(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 255) shl 24) or ((b[p + 1].toInt() and 255) shl 16) or
            ((b[p + 2].toInt() and 255) shl 8) or (b[p + 3].toInt() and 255)
    private fun readInt(b: ByteArray, p: Int, little: Boolean): Long =
        if (little) le32(b, p) else beInt(b, p).toLong() and 0xFFFFFFFFL

    private fun probeJpeg(b: ByteArray): IntArray? {
        var p = 2
        while (p + 9 < b.size) {
            if ((b[p].toInt() and 255) != 0xFF) { p++; continue }
            val marker = b[p + 1].toInt() and 255
            if (marker in 0xC0..0xCF && marker !in setOf(0xC4, 0xC8, 0xCC)) {
                val h = ((b[p + 5].toInt() and 255) shl 8) or (b[p + 6].toInt() and 255)
                val w = ((b[p + 7].toInt() and 255) shl 8) or (b[p + 8].toInt() and 255)
                return intArrayOf(w, h)
            }
            val len = ((b[p + 2].toInt() and 255) shl 8) or (b[p + 3].toInt() and 255)
            if (len < 2) break
            p += 2 + len
        }
        return null
    }

    private class PnmReader(private val b: ByteArray) {
        var pos = 0
        fun token(): String {
            while (pos < b.size) {
                val c = b[pos].toInt().toChar()
                if (c.isWhitespace()) { pos++; continue }
                if (c == '#') { while (pos < b.size && b[pos].toInt().toChar() != '\n') pos++; continue }
                break
            }
            val start = pos
            while (pos < b.size && !b[pos].toInt().toChar().isWhitespace() && b[pos].toInt().toChar() != '#') pos++
            return String(b.copyOfRange(start, pos), Charsets.US_ASCII)
        }
        fun byte(): Int = if (pos < b.size) b[pos++].toInt() and 255 else 0
    }

    // ------------------------------------------------------------------ TGA
    private fun decodeTga(b: ByteArray): BitmapData? {
        if (b.size < 18) return null
        val idLen = b[0].toInt() and 255
        val imageType = b[2].toInt() and 255
        val w = le16(b, 12); val h = le16(b, 13)
        val depth = b[16].toInt() and 255
        val descriptor = b[17].toInt() and 255
        if (w <= 0 || h <= 0 || imageType !in setOf(2, 3, 10, 11)) return null
        if (depth != 8 && depth != 24 && depth != 32) return null
        val rle = imageType == 10 || imageType == 11
        var p = 18 + idLen
        val raw = IntArray(w * h)
        fun put(index: Int, argb: Int) {
            val x = index % w
            val y = index / w
            val target = if ((descriptor and 0x20) != 0) y * w + x else (h - 1 - y) * w + x
            if (target in raw.indices) raw[target] = argb
        }
        var pixel = 0
        var lastColor = 0
        while (pixel < w * h && p < b.size) {
            var count = 1
            var packetIsRaw = true
            if (rle) {
                if (p >= b.size) break
                val header = b[p++].toInt() and 255
                count = (header and 0x7F) + 1
                packetIsRaw = (header and 0x80) == 0
            }
            repeat(count) {
                if (pixel >= w * h || p >= b.size) return@repeat
                val argb: Int
                if (!rle || packetIsRaw || pixel == 0) {
                    when (depth) {
                        8 -> { val g = b[p++].toInt() and 255; argb = Argb.argb(255, g, g, g) }
                        24 -> {
                            if (p + 2 >= b.size) return@repeat
                            val bb = b[p++].toInt() and 255; val gg = b[p++].toInt() and 255; val rr = b[p++].toInt() and 255
                            argb = Argb.argb(255, rr, gg, bb)
                        }
                        else -> {
                            if (p + 3 >= b.size) return@repeat
                            val bb = b[p++].toInt() and 255; val gg = b[p++].toInt() and 255
                            val rr = b[p++].toInt() and 255; val aa = b[p++].toInt() and 255
                            argb = Argb.argb(aa, rr, gg, bb)
                        }
                    }
                    lastColor = argb
                } else argb = lastColor
                put(pixel++, argb)
            }
        }
        return BitmapData(w, h, raw)
    }

    // ------------------------------------------------------------------ PNM
    private fun decodePnm(b: ByteArray): BitmapData? {
        val r = PnmReader(b)
        val magic = r.token()
        val w = r.token().toIntOrNull() ?: return null
        val h = r.token().toIntOrNull() ?: return null
        val max = r.token().toIntOrNull() ?: return null
        if (w <= 0 || h <= 0 || max != 255 || (magic != "P6" && magic != "P5")) return null
        if (r.pos < b.size && b[r.pos].toInt().toChar().isWhitespace()) r.pos++
        val out = IntArray(w * h)
        for (i in out.indices) {
            if (magic == "P6") {
                val rr = r.byte(); val gg = r.byte(); val bb = r.byte()
                out[i] = Argb.argb(255, rr, gg, bb)
            } else {
                val g = r.byte(); out[i] = Argb.argb(255, g, g, g)
            }
        }
        return BitmapData(w, h, out)
    }

    // ------------------------------------------------------------------ DDS
    private fun decodeDds(b: ByteArray): BitmapData? {
        if (b.size < 128 || String(b.copyOfRange(0, 4), Charsets.US_ASCII) != "DDS ") return null
        val h = le32(b, 12).toInt(); val w = le32(b, 16).toInt()
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
        val fourCC = String(b.copyOfRange(84, 88), Charsets.US_ASCII)
        val rgbBitCount = le32(b, 88).toInt()
        val out = IntArray(w * h)
        if (fourCC == "DXT1" || fourCC == "DXT3" || fourCC == "DXT5") {
            val blockBytes = if (fourCC == "DXT1") 8 else 16
            val bw = (w + 3) / 4; val bh = (h + 3) / 4
            var off = 128
            for (by in 0 until bh) for (bx in 0 until bw) {
                if (off + blockBytes > b.size) return BitmapData(w, h, out)
                val alphaOff = off
                val colorOff = if (fourCC == "DXT1") off else off + 8
                val colors = decodeDxtColors(b, colorOff, fourCC == "DXT1")
                val alphas = when (fourCC) {
                    "DXT3" -> decodeDxt3Alpha(b, alphaOff)
                    "DXT5" -> decodeDxt5Alpha(b, alphaOff)
                    else -> IntArray(16) { 255 }
                }
                val bits = le64(b, colorOff + 4)
                for (j in 0 until 16) {
                    val code = ((bits ushr (j * 2)) and 3L).toInt()
                    val c = colors[code]
                    val a = alphas[j]
                    val x = bx * 4 + (j % 4); val y = by * 4 + (j / 4)
                    if (x < w && y < h) out[y * w + x] = Argb.argb(a, Argb.red(c), Argb.green(c), Argb.blue(c))
                }
                off += blockBytes
            }
            return BitmapData(w, h, out)
        }
        if (rgbBitCount == 32) {
            var p = 128
            for (i in out.indices) {
                if (p + 3 >= b.size) break
                val bb = b[p++].toInt() and 255; val gg = b[p++].toInt() and 255
                val rr = b[p++].toInt() and 255; val aa = b[p++].toInt() and 255
                out[i] = Argb.argb(aa, rr, gg, bb)
            }
            return BitmapData(w, h, out)
        }
        return null
    }

    private fun le64(b: ByteArray, p: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[p + i].toInt() and 255).toLong() shl (i * 8))
        return v
    }

    private fun rgb565(c: Int): Int {
        val r = ((c shr 11) and 31) * 255 / 31
        val g = ((c shr 5) and 63) * 255 / 63
        val b = (c and 31) * 255 / 31
        return Argb.rgb(r, g, b)
    }

    private fun decodeDxtColors(b: ByteArray, p: Int, dxt1: Boolean): IntArray {
        val c0 = le16(b, p); val c1 = le16(b, p + 2)
        val a = rgb565(c0); val d = rgb565(c1)
        val ar = Argb.red(a); val ag = Argb.green(a); val ab = Argb.blue(a)
        val dr = Argb.red(d); val dg = Argb.green(d); val db = Argb.blue(d)
        val c = Argb.rgb((2 * ar + dr) / 3, (2 * ag + dg) / 3, (2 * ab + db) / 3)
        val e = Argb.rgb((ar + 2 * dr) / 3, (ag + 2 * dg) / 3, (ab + 2 * db) / 3)
        val third = if (!dxt1 || c0 > c1) c else Argb.rgb((ar + dr) / 2, (ag + dg) / 2, (ab + db) / 2)
        val fourth = if (!dxt1 || c0 > c1) e else Argb.TRANSPARENT
        return intArrayOf(a, d, third, fourth)
    }

    private fun decodeDxt3Alpha(b: ByteArray, p: Int): IntArray {
        val out = IntArray(16)
        for (i in 0 until 16) {
            val nibble = if (i % 2 == 0) (b[p + i / 2].toInt() and 0x0F) else ((b[p + i / 2].toInt() shr 4) and 0x0F)
            out[i] = nibble * 17
        }
        return out
    }

    private fun decodeDxt5Alpha(b: ByteArray, p: Int): IntArray {
        val a0 = b[p].toInt() and 255; val a1 = b[p + 1].toInt() and 255
        var bits = 0L
        for (i in 0 until 6) bits = bits or ((b[p + 2 + i].toInt() and 255).toLong() shl (i * 8))
        val alpha = IntArray(8)
        alpha[0] = a0; alpha[1] = a1
        if (a0 > a1) for (i in 2 until 8) alpha[i] = ((8 - i) * a0 + (i - 1) * a1) / 7
        else for (i in 2 until 6) alpha[i] = ((6 - i) * a0 + (i - 1) * a1) / 5
        if (a0 <= a1) { alpha[6] = 0; alpha[7] = 255 }
        val out = IntArray(16)
        for (i in 0 until 16) out[i] = alpha[((bits ushr (i * 3)) and 7L).toInt()]
        return out
    }

    // ------------------------------------------------------------------ KTX
    private fun isKtxMagic(b: ByteArray): Boolean {
        val magic = byteArrayOf(0xAB.toByte(), 'K'.code.toByte(), 'T'.code.toByte(), 'X'.code.toByte(), ' '.code.toByte(), '1'.code.toByte(), '1'.code.toByte(), 0xBB.toByte(), 13, 10, 26, 10)
        return b.size >= magic.size && b.copyOfRange(0, magic.size).contentEquals(magic)
    }

    private fun decodeKtx(b: ByteArray): BitmapData? {
        if (!isKtxMagic(b) || b.size < 68) return null
        val little = le32(b, 12) == 0x04030201L
        val glType = readInt(b, 16, little)
        val glFormat = readInt(b, 24, little)
        val w = readInt(b, 36, little).toInt(); val h = readInt(b, 40, little).toInt()
        val keyLen = readInt(b, 60, little).toInt()
        val imageSizePos = 64 + keyLen
        if (glType != 0x1401L || (glFormat != 0x1908L && glFormat != 0x1907L) || w <= 0 || h <= 0 || imageSizePos + 4 > b.size) return null
        val imageSize = readInt(b, imageSizePos, little).toInt()
        val data = imageSizePos + 4
        if (imageSize <= 0 || data + imageSize > b.size) return null
        val rgba = glFormat == 0x1908L
        val bpp = if (rgba) 4 else 3
        val out = IntArray(w * h)
        var p = data
        for (y in 0 until h) for (x in 0 until w) {
            if (p + bpp > b.size) break
            val rr = b[p++].toInt() and 255; val gg = b[p++].toInt() and 255; val bb = b[p++].toInt() and 255
            val aa = if (rgba) b[p++].toInt() and 255 else 255
            out[(h - 1 - y) * w + x] = Argb.argb(aa, rr, gg, bb)
        }
        return BitmapData(w, h, out)
    }

    // ------------------------------------------------------------------ Radiance HDR
    private fun decodeHdr(b: ByteArray): BitmapData? {
        val text = String(b, Charsets.US_ASCII)
        val lines = text.lines()
        var i = 0
        while (i < lines.size && lines[i].isNotBlank()) i++
        while (i < lines.size && lines[i].isBlank()) i++
        val res = lines.getOrNull(i)?.trim()?.split(Regex("\\s+")) ?: return null
        if (res.size < 4) return null
        val w = res[3].toIntOrNull() ?: return null
        val h = res[1].toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        val headerBytes = lines.take(i + 1).sumOf { it.length + 1 }
        var p = headerBytes
        val out = IntArray(w * h)
        for (y in 0 until h) {
            if (p + 4 > b.size) return BitmapData(w, h, out)
            val rle = (b[p].toInt() and 255) == 2 && (b[p + 1].toInt() and 255) == 2 &&
                (((b[p + 2].toInt() and 255) shl 8) or (b[p + 3].toInt() and 255)) == w
            p += 4
            val row = ByteArray(w * 4)
            if (rle) {
                for (c in 0 until 4) {
                    var x = 0
                    while (x < w && p < b.size) {
                        val count = b[p++].toInt() and 255
                        if (count > 128) {
                            val value = if (p < b.size) b[p++] else 0
                            repeat(count - 128) { if (x < w) row[(x++ * 4) + c] = value }
                        } else {
                            repeat(count) { if (x < w && p < b.size) row[(x++ * 4) + c] = b[p++] }
                        }
                    }
                }
            } else {
                p -= 4
                for (x in 0 until w) for (c in 0 until 4) row[x * 4 + c] = if (p < b.size) b[p++] else 0
            }
            for (x in 0 until w) {
                val rr = row[x * 4].toInt() and 255; val gg = row[x * 4 + 1].toInt() and 255
                val bb = row[x * 4 + 2].toInt() and 255; val ee = row[x * 4 + 3].toInt() and 255
                val scale = 2f.pow(ee - 128) / 255f
                out[(h - 1 - y) * w + x] = Argb.rgb(
                    (rr * scale).toInt().coerceIn(0, 255),
                    (gg * scale).toInt().coerceIn(0, 255),
                    (bb * scale).toInt().coerceIn(0, 255),
                )
            }
        }
        return BitmapData(w, h, out)
    }
}
