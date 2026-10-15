package com.sengine.project

import java.io.File

/** One export policy for project archives and embedded game APKs. */
object ProjectFiles {
    fun isPublishable(root: File, file: File): Boolean {
        if (!file.isFile) return false
        val base = root.canonicalFile.toPath()
        val path = file.canonicalFile.toPath()
        if (!path.startsWith(base)) return false
        val relative = base.relativize(path)
        if (relative.any { it.toString().startsWith(".") }) return false
        return !file.name.matches(Regex(".*\\.bak\\d+$")) && !file.name.endsWith(".tmp")
    }
}
