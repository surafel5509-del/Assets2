package com.sengine.project

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ProjectManager {
    /**
     * Resolves a zip entry inside [root], or null when it would escape the
     * extraction directory (zip-slip). Pure JVM so it is unit-testable.
     */
    fun resolveZipEntry(root: File, entryName: String): File? {
        val entry = entryName.replace('\\', '/')
        if (entry.startsWith("/") || entry.contains("..") || entry.contains(':')) return null
        val f = File(root, entry)
        val prefix = root.canonicalPath + File.separator
        if (f.canonicalPath != root.canonicalPath && !f.canonicalPath.startsWith(prefix)) return null
        return f
    }

    fun root(ctx: Context) = File(ctx.filesDir, "projects").also { it.mkdirs() }

    fun list(ctx: Context): List<Project> =
        (root(ctx).listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .map { Project(it) }
            .sortedByDescending { it.dir.lastModified() }

    fun open(ctx: Context, name: String) = Project(File(root(ctx), name))

    fun sanitize(name: String) = name.trim().replace(Regex("[^A-Za-z0-9 _\\-]"), "").take(40)

    fun exists(ctx: Context, name: String) = File(root(ctx), name).exists()

    fun create(ctx: Context, name: String, template: Templates.Template): Project {
        val p = Project(File(root(ctx), name))
        p.saveMeta()
        template.build(p)
        p.saveMeta()
        return p
    }

    fun delete(p: Project) = p.dir.deleteRecursively()

    fun rename(ctx: Context, p: Project, newName: String): Project? {
        val target = File(root(ctx), newName)
        if (target.exists()) return null
        return if (p.dir.renameTo(target)) Project(target) else null
    }

    fun duplicate(ctx: Context, p: Project): Project {
        var n = "${p.name} Copy"
        var i = 2
        while (exists(ctx, n)) n = "${p.name} Copy ${i++}"
        val target = File(root(ctx), n)
        p.dir.copyRecursively(target)
        return Project(target)
    }

    /** True when a file (or any of its parents) is a hidden/internal work file. */
    private fun isInternal(f: File, root: File): Boolean {
        var cur: File? = f
        while (cur != null && cur != root) {
            if (cur.name.startsWith(".")) return true
            cur = cur.parentFile
        }
        return false
    }

    fun exportZip(p: Project, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            p.dir.walkTopDown()
                .filter { it.isFile && !isInternal(it, p.dir) }
                .forEach { f ->
                    val rel = f.relativeTo(p.dir).path.replace('\\', '/')
                    zip.putNextEntry(ZipEntry("${p.name}/$rel"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    /** Imports a project ZIP exported by S Engine. Returns the new project. */
    fun importZip(ctx: Context, input: InputStream, fallbackName: String): Project {
        val tmp = File(ctx.cacheDir, "import_${System.currentTimeMillis()}")
        tmp.mkdirs()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                // Zip-slip guard: compare with a trailing separator so a sibling
                // directory whose name merely starts with tmp's name is rejected.
                val f = resolveZipEntry(tmp, e.name) ?: continue
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        return adoptProjectDir(ctx, tmp, fallbackName)
    }

    /** Imports an S Engine project from ZIP, store-mode RAR or ESR archive. */
    fun importArchive(ctx: Context, input: InputStream, fallbackName: String): Project {
        val tmp = File(ctx.cacheDir, "import_${System.currentTimeMillis()}")
        tmp.mkdirs()
        ArchiveImporters.extract(input, tmp, fallbackName)
        return adoptProjectDir(ctx, tmp, fallbackName)
    }

    private fun adoptProjectDir(ctx: Context, tmp: File, fallbackName: String): Project {
        // project root is the folder containing project.json
        val metaDir = tmp.walkTopDown().firstOrNull { it.name == "project.json" }?.parentFile
            ?: throw IllegalArgumentException("Not an S Engine project (project.json missing)")
        val requested = if (metaDir == tmp) fallbackName.substringBeforeLast('.') else metaDir.name
        var name = sanitize(requested).ifBlank { "Imported" }
        val base = name
        var i = 2
        while (exists(ctx, name)) name = "$base ${i++}"
        val target = File(root(ctx), name)
        metaDir.copyRecursively(target)
        tmp.deleteRecursively()
        return Project(target)
    }
}
