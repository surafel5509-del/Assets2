package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.SimulationClock
import com.sengine.engine.camera.CameraDirector
import com.sengine.engine.camera.CameraPath
import com.sengine.engine.camera.CameraPose
import com.sengine.engine.camera.CameraRig
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.DeterministicRandom
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.physics.Aabb2
import com.sengine.engine.physics.Aabb3
import com.sengine.engine.physics.CastShape2
import com.sengine.engine.physics.CastShape3
import com.sengine.engine.physics.Falloff
import com.sengine.engine.physics.ForceFields
import com.sengine.engine.physics.ForcePointImpl
import com.sengine.engine.physics.JointLink
import com.sengine.engine.physics.JointMode
import com.sengine.engine.physics.JointPoint
import com.sengine.engine.physics.JointSolver
import com.sengine.engine.physics.ShapeCasts
import com.sengine.engine.physics.SoftBody
import com.sengine.engine.physics.SpatialHash2D
import com.sengine.engine.physics.SpatialHash3D
import com.sengine.engine.script.EventBus
import com.sengine.project.EngineInfo
import com.sengine.project.Project
import com.sengine.project.ProjectDiagnostics
import com.sengine.project.ProjectSchema
import com.sengine.project.ProjectSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/** v3 Professional Studio Edition: pure cores, script runtime and full-game regressions. */
class EngineV3Test {

    private fun newProject(name: String = "V3"): Project {
        val dir = Files.createTempDirectory("sengine3").toFile()
        val p = Project(File(dir, name))
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
                logs.add(message)
                if (level >= 2) errors.add(message)
                println("SIM v3 log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors, logs)
    }

    private fun Run.frames(n: Int, each: (Int) -> Unit = {}) {
        repeat(n) { i -> each(i); synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    // ============================================================ deterministic core
    @Test
    fun simulationClockIsDeterministicAndBounded() {
        val c = SimulationClock(1f / 60f, maxSubSteps = 5)
        var steps = 0
        repeat(120) { steps += c.advance(1f / 120f) }
        assertEquals(60, steps)
        assertEquals(60L, c.totalSteps)
        assertTrue(c.alpha in 0f..1f)
        val spike = c.advance(5f)
        assertEquals("frame spikes are clamped", 5, spike)
        assertEquals(0f, c.alpha, 1e-5f)
        c.reset()
        assertEquals(0L, c.totalSteps)
        assertEquals(1, c.advance(1f / 60f))
        println("SIM clock steps=$steps alpha=${c.alpha}")
    }

    @Test
    fun deterministicRandomReplaysExactly() {
        val a = DeterministicRandom(4242)
        val b = DeterministicRandom(4242)
        val seqA = (0 until 64).map { a.nextFloat() }
        val seqB = (0 until 64).map { b.nextFloat() }
        assertEquals(seqA, seqB)
        assertTrue(seqA.all { it in 0f..1f })
        a.reset()
        assertEquals(seqA.first(), a.nextFloat())
        assertTrue(a.nextInt(1, 6) in 1..6)
    }

    @Test
    fun spatialHashIsConservativeAndFaster() {
        val rnd = DeterministicRandom(9)
        val boxes = (0 until 120).map { i ->
            val x = rnd.nextFloat() * 100f - 50f
            val y = rnd.nextFloat() * 100f - 50f
            Aabb2(x, y, x + 0.5f + rnd.nextFloat(), y + 0.5f + rnd.nextFloat(), i)
        }
        val hash = SpatialHash2D(4f)
        val pairs = hash.candidatePairs(boxes).toSet()
        val brute = HashSet<Pair<Int, Int>>()
        for (i in boxes.indices) for (j in i + 1 until boxes.size) if (boxes[i].overlaps(boxes[j])) brute.add(i to j)
        for (p in brute) assertTrue("broad phase missed $p", pairs.contains(p))
        assertTrue("broad phase should examine fewer pairs", hash.lastPairCount < hash.lastBruteForcePairs)
        // very large body is paired with everything (global fallback)
        val big = listOf(Aabb2(-1e4f, -1e4f, 1e4f, 1e4f, 0), Aabb2(500f, 500f, 501f, 501f, 1))
        assertEquals(1, SpatialHash2D(4f, maxCellsPerBody = 16).candidatePairs(big).size)
        // 3D
        val boxes3 = (0 until 40).map { i ->
            val x = rnd.nextFloat() * 40f; val y = rnd.nextFloat() * 40f; val z = rnd.nextFloat() * 40f
            Aabb3(x, y, z, x + 1f, y + 1f, z + 1f, i)
        }
        val pairs3 = SpatialHash3D(6f).candidatePairs(boxes3).toSet()
        for (i in boxes3.indices) for (j in i + 1 until boxes3.size)
            if (boxes3[i].overlaps(boxes3[j])) assertTrue("3D broad phase missed $i/$j", pairs3.contains(i to j))
        println("SIM broadphase 2D ${hash.lastPairCount}/${hash.lastBruteForcePairs}")
    }

    @Test
    fun jointSolverDistanceSpringRopeAndBreak() {
        val a = JointPoint(0f, 0f, invMass = 1f)
        val b = JointPoint(5f, 0f, invMass = 1f)
        val link = JointLink(a, b, JointMode.DISTANCE, 1f, 0.9f, 0.1f)
        repeat(20) { JointSolver.solve(listOf(link), 1f / 60f, 4) }
        val d = abs(b.px - a.px)
        assertTrue("distance joint should converge ($d)", d in 0.9f..1.2f)

        val ropeA = JointPoint(0f, 0f)
        val ropeB = JointPoint(0.5f, 0f)
        val rope = JointLink(ropeA, ropeB, JointMode.ROPE, 2f, 1f, 0f)
        JointSolver.solve(listOf(rope), 1f / 60f, 4)
        assertTrue("rope must not push", abs(ropeB.px - ropeA.px) < 1f)

        val spring = JointLink(JointPoint(0f, 0f), JointPoint(4f, 0f), JointMode.SPRING, 2f, 12f, 0.5f)
        repeat(60) { JointSolver.solve(listOf(spring), 1f / 60f, 2) }
        assertTrue("spring link exists", spring.lastForce >= 0f)

        val breaking = JointLink(JointPoint(0f, 0f), JointPoint(9f, 0f), JointMode.DISTANCE, 1f, 1f, 0f, breakForce = 4f)
        assertEquals(1, JointSolver.solve(listOf(breaking), 1f / 60f, 1))
        assertTrue(breaking.broken)
    }

    @Test
    fun verletRopeAndClothAreStable() {
        val rope = SoftBody.rope("Rope", 0f, 10f, 0f, 12, 0.4f)
        rope.iterations = 8
        repeat(240) { rope.step(1f / 60f) }
        assertTrue("pinned head stays put", abs(rope.points[0].x) < 1e-4f && abs(rope.points[0].y - 10f) < 1e-4f)
        val tail = rope.points.last()
        assertTrue("tail hangs below the head", tail.y < 9f)
        assertTrue("no NaN", rope.points.all { it.x.isFinite() && it.y.isFinite() })
        val rest = rope.constraints[0].rest
        val seg = kotlin.math.sqrt((rope.points[1].x - rope.points[0].x).let { it * it } +
            (rope.points[1].y - rope.points[0].y).let { it * it })
        assertTrue("segment length ~$seg vs $rest", abs(seg - rest) < 0.15f)
        rope.radialImpulse(0f, 5f, 0f, 20f, 40f)
        rope.step(1f / 60f)
        assertTrue("explosion keeps rope finite", rope.points.all { it.x.isFinite() })
        val cloth = SoftBody.cloth("Banner", 0f, 5f, 0f, 6, 5, 0.5f)
        repeat(120) { cloth.step(1f / 60f) }
        assertEquals(30, cloth.points.size)
        assertTrue("cloth finite", cloth.points.all { it.y.isFinite() })
    }

    @Test
    fun shapeCastsAndAreaForces() {
        val shapes = listOf(CastShape2(5f, 0f, 0.5f, 2f), CastShape2(12f, 0f, 0f, 0f, true, 1f))
        val circle = ShapeCasts.circleCast(shapes, 0f, 0f, 1f, 0f, 0.5f, 20f)
        assertTrue("circle cast hits the near wall", circle != null && circle.distance < 5f)
        val miss = ShapeCasts.circleCast(shapes, 0f, 20f, 1f, 0f, 0.2f, 20f)
        assertTrue("circle cast misses above walls", miss == null)
        val box = ShapeCasts.boxCast(shapes, 0f, 0f, 1f, 0f, 0.5f, 0.5f, 20f)
        assertTrue(box != null)
        val shapes3 = listOf(CastShape3(0f, 0f, 8f, 1f, 1f, 1f))
        val sphere = ShapeCasts.sphereCast(shapes3, 0f, 0f, 0f, 0f, 0f, 1f, 0.6f, 30f)
        assertTrue(sphere != null && sphere.distance in 6f..8f)

        val bodies = listOf(
            ForcePointImpl(1f, 0f, mass = 2f),
            ForcePointImpl(9f, 0f, mass = 1f),
            ForcePointImpl(40f, 0f, mass = 1f)
        )
        assertEquals(2, ForceFields.explode(bodies, 0f, 0f, 0f, 10f, 20f, Falloff.LINEAR))
        assertTrue("near body pushed away", bodies[0].vx > 0f)
        assertEquals(0.0, bodies[2].vx.toDouble(), 0.0)
        ForceFields.wind(bodies, 3f, 0f, 0f, 1f)
        assertEquals(3f, bodies[2].vx - 0f, 0.001f)
        ForceFields.attract(bodies, 0f, 0f, 0f, 50f, 5f, Falloff.CONSTANT)
        assertTrue("far body pulled back", bodies[2].vx < 3f)
    }

    @Test
    fun cameraRigDeadZoneBoundsShakeAndPath() {
        val rig = CameraRig(smoothing = 8f)
        rig.snap(0f, 0f, 0f)
        rig.deadZoneW = 2f
        rig.deadZoneH = 2f
        rig.follow(0.5f, 0f, 0f, 0f, 0f, 1f / 60f)
        assertTrue("target inside dead zone keeps camera still", abs(rig.pose.x) < 0.35f)
        repeat(120) { rig.follow(6f, 0f, 0f, 4f, 0f, 1f / 60f) }
        assertTrue("camera eventually tracks the target", rig.pose.x > 3f)
        rig.bounds.enabled = true
        rig.bounds.minX = -2f; rig.bounds.maxX = 2f; rig.bounds.viewWidth = 0f
        repeat(60) { rig.follow(20f, 0f, 0f, 0f, 0f, 1f / 60f) }
        assertTrue("bounds clamp X", rig.pose.x <= 2.01f)
        rig.bounds.enabled = false
        rig.addTrauma(1f)
        rig.follow(0f, 0f, 0f, 0f, 0f, 1f / 60f)
        assertTrue("shake is non-zero", rig.shakeX != 0f || rig.shakeY != 0f)
        repeat(120) { rig.follow(0f, 0f, 0f, 0f, 0f, 1f / 60f) }
        assertEquals(0f, rig.trauma, 1e-4f)

        val path = CameraPath(listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(10f, 5f, 0f), floatArrayOf(20f, 0f, 0f)))
        assertTrue(path.totalLength > 20f)
        val p0 = path.sample(0f); val p1 = path.sample(1f)
        assertTrue(abs(p0[0]) < 0.01f)
        assertTrue(abs(p1[0] - 20f) < 0.01f)
        val mid = path.sample(0.5f)
        assertTrue("mid path near the middle control point", mid[0] in 8f..13f)
        val blend = CameraRig.blend(CameraPose(0f, 0f, 0f), CameraPose(10f, 0f, 0f), 0.5f)
        assertEquals(5f, blend.x, 0.01f)

        val director = CameraDirector()
        director.rig(1).priority = 1
        director.rig(2).priority = 9
        assertEquals(2L, director.pick(listOf(1L, 2L)))
        director.activeId = 2L
        director.switchTo(1L, 0.5f)
        assertTrue(director.isBlending)
        director.update(0.25f)
        assertEquals(0.5f, director.blendProgress, 0.01f)
        director.playPath(CameraPath(listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(4f, 0f, 0f))), 1f)
        assertTrue(director.isCinematic)
        assertEquals(1f, director.update(1f)!!, 0.01f)
        assertFalse(director.isCinematic)
        assertFalse(director.isBlending)
    }

    @Test
    fun eventBusOrderOnceAndWildcard() {
        val bus = EventBus()
        val order = ArrayList<String>()
        bus.on("hit") { order.add("a:${it}") }
        bus.once("hit") { order.add("b") }
        bus.on("*") { order.add("wild") }
        assertEquals(3, bus.emit("hit", 7))
        assertEquals(2, bus.emit("hit", 8))
        assertEquals(listOf("a:7", "b", "wild", "a:8", "wild"), order)
        assertEquals(1, bus.listenerCount("hit"))
        assertEquals(1, bus.listenerCount("*"))
        assertTrue(bus.off(1))
        assertEquals(1, bus.emit("hit", 9))
        bus.clear()
        assertEquals(0, bus.totalSubscriptions)
    }

    @Test
    fun projectSchemaMigratesAllGenerations() {
        val v1 = JSONObject().put("name", "Old").put("engine", "S Engine 1.0").put("startScene", "Main")
        val applied = ProjectSchema.migrate(v1)
        assertEquals(3, applied.size)
        assertEquals(EngineInfo.SCHEMA_VERSION, v1.getInt("schemaVersion"))
        assertEquals(EngineInfo.VERSION, v1.getString("engineVersion"))
        assertTrue(v1.has("settings"))
        assertTrue(ProjectSchema.validate(v1).isEmpty())
        val s = ProjectSettings.fromJson(v1.getJSONObject("settings"))
        assertEquals(60, s.fixedHz)
        assertEquals(250_000, s.scriptBudget)
        assertEquals("High", ProjectSettings.QUALITY[s.quality])
        assertTrue(ProjectSchema.migrate(v1).isEmpty()) // idempotent

        val p = newProject("Diag")
        val scene = Scene("Main")
        scene.create("Main Camera").add(Camera2D())
        scene.create("Hero").add(ScriptComponent().also { it.script = "Hero.js" })
        p.writeAsset("Hero.js", "function update(dt) {}")
        p.saveScene(scene)
        p.saveMeta()
        val report = ProjectDiagnostics.report(p)
        assertTrue(report.contains(EngineInfo.VERSION))
        assertTrue(report.contains("No issues found"))
        assertEquals(EngineInfo.SCHEMA_VERSION, p.schemaVersion)
        // migration is persisted on open
        val legacy = JSONObject().put("name", "Legacy").put("engine", "S Engine 1.0").put("startScene", "Main")
        File(p.dir, "project.json").writeText(legacy.toString(2))
        val reopened = Project(p.dir)
        assertEquals(EngineInfo.SCHEMA_VERSION, reopened.schemaVersion)
        assertTrue(reopened.appliedMigrations.isNotEmpty())
        assertEquals(EngineInfo.SCHEMA_VERSION, JSONObject(File(p.dir, "project.json").readText()).getInt("schemaVersion"))
    }

    // ============================================================ script runtime
    @Test
    fun v3ScriptApiRunsCleanCoroutinesEventsStateSavesAndHotReload() {
        val p = newProject("ScriptV3")
        p.writeAsset("V3.js", """
var steps = [];
function start() {
    log("V3 start");
    persist.hp = 3;
    events.on("go", function (n) { log("event " + n); });
    state.set("idle");
    coroutine(function* () {
        steps.push("a");
        yield waitSec(0.2);
        steps.push("b");
        yield waitUntil(function () { return time.time > 0.3; });
        steps.push("c");
        yield waitFor("go");
        steps.push("d");
        log("coroutine done");
    });
    soft.rope("Rope", 0, 5, 6, 0.5);
    soft.wind("Rope", 1, 0);
    physics.setIterations(2);
    game.save("score", 7);
    camera.setBounds(-20, -20, 20, 20);
    camera.setDeadZone(1, 1);
    camera.setLookAhead(0.2);
    camera.zoomTo(1.1);
    camera.shake(0.4);
    camera.playPath([[0,0,0],[5,5,0],[10,0,0]], 2);
    camera.stopPath();
    camera.setPriority(3);
}
function idle_enter() { log("idle enter"); }
function idle_update(dt) { }
function onHotReload() { log("reload hp=" + persist.hp); }
function update(dt) {
    state.update(dt);
    if (time.frame == 20) events.emit("go", 42);
    if (time.frame == 30) {
        var wall = physics.circleCast(0, 0, 1, 0, 0.5, 12, "Wall");
        log("steps=" + steps.join("") + " hp=" + persist.hp + " score=" + game.load("score", 0) +
            " wall=" + (wall ? wall.name : "none") + " dist=" + physics.hitDistance().toFixed(2) +
            " soft=" + soft.count() + " state=" + state.get() + " fixed=" + physics.getFixedStep().toFixed(4) +
            " rng=" + (random() > 0) + " version=" + game.getVersion());
    }
}
""".trimIndent())
        val s = Scene("Main")
        s.create("Main Camera").add(Camera2D().also { it.follow = "Tester" })
        s.create("Tester").add(ScriptComponent().also { it.script = "V3.js" })
        val wall = s.create("Wall")
        wall.x = 4f; wall.tag = "Wall"
        wall.add(Collider2D())
        p.saveScene(s); p.startScene = "Main"; p.saveMeta()
        val r = start(p)
        r.frames(40)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
        assertTrue(r.logs.any { it.contains("V3 start") })
        assertTrue(r.logs.any { it.contains("idle enter") })
        assertTrue(r.logs.any { it.contains("event 42") })
        assertTrue("coroutine must complete: ${r.logs}", r.logs.any { it.contains("steps=abcd") })
        assertTrue(r.logs.any { it.contains("coroutine done") })
        assertTrue(r.logs.any { it.contains("hp=3") && it.contains("score=7") })
        assertEquals(7.0, (r.engine.gameState.load("score") as Number).toDouble(), 0.0)
        assertEquals(1, r.engine.softBodies.size)
        assertEquals(6, r.engine.softBodies.get("Rope")!!.points.size)
        assertEquals(2, r.engine.physics.solverIterations)
        assertTrue(r.engine.scripts.timings.containsKey("V3.js"))
        assertTrue(r.engine.scripts.frameInstructions >= 0)

        r.engine.hotReloadScripts()
        r.frames(3)
        assertTrue("hot reload keeps persist: ${r.logs}", r.logs.any { it.contains("reload hp=3") })
        assertTrue("script errors after reload: ${r.errors}", r.errors.isEmpty())
        r.engine.stop(); r.frames(1)
    }

    @Test
    fun runawayScriptIsStoppedByInstructionBudget() {
        val p = newProject("Budget")
        p.settings.scriptBudget = 20_000
        p.writeAsset("Loop.js", "function update(dt) { var i = 0; while (true) { i++; } }")
        val s = Scene("Main")
        s.create("Main Camera").add(Camera2D())
        s.create("Looper").add(ScriptComponent().also { it.script = "Loop.js" })
        p.saveScene(s); p.startScene = "Main"; p.saveMeta()
        val r = start(p)
        r.frames(3)
        assertTrue("budget error missing: ${r.errors}", r.errors.any { it.contains("budget") })
        r.frames(3)
        assertEquals("engine must keep running", Engine.Mode.PLAY, r.engine.mode)
        r.engine.stop(); r.frames(1)
    }

    @Test
    fun cameraAndPhysicsIntegrationHeadless() {
        val p = newProject("Integration")
        p.settings.solverIterations = 2
        p.settings.broadphase = true
        p.saveMeta()
        val s = Scene("Main")
        val camGo = s.create("Main Camera")
        camGo.add(Camera2D().also {
            it.follow = "Hero"; it.boundsEnabled = true
            it.boundMinX = -5f; it.boundMaxX = 5f; it.boundMinY = -5f; it.boundMaxY = 5f
            it.deadZoneW = 1f; it.lookAhead = 0.2f
        })
        val hero = s.create("Hero")
        hero.add(com.sengine.engine.core.Rigidbody2D().also { it.gravityScale = 0f })
        hero.add(Collider2D().also { it.shape = 1 })
        hero.add(ScriptComponent().also { it.script = "Move.js" })
        p.writeAsset("Move.js", "function update(dt) { self.vx = 6; }")
        p.saveScene(s); p.startScene = "Main"; p.saveMeta()
        val r = start(p)
        r.frames(90)
        assertTrue("hero should move", r.engine.scene.find("Hero")!!.x > 3f)
        val cam = r.engine.scene.find("Main Camera")!!
        assertTrue("camera must clamp to bounds", cam.x <= 5.01f && cam.x >= -5.01f)
        assertTrue(r.engine.physics.clock.totalSteps >= 90)
        assertTrue(r.engine.physics.lastBroadphasePairs <= r.engine.physics.lastBruteForcePairs)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
        r.engine.stop(); r.frames(1)
    }
}
