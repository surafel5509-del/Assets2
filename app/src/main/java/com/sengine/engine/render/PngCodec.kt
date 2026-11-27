package com.sengine.engine.render

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/**
 * Pure-JVM PNG encoder/decoder for sprite export and portable texture import.
 * Supports non-interlaced 8-bit PNG colour types 0, 2, 3, 4 and 6 with all
 * five standard row filters. No Android Bitmap methods are needed, so the same
 * code runs in the offline test suite and on the device.
 */
object PngCodec {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    fun encode(width: Int, height: Int, argb: IntArray): ByteArray {
        require(width > 0 && height > 0) { "invalid PNG size" }
        require(argb.size >= width * height) { "PNG pixel buffer too small" }
        val raw = ByteArray((width * 4 + 1) * height)
        var p = 0
        for (y in 0 until height) {
            raw[p++] = 0 // filter: None
            for (x in 0 until width) {
                val c = argb[y * width + x]
                raw[p++] = ((c shr 16) and 255).toByte()
                raw[p++] = ((c shr 8) and 255).toByte()
                raw[p++] = (c and 255).toByte()
                raw[p++] = ((c ushr 24) and 255).toByte()
            }
        }
        val ihdr = ByteArray(13)
        putInt(ihdr, 0, width)
        putInt(ihdr, 4, height)
        ihdr[8] = 8 // bit depth
        ihdr[9] = 6 // RGBA
        ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0
        val compressed = zlib(raw)
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        out.write(chunk("IHDR", ihdr))
        out.write(chunk("IDAT", compressed))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): BitmapData? {
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) return null
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = -1
        var interlace = 0
        var palette: IntArray? = null
        var transparency: ByteArray? = null
        val idat = ByteArrayOutputStream()
        var p = 8
        while (p + 8 <= bytes.size) {
            val len = beInt(bytes, p)
            if (len < 0 || p + 12 + len > bytes.size) break
            val type = String(bytes.copyOfRange(p + 4, p + 8), Charsets.US_ASCII)
            val data = bytes.copyOfRange(p + 8, p + 8 + len)
            when (type) {
                "IHDR" -> {
                    if (data.size < 13) return null
                    width = beInt(data, 0); height = beInt(data, 4)
                    bitDepth = data[8].toInt() and 255
                    colorType = data[9].toInt() and 255
                    interlace = data[12].toInt() and 255
                }
                "PLTE" -> {
                    palette = IntArray(data.size / 3) { i ->
                        val o = i * 3
                        argb(255, data[o].toInt() and 255, data[o + 1].toInt() and 255, data[o + 2].toInt() and 255)
                    }
                }
                "tRNS" -> transparency = data
                "IDAT" -> idat.write(data)
                "IEND" -> break
            }
            p += 12 + len
        }
        if (width <= 0 || height <= 0 || bitDepth != 8 || interlace != 0) return null
        val channels = when (colorType) {
            0 -> 1 // grayscale
            2 -> 3 // RGB
            3 -> 1 // palette index
            4 -> 2 // grayscale + alpha
            6 -> 4 // RGBA
            else -> return null
        }
        if (colorType == 3 && palette == null) return null
        val rowLen = width * channels
        val expected = (rowLen + 1) * height
        val scan = ByteArray(expected)
        try {
            InflaterInputStream(ByteArrayInputStream(idat.toByteArray())).use { inf ->
                var off = 0
                while (off < expected) {
                    val n = inf.read(scan, off, expected - off)
                    if (n <= 0) break
                    off += n
                }
                if (off < expected) return null
            }
        } catch (_: Exception) { return null }
        val out = IntArray(width * height)
        var prev = ByteArray(rowLen)
        val bpp = channels
        var src = 0
        for (y in 0 until height) {
            val filter = scan[src++].toInt() and 255
            val row = ByteArray(rowLen)
            for (i in 0 until rowLen) {
                val raw = scan[src++].toInt() and 255
                val a = if (i >= bpp) row[i - bpp].toInt() and 255 else 0
                val b = prev[i].toInt() and 255
                val c = if (i >= bpp) prev[i - bpp].toInt() and 255 else 0
                val v = when (filter) {
                    0 -> raw
                    1 -> raw + a
                    2 -> raw + b
                    3 -> raw + ((a + b) / 2)
                    4 -> raw + paeth(a, b, c)
                    else -> return null
                }
                row[i] = (v and 255).toByte()
            }
            for (x in 0 until width) {
                val o = x * channels
                out[y * width + x] = when (colorType) {
                    0 -> {
                        val g = row[o].toInt() and 255
                        val tr = transparency
                        val a = if (tr != null && tr.size >= 2 && ((tr[0].toInt() and 255) shl 8 or (tr[1].toInt() and 255)) == g) 0 else 255
                        argb(a, g, g, g)
                    }
                    2 -> argb(255, row[o].toInt() and 255, row[o + 1].toInt() and 255, row[o + 2].toInt() and 255)
                    3 -> {
                        val idx = row[o].toInt() and 255
                        val base = palette?.getOrNull(idx) ?: 0
                        val alpha = transparency?.getOrNull(idx)?.toInt()?.and(255) ?: 255
                        argb(alpha, red(base), green(base), blue(base))
                    }
                    4 -> {
                        val g = row[o].toInt() and 255
                        argb(row[o + 1].toInt() and 255, g, g, g)
                    }
                    6 -> argb(row[o + 3].toInt() and 255, row[o].toInt() and 255, row[o + 1].toInt() and 255, row[o + 2].toInt() and 255)
                    else -> 0
                }
            }
            prev = row
        }
        return BitmapData(width, height, out)
    }

    // ------------------------------------------------------------------ helpers
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 255) shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)
    private fun red(c: Int): Int = (c shr 16) and 255
    private fun green(c: Int): Int = (c shr 8) and 255
    private fun blue(c: Int): Int = c and 255

    private fun zlib(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n == 0) break
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val t = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(t); update(data) }.value.toInt()
        val out = ByteArrayOutputStream(12 + data.size)
        out.write(intBytes(data.size))
        out.write(t)
        out.write(data)
        out.write(intBytes(crc))
        return out.toByteArray()
    }

    private fun intBytes(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )

    private fun putInt(b: ByteArray, p: Int, v: Int) {
        b[p] = (v ushr 24).toByte(); b[p + 1] = (v ushr 16).toByte()
        b[p + 2] = (v ushr 8).toByte(); b[p + 3] = v.toByte()
    }

    private fun beInt(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 255) shl 24) or ((b[p + 1].toInt() and 255) shl 16) or
            ((b[p + 2].toInt() and 255) shl 8) or (b[p + 3].toInt() and 255)

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }
}
