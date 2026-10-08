package com.sengine

import com.sengine.export.ApkBuilder
import com.sengine.export.AxmlPatcher
import com.sengine.export.GameBuildConfig
import com.sengine.project.Project
import com.sengine.project.Templates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * Exports a real standalone game APK from the built S Engine APK -- the in-app
 * "export APK" path, which is the one feature nothing else exercises.
 *
 * Runs only when SENGINE_SOURCE_APK, SENGINE_KEYSTORE and SENGINE_EXPORT_OUT are
 * set; CI sets them after assembleDebug and then checks the output with
 * `apksigner verify` and `aapt2 dump badging`.  Locally, without a built APK, it
 * is skipped rather than failed.
 *
 * The source template is resolved by name.  This test used to build the "Space
 * Shooter" template, which was removed as explicitly out of scope; because the
 * test is skipped by default that broke silently -- the reference would have
 * thrown NoSuchElementException the first time anyone supplied the env vars.
 */
class ApkExportTest {
    @Test
    fun exportStandaloneGameApk() {
        val src = System.getenv("SENGINE_SOURCE_APK")
        val ks = System.getenv("SENGINE_KEYSTORE")
        val out = System.getenv("SENGINE_EXPORT_OUT")
        assumeTrue("export env not set", src != null && ks != null && out != null)
        assertTrue("source APK missing: $src", File(src!!).exists())

        val store = KeyStore.getInstance("PKCS12")
        File(ks!!).inputStream().use { store.load(it, "sengine".toCharArray()) }
        val alias = store.aliases().toList().first()
        val key = store.getKey(alias, "sengine".toCharArray()) as PrivateKey
        val certs = store.getCertificateChain(alias).map { it as X509Certificate }

        val dir = Files.createTempDirectory("export").toFile()
        val p = Project(File(dir, "Physics Run"))
        p.saveMeta()
        val template = Templates.byName("Physics Sandbox") ?: error("Physics Sandbox template missing")
        template.build(p)
        p.saveMeta()
        File(p.dir, ".build_settings.json").writeText("{}") // dotfiles must not be exported

        val cfg = GameBuildConfig("Physics Run", "com.sengine.game.physicsrun", "1.2.3", 7)
        val steps = ArrayList<String>()
        val t0 = System.currentTimeMillis()
        ApkBuilder.build(File(src), p.dir, cfg, key, certs, File(out!!), File(dir, "work")) { s, _ -> steps += s }
        println("SIM export ${File(out).length()} bytes in ${System.currentTimeMillis() - t0} ms, steps=${steps.distinct().size}")

        ZipFile(out).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue("exported APK must contain classes.dex", names.contains("classes.dex"))
            assertTrue("exported APK must contain resources.arsc", names.contains("resources.arsc"))
            assertTrue("exported APK must contain the game project",
                names.contains("assets/game/project/project.json"))
            assertTrue("exported APK must contain the game scenes",
                names.any { it.startsWith("assets/game/project/scenes/") })
            assertTrue("dotfiles excluded", names.none { it.endsWith(".build_settings.json") })
            assertTrue("old signature removed", names.none { it.endsWith(".RSA") || it.endsWith(".SF") })
            val info = JSONObject(z.getInputStream(z.getEntry("assets/game/build.json")).readBytes().decodeToString())
            assertEquals("com.sengine.game.physicsrun", info.getString("package"))
            assertEquals("Physics Run", info.getString("project"))
            val manifest = z.getInputStream(z.getEntry("AndroidManifest.xml")).readBytes()
            // Patching again is idempotent for already-renamed packages and exposes the string pool
            val strings = AxmlPatcher.patch(manifest, "com.sengine.game.physicsrun", "com.sengine.game.physicsrun", null, null, null).strings
            println("SIM export manifest strings: ${strings.filter { it.contains("sengine") || it == "Physics Run" || it == "1.2.3" }}")
            assertTrue("package name rewritten", strings.contains("com.sengine.game.physicsrun"))
            assertTrue("app label rewritten", strings.contains("Physics Run"))
            assertTrue("versionName rewritten", strings.contains("1.2.3"))
            assertTrue("fileprovider authority renamed", strings.contains("com.sengine.game.physicsrun.fileprovider"))
        }
    }
}
