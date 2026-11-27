package com.sengine.engine.core

import java.io.ByteArrayOutputStream

/**
 * Small dependency-free Base64 codec. Works on Android and in the offline JVM
 * test runner, and is used by PNG/glTF data URIs, ESR JSON bundles and sprite
 * documents. Whitespace, standard padding and URL-safe variants are accepted.
 */
object Base64Codec {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i + 3 <= bytes.size) {
            val n = ((bytes[i].toInt() and 255) shl 16) or
                ((bytes[i + 1].toInt() and 255) shl 8) or
                (bytes[i + 2].toInt() and 255)
            out.append(ALPHABET[(n shr 18) and 63])
            out.append(ALPHABET[(n shr 12) and 63])
            out.append(ALPHABET[(n shr 6) and 63])
            out.append(ALPHABET[n and 63])
            i += 3
        }
        val rem = bytes.size - i
        if (rem == 1) {
            val n = (bytes[i].toInt() and 255) shl 16
            out.append(ALPHABET[(n shr 18) and 63])
            out.append(ALPHABET[(n shr 12) and 63])
            out.append("==")
        } else if (rem == 2) {
            val n = ((bytes[i].toInt() and 255) shl 16) or ((bytes[i + 1].toInt() and 255) shl 8)
            out.append(ALPHABET[(n shr 18) and 63])
            out.append(ALPHABET[(n shr 12) and 63])
            out.append(ALPHABET[(n shr 6) and 63])
            out.append('=')
        }
        return out.toString()
    }

    fun decode(text: String): ByteArray? {
        val clean = text.filter { !it.isWhitespace() }
            .replace('-', '+').replace('_', '/')
        if (clean.isEmpty()) return ByteArray(0)
        if (clean.length % 4 == 1) return null
        val out = ByteArrayOutputStream((clean.length / 4) * 3)
        var buffer = 0
        var bits = 0
        var sawPadding = false
        for (c in clean) {
            if (c == '=') {
                sawPadding = true
                continue
            }
            if (sawPadding) return null
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 255)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }
}
