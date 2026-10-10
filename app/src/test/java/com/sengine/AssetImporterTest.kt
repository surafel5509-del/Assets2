package com.sengine

import com.sengine.project.AssetFolders
import com.sengine.project.AssetImporter
import com.sengine.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AssetImporterTest {

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((n, body) in entries) {
                z.putNextEntry(ZipEntry(n)); z.write(body.toByteArray()); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun tempRoot(): File =
        File(System.getProperty("java.io.tmpdir"), "importer-test-" + System.nanoTime()).apply { mkdirs() }

    @Test
    fun bareNamesGoToTheRightBranch() {
        assertEquals("Sprites", AssetFolders.folderFor("hero.gif"))
        assertEquals("Sprites", AssetFolders.folderFor("logo.svg"))
        assertEquals("Fonts", AssetFolders.folderFor("Roboto.ttf"))
        assertEquals("Fonts", AssetFolders.folderFor("Roboto.woff2"))
        assertEquals("Models", AssetFolders.folderFor("Tree.glb"))
        assertEquals("Models", AssetFolders.folderFor("Tree.blend"))
        assertEquals("Archives", AssetFolders.folderFor("pack.7z"))
        assertEquals("Audio/Music", AssetFolders.folderFor("theme.ogg"))
    }

    @Test
    fun supportedTypesAreAcceptedAndOthersRefused() {
        assertTrue(AssetImporter.isSupported("a.TTF"))
        assertTrue(AssetImporter.isSupported("hud.ui.json"))
        assertFalse(AssetImporter.isSupported("a.exe"))
    }

    @Test
    fun singleFileImportAddsSuffixOnDuplicate() {
        val root = tempRoot()
        val project = Project(root).also { it.assetsDir.mkdirs() }
        val src = File(root, "src").apply { mkdirs() }
        val f = File(src, "hero.png").apply { writeText("a") }
        assertEquals(listOf("Sprites/hero.png"), AssetImporter.importFile(project, f).imported)
        assertEquals(listOf("Sprites/hero_2.png"), AssetImporter.importFile(project, f).imported)
        assertEquals(1, AssetImporter.importFile(project, File(src, "x.exe").apply { writeText("x") }).skipped.size)
        root.deleteRecursively()
    }

    @Test
    fun folderImportKeepsStructureAndIgnoresHiddenAndMacos() {
        val root = tempRoot()
        val project = Project(root).also { it.assetsDir.mkdirs() }
        val folder = File(root, "pack").apply { mkdirs() }
        File(folder, "music").mkdirs(); File(folder, "music/level.mp3").writeText("m")
        File(folder, ".DS_Store").writeText("x")
        File(folder, "__MACOSX").mkdirs(); File(folder, "__MACOSX/junk.png").writeText("x")
        File(folder, "Fonts").mkdirs(); File(folder, "Fonts/Body.otf").writeText("f")
        val r = AssetImporter.importFolder(project, folder)
        assertTrue("music/level.mp3" in r.imported)
        assertTrue("Fonts/Body.otf" in r.imported)
        assertTrue(r.imported.none { it.contains("junk") || it.contains(".DS") })
        root.deleteRecursively()
    }

    @Test
    fun zipImportRefusesZipSlipAndSystemEntries() {
        val root = tempRoot()
        val project = Project(root).also { it.assetsDir.mkdirs() }
        val zip = zipOf(
            "Scripts/player.js" to "//js",
            "../escape.js" to "bad",
            "/abs/evil.js" to "bad",
            "Sprites/tile.png" to "p",
            "__MACOSX/ghost.png" to "g",
            "readme.exe" to "e",
        )
        val r = AssetImporter.importZip(project, ByteArrayInputStream(zip))
        assertTrue("Scripts/player.js" in r.imported)
        assertTrue("Sprites/tile.png" in r.imported)
        assertFalse(File(root, "escape.js").exists())
        assertFalse(File(root.parentFile, "escape.js").exists())
        assertTrue(r.skipped.any { it.contains("unsafe") })
        assertTrue(r.skipped.any { it.contains("hidden or system") })
        assertTrue(r.skipped.any { it.contains("readme.exe") && it.contains("unsupported") })
        root.deleteRecursively()
    }

    @Test
    fun searchMatchesEveryTermAnywhereInThePath() {
        val root = tempRoot()
        val project = Project(root).also { it.assetsDir.mkdirs() }
        AssetImporter.importStream(project, "hero.png", ByteArrayInputStream(byteArrayOf(1)))
        AssetImporter.importStream(project, "music/level.mp3", ByteArrayInputStream(byteArrayOf(1)))
        assertTrue(AssetImporter.search(project, "hero png").contains("Sprites/hero.png"))
        assertTrue(AssetImporter.search(project, "level").contains("music/level.mp3"))
        assertEquals(project.listAssets().size, AssetImporter.search(project, "").size)
        assertTrue(AssetImporter.search(project, "nonexistent-xyz").isEmpty())
        root.deleteRecursively()
    }

    @Test
    fun safeNameSanitisesInput() {
        assertEquals("my_hero__v2_.png", AssetImporter.safeName("my hero (v2).png"))
        assertEquals("passwd", AssetImporter.safeName("../../etc/passwd"))
    }
}
