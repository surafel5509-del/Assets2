package com.sengine.project

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Imports files, folders and ZIP archives into a project's asset tree.
 *
 * Pure JVM code (no Android types), so the same logic runs in the app and in
 * the local unit checks. Every write goes through [Project.assetFile] or a path
 * checked by [AssetFolders.isSafeRelative], so nothing can land outside
 * `assets/` (zip-slip protection).
 */
object AssetImporter {

    /** The outcome of one import: what was written, and what was refused and why. */
    data class Report(
        val imported: MutableList<String> = mutableListOf(),
        val skipped: MutableList<String> = mutableListOf(),
    ) {
        val count get() = imported.size
    }

    /** Makes a file name safe to store: keeps letters, digits, `_ - .`, and no leading dot. */
    fun safeName(raw: String): String {
        val cleaned = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9_.\\-]"), "_")
            .trimStart('.')
        return cleaned.ifBlank { "asset" }
    }

    /** True when the file name's extension is one the importer accepts. */
    fun isSupported(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.endsWith(".ui.json") || lower.endsWith(".ctrl.json")) return true
        val ext = lower.substringAfterLast('.', "")
        return ext.isNotEmpty() && ext in AssetFolders.SUPPORTED_EXTENSIONS
    }

    /**
     * Writes one stream into the tree. A bare name goes to the branch its
     * extension belongs to; a path with folders keeps those folders.
     * Returns the stored relative path, or null when the name is refused.
     */
    fun importStream(project: Project, name: String, input: InputStream): String? {
        val rel = relativeTarget(name) ?: return null
        val unique = uniqueRelative(project, rel)
        val target = File(project.assetsDir, unique)
        target.parentFile?.mkdirs()
        target.outputStream().use { input.copyTo(it) }
        return unique
    }

    /** Imports a file already on disk. */
    fun importFile(project: Project, file: File): Report {
        val report = Report()
        if (!file.isFile) { report.skipped.add("${file.name}: not a file"); return report }
        if (!isSupported(file.name)) { report.skipped.add("${file.name}: unsupported type"); return report }
        val stored = file.inputStream().use { importStream(project, file.name, it) }
        if (stored != null) report.imported.add(stored) else report.skipped.add("${file.name}: refused")
        return report
    }

    /** Imports a folder, keeping its sub-folder structure. Hidden files and `__MACOSX` are ignored. */
    fun importFolder(project: Project, folder: File): Report {
        val report = Report()
        if (!folder.isDirectory) { report.skipped.add("${folder.name}: not a folder"); return report }
        folder.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.name != "__MACOSX" }
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { f ->
                val rel = f.relativeTo(folder).path.replace('\\', '/')
                if (!isSupported(f.name)) { report.skipped.add("$rel: unsupported type"); return@forEach }
                val stored = f.inputStream().use { importStream(project, rel, it) }
                if (stored != null) report.imported.add(stored) else report.skipped.add("$rel: refused")
            }
        return report
    }

    /**
     * Imports a ZIP stream. Entries keep their folders. Entries with unsafe paths
     * (absolute, `..`), hidden entries, `__MACOSX`, and unsupported types are skipped.
     */
    fun importZip(project: Project, input: InputStream): Report {
        val report = Report()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                when {
                    entry.isDirectory -> Unit
                    name.split('/').any { it.startsWith(".") || it == "__MACOSX" } ->
                        report.skipped.add("$name: hidden or system entry")
                    !AssetFolders.isSafeRelative(name) ->
                        report.skipped.add("$name: unsafe path")
                    !isSupported(name) ->
                        report.skipped.add("$name: unsupported type")
                    else -> {
                        val rel = importStream(project, name, zip.nonClosingView())
                        if (rel != null) report.imported.add(rel) else report.skipped.add("$name: refused")
                    }
                }
                zip.closeEntry()
            }
        }
        return report
    }

    /**
     * Relative path to store [name] at, or null when it is unsafe. A bare name is
     * placed by [AssetFolders.folderFor]; a name with folders is kept as given.
     */
    internal fun relativeTarget(name: String): String? {
        val normalized = name.replace('\\', '/')
        if (normalized.contains('/')) {
            val parts = normalized.split('/').filter { it.isNotEmpty() }
                .map { safeName(it) }
            val path = parts.joinToString("/")
            return if (AssetFolders.isSafeRelative(path)) path else null
        }
        val safe = safeName(normalized)
        return AssetFolders.join(AssetFolders.folderFor(safe), safe)
    }

    /** Adds " (2)", " (3)"… before the extension until the name is free. */
    internal fun uniqueRelative(project: Project, rel: String): String {
        if (!File(project.assetsDir, rel).exists()) return rel
        val folder = AssetFolders.parentOf(rel)
        val name = AssetFolders.nameOf(rel)
        val stem = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 2
        while (true) {
            val candidate = AssetFolders.join(folder, "${stem}_$i$ext")
            if (!File(project.assetsDir, candidate).exists()) return candidate
            i++
        }
    }

    /**
     * Case-insensitive search over every asset path. Each whitespace-separated
     * term must appear somewhere in the path, so "hero png" matches
     * `Sprites/hero_idle.png`. An empty query returns everything.
     */
    fun search(project: Project, query: String): List<String> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val all = project.listAssets()
        if (terms.isEmpty()) return all
        return all.filter { path -> terms.all { path.lowercase().contains(it) } }
    }
}

/** A view over a stream that ignores close(), so a zip entry can be copied without closing the zip. */
private fun ZipInputStream.nonClosingView(): InputStream = object : InputStream() {
    override fun read(): Int = this@nonClosingView.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = this@nonClosingView.read(b, off, len)
    override fun close() {}
}
