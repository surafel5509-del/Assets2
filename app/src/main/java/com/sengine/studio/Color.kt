package com.sengine.studio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colour utilities for the Sprite Studio. Colours are packed ARGB ints
 * (0xAARRGGBB), non-premultiplied, the same layout the engine uses everywhere.
 */
object Argb {
    fun of(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun a(c: Int) = (c ushr 24) and 0xFF
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF

    /** "#AARRGGBB", "#RRGGBB" or "RRGGBB" to ARGB. Returns null when malformed. */
    fun parse(text: String): Int? {
        val s = text.trim().removePrefix("#")
        return when (s.length) {
            6 -> s.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> s.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    fun toHex(c: Int): String = String.format("#%08X", c)

    /** Source-over compositing of [src] onto [dst], both non-premultiplied. */
    fun over(dst: Int, src: Int): Int {
        val sa = a(src)
        if (sa == 0) return dst
        if (sa == 255) return src
        val da = a(dst)
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun ch(s: Int, d: Int) = (s * sa + d * da * (255 - sa) / 255) / outA
        return of(outA, ch(r(src), r(dst)), ch(g(src), g(dst)), ch(b(src), b(dst)))
    }

    /** Linear interpolation of two colours, channel by channel. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * k).toInt().coerceIn(0, 255)
        return of(ch(a(a), a(b)), ch(r(a), r(b)), ch(g(a), g(b)), ch(b(a), b(b)))
    }

    /** HSV to ARGB. h in degrees [0,360), s and v in [0,1]. */
    fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1 - abs((hh / 60f) % 2f - 1))
        val m = v - c
        val (r1, g1, b1) = when {
            hh < 60 -> Triple(c, x, 0f)
            hh < 120 -> Triple(x, c, 0f)
            hh < 180 -> Triple(0f, c, x)
            hh < 240 -> Triple(0f, x, c)
            hh < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun to8(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return of(alpha, to8(r1), to8(g1), to8(b1))
    }

    /** ARGB to HSV: returns [h degrees, s, v]. Grey colours have h = 0. */
    fun toHsv(c: Int): FloatArray {
        val r = r(c) / 255f; val g = g(c) / 255f; val b = b(c) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        val hh = if (h < 0f) h + 360f else h
        val s = if (mx == 0f) 0f else d / mx
        return floatArrayOf(hh, s, mx)
    }

    /**
     * Colour grading for one pixel. [hue] shifts degrees, [saturation] and
     * [brightness] are multipliers around 1, [contrast] is a multiplier around
     * mid-grey. Alpha is preserved.
     */
    fun grade(c: Int, hue: Float, saturation: Float, brightness: Float, contrast: Float): Int {
        val al = a(c)
        if (al == 0) return c
        val hsv = toHsv(c)
        val h = hsv[0] + hue
        val s = (hsv[1] * saturation).coerceIn(0f, 1f)
        var v = (hsv[2] * brightness).coerceIn(0f, 1f)
        v = ((v - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
        return fromHsv(h, s, v, al)
    }

    /** Euclidean distance in RGBA space, used by palette matching. */
    fun distance(a: Int, b: Int): Int {
        val dr = r(a) - r(b); val dg = g(a) - g(b); val db = b(a) - b(b); val da = a(a) - a(b)
        return dr * dr + dg * dg + db * db + da * da
    }

    /** The palette entry closest to [c]. Returns [c] unchanged when the palette is empty. */
    fun nearest(c: Int, palette: List<Int>): Int {
        if (palette.isEmpty()) return c
        var best = palette[0]; var bd = Int.MAX_VALUE
        for (p in palette) {
            val d = distance(c, p)
            if (d < bd) { bd = d; best = p }
        }
        return best
    }
}
