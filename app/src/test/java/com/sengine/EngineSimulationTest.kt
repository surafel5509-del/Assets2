package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.SceneSerializer
import com.sengine.project.Project
import com.sengine.project.Templates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Headless simulation of the bundled templates: runs the real engine loop,
 * physics and Rhino scripts without any rendering.
 *
 * Templates are resolved by name via [Templates.byName], never by index. The
 * previous version of this file indexed `Templates.all` positionally, so when
 * the Platformer and Space Shooter templates were removed -- they are explicitly
 * out of scope for this engine -- every index silently retargeted at a different
 * template and these tests failed against scenes they were never written for.
 */
class EngineSimulationTest {

    private fun newProject(name: String): Project {
        val dir = Files.createTempDirectory("sengine").toFile()
        val p = Project(File(dir, "Test"))
        p.saveMeta()
        val t = Templates.byName(name) ?: error("no template named '$name'")
        t.build(p)
        p.saveMeta()
        return p
    }

    private class Run(val engine: Engine, val errors: MutableList<String>, val logs: MutableList<String>)

    private fun start(p: Project): Run {
        val e = Engine(p, p.loadScene(p.startScene))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = ArrayList<String>()
        val logs = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                logs.add(message); if (level >= 2) errors.add(message)
                println("SIM log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors, logs)
    }

    private fun Run.frames(n: Int, each: (Int) -> Unit = {}) {
        repeat(n) { i -> each(i); synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    @Test
    fun serializationRoundTrip() {
        val p = newProject("Physics Sandbox")
        val s = p.loadScene("Main")
        val json = SceneSerializer.toJson(s).toString()
        val s2 = SceneSerializer.fromJson(JSONObject(json))
        assertEquals(s.objects.size, s2.objects.size)
        assertEquals(json, SceneSerializer.toJson(s2).toString())
        println("SIM physics sandbox objects=${s.objects.size}")
    }

    /**
     * Dynamic bodies in the sandbox must come to rest on the static floor rather
     * than sinking through it.  This is the grounding coverage the removed
     * Platformer template used to provide.
     */
    @Test
    fun dynamicBodiesComeToRestOnTheFloor() {
        val r = start(newProject("Physics Sandbox"))
        val crates = r.engine.scene.objects.filter { it.name.startsWith("Crate") }
        assertTrue("sandbox should contain crates", crates.isNotEmpty())
        r.frames(180)
        val bodies = crates.mapNotNull { it.getAny<Rigidbody2D>() }
        println("SIM crates=${crates.size} bodies=${bodies.size} " +
                "grounded=${bodies.count { it.grounded }} lowestY=${crates.minOf { it.y }}")
        assertTrue("crates must not fall through the floor at y=-6.5",
            crates.all { it.y > -6.5f })
        assertTrue("at least one crate should be resting (grounded)",
            bodies.any { it.grounded })
        assertTrue("crates should have stopped drifting",
            bodies.all { kotlin.math.abs(it.vy) < 1f })
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun physicsSandboxTapSpawns() {
        val r = start(newProject("Physics Sandbox"))
        val before = r.engine.scene.objects.size
        r.frames(30)
        r.engine.input.rawTouchSX = 800f; r.engine.input.rawTouchSY = 200f; r.engine.input.tapPending = true
        r.frames(2)
        r.engine.input.tapPending = true
        r.frames(120)
        val after = r.engine.scene.objects.size
        val crates = r.engine.scene.objects.filter { it.name.startsWith("Crate") }
        println("SIM sandbox objects $before -> $after, lowest crate y=${crates.minOf { it.y }}")
        assertEquals(before + 2, after)
        assertTrue("crates should rest on the floor", crates.minOf { it.y } > -6.5f)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    /**
     * Stopping play mode must return the engine to EDIT and restore the scene, so
     * anything spawned during the run does not leak into the editor.  This was
     * previously only covered by the Platformer template's coin test.
     */
    @Test
    fun stoppingPlayModeRestoresTheScene() {
        val r = start(newProject("Physics Sandbox"))
        val before = r.engine.scene.objects.size
        r.engine.input.rawTouchSX = 800f; r.engine.input.rawTouchSY = 200f
        repeat(4) { r.engine.input.tapPending = true; r.frames(2) }
        r.frames(30)
        val during = r.engine.scene.objects.size
        assertTrue("tapping should have spawned objects", during > before)
        r.engine.stop()
        r.frames(2)
        val after = r.engine.scene.objects.size
        println("SIM stop: before=$before during=$during after=$after mode=${r.engine.mode}")
        assertEquals(Engine.Mode.EDIT, r.engine.mode)
        assertEquals("scene should be restored on stop", before, after)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    /**
     * Every template that ships must survive a few seconds of play with input
     * held down and produce no script errors.  This replaces the Space Shooter
     * long-run test and is strictly broader: it covers all remaining templates
     * instead of one, and it is immune to templates being added or removed.
     */
    @Test
    fun everyTemplateRunsWithoutScriptErrors() {
        for (t in Templates.all) {
            val r = start(newProject(t.name))
            r.frames(240) { i ->
                r.engine.input.joyX = if ((i / 60) % 2 == 0) 1f else -1f
                r.engine.input.rawA = i % 90 < 3
            }
            println("SIM template '${t.name}' ran 240 frames, objects=${r.engine.scene.objects.size}")
            assertTrue("template '${t.name}' reported script errors: ${r.errors}", r.errors.isEmpty())
        }
    }

    @Test
    fun scriptErrorsAreReportedNotThrown() {
        val p = newProject("Empty 2D")
        p.writeAsset("Bad.js", "function update(dt) { undefinedThing.foo(); }")
        val s = p.loadScene("Main")
        s.find("Square")!!.add(com.sengine.engine.core.ScriptComponent().also { it.script = "Bad.js" })
        p.saveScene(s)
        val r = start(p)
        r.frames(5)
        println("SIM errors=${r.errors}")
        assertEquals(1, r.errors.size)
    }
}
