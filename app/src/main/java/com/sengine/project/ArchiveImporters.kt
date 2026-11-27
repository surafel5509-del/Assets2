package com.sengine.project

import com.sengine.engine.core.Base64Codec
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

enum class ArchiveFormat(val label: String) {
    ZIP("ZIP"), ESR("ESR resource bundle"), RAR4("RAR 4.x store archive"),
    RAR5("RAR 5.x store archive"), SEVEN_ZIP("7-Zip archive"), UNKNOWN("unknown"),
}

class UnsupportedArchiveException(message: String) : IllegalArgumentException(message)

/**
 * Phase 3 archive import pipeline. ZIP is fully supported; `.esr` is retained
 * as a requested bundle alias and accepts ZIP, ESR1-prefixed ZIP, or a JSON
 * manifest with Base64/text members. RAR4/RAR5 store-mode members are decoded
 * without native libraries. Proprietary compressed RAR members are rejected
 * with an actionable message rather than being silently dropped.
 */
object ArchiveImporters {
    const val MAX_ARCHIVE_BYTES = 128 * 1024 * 1024
    const val MAX_FILE_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRIES = 4096

    private val ZIP_LOCAL = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)
    private val ZIP_EMPTY = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 5, 6)
    private val ZIP_SPANNED = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 7, 8)
    private val RAR4 = byteArrayOf('R'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), '!'.code.toByte(), 0x1A, 7, 0)
    private val RAR5 = byteArrayOf('R'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), '!'.code.toByte(), 0x1A, 7, 1, 0)
    private val SEVENZ = byteArrayOf('7'.code.toByte(), 'z'.code.toByte(), 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    private val ESR1 = "ESR1".toByteArray(Charsets.US_ASCII)

    fun isArchiveName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AssetKinds.ARCHIVE_EXTENSIONS

    fun detect(bytes: ByteArray, name: String = ""): ArchiveFormat {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            starts(bytes, ESR1) -> ArchiveFormat.ESR
            ext == "esr" && (zipOffset(bytes) >= 0 || isJsonBytes(bytes)) -> ArchiveFormat.ESR
            starts(bytes, RAR4) -> ArchiveFormat.RAR4
            starts(bytes, RAR5) -> ArchiveFormat.RAR5
            zipOffset(bytes) >= 0 -> ArchiveFormat.ZIP
            starts(bytes, SEVENZ) -> ArchiveFormat.SEVEN_ZIP
            else -> ArchiveFormat.UNKNOWN
        }
    }

    fun extract(file: File, dest: File): List<File> {
        require(file.isFile) { "archive missing: ${file.name}" }
        val bytes = file.readBytes()
        if (bytes.size > MAX_ARCHIVE_BYTES) throw UnsupportedArchiveException("archive exceeds 128 MB")
        return extractBytes(bytes, dest, file.name)
    }

    fun extract(input: InputStream, dest: File, name: String): List<File> {
        val bytes = readLimited(input)
        return extractBytes(bytes, dest, name)
    }

    fun extractBytes(bytes: ByteArray, dest: File, name: String): List<File> {
        if (bytes.size > MAX_ARCHIVE_BYTES) throw UnsupportedArchiveException("archive exceeds 128 MB")
        dest.mkdirs()
        val format = detect(bytes, name)
        val writer = ArchiveWriter(dest)
        when (format) {
            ArchiveFormat.ZIP -> {
                val off = zipOffset(bytes)
                if (off < 0) throw UnsupportedArchiveException("ZIP signature missing")
                extractZip(ByteArrayInputStream(bytes, off, bytes.size - off), writer)
            }
            ArchiveFormat.ESR -> extractEsr(bytes, writer)
            ArchiveFormat.RAR4 -> extractRar4(bytes, writer)
            ArchiveFormat.RAR5 -> extractRar5(bytes, writer)
            ArchiveFormat.SEVEN_ZIP -> throw UnsupportedArchiveException("7-Zip is recognized but not decoded; re-save the bundle as ZIP or ESR")
            ArchiveFormat.UNKNOWN -> throw UnsupportedArchiveException("unsupported archive: use ZIP, ESR or store-mode RAR")
        }
        return writer.files()
    }

    /** Builds an uncompressed RAR 4.x archive; useful for bundle templates and tests. */
    fun createRar4Store(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(RAR4)
        // Main archive header: type 0x73, no extra area.
        out.write(le16(0)); out.write(0x73); out.write(le16(0)); out.write(le16(13))
        out.write(le16(0)); out.write(le32(0))
        for ((name, data) in entries) {
            val nameBytes = name.replace('\\', '/').toByteArray(Charsets.UTF_8)
            val headerSize = 7 + 4 + 25 + nameBytes.size
            out.write(le16(0))                  // header CRC, not checked by store parser
            out.write(0x74)                     // file header
            out.write(le16(0x8000))             // LONG_BLOCK: add size present
            out.write(le16(headerSize))
            out.write(le32(data.size.toLong())) // add size = packed size
            out.write(le32(data.size.toLong())) // packed size
            out.write(le32(data.size.toLong())) // unpacked size
            out.write(0)                        // host OS: MS-DOS
            out.write(le32(0))                  // file CRC
            out.write(le32(0))                  // file time
            out.write(20)                       // unpack version
            out.write(0x30)                     // method 0x30 = store
            out.write(le16(nameBytes.size))
            out.write(le32(0))                  // attributes
            out.write(nameBytes)
            out.write(data)
        }
        // End archive header: type 0x7B.
        out.write(le16(0)); out.write(0x7B); out.write(le16(0)); out.write(le16(7))
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ ZIP / ESR
    private fun extractZip(input: InputStream, writer: ArchiveWriter) {
        ZipInputStream(input).use { zip ->
            val buf = ByteArray(16384)
            while (true) {
                val e = zip.nextEntry ?: break
                val f = ProjectManager.resolveZipEntry(writer.dest, e.name)
                if (f == null) { zip.closeEntry(); continue }
                if (e.isDirectory) {
                    f.mkdirs()
                    zip.closeEntry()
                    continue
                }
                f.parentFile?.mkdirs()
                var total = 0
                f.outputStream().use { out ->
                    while (true) {
                        val n = zip.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_FILE_BYTES || writer.total + total > MAX_ARCHIVE_BYTES) {
                            throw UnsupportedArchiveException("archive member exceeds safety limits")
                        }
                        out.write(buf, 0, n)
                    }
                }
                writer.note(total)
                zip.closeEntry()
            }
        }
    }

    private fun extractEsr(bytes: ByteArray, writer: ArchiveWriter) {
        val off = zipOffset(bytes)
        if (off >= 0) {
            extractZip(ByteArrayInputStream(bytes, off, bytes.size - off), writer)
            return
        }
        val textOff = if (starts(bytes, ESR1)) ESR1.size else 0
        val text = String(bytes.copyOfRange(textOff, bytes.size), Charsets.UTF_8).trim()
        if (!text.startsWith("{")) throw UnsupportedArchiveException("ESR bundle must be ZIP, ESR1+ZIP or JSON")
        extractJsonEsr(JSONObject(text), writer)
    }

    private fun extractJsonEsr(root: JSONObject, writer: ArchiveWriter) {
        var written = 0
        val obj = root.optJSONObject("files") ?: root.optJSONObject("entries")
        if (obj != null) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val path = keys.next()
                val value = obj.opt(path)
                val data = valueToBytes(value) ?: continue
                writer.write(path, data)
                written++
            }
        }
        val arr = root.optJSONArray("files") ?: root.optJSONArray("entries")
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val path = o.optString("path", o.optString("name", ""))
            if (path.isBlank()) continue
            val data = if (o.has("base64")) Base64Codec.decode(o.optString("base64"))
            else o.optString("text", "").toByteArray(Charsets.UTF_8)
            if (data == null) continue
            writer.write(path, data)
            written++
        }
        if (written == 0) throw UnsupportedArchiveException("ESR JSON has no files")
    }

    private fun valueToBytes(value: Any?): ByteArray? = when (value) {
        is String -> Base64Codec.decode(value) ?: value.toByteArray(Charsets.UTF_8)
        is JSONObject -> when {
            value.has("base64") -> Base64Codec.decode(value.optString("base64"))
            value.has("text") -> value.optString("text").toByteArray(Charsets.UTF_8)
            value.has("content") -> value.optString("content").toByteArray(Charsets.UTF_8)
            else -> null
        }
        else -> null
    }

    // ------------------------------------------------------------------ RAR4
    private fun extractRar4(b: ByteArray, writer: ArchiveWriter) {
        var p = RAR4.size
        var entries = 0
        val compressed = ArrayList<String>()
        while (p + 7 <= b.size) {
            val start = p
            val type = b[start + 2].toInt() and 255
            val flags = u16(b, start + 3)
            val size = u16(b, start + 5)
            if (size < 7 || start + size > b.size) break
            var q = start + 7
            var addSize = 0L
            if ((flags and 0x8000) != 0) {
                if (q + 4 > start + size) break
                addSize = u32(b, q)
                q += 4
            }
            if (type == 0x74) {
                if (q + 25 > start + size) break
                val packLow = u32(b, q)
                val method = b[q + 18].toInt() and 255
                val nameSize = u16(b, q + 19)
                q += 25
                if ((flags and 0x0100) != 0) q += 8 // LARGE: high pack/unpack sizes
                if (q + nameSize > start + size) break
                val name = String(b.copyOfRange(q, q + nameSize), Charsets.UTF_8).trimEnd('\u0000')
                val packed = if (addSize > 0) addSize else packLow
                val dataOff = start + size
                if (packed < 0 || dataOff + packed > b.size) break
                if (method == 0x30) writer.write(name, b, dataOff, packed.toInt())
                else compressed.add(name)
                p = dataOff + packed.toInt()
            } else {
                p = start + size + addSize.toInt()
            }
            entries++
            if (entries > MAX_ENTRIES) throw UnsupportedArchiveException("RAR has too many headers")
            if (type == 0x7B) break // end archive
        }
        rejectCompressed(compressed)
    }

    // ------------------------------------------------------------------ RAR5
    private data class VInt(val value: Long, val bytes: Int)

    private fun readVint(b: ByteArray, p: Int): VInt? {
        var value = 0L
        var shift = 0
        var i = p
        var count = 0
        while (i < b.size && count < 10) {
            val c = b[i++].toInt() and 255
            value = value or ((c and 0x7F).toLong() shl shift)
            if ((c and 0x80) == 0) return VInt(value, i - p)
            shift += 7
            count++
        }
        return null
    }

    private fun extractRar5(b: ByteArray, writer: ArchiveWriter) {
        var p = RAR5.size
        var entries = 0
        val compressed = ArrayList<String>()
        while (p + 8 <= b.size) {
            var q = p + 4 // skip header CRC32
            val headerSize = readVint(b, q) ?: break
            q += headerSize.bytes
            val headerEnd = q + headerSize.value.toInt()
            if (headerEnd < q || headerEnd > b.size) break
            val type = readVint(b, q) ?: break; q += type.bytes
            val flags = readVint(b, q) ?: break; q += flags.bytes
            var dataSize = 0L
            if ((flags.value and 1L) != 0L) {
                val extra = readVint(b, q) ?: break; q += extra.bytes
            }
            if ((flags.value and 2L) != 0L) {
                val data = readVint(b, q) ?: break; q += data.bytes
                dataSize = data.value
            }
            if (type.value == 2L) { // file header
                val fileFlags = readVint(b, q) ?: break; q += fileFlags.bytes
                val unpacked = readVint(b, q) ?: break; q += unpacked.bytes
                val attributes = readVint(b, q) ?: break; q += attributes.bytes
                if ((fileFlags.value and 2L) != 0L) q += 4 // mtime
                if ((fileFlags.value and 4L) != 0L) q += 4 // data CRC32
                val compressionInfo = readVint(b, q) ?: break; q += compressionInfo.bytes
                val hostOS = readVint(b, q) ?: break; q += hostOS.bytes
                val nameLength = readVint(b, q) ?: break; q += nameLength.bytes
                val n = nameLength.value.toInt()
                if (n < 0 || q + n > headerEnd) break
                val name = String(b, q, n, Charsets.UTF_8)
                val method = ((compressionInfo.value shr 7) and 0x0F).toInt()
                val isDirectory = (fileFlags.value and 1L) != 0L
                if (!isDirectory) {
                    if (method == 0 && dataSize > 0L && headerEnd + dataSize <= b.size) {
                        writer.write(name, b, headerEnd, dataSize.toInt())
                    } else if (method != 0) compressed.add(name)
                }
            }
            p = headerEnd + dataSize.toInt()
            entries++
            if (entries > MAX_ENTRIES) throw UnsupportedArchiveException("RAR has too many headers")
            if (type.value == 5L) break // end archive
        }
        rejectCompressed(compressed)
    }

    private fun rejectCompressed(entries: List<String>) {
        if (entries.isNotEmpty()) {
            throw UnsupportedArchiveException(
                "compressed RAR member ${entries.first()} uses proprietary RAR compression; re-save as ZIP, ESR or RAR store mode"
            )
        }
    }

    // ------------------------------------------------------------------ limits / helpers
    private class ArchiveWriter(val dest: File) {
        var total = 0L
            private set
        var count = 0
            private set

        fun write(name: String, bytes: ByteArray, off: Int = 0, len: Int = bytes.size) {
            if (len <= 0 || name.endsWith("/") || name.endsWith("\\")) return
            if (len > MAX_FILE_BYTES || total + len > MAX_ARCHIVE_BYTES || count >= MAX_ENTRIES) {
                throw UnsupportedArchiveException("archive member exceeds safety limits")
            }
            val f = ProjectManager.resolveZipEntry(dest, name) ?: return
            if (off < 0 || off + len > bytes.size) return
            f.parentFile?.mkdirs()
            f.writeBytes(bytes.copyOfRange(off, off + len))
            total += len
            count++
        }

        fun note(bytes: Int) {
            count++
            total += bytes
            if (count > MAX_ENTRIES || total > MAX_ARCHIVE_BYTES) {
                throw UnsupportedArchiveException("archive exceeds safety limits")
            }
        }

        fun files(): List<File> = dest.walkTopDown()
            .filter { it.isFile && !internalPath(dest, it) }
            .toList().sortedBy { it.path }
    }

    private fun internalPath(root: File, f: File): Boolean {
        val rel = try { f.relativeTo(root).path.replace('\\', '/') } catch (_: Exception) { return true }
        if (rel.isBlank()) return false
        val parts = rel.split('/')
        if (parts.any { it.startsWith(".") || it == "__MACOSX" }) return true
        val n = parts.last().lowercase()
        return n in setOf(".ds_store", "thumbs.db", "desktop.ini")
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > MAX_ARCHIVE_BYTES) throw UnsupportedArchiveException("archive exceeds 128 MB")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun starts(b: ByteArray, sig: ByteArray): Boolean =
        b.size >= sig.size && b.copyOfRange(0, sig.size).contentEquals(sig)

    private fun zipOffset(b: ByteArray): Int {
        for (o in intArrayOf(0, ESR1.size)) {
            if (o >= b.size) continue
            for (sig in arrayOf(ZIP_LOCAL, ZIP_EMPTY, ZIP_SPANNED)) {
                if (o + sig.size <= b.size && b.copyOfRange(o, o + sig.size).contentEquals(sig)) return o
            }
        }
        // Some ESR producers use a short text preamble before the ZIP stream.
        if (b.size > 4) for (o in 0 until minOf(b.size - 4, 64)) {
            if (b[o] == ZIP_LOCAL[0] && b[o + 1] == ZIP_LOCAL[1] && b[o + 2] == ZIP_LOCAL[2] && b[o + 3] == ZIP_LOCAL[3]) return o
        }
        return -1
    }

    private fun isJsonBytes(b: ByteArray): Boolean =
        b.isNotEmpty() && String(b.copyOfRange(0, minOf(b.size, 256)), Charsets.UTF_8).trimStart().startsWith("{")

    private fun u16(b: ByteArray, p: Int): Int = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)

    private fun le16(v: Int): ByteArray = byteArrayOf(v.toByte(), (v shr 8).toByte())

    private fun le32(v: Long): ByteArray = byteArrayOf(
        v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()
    )

    private fun u32(b: ByteArray, p: Int): Long =
        (b[p].toInt() and 255).toLong() or ((b[p + 1].toInt() and 255).toLong() shl 8) or
            ((b[p + 2].toInt() and 255).toLong() shl 16) or ((b[p + 3].toInt() and 255).toLong() shl 24)

    private object AssetKinds {
        val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "esr")
    }
}
