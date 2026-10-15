package com.sengine

import com.sengine.project.AtomicFiles
import com.sengine.project.ProjectFiles
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectFilesTest {
    @Test fun exportsExcludeRecoveryBackupsAndHiddenFolders() {
        val dir = Files.createTempDirectory("export-policy").toFile()
        try {
            for ((path, included) in listOf("project.json" to true, "assets/player.png" to true,
                "scenes/Main.scene.json" to true, "scenes/Main.scene.json.bak1" to false,
                ".recovery/Main.scene.json" to false, ".git/config" to false, "assets/.temp" to false,
                "assets/work.tmp" to false)) {
                val file = File(dir, path); file.parentFile!!.mkdirs(); file.writeText("test")
                assertEquals(path, included, ProjectFiles.isPublishable(dir, file))
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun similarSiblingDirectoryIsNotInsideProject() {
        val root = Files.createTempDirectory("project-policy").toFile()
        try {
            val project = File(root, "project").apply { mkdirs() }
            val sibling = File(root, "project-escaped/payload").apply { parentFile!!.mkdirs(); writeText("bad") }
            assertFalse(ProjectFiles.isPublishable(project, sibling))
            try { AtomicFiles.child(project, "../project-escaped/payload"); fail() } catch (_: IllegalArgumentException) {}
            try { AtomicFiles.child(project, sibling.absolutePath); fail() } catch (_: IllegalArgumentException) {}
        } finally { root.deleteRecursively() }
    }
}
