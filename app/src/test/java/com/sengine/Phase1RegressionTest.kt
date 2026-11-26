package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.script.ApiReference
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

/**
 * Phase 1 regression suite: real runtime behaviour of the bugs found during
 * the architecture inspection (not just "does the symbol exist").
 */
class Phase1RegressionTest {

    private fun project(): Project {
        val dir = Files.createTempDirectory("sengine-phase1").toFile()
        return Project(File(dir, "Test")).also { it.saveMeta() }
    }

    private class Run(val engine: Engine, val errors: MutableList<String>, val logs: MutableList<String>)

    private fun start(p: Project, sceneName: String = "Main"): Run {
        val e = Engine(p, p.loadScene(sceneName))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = ArrayList<String>()
        val logs = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                logs.add(message)
                if (level >= 2) errors.add(message)
            }
        })
        e.play()
        return Run(e, errors, logs)
    }

    private fun Run.frames(n: Int) {
        repeat(n) { synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    private fun sceneWithState(p: Project, script: String, name: String = "Probe.js"): GameObject {
        val s = p.loadScene("Main")
        val state = s.create("State")
        state.add(TextRenderer().also { it.text = "init" })
        p.writeAsset(name, script)
        val probe = s.create("Probe")
        probe.add(ScriptComponent().also { it.script = name })
        p.saveScene(s)
        return state
    }

    private fun Run.state(): String =
        engine.scene.find("State")?.getAny<TextRenderer>()?.text ?: ""

    // ------------------------------------------------------------------ sandbox
    @Test
    fun scriptsCannotReachHostJavaClasses() {
        val p = project()
        sceneWithState(p, """
function start() {
    var out = [];
    out.push("packages=" + (typeof Packages));
    out.push("java=" + (typeof java));
    var blocked = false;
    try { var f = Packages.java.io.File; } catch (e) { blocked = true; }
    out.push("blocked=" + (blocked ? 1 : 0));
    try { var c = self.getClass(); } catch (e) { out.push("classBlocked=1"); }
    try { var raw = self.unwrap().getClass(); } catch (e) { out.push("rawClassBlocked=1"); }
    try { var env = Packages.java.lang.System; } catch (e) { out.push("systemBlocked=1"); }
    scene.find("State").text = out.join(",");
}
""".trimIndent())
        val r = start(p)
        r.frames(3)
        val text = r.state()
        println("PHASE1 sandbox state='$text' errors=${r.errors}")
        assertTrue("Packages must be absent: $text", text.contains("packages=undefined"))
        assertTrue("java package must be absent: $text", text.contains("java=undefined"))
        assertTrue("LiveConnect lookup must fail: $text", text.contains("blocked=1"))
        assertTrue("raw unwrap() must not expose java.lang.Class: $text", text.contains("rawClassBlocked=1"))
        assertTrue("java.lang.System must be blocked: $text", text.contains("systemBlocked=1"))
        assertTrue("unexpected script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun infiniteScriptLoopIsAbortedAndEngineKeepsRunning() {
        val p = project()
        sceneWithState(p, """
function update(dt) {
    if (time.frame <= 2) { while (true) { } }
}
""".trimIndent(), "Loop.js")
        // A second script must keep running even when the first one is aborted.
        val s = p.loadScene("Main")
        p.writeAsset("Watchdog.js", """
function update(dt) { scene.find("State").text = "watchdog=" + time.frame; }
""".trimIndent())
        s.create("Watchdog").add(ScriptComponent().also { it.script = "Watchdog.js" })
        p.saveScene(s)
        val r = start(p)
        r.frames(8)
        println("PHASE1 loop state='${r.state()}' frame=${r.engine.frame} errors=${r.errors}")
        assertTrue("budget guard must report an error: ${r.errors}",
            r.errors.any { it.contains("exceeded", ignoreCase = true) || it.contains("budget", ignoreCase = true) })
        assertTrue("engine kept ticking after abort (frame=${r.engine.frame})", r.engine.frame >= 8L)
        assertTrue("other scripts must keep running: ${r.state()}", r.state().startsWith("watchdog="))
    }

    // ------------------------------------------------------------------ timers / helpers
    @Test
    fun timerErrorDoesNotBlockLaterTimersOrTweens() {
        val p = project()
        sceneWithState(p, """
function start() {
    after(0.02, function () { throw new Error("boom"); });
    after(0.06, function () { scene.find("State").text = "secondTimerRan"; });
    every(0.03, function () { if (time.frame < 4) throw new Error("repeatBoom"); });
    tween(self, "x", 5, 0.05, "quadOut");
}
""".trimIndent(), "Timers.js")
        val r = start(p)
        r.frames(20)
        println("PHASE1 timers state='${r.state()}' errors=${r.errors}")
        assertEquals("secondTimerRan", r.state())
        assertTrue("timer error must be reported", r.errors.any { it.contains("timer") })
        assertTrue("repeating timer error must be reported", r.errors.any { it.contains("repeatBoom") })
    }

    @Test
    fun randomHelpersHandleZeroAndOneArguments() {
        val p = project()
        sceneWithState(p, """
function start() {
    scene.find("State").text = [random(), random(5), randomInt(), randomInt(7), randomInt(3, 5)].join(",");
}
""".trimIndent(), "Random.js")
        val r = start(p)
        r.frames(2)
        val parts = r.state().split(",").map { it.toDouble() }
        println("PHASE1 random=${r.state()} errors=${r.errors}")
        assertEquals(5, parts.size)
        assertTrue("random() must be finite in [0,1)", parts[0] >= 0.0 && parts[0] < 1.0)
        assertTrue("random(5) must be finite in [0,5)", parts[1] >= 0.0 && parts[1] < 5.0)
        assertTrue("randomInt() must be 0 or 1", parts[2] == 0.0 || parts[2] == 1.0)
        assertTrue("randomInt(7) must be in 0..7", parts[3] >= 0.0 && parts[3] <= 7.0)
        assertTrue("randomInt(3,5) must be in 3..5", parts[4] >= 3.0 && parts[4] <= 5.0)
        assertTrue("unexpected errors: ${r.errors}", r.errors.isEmpty())
    }

    // ------------------------------------------------------------------ lifecycle
    @Test
    fun stoppingPlayResetsMutableRuntimeState() {
        val p = project()
        sceneWithState(p, """
function start() { log("started"); }
""".trimIndent(), "Freeze.js")
        val r = start(p)
        r.frames(1) // let the queued play() command initialise the runtime
        // Simulate a script leaving the engine in slow-motion / frozen state.
        r.engine.timeScale = 0f
        r.frames(3)
        assertEquals(0f, r.engine.timeScale, 0.0001f)
        assertTrue("time must be frozen by scale 0", r.engine.time < 0.02)
        r.engine.stop()
        r.frames(1)
        assertEquals(Engine.Mode.EDIT, r.engine.mode)
        assertEquals(1f, r.engine.timeScale, 0.0001f)
        r.engine.play()
        r.frames(10)
        println("PHASE1 reset time=${r.engine.time} scale=${r.engine.timeScale}")
        assertTrue("time must advance on the second play", r.engine.time > 0.1)
        assertEquals(1f, r.engine.timeScale, 0.0001f)
    }

    @Test
    fun releaseFromAnotherThreadStillDeliversOnStopOnOwnerThread() {
        val p = project()
        sceneWithState(p, """
function onStop() { log("onStopRan"); }
""".trimIndent(), "Stop.js")
        val r = start(p)
        r.frames(3)
        val t = Thread { r.engine.release() }
        t.start(); t.join(5000)
        // EditorActivity queues this on the GL thread; simulate that tick.
        synchronized(r.engine.lock) { r.engine.tick(1f / 60f) }
        println("PHASE1 release logs=${r.logs} mode=${r.engine.mode}")
        assertTrue("onStop() must be delivered before teardown: ${r.logs}", r.logs.contains("onStopRan"))
        assertEquals(Engine.Mode.EDIT, r.engine.mode)
    }

    // ------------------------------------------------------------------ physics
    @Test
    fun physics3DAppliesHorizontalGravity() {
        val p = project()
        val s = p.loadScene("Main")
        s.gravityX = 3f
        s.gravityZ = -2f
        s.gravity3D = 0f
        val go = s.create("Mover")
        go.add(Rigidbody3D().also { it.drag = 0f; it.gravityScale = 1f })
        p.saveScene(s)
        val r = start(p)
        r.frames(60)
        val rb = r.engine.scene.find("Mover")!!.getAny<Rigidbody3D>()!!
        println("PHASE1 gravity3D vx=${rb.vx} vz=${rb.vz} x=${r.engine.scene.find("Mover")!!.x}")
        assertTrue("gravityX must accelerate +X", rb.vx > 1f)
        assertTrue("gravityZ must accelerate -Z", rb.vz < -0.5f)
        assertTrue("unexpected errors: ${r.errors}", r.errors.isEmpty())
    }


    @Test
    fun newEngineOwnsItsOwnSandboxedRhinoContext() {
        // Phase 1 bug: Rhino reuses a thread-local Context. A previous Engine
        // left one associated with the test/GL thread, so the next Engine's
        // ScriptContextFactory.onContextCreated() was skipped and the loop
        // budget disappeared. This test deliberately leaks the first Engine.
        val p1 = project()
        sceneWithState(p1, "function start() { }", "Idle.js")
        val first = start(p1)
        first.frames(2) // intentionally no stop()

        val p2 = project()
        sceneWithState(p2, """
function update(dt) { if (time.frame <= 2) { while (true) { } } }
""".trimIndent(), "Loop.js")
        val s2 = p2.loadScene("Main")
        p2.writeAsset("Watchdog.js", "function update(dt) { scene.find(\"State\").text = \"watchdog=\" + time.frame; }")
        s2.create("Watchdog").add(ScriptComponent().also { it.script = "Watchdog.js" })
        p2.saveScene(s2)
        val second = start(p2)
        second.frames(8)
        println("PHASE1 stale-context state='${second.state()}' frame=${second.engine.frame} errors=${second.errors}")
        assertTrue("second engine must still enforce the budget: ${second.errors}",
            second.errors.any { it.contains("budget", ignoreCase = true) || it.contains("exceeded", ignoreCase = true) })
        assertTrue("watchdog must keep running", second.state().startsWith("watchdog="))
        second.engine.stop()
        second.frames(1)
    }

    @Test
    fun overlapsUsesRealColliderShapesAndOffsets() {
        val p = project()
        val s = p.loadScene("Main")
        val state = s.create("State"); state.add(TextRenderer())
        val circle = s.create("Circle")
        circle.add(Collider2D().also { it.shape = 1; it.radius = 0.2f })
        val far = s.create("FarBox"); far.x = 0.8f; far.add(Collider2D())
        val near = s.create("NearBox"); near.x = 0.65f; near.add(Collider2D())
        val offset = s.create("OffsetBox"); offset.add(Collider2D().also { it.offsetX = 3f })
        val target = s.create("Target"); target.x = 3f; target.add(Collider2D())
        p.writeAsset("Overlap.js", """
function start() {
    var a = scene.find("Circle");
    var out = "far=" + a.overlaps(scene.find("FarBox")) +
              ",near=" + a.overlaps(scene.find("NearBox")) +
              ",offset=" + scene.find("OffsetBox").overlaps(scene.find("Target"));
    scene.find("State").text = out;
}
""".trimIndent())
        s.create("Probe").add(ScriptComponent().also { it.script = "Overlap.js" })
        p.saveScene(s)
        val r = start(p)
        r.frames(2)
        println("PHASE1 overlaps='${r.state()}' errors=${r.errors}")
        assertEquals("far=false,near=true,offset=true", r.state())
        assertTrue("unexpected errors: ${r.errors}", r.errors.isEmpty())
    }


    @Test
    fun kinematicCarryWorksWhenRiderIsFirstInPair() {
        val p = project()
        val s = p.loadScene("Main")
        // Deliberate order: rider is added before the mover. In the broadphase
        // pair the kinematic platform becomes `b`, producing a -Y normal. The
        // old `m.ny >= -0.55f` guard skipped this case entirely.
        val rider = s.create("Rider")
        rider.x = -10f; rider.y = 20.45f
        rider.add(Collider2D().also { it.width = 1f; it.height = 1f })
        rider.add(Rigidbody2D().also { it.gravityScale = 0f })
        val platform = s.create("Platform")
        platform.x = -10f; platform.y = 20f
        platform.add(Collider2D().also { it.width = 4f; it.height = 0.5f })
        platform.add(Rigidbody2D().also { it.bodyType = 1 })
        p.saveScene(s)
        val r = start(p)
        r.frames(1) // drain queued play() and resolve live scene objects
        val livePlatform = r.engine.scene.find("Platform")!!
        val liveRider = r.engine.scene.find("Rider")!!
        synchronized(r.engine.lock) {
            livePlatform.get<Rigidbody2D>()!!.vx = 3f
        }
        r.frames(90)
        println("PHASE1 reverse carry platform=${livePlatform.x} rider=${liveRider.x}")
        assertTrue("platform moved", livePlatform.x > -8.5f)
        assertTrue("rider must be carried when the mover is the second body: ${liveRider.x}", liveRider.x > -8.5f)
        assertTrue("unexpected errors: ${r.errors}", r.errors.isEmpty())
    }

    // ------------------------------------------------------------------ persistence
    @Test
    fun sceneSavesAreAtomicAndRecoverFromCorruption() {
        val p = project()
        val s = p.loadScene("Main")
        s.create("Hero")
        p.saveScene(s)
        s.create("Villain")
        p.saveScene(s) // backup now holds the Hero-only version
        assertEquals(2, p.loadScene("Main").objects.size)
        assertFalse("a clean load must not report recovery", p.lastLoadRecovered)

        p.sceneFile("Main").writeText("{ truncated json")
        val recovered = p.loadScene("Main")
        println("PHASE1 recovery objects=${recovered.objects.size} recovered=${p.lastLoadRecovered}")
        assertTrue("corrupt main file must trigger backup recovery", p.lastLoadRecovered)
        assertEquals(1, recovered.objects.size)
        assertEquals("Hero", recovered.objects[0].name)
        assertTrue("no temp file left behind", p.sceneFile("Main").parentFile!!.listFiles()!!
            .none { it.name.endsWith(".tmp") })
    }

    @Test
    fun assetAndSceneNamesCannotEscapeTheProject() {
        val p = project()
        assertEquals("script.js", Project.safeName("../../script.js"))
        assertEquals("hero.png", Project.safeName("/sdcard/hero.png"))
        assertTrue(Project.isSafeName("Player.js"))
        assertFalse(Project.isSafeName("../Player.js"))
        p.writeAsset("Player.js", "function start(){}")
        assertNull("path traversal must not read project.json", p.readAsset("../../project.json"))
        assertEquals("function start(){}", p.readAsset("Player.js"))
        try {
            p.writeAsset("../evil.js", "x")
            assertTrue("writeAsset must reject traversal", false)
        } catch (_: IllegalArgumentException) {}
        assertFalse(p.sceneExists("../../project.json"))
    }


    @Test
    fun extensionlessAssetNamesGetNoTrailingDot() {
        val p = project()
        p.writeAsset("Sprite", "texture-without-extension")
        assertEquals("Sprite_1", p.uniqueAssetName("Sprite"))
        assertEquals("Sprite_1", p.assetFile(p.uniqueAssetName("Sprite")).name)
        p.writeAsset("hero.png", "png")
        assertEquals("hero_1.png", p.uniqueAssetName("hero.png"))
    }

    @Test
    fun zipSlipEntriesAreRejected() {
        val root = Files.createTempDirectory("sengine-zip").toFile()
        assertNull(ProjectManager.resolveZipEntry(root, "../evil.txt"))
        assertNull(ProjectManager.resolveZipEntry(root, "a/../../evil.txt"))
        assertNull(ProjectManager.resolveZipEntry(root, "/tmp/evil.txt"))
        assertNull(ProjectManager.resolveZipEntry(root, "C:/evil.txt"))
        assertNotNull(ProjectManager.resolveZipEntry(root, "assets/hero.png"))
        assertNotNull(ProjectManager.resolveZipEntry(root, "scenes/Main.scene.json"))
    }

    @Test
    fun projectExportExcludesInternalBackupFiles() {
        val p = project()
        p.writeAsset("hero.png", "not-a-real-png")
        val s = p.loadScene("Main"); s.create("Hero"); p.saveScene(s)
        s.create("Villain"); p.saveScene(s)
        File(p.dir, ".build_settings.json").writeText("{}")
        val bytes = ByteArrayOutputStream()
        ProjectManager.exportZip(p, bytes)
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names.add(e.name)
            }
        }
        println("PHASE1 export entries=$names")
        assertTrue("hero asset must be exported", names.any { it.endsWith("assets/hero.png") })
        assertTrue("scene must be exported", names.any { it.endsWith("scenes/Main.scene.json") })
        assertTrue("dot files/backups must not be exported: $names",
            names.none { it.substringAfterLast('/').startsWith(".") })
    }

    @Test
    fun audioStopAllClearsStateWithoutAPool() {
        val a = com.sengine.engine.AudioSystem(project())
        // Simulate stale stream ids left by a released SoundPool (the real bug).
        val field = com.sengine.engine.AudioSystem::class.java.getDeclaredField("streams")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val streams = field.get(a) as MutableList<Int>
        repeat(200) { streams.add(it + 1) }
        assertTrue("stream tracking should start dirty for this test", a.activeStreams > 0)
        a.stopAll()
        assertEquals(0, a.activeStreams)
        assertEquals(0, a.active3dSounds)
        assertTrue("stream tracking must be bounded",
            com.sengine.engine.AudioSystem.MAX_TRACKED_STREAMS in 1..1024)
    }

    // ------------------------------------------------------------------ docs
    @Test
    fun markdownApiDocMatchesCanonicalReference() {
        val locations = listOfNotNull(
            System.getProperty("sengine.repo"),
            System.getenv("SENGINE_REPO"),
            File(".").canonicalPath,
            File("..").canonicalPath,
            File("../..").canonicalPath,
        )
        val file = locations.map { File(it, "docs/SCRIPTING_API.md") }.firstOrNull { it.isFile }
            ?: locations.map { File(it, "SCRIPTING_API.md") }.firstOrNull { it.isFile }
        assertNotNull("docs/SCRIPTING_API.md must be reachable (set -Dsengine.repo=...)", file)
        assertEquals("editor + Markdown docs must share one canonical reference",
            ApiReference.API_DOC.trim(), file!!.readText().trim())
    }
}
