package com.sengine.project

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Write to a sibling, sync it, then atomically replace. Never truncates the live file. */
object AtomicFiles {
    @Synchronized
    fun write(file: File, text: String, backups: Int = 0) {
        val parent = file.absoluteFile.parentFile!!
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create ${parent.name}" }
        val temp = File.createTempFile(".${file.name}.", ".tmp", parent)
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            val count = backups.coerceIn(0, 10)
            if (file.exists() && count > 0) {
                for (i in count downTo 2) {
                    val previous = File(parent, "${file.name}.bak${i - 1}")
                    if (previous.exists()) Files.copy(previous.toPath(), File(parent, "${file.name}.bak$i").toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                Files.copy(file.toPath(), File(parent, "${file.name}.bak1").toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            // minSdk 26 supports java.nio. If atomic moves are unavailable, fail without
            // deleting the original instead of silently falling back to a destructive write.
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }

    fun child(root: File, name: String): File {
        require(!File(name).isAbsolute && name.isNotBlank() && !name.contains('\\') && !name.contains('\u0000')) { "Invalid file name" }
        val base = root.canonicalFile.toPath()
        val target = File(root, name).canonicalFile
        require(target.toPath().startsWith(base) && target.toPath() != base) { "Path escapes project folder" }
        return target
    }
}
