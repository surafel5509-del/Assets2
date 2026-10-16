package com.sengine.project

import com.sengine.engine.core.AssetKind
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Safe ZIP import & extraction used for project zips and asset-pack zips.
 *
 * Protections: zip-slip (entries escaping the target folder), a maximum entry
 * count, a maximum total uncompressed size and per-entry size limits, so a
 * malicious or broken zip can never fill the phone or overwrite other files.
 */
object ZipExtractor {
    const val MAX_ENTRIES = 2000
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    class Report {
        val imported = ArrayList<String>()
        val skipped = ArrayList<String>()
        var scenes = 0
        override fun toString(): String {
            val base = "${imported.size} file(s) imported"
            val sc = if (scenes > 0) ", $scenes scene(s)" else ""
            val sk = if (skipped.isNotEmpty()) ", ${skipped.size} skipped" else ""
            return base + sc + sk
        }
    }

    private fun safeFile(root: File, entryName: String): File? {
        val f = File(root, entryName.replace('\\', '/'))
        val rootPath = root.canonicalPath
        return if (f.canonicalPath == rootPath || f.canonicalPath.startsWith(rootPath + File.separator)) f else null
    }

    /** Extract a whole zip into [destDir]. Returns the report; throws on hard limits. */
    fun extract(input: InputStream, destDir: File): Report {
        val report = Report()
        destDir.mkdirs()
        var total = 0L
        var entries = 0
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val e = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IllegalArgumentException("ZIP has too many files (max $MAX_ENTRIES)")
                val name = e.name
                if (e.isDirectory) { safeFile(destDir, name)?.mkdirs(); continue }
                val f = safeFile(destDir, name)
                if (f == null) { report.skipped.add(name); continue }
                f.parentFile?.mkdirs()
                var entryBytes = 0L
                f.outputStream().use { out ->
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        entryBytes += n; total += n
                        if (entryBytes > MAX_ENTRY_BYTES) throw IllegalArgumentException("File too large in ZIP: $name")
                        if (total > MAX_TOTAL_BYTES) throw IllegalArgumentException("ZIP is too large when extracted")
                        out.write(buffer, 0, n)
                    }
                }
                report.imported.add(name)
            }
        }
        return report
    }

    /**
     * Import an asset-pack zip into [project]: every supported asset file
     * (textures, sounds, scripts, shaders, animations, OBJ models) is copied
     * into the project's assets folder (flattened, with unique names), and any
     * `*.scene.json` files become scenes of the project.
     */
    fun importAssetPack(project: Project, input: InputStream): Report {
        val report = Report()
        var total = 0L
        var entries = 0
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IllegalArgumentException("ZIP has too many files (max $MAX_ENTRIES)")
                if (e.isDirectory) continue
                val rawName = e.name.replace('\\', '/').substringAfterLast('/')
                val isScene = rawName.endsWith(".scene.json")
                val kind = AssetKind.of(rawName)
                if (!isScene && kind == null) { report.skipped.add(rawName); drain(zip, buffer); continue }
                // read entry into memory (bounded) then write it out
                val bytes = readEntry(zip, buffer, rawName).also { total += it.size }
                if (total > MAX_TOTAL_BYTES) throw IllegalArgumentException("ZIP is too large when extracted")
                if (isScene) {
                    val sceneName = rawName.removeSuffix(".scene.json")
                        .replace(Regex("[^A-Za-z0-9 _\\-]"), "").trim().ifBlank { "Imported Scene" }
                    project.scenesDir.mkdirs()
                    File(project.scenesDir, "$sceneName.scene.json").writeBytes(bytes)
                    report.scenes++
                    report.imported.add(rawName)
                } else {
                    val safe = rawName.replace(Regex("[^A-Za-z0-9_.\\-]"), "_")
                    val unique = project.uniqueAssetName(safe)
                    project.assetsDir.mkdirs()
                    project.assetFile(unique).writeBytes(bytes)
                    report.imported.add(unique)
                }
            }
        }
        return report
    }

    /** True when the zip looks like an S Engine project (contains project.json). */
    fun containsProject(input: InputStream): Boolean {
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.replace('\\', '/').substringAfterLast('/') == "project.json") return true
            }
        }
        return false
    }

    private fun drain(zip: ZipInputStream, buffer: ByteArray) {
        while (zip.read(buffer) >= 0) { /* discard */ }
    }

    private fun readEntry(zip: ZipInputStream, buffer: ByteArray, name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var entryBytes = 0L
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            entryBytes += n
            if (entryBytes > MAX_ENTRY_BYTES) throw IllegalArgumentException("File too large in ZIP: $name")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
