package com.sengine.engine.render

import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.Base64Codec
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Phase 3 model-format catalogue. Proprietary authoring formats are accepted
 * as source assets and pack members; runtime meshes are decoded from open
 * interchange formats or from an explicitly declared renderModel companion.
 */
object ModelFormatRegistry {
    /** User-facing categories mirror the supplied model-store screenshot. */
    val CATEGORIES = listOf(
        "All Formats", "Blender Models", "FBX Models", "Cinema 4D Models",
        "3ds Max Models", "Maya Models", "Obj Models", "Rigged",
        "Animated Models", "Lowpoly Models", "ZIP / RAR / ESR Packs",
    )

    private val NATIVE = setOf("obj", "mtl", "stl", "ply", "off", "gltf", "glb", "dae", "fbx", "3ds")
    private val CATEGORY_BY_EXT = mapOf(
        "blend" to "Blender Models",
        "fbx" to "FBX Models", "fbxa" to "FBX Models",
        "c4d" to "Cinema 4D Models",
        "3ds" to "3ds Max Models", "max" to "3ds Max Models",
        "ma" to "Maya Models", "mb" to "Maya Models",
        "obj" to "Obj Models", "mtl" to "Obj Models",
        "gltf" to "Rigged", "glb" to "Rigged", "dae" to "Rigged", "skel" to "Rigged", "skin" to "Rigged",
        "bvh" to "Animated Models", "anim3d" to "Animated Models", "glanim" to "Animated Models",
        "motion" to "Animated Models", "clip" to "Animated Models", "fbxanim" to "Animated Models",
        "stl" to "Lowpoly Models", "ply" to "Lowpoly Models", "off" to "Lowpoly Models",
        "vox" to "Lowpoly Models", "md2" to "Lowpoly Models", "md3" to "Lowpoly Models",
        "usd" to "Rigged", "usdz" to "Rigged", "x" to "Rigged", "ase" to "Rigged",
        "ms3d" to "Rigged", "md5mesh" to "Rigged", "smd" to "Rigged",
        "lwo" to "Lowpoly Models", "lws" to "Animated Models",
    )

    val OPEN_EXTENSIONS = AssetKind.MODEL.extensions.toList()
    val TEXTURE_EXTENSIONS = AssetKind.TEXTURE.extensions.toList()
    val ARCHIVE_EXTENSIONS = AssetKind.ARCHIVE.extensions.toList()
    val ANIMATION_EXTENSIONS = AssetKind.ANIMATION3D.extensions.toList()

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun isModel(name: String): Boolean = ext(name) in OPEN_EXTENSIONS
    fun isTexture(name: String): Boolean = ext(name) in TEXTURE_EXTENSIONS
    fun isAnimation(name: String): Boolean = ext(name) in ANIMATION_EXTENSIONS || ext(name) == "anim"
    fun isArchive(name: String): Boolean = ext(name) in ARCHIVE_EXTENSIONS
    fun isRuntimeDecodable(name: String): Boolean = ext(name) in NATIVE
    fun categoryFor(name: String): String = CATEGORY_BY_EXT[ext(name)] ?: "All Formats"
    fun labelFor(name: String): String = ext(name).uppercase()
    fun description(name: String): String {
        val e = ext(name)
        val category = categoryFor(name)
        return when {
            e in NATIVE -> "$category · native ${e.uppercase()} importer"
            isArchive(name) -> "Archive pack · ZIP / RAR / ESR extraction"
            isAnimation(name) -> "$category · animation track importer"
            isModel(name) -> "$category · source asset, use an OBJ / glTF companion for rendering"
            else -> "Asset"
        }
    }
}

/**
 * Pure-JVM decoders for open 3D mesh formats. Output is interleaved
 * position(3), normal(3), uv(2), matching [Mesh].
 */
object ModelImporters {
    private class Builder {
        val d = ArrayList<Float>(4096)
        fun vertex(p: FloatArray, n: FloatArray, u: Float, v: Float) {
            d.add(p[0]); d.add(p[1]); d.add(p[2])
            d.add(n[0]); d.add(n[1]); d.add(n[2])
            d.add(u); d.add(v)
        }
        fun data(): FloatArray = d.toFloatArray()
    }

    private fun normal(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-8f)
        nx /= l; ny /= l; nz /= l
        return floatArrayOf(nx, ny, nz)
    }

    private fun tri(b: Builder, a: FloatArray, c: FloatArray, d: FloatArray, uv: Array<FloatArray>? = null) {
        val n = normal(a, c, d)
        val u0 = uv?.getOrNull(0) ?: floatArrayOf(a[0] + 0.5f, a[2] + 0.5f)
        val u1 = uv?.getOrNull(1) ?: floatArrayOf(c[0] + 0.5f, c[2] + 0.5f)
        val u2 = uv?.getOrNull(2) ?: floatArrayOf(d[0] + 0.5f, d[2] + 0.5f)
        b.vertex(a, n, u0[0], u0[1]); b.vertex(c, n, u1[0], u1[1]); b.vertex(d, n, u2[0], u2[1])
    }

    fun decode(file: File): FloatArray? = decode(file.name, file.readBytes(), file.parentFile)

    fun decode(name: String, bytes: ByteArray, baseDir: File? = null): FloatArray? {
        val e = name.substringAfterLast('.', "").lowercase()
        return when (e) {
            "stl" -> decodeStl(bytes)
            "ply" -> decodePly(String(bytes, Charsets.UTF_8))
            "off" -> decodeOff(String(bytes, Charsets.UTF_8))
            "gltf" -> decodeGltf(String(bytes, Charsets.UTF_8), baseDir, null)
            "glb" -> decodeGlb(bytes, baseDir)
            "dae" -> decodeDae(String(bytes, Charsets.UTF_8))
            "fbx", "fbxa" -> decodeFbxAscii(bytes)
            "3ds" -> decode3ds(bytes)
            else -> null
        }
    }

    /** Reads a Wavefront .mtl referenced by an .obj and returns the first diffuse texture. */
    fun objMaterialTexture(file: File): String? {
        if (!file.name.endsWith(".obj", ignoreCase = true)) return null
        val text = try { file.readText() } catch (_: Exception) { return null }
        val mtl = Regex("""(?m)^\s*mtllib\s+(.+)$""").find(text)?.groupValues?.get(1)?.trim() ?: return null
        val safe = mtl.replace('\\', '/').substringAfterLast('/')
        if (safe.isBlank() || safe.contains("..")) return null
        val mtlFile = File(file.parentFile, safe)
        val body = try { mtlFile.readText() } catch (_: Exception) { return null }
        return Regex("""(?m)^\s*map_Kd\s+(.+)$""").find(body)?.groupValues?.get(1)?.trim()
            ?.replace('\\', '/')?.substringAfterLast('/')
    }

    // ------------------------------------------------------------------ STL
    fun decodeStl(bytes: ByteArray): FloatArray? {
        if (bytes.size < 84) return null
        val ascii = bytes.take(16).toByteArray().toString(Charsets.US_ASCII).startsWith("solid") &&
            String(bytes, Charsets.US_ASCII).contains("facet normal", ignoreCase = true)
        return if (ascii) decodeStlAscii(String(bytes, Charsets.UTF_8)) else decodeStlBinary(bytes)
    }

    private fun decodeStlAscii(text: String): FloatArray? {
        val b = Builder()
        var normal = floatArrayOf(0f, 1f, 0f)
        val verts = ArrayList<FloatArray>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("facet normal") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 5) normal = floatArrayOf(p[2].toFloatOrNull() ?: 0f, p[3].toFloatOrNull() ?: 1f, p[4].toFloatOrNull() ?: 0f)
                }
                line.startsWith("vertex") -> {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) verts.add(floatArrayOf(p[1].toFloatOrNull() ?: 0f, p[2].toFloatOrNull() ?: 0f, p[3].toFloatOrNull() ?: 0f))
                    if (verts.size == 3) {
                        val a = verts[0]; val c = verts[1]; val d = verts[2]
                        val n = if (normal[0] == 0f && normal[1] == 0f && normal[2] == 0f) normal(a, c, d) else normal
                        b.vertex(a, n, a[0] + 0.5f, a[2] + 0.5f)
                        b.vertex(c, n, c[0] + 0.5f, c[2] + 0.5f)
                        b.vertex(d, n, d[0] + 0.5f, d[2] + 0.5f)
                        verts.clear()
                    }
                }
            }
        }
        return b.data().takeIf { it.isNotEmpty() }
    }

    private fun decodeStlBinary(bytes: ByteArray): FloatArray? {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.getInt(80)
        if (count <= 0 || 84 + count * 50 > bytes.size) return null
        val b = Builder()
        var p = 84
        repeat(count) {
            if (p + 50 > bytes.size) return b.data().takeIf { it.isNotEmpty() }
            val n = floatArrayOf(bb.getFloat(p), bb.getFloat(p + 4), bb.getFloat(p + 8))
            val a = floatArrayOf(bb.getFloat(p + 12), bb.getFloat(p + 16), bb.getFloat(p + 20))
            val c = floatArrayOf(bb.getFloat(p + 24), bb.getFloat(p + 28), bb.getFloat(p + 32))
            val d = floatArrayOf(bb.getFloat(p + 36), bb.getFloat(p + 40), bb.getFloat(p + 44))
            val nn = if (n[0] == 0f && n[1] == 0f && n[2] == 0f) normal(a, c, d) else n
            b.vertex(a, nn, a[0] + 0.5f, a[2] + 0.5f)
            b.vertex(c, nn, c[0] + 0.5f, c[2] + 0.5f)
            b.vertex(d, nn, d[0] + 0.5f, d[2] + 0.5f)
            p += 50
        }
        return b.data().takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ PLY / OFF
    fun decodePly(text: String): FloatArray? {
        val lines = text.lines()
        var vertexCount = 0
        var faceCount = 0
        var body = 0
        for (i in lines.indices) {
            val l = lines[i].trim()
            if (l.startsWith("element vertex")) vertexCount = l.split(Regex("\\s+")).getOrNull(2)?.toIntOrNull() ?: 0
            if (l.startsWith("element face")) faceCount = l.split(Regex("\\s+")).getOrNull(2)?.toIntOrNull() ?: 0
            if (l == "end_header") { body = i + 1; break }
        }
        if (vertexCount <= 0 || body == 0) return null
        val pos = ArrayList<FloatArray>()
        for (i in body until minOf(lines.size, body + vertexCount)) {
            val p = lines[i].trim().split(Regex("\\s+"))
            if (p.size >= 3) pos.add(floatArrayOf(p[0].toFloatOrNull() ?: 0f, p[1].toFloatOrNull() ?: 0f, p[2].toFloatOrNull() ?: 0f))
        }
        val b = Builder()
        for (i in body + vertexCount until minOf(lines.size, body + vertexCount + faceCount)) {
            val p = lines[i].trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            if (p.size < 4 || p[0] < 3) continue
            val idx = p.drop(1).take(p[0])
            for (k in 1 until idx.size - 1) {
                val a = pos.getOrNull(idx[0]) ?: continue
                val c = pos.getOrNull(idx[k]) ?: continue
                val d = pos.getOrNull(idx[k + 1]) ?: continue
                tri(b, a, c, d)
            }
        }
        return b.data().takeIf { it.isNotEmpty() }
    }

    fun decodeOff(text: String): FloatArray? {
        val lines = text.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (lines.isEmpty()) return null
        if (lines[0].equals("OFF", ignoreCase = true)) lines.removeAt(0)
        val counts = lines.removeAt(0).split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
        if (counts.size < 2) return null
        val nv = counts[0]; val nf = counts[1]
        if (nv <= 0 || lines.size < nv) return null
        val pos = (0 until nv).map { i ->
            val p = lines[i].split(Regex("\\s+"))
            floatArrayOf(p.getOrNull(0)?.toFloatOrNull() ?: 0f, p.getOrNull(1)?.toFloatOrNull() ?: 0f, p.getOrNull(2)?.toFloatOrNull() ?: 0f)
        }
        val b = Builder()
        for (i in nv until minOf(lines.size, nv + nf)) {
            val p = lines[i].split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            if (p.isEmpty() || p[0] < 3) continue
            val idx = p.drop(1).take(p[0])
            for (k in 1 until idx.size - 1) {
                val a = pos.getOrNull(idx[0]) ?: continue
                val c = pos.getOrNull(idx[k]) ?: continue
                val d = pos.getOrNull(idx[k + 1]) ?: continue
                tri(b, a, c, d)
            }
        }
        return b.data().takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ glTF / GLB
    fun decodeGlb(bytes: ByteArray, baseDir: File?): FloatArray? {
        if (bytes.size < 20) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.getInt(0) != 0x46546C67 || bb.getInt(4) != 2) return null
        val total = bb.getInt(8).coerceAtMost(bytes.size)
        var json = ""
        var bin: ByteArray? = null
        var p = 12
        while (p + 8 <= total) {
            val len = bb.getInt(p)
            val type = bb.getInt(p + 4)
            if (len < 0 || p + 8 + len > total) break
            val chunk = bytes.copyOfRange(p + 8, p + 8 + len)
            if (type == 0x4E4F534A) json = String(chunk, Charsets.UTF_8).trimEnd('\u0000', ' ')
            else if (type == 0x004E4942) bin = chunk
            p += 8 + len + ((4 - len % 4) % 4)
        }
        if (json.isBlank()) return null
        return decodeGltf(json, baseDir, bin)
    }

    fun decodeGltf(text: String, baseDir: File?, glbBin: ByteArray?): FloatArray? {
        val root = try { JSONObject(text) } catch (_: Exception) { return null }
        val buffers = ArrayList<ByteArray?>()
        val bufferJson = root.optJSONArray("buffers")
        if (bufferJson != null) for (i in 0 until bufferJson.length()) {
            val o = bufferJson.optJSONObject(i)
            val uri = o?.optString("uri", "") ?: ""
            val data = when {
                uri.isBlank() && i == 0 -> glbBin
                uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri)
                baseDir != null -> resolveExternal(baseDir, uri)?.takeIf { it.isFile }?.readBytes()
                else -> null
            }
            buffers.add(data)
        }
        if (buffers.isEmpty()) buffers.add(glbBin)
        val accessors = root.optJSONArray("accessors") ?: return null
        val views = root.optJSONArray("bufferViews") ?: return null
        fun readAccessor(index: Int): List<FloatArray>? {
            if (index < 0 || index >= accessors.length()) return null
            val a = accessors.optJSONObject(index) ?: return null
            val count = a.optInt("count", 0)
            val type = a.optString("type", "SCALAR")
            val comps = when (type) { "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1 }
            val component = a.optInt("componentType", 5126)
            val viewIndex = a.optInt("bufferView", -1)
            if (count <= 0 || viewIndex < 0 || viewIndex >= views.length()) return null
            val v = views.optJSONObject(viewIndex) ?: return null
            val bufferIndex = v.optInt("buffer", 0)
            val src = buffers.getOrNull(bufferIndex) ?: return null
            val srcBB = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN)
            val size = when (component) { 5120, 5121 -> 1; 5122, 5123 -> 2; 5125, 5126 -> 4; else -> return null }
            val stride = if (v.optInt("byteStride", 0) > 0) v.optInt("byteStride", 0) else comps * size
            val start = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val out = ArrayList<FloatArray>(count)
            for (i in 0 until count) {
                val off = start + i * stride
                if (off + comps * size > src.size) break
                val row = FloatArray(comps)
                for (k in 0 until comps) {
                    val o = off + k * size
                    row[k] = when (component) {
                        5126 -> srcBB.getFloat(o)
                        5123 -> (srcBB.getShort(o).toInt() and 0xFFFF).toFloat()
                        5125 -> srcBB.getInt(o).toLong().toFloat()
                        5121 -> (src[o].toInt() and 0xFF).toFloat()
                        5122 -> srcBB.getShort(o).toFloat()
                        else -> (src[o].toInt() and 0xFF).toFloat()
                    }
                }
                out.add(row)
            }
            return out
        }
        val mesh = root.optJSONArray("meshes")?.optJSONObject(0) ?: return null
        val prim = mesh.optJSONArray("primitives")?.optJSONObject(0) ?: return null
        val attrs = prim.optJSONObject("attributes") ?: return null
        val pos = readAccessor(attrs.optInt("POSITION", -1)) ?: return null
        val normals = readAccessor(attrs.optInt("NORMAL", -1))
        val uvs = readAccessor(attrs.optInt("TEXCOORD_0", -1))
        val indicesAcc = prim.optInt("indices", -1)
        val indices = if (indicesAcc >= 0) readAccessor(indicesAcc)?.map { it[0].toInt() } ?: emptyList()
        else pos.indices.toList()
        val b = Builder()
        var i = 0
        while (i + 2 < indices.size) {
            val ia = indices[i]; val ib = indices[i + 1]; val ic = indices[i + 2]
            val a = pos.getOrNull(ia)?.let { floatArrayOf(it.getOrElse(0) { 0f }, it.getOrElse(1) { 0f }, it.getOrElse(2) { 0f }) } ?: continue
            val c = pos.getOrNull(ib)?.let { floatArrayOf(it.getOrElse(0) { 0f }, it.getOrElse(1) { 0f }, it.getOrElse(2) { 0f }) } ?: continue
            val d = pos.getOrNull(ic)?.let { floatArrayOf(it.getOrElse(0) { 0f }, it.getOrElse(1) { 0f }, it.getOrElse(2) { 0f }) } ?: continue
            val n = normals?.getOrNull(ia)?.let { floatArrayOf(it.getOrElse(0) { 0f }, it.getOrElse(1) { 1f }, it.getOrElse(2) { 0f }) } ?: normal(a, c, d)
            b.vertex(a, n, uvs?.getOrNull(ia)?.getOrElse(0) { 0f } ?: 0f, uvs?.getOrNull(ia)?.getOrElse(1) { 0f } ?: 0f)
            b.vertex(c, n, uvs?.getOrNull(ib)?.getOrElse(0) { 1f } ?: 1f, uvs?.getOrNull(ib)?.getOrElse(1) { 0f } ?: 0f)
            b.vertex(d, n, uvs?.getOrNull(ic)?.getOrElse(0) { 1f } ?: 1f, uvs?.getOrNull(ic)?.getOrElse(1) { 1f } ?: 1f)
            i += 3
        }
        return b.data().takeIf { it.isNotEmpty() }
    }

    private fun decodeDataUri(uri: String): ByteArray? {
        val comma = uri.indexOf(',')
        if (comma < 0) return null
        val meta = uri.substring(0, comma)
        val body = uri.substring(comma + 1)
        return if (meta.contains(";base64", ignoreCase = true)) Base64Codec.decode(body) else body.toByteArray(Charsets.UTF_8)
    }

    private fun resolveExternal(base: File, uri: String): File? {
        val clean = uri.substringBefore('?').substringBefore('#').replace('\\', '/')
        if (clean.startsWith("/") || clean.contains("..") || clean.contains(':')) return null
        val f = File(base, clean)
        val prefix = base.canonicalPath + File.separator
        return if (f.canonicalPath.startsWith(prefix)) f else null
    }

    // ------------------------------------------------------------------ COLLADA / FBX / 3DS
    fun decodeDae(text: String): FloatArray? {
        val arrays = Regex("""<float_array[^>]*>([\s\S]*?)</float_array>""").findAll(text).toList()
        if (arrays.isEmpty()) return null
        val pos = ArrayList<FloatArray>()
        val nums = arrays[0].groupValues[1].trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
        for (i in nums.indices step 3) if (i + 2 < nums.size) pos.add(floatArrayOf(nums[i], nums[i + 1], nums[i + 2]))
        val block = Regex("""<triangles\b[^>]*>([\s\S]*?)</triangles>""").find(text)?.groupValues?.get(1) ?: return null
        var stride = 1
        var vertexOffset = 0
        for (m in Regex("""<input\b[^>]*>""").findAll(block)) {
            val tag = m.value
            val offset = Regex("offset=\"(\\d+)\"").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            stride = maxOf(stride, offset + 1)
            if (tag.contains("semantic=\"VERTEX\"")) vertexOffset = offset
        }
        val b = Builder()
        for (pTag in Regex("""<p>([\s\S]*?)</p>""").findAll(block)) {
            val ids = pTag.groupValues[1].trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            var i = 0
            while (i + stride * 2 < ids.size) {
                val ia = ids[i + vertexOffset]; val ib = ids[i + stride + vertexOffset]; val ic = ids[i + stride * 2 + vertexOffset]
                val a = pos.getOrNull(ia); val c = pos.getOrNull(ib); val d = pos.getOrNull(ic)
                if (a != null && c != null && d != null) tri(b, a, c, d)
                i += stride * 3
            }
        }
        return b.data().takeIf { it.isNotEmpty() }
    }

    private fun fbxNumbers(text: String, marker: String): List<Float>? {
        val start = text.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val open = text.indexOf('{', start)
        val close = text.indexOf('}', open)
        if (open < 0 || close < 0) return null
        return text.substring(open + 1, close).split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
    }

    fun decodeFbxAscii(bytes: ByteArray): FloatArray? {
        if (bytes.take(18).toByteArray().toString(Charsets.US_ASCII).startsWith("Kaydara FBX Binary")) return null
        val text = String(bytes, Charsets.UTF_8)
        val raw = fbxNumbers(text, "Vertices:") ?: return null
        val poly = fbxNumbers(text, "PolygonVertexIndex:") ?: return null
        val pos = ArrayList<FloatArray>()
        for (i in raw.indices step 3) if (i + 2 < raw.size) pos.add(floatArrayOf(raw[i], raw[i + 1], raw[i + 2]))
        val b = Builder()
        val polygon = ArrayList<FloatArray>()
        fun flush() {
            for (k in 1 until polygon.size - 1) {
                tri(b, polygon[0], polygon[k], polygon[k + 1])
            }
            polygon.clear()
        }
        for (v in poly) {
            val i = if (v < 0f) (-v - 1f).toInt() else v.toInt()
            pos.getOrNull(i)?.let { polygon.add(it) }
            if (v < 0f) flush()
        }
        if (polygon.isNotEmpty()) flush()
        return b.data().takeIf { it.isNotEmpty() }
    }

    private fun u16(b: ByteArray, p: Int): Int = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int): Long =
        (b[p].toInt() and 255).toLong() or ((b[p + 1].toInt() and 255).toLong() shl 8) or
            ((b[p + 2].toInt() and 255).toLong() shl 16) or ((b[p + 3].toInt() and 255).toLong() shl 24)
    private fun f32(b: ByteArray, p: Int): Float = ByteBuffer.wrap(b, p, 4).order(ByteOrder.LITTLE_ENDIAN).float

    fun decode3ds(bytes: ByteArray): FloatArray? {
        val verts = ArrayList<FloatArray>()
        val uvs = ArrayList<FloatArray>()
        val faces = ArrayList<IntArray>()
        fun walk(start: Int, end: Int) {
            var p = start
            while (p + 6 <= end && p + 6 <= bytes.size) {
                val id = u16(bytes, p)
                val len = u32(bytes, p + 2)
                if (len < 6 || p + len > end) break
                val data = p + 6
                val next = p + len.toInt()
                when (id) {
                    0x4110 -> {
                        val n = u16(bytes, data)
                        for (i in 0 until n) {
                            val o = data + 2 + i * 12
                            if (o + 12 > next) break
                            verts.add(floatArrayOf(f32(bytes, o), f32(bytes, o + 4), f32(bytes, o + 8)))
                        }
                    }
                    0x4140 -> {
                        val n = u16(bytes, data)
                        for (i in 0 until n) {
                            val o = data + 2 + i * 8
                            if (o + 8 > next) break
                            uvs.add(floatArrayOf(f32(bytes, o), f32(bytes, o + 4)))
                        }
                    }
                    0x4120 -> {
                        val n = u16(bytes, data)
                        for (i in 0 until n) {
                            val o = data + 2 + i * 8
                            if (o + 8 > next) break
                            faces.add(intArrayOf(u16(bytes, o), u16(bytes, o + 2), u16(bytes, o + 4)))
                        }
                    }
                    0x4D4D, 0x3D3D, 0x4000, 0x4100 -> walk(data, next)
                }
                p = next
            }
        }
        walk(0, bytes.size)
        if (verts.isEmpty() || faces.isEmpty()) return null
        val b = Builder()
        for (f in faces) {
            val a = verts.getOrNull(f[0]) ?: continue
            val c = verts.getOrNull(f[1]) ?: continue
            val d = verts.getOrNull(f[2]) ?: continue
            val uv = if (uvs.size > maxOf(f[0], f[1], f[2])) arrayOf(uvs[f[0]], uvs[f[1]], uvs[f[2]]) else null
            tri(b, a, c, d, uv)
        }
        return b.data().takeIf { it.isNotEmpty() }
    }
}
