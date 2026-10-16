package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.Human
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.ParticleEmitter3D
import com.sengine.engine.core.ParticlePresets
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.Sun
import com.sengine.engine.core.Terrain
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.Water
import com.sengine.project.Project
import com.sengine.project.Templates
import com.sengine.project.ZipExtractor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Headless tests for the Professional upgrade: Sun, terrain, particles, materials, ZIP and the 2 new games. */
class ProEngineTest {

    private fun newProject(name: String): Project {
        val dir = Files.createTempDirectory("sengine-pro").toFile()
        val p = Project(File(dir, "Test"))
        p.saveMeta()
        Templates.all.first { it.name == name }.build(p)
        p.saveMeta()
        return p
    }

    private class Run(val engine: Engine, val errors: MutableList<String>)

    private fun start(p: Project): Run {
        val e = Engine(p, p.loadScene(p.startScene))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                if (level >= 2) errors.add(message)
                println("PRO log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors)
    }

    private fun Run.frames(n: Int, each: (Int) -> Unit = {}) {
        repeat(n) { i -> each(i); synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    private fun compiles(js: String, name: String) {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            cx.compileString(js, name, 1, null)
        } finally { Context.exit() }
    }

    @Test
    fun sunEditorMaths() {
        val sun = Sun()
        // noon sun shines mostly downwards, warm 3200K is redder than cold 9000K
        val d = sun.lightDirection()
        assertTrue(d[1] < -0.5f)
        assertTrue(sun.dayFactor() > 0.5f)
        val warm = Sun().also { it.temperature = 2500f }.effectiveColor()
        val cold = Sun().also { it.temperature = 9500f }.effectiveColor()
        val warmRed = (warm shr 16) and 0xFF; val warmBlue = warm and 0xFF
        val coldBlue = cold and 0xFF
        assertTrue("warm light should be red-dominant", warmRed > warmBlue)
        assertTrue("cold light should carry more blue", coldBlue > warmBlue)
        // kelvin conversion is monotonic-ish and clamped
        val k = Sun.kelvinToRgb(100f)
        assertTrue(k.all { it in 0f..1f })
        // day cycle: midnight fraction -> sun below horizon
        val night = Sun().also { it.animateDay = true; it.dayFraction = 0f }
        assertEquals(0f, night.dayFactor(), 0.001f)
    }

    @Test
    fun terrainNoiseIsDeterministicAndBounded() {
        val t = Terrain()
        val a = t.heightAt(3f, -2f)
        assertEquals(a, t.heightAt(3f, -2f), 0.0001f)
        assertTrue(a in 0f..t.height + 0.001f)
        // edges flatten to the border height
        assertEquals(0f, t.heightAt(-t.width / 2f, 0f), 0.001f)
        assertEquals(0f, t.heightAt(t.width / 2f, t.depth / 2f), 0.001f)
        // outside the terrain we report the origin height
        assertEquals(5f, t.heightAt(999f, 0f, originY = 5f), 0.001f)
    }

    @Test
    fun fourteenParticlePresetsApplyTo2DAnd3D() {
        assertTrue("need 10+ presets", ParticlePresets.all.size >= 14)
        assertEquals(ParticlePresets.all.size, ParticlePresets.NAMES.toSet().size)
        val e2 = ParticleEmitter()
        ParticlePresets.apply2D(e2, ParticlePresets.indexOf("Rain"))
        assertEquals(-90f, e2.direction, 0.001f)
        assertTrue(e2.gravity < 0f)
        val e3 = ParticleEmitter3D()
        ParticlePresets.apply3D(e3, ParticlePresets.indexOf("Fire"))
        assertEquals(1f, e3.directionY, 0.001f)
        assertTrue(e3.additive)
        assertEquals(-1, ParticlePresets.indexOf("No Such Preset"))
    }

    @Test
    fun perFaceTexturesResolveWithFallback() {
        val mr = MeshRenderer()
        mr.texture = "All.png"
        mr.textureMode = 1
        mr.textureTop = "Grass.png"
        assertEquals("Grass.png", mr.faceTexture(4))
        assertEquals("All.png", mr.faceTexture(0)) // unset face falls back to the main texture
        assertTrue(mr.usesPerFace)
        mr.mesh = 1
        assertFalse("per-face only applies to cubes", mr.usesPerFace)
    }

    @Test
    fun waterSurfaceStaysWithinWaveHeight() {
        val w = Water()
        for (i in 0 until 50) {
            val h = w.surfaceHeight(i * 0.37f, i * -0.21f, i * 0.13f)
            assertTrue(h >= -w.waveHeight - 0.001f && h <= w.waveHeight + 0.001f)
        }
    }

    @Test
    fun zipAssetPackImportAndZipSlipProtection() {
        val dir = Files.createTempDirectory("zip-pack").toFile()
        val p = Project(File(dir, "Pack")); p.saveMeta()
        val bytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zip ->
                fun entry(name: String, content: ByteArray) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
                }
                entry("textures/Hero.png", byteArrayOf(1, 2, 3))
                entry("scripts/Spin.js", "function update(dt){}".toByteArray())
                entry("Main.scene.json", "{\"name\":\"Imported\"}".toByteArray())
                entry("notes/readme.txt", "ignored".toByteArray())
                entry("../evil.png", byteArrayOf(9))
            }
        }.toByteArray()
        val report = ZipExtractor.importAssetPack(p, ByteArrayInputStream(bytes))
        println("ZIP report: $report imported=${report.imported}")
        assertTrue(p.assetFile("Hero.png").exists())
        assertTrue(p.assetFile("Spin.js").exists())
        assertTrue(p.sceneExists("Main"))
        assertTrue(report.skipped.contains("readme.txt"))
        // extract() must refuse to write outside the target folder
        val outDir = File(dir, "extract")
        ZipExtractor.extract(ByteArrayInputStream(bytes), outDir)
        assertFalse("zip-slip file must not escape", File(dir, "evil.png").exists())
        assertTrue(File(outDir, "textures/Hero.png").exists())
    }

    @Test
    fun sunnyValley3DRunsWithHumanSunGrassAndWater() {
        val p = newProject("Sunny Valley 3D")
        val scene = p.loadScene("Main")
        val names = scene.objects.map { it.name }
        for (required in listOf("Sun", "Valley Terrain", "Meadow Grass", "Pond", "Player", "Body", "Score Panel"))
            assertTrue("missing $required in $names", required in names)
        assertTrue(scene.objects.any { it.getAny<Human>() != null })
        assertTrue(scene.objects.any { it.getAny<Sun>() != null })
        // every script of the template must compile
        for (js in p.listAssets().filter { it.endsWith(".js") }) compiles(p.readAsset(js)!!, js)

        val r = start(p)
        val player = r.engine.scene.find("Player")!!
        r.frames(90)
        val rb = player.getAny<com.sengine.engine.core.Rigidbody3D>()!!
        assertTrue("hero should land", rb.grounded)
        val x0 = player.x
        r.frames(45) { r.engine.input.joyX = 1f }
        r.engine.input.joyX = 0f
        assertTrue("hero should walk east", player.x > x0 + 1f)
        val human = r.engine.scene.find("Body")!!.getAny<Human>()!!
        assertTrue("human walk cycle should advance", human.walkPhase > 0f)
        // collect a crystal by walking the player onto it
        val crystal = r.engine.scene.objects.first { it.tag == "Crystal" }
        val before = r.engine.scene.objects.count { it.tag == "Crystal" }
        synchronized(r.engine.lock) { player.x = crystal.x; player.z = crystal.z; player.y = crystal.y }
        r.frames(3)
        val after = r.engine.scene.objects.count { it.tag == "Crystal" }
        println("PRO valley crystals $before -> $after")
        assertEquals(before - 1, after)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun crystalCavern2DRunsWithPanelsAndParticles() {
        val p = newProject("Crystal Cavern 2D")
        val scene = p.loadScene("Main")
        assertTrue(scene.objects.any { it.getAny<com.sengine.engine.core.UIPanel>() != null })
        val emitters = scene.objects.count { it.getAny<ParticleEmitter>() != null }
        assertTrue("cavern should showcase several particle systems, got $emitters", emitters >= 5)
        for (js in p.listAssets().filter { it.endsWith(".js") }) compiles(p.readAsset(js)!!, js)

        val r = start(p)
        val player = r.engine.scene.find("Player")!!
        r.frames(60)
        val x0 = player.x
        r.frames(40) { r.engine.input.joyX = 1f }
        r.engine.input.joyX = 0f
        assertTrue("hero should run", player.x > x0 + 0.5f)
        // collect a crystal through the trigger
        val crystal = r.engine.scene.objects.first { it.tag == "Crystal" }
        val before = r.engine.scene.objects.count { it.tag == "Crystal" }
        synchronized(r.engine.lock) { player.x = crystal.x; player.y = crystal.y }
        r.frames(3)
        val after = r.engine.scene.objects.count { it.tag == "Crystal" }
        val label = r.engine.scene.find("ScoreText")!!.getAny<TextRenderer>()!!.text
        println("PRO cavern crystals $before -> $after label='$label'")
        assertEquals(before - 1, after)
        assertTrue(label.contains("Crystals: 1"))
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun allTemplatesSerializeRoundTrip() {
        for (t in Templates.all) {
            val dir = Files.createTempDirectory("tpl").toFile()
            val p = Project(File(dir, "T")); p.saveMeta()
            t.build(p); p.saveMeta()
            val s = p.loadScene(p.startScene)
            val json = SceneSerializer.toJson(s).toString()
            assertEquals("template '${t.name}' round trip", json,
                SceneSerializer.toJson(SceneSerializer.fromJson(JSONObject(json))).toString())
            println("PRO template '${t.name}' objects=${s.objects.size}")
        }
    }

    @Test
    fun newScriptGlobalsExist() {
        // the extended prelude + API must at least compile as a script using them
        compiles(
            """
            function awake() { log(world.terrainHeight(0, 0)); }
            function start() { events.on("x", function (v) {}); game.timeScale = 1; }
            function update(dt) { self.burstPreset("Fire", 3); self.setFaceTexture("top", "Grass.png"); }
            function lateUpdate(dt) { var t = instantiate("Coin", 1, 2, 3); destroy(t, 1); }
            """.trimIndent(), "globals.js")
    }
}
