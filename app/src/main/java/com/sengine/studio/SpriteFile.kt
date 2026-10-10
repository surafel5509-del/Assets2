package com.sengine.studio

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The editable Sprite Studio file (`Sprites/Studio/<name>.sprite`).
 *
 * A sprite keeps its layers and frames, so it can be reopened and edited after
 * export. The format is line-based text: one header per document, then each
 * layer's pixels as a deflated, base64-encoded block on a single line. It is
 * small for pixel art and diffable in version control.
 *
 *     SPRITE 1
 *     name hero
 *     size 32 32
 *     anim 8 1 1
 *     palette FF000000,FFFFFFFF
 *     frames 2
 *     frame 100 2
 *     layer Body 1 1.0 <base64>
 *     layer Outline 1 1.0 <base64>
 *     frame 120 1
 *     layer Layer_1 1 1.0 <base64>
 */
object SpriteFile {
    const val MAGIC = "SPRITE 1"

    fun encode(doc: SpriteDocument): String {
        val sb = StringBuilder()
        sb.append(MAGIC).append('\n')
        sb.append("name ").append(doc.name.replace(Regex("\\s+"), "_")).append('\n')
        sb.append("size ").append(doc.width).append(' ').append(doc.height).append('\n')
        sb.append("anim ").append(doc.fps).append(' ').append(if (doc.loop) 1 else 0).append(' ')
            .append(if (doc.onionSkin) 1 else 0).append('\n')
        sb.append("palette ").append(doc.palette.joinToString(",") { String.format("%08X", it) }).append('\n')
        sb.append("frames ").append(doc.frameCount).append('\n')
        for (f in doc.frames) {
            sb.append("frame ").append(f.durationMs).append(' ').append(f.layers.size).append('\n')
            for (l in f.layers) {
                sb.append("layer ").append(l.name.replace(Regex("\\s+"), "_")).append(' ')
                    .append(if (l.visible) 1 else 0).append(' ')
                    .append(if (l.locked) 1 else 0).append(' ')
                    .append(l.opacity).append(' ')
                    .append(pack(l.px)).append('\n')
            }
        }
        return sb.toString()
    }

    /** Rebuilds a document from [encode]'s output. Throws [IllegalArgumentException] for anything it does not recognise. */
    fun decode(text: String): SpriteDocument {
        val lines = text.lines().filter { it.isNotBlank() }
        require(lines.firstOrNull() == MAGIC) { "Not a sprite file" }
        var name = "sprite"
        var w = 0; var h = 0
        var fps = 8f; var loop = true; var onion = true
        val palette = ArrayList<Int>()
        var doc: SpriteDocument? = null
        var frame: PixelFrame? = null
        var frames = 0
        for (line in lines.drop(1)) {
            val p = line.split(' ')
            when (p[0]) {
                "name" -> name = p.getOrElse(1) { "sprite" }
                "size" -> { w = p[1].toInt(); h = p[2].toInt() }
                "anim" -> { fps = p[1].toFloat(); loop = p[2] == "1"; onion = p.getOrElse(3) { "1" } == "1" }
                "palette" -> p.getOrElse(1) { "" }.split(',').filter { it.isNotBlank() }
                    .mapTo(palette) { it.toLong(16).toInt() }
                "frames" -> {
                    frames = p[1].toInt()
                    doc = SpriteDocument(w, h, name).also { d ->
                        d.fps = fps; d.loop = loop; d.onionSkin = onion
                        d.palette.clear(); d.palette.addAll(palette)
                        d.frames.clear()
                    }
                }
                "frame" -> {
                    val d = doc ?: throw IllegalArgumentException("frame before header")
                    frame = PixelFrame(ArrayList(), p[1].toInt())
                    d.frames.add(frame)
                }
                "layer" -> {
                    val f = frame ?: throw IllegalArgumentException("layer before frame")
                    val layer = PixelLayer(p[1], w, h, unpack(p[5], w * h))
                    layer.visible = p[2] == "1"
                    layer.locked = p[3] == "1"
                    layer.opacity = p[4].toFloat()
                    f.layers.add(layer)
                }
                else -> throw IllegalArgumentException("unknown line: ${p[0]}")
            }
        }
        val d = doc ?: throw IllegalArgumentException("missing frames")
        require(d.frames.size == frames) { "frame count mismatch" }
        require(d.frames.all { it.layers.isNotEmpty() }) { "a frame has no layers" }
        return d
    }

    private fun pack(px: IntArray): String {
        val raw = java.nio.ByteBuffer.allocate(px.size * 4)
        for (c in px) raw.putInt(c)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw.array())
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private fun unpack(text: String, count: Int): IntArray {
        val compressed = Base64.getDecoder().decode(text)
        val inflater = Inflater()
        inflater.setInput(compressed)
        val raw = ByteArray(count * 4)
        var n = 0
        while (n < raw.size && !inflater.finished()) {
            val k = inflater.inflate(raw, n, raw.size - n)
            if (k == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            n += k
        }
        inflater.end()
        require(n == raw.size) { "layer pixel data is truncated" }
        val bb = java.nio.ByteBuffer.wrap(raw)
        return IntArray(count) { bb.int }
    }
}
