package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.ScriptComponent
import com.sengine.project.Project
import com.sengine.project.ProjectDiagnostics
import com.sengine.project.StudioPlan
import com.sengine.project.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import java.io.File
import java.nio.file.Files

/**
 * Acceptance tests for the Professional Studio Edition: the two complete
 * reference games, their scene/asset completeness, headless playability, and
 * the two-year project system exposed to both Kotlin and JavaScript.
 */
class BigGamesTest {

    private fun newProject(name: String): Project {
        val dir = Files.createTempDirectory("sengine3-games").toFile()
        val p = Project(File(dir, name))
        p.saveMeta()
        Templates.all.first { it.name == name }.build(p)
        p.saveMeta()
        return p
    }

    private class Run(val engine: Engine, val errors: MutableList<String>, val logs: MutableList<String>)

    private fun start(p: Project, scene: String = p.startScene): Run {
        val e = Engine(p, p.loadScene(scene))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = ArrayList<String>()
        val logs = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                logs.add(message)
                if (level >= 2) errors.add(message)
                if (level >= 1 || message.startsWith("Loaded scene")) println("SIM game log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors, logs)
    }

    private fun Run.frames(n: Int, each: (Int) -> Unit = {}) {
        repeat(n) { i -> each(i); synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    private fun Run.stop() {
        engine.stop(); frames(1)
    }

    private fun compiles(js: String, name: String) {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            cx.compileString(js, name, 1, null)
        } finally { Context.exit() }
    }

    private fun assertGameProject(p: Project, scenes: List<String>, scripts: List<String>) {
        val actualScenes = p.listScenes()
        for (s in scenes) assertTrue("missing scene $s in ${p.name}: $actualScenes", actualScenes.contains(s))
        val assets = p.listAssets()
        for (s in scripts) assertTrue("missing asset $s in ${p.name}: $assets", assets.contains(s))
        for (s in actualScenes) {
            val scene = p.loadScene(s)
            assertTrue("scene $s is empty", scene.objects.isNotEmpty())
            assertTrue("scene $s has no camera", scene.objects.any {
                it.components.any { c -> c.type == "Camera" || c.type == "Camera3D" }
            })
            val roundTrip = SceneSerializer.fromJson(SceneSerializer.toJson(scene))
            assertEquals("scene $s serialization", scene.objects.size, roundTrip.objects.size)
        }
        for (s in scripts) {
            val js = p.readAsset(s) ?: error("cannot read $s")
            compiles(js, s)
        }
        val report = ProjectDiagnostics.report(p)
        assertTrue("diagnostics must identify v3: $report", report.contains("3.0.0"))
        assertTrue("diagnostics must identify the project: $report", report.contains(p.name))
        assertFalse("diagnostics should not warn on complete games: $report", report.contains("Warnings:"))
    }

    private fun assertSceneRuns(p: Project, scene: String, frames: Int = 30) {
        val r = start(p, scene)
        r.frames(frames)
        assertTrue("errors in ${p.name}/$scene: ${r.errors}", r.errors.isEmpty())
        r.stop()
        assertTrue("errors while stopping ${p.name}/$scene: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun bothFullGamesAreRegisteredCompleteAndSerialisable() {
        val names = Templates.all.map { it.name }
        assertTrue("Citadel of Echoes template missing", names.contains("Citadel of Echoes"))
        assertTrue("Neon Apex GP template missing", names.contains("Neon Apex GP"))
        assertEquals("10 classic templates + 2 full games", 12, Templates.all.size)

        val citadel = newProject("Citadel of Echoes")
        assertGameProject(
            citadel,
            listOf("Main", "Crypt", "Halls", "Throne", "Victory", "GameOver"),
            listOf("CitadelMenu.js", "CitadelHero.js", "CitadelBolt.js", "CitadelEnemy.js",
                "CitadelRelic.js", "CitadelHud.js", "CitadelDoor.js", "CitadelPlatform.js",
                "CitadelBoss.js", "CitadelOrb.js", "CitadelEnd.js")
        )
        val apex = newProject("Neon Apex GP")
        assertGameProject(
            apex,
            listOf("Main", "Circuit", "Podium"),
            listOf("ApexMenu.js", "ApexRace.js", "ApexCar.js", "ApexRival.js", "ApexPodium.js")
        )
        println("SIM games Citadel scenes=${citadel.listScenes().size} assets=${citadel.listAssets().size} " +
            "Apex scenes=${apex.listScenes().size} assets=${apex.listAssets().size}")
    }

    @Test
    fun citadelOfEchoesPlaysFromMenuThroughBossToVictory() {
        val p = newProject("Citadel of Echoes")
        val r = start(p)
        r.frames(60)
        assertEquals("Main", r.engine.scene.name)
        assertTrue("menu errors: ${r.errors}", r.errors.isEmpty())

        // Press A on the title screen -> Crypt
        r.engine.input.rawA = true
        r.frames(1)
        r.engine.input.rawA = false
        r.frames(4)
        assertEquals("title screen must enter the first level", "Crypt", r.engine.scene.name)
        r.frames(50)
        assertTrue("Crypt errors: ${r.errors}", r.errors.isEmpty())
        assertTrue("Citadel must create a soft-body lantern", r.engine.softBodies.size >= 1)

        // Walk through all six scenes; every one must run headlessly without script errors.
        for (s in listOf("Crypt", "Halls", "Throne", "Victory", "GameOver")) assertSceneRuns(p, s, 25)

        // Two-phase boss: 12 damage events defeat it, persist the haul and load Victory.
        val bossRun = start(p, "Throne")
        bossRun.frames(20)
        repeat(12) {
            bossRun.engine.scripts.emitEvent("bossDamage", null)
            bossRun.frames(1)
        }
        bossRun.frames(3)
        assertEquals("defeated boss must load Victory", "Victory", bossRun.engine.scene.name)
        assertEquals(0.0, (bossRun.engine.gameState.load("bossHp") as Number).toDouble(), 0.0)
        assertTrue("victory must award relics", (bossRun.engine.gameState.load("relics") as Number).toDouble() >= 10.0)
        assertTrue("boss fight errors: ${bossRun.errors}", bossRun.errors.isEmpty())
        bossRun.stop()
    }

    @Test
    fun neonApexGpRunsCountdownWaypointsLapsAndPodium() {
        val p = newProject("Neon Apex GP")
        val r = start(p)
        r.frames(40)
        assertEquals("Main", r.engine.scene.name)
        assertTrue("menu errors: ${r.errors}", r.errors.isEmpty())

        r.engine.input.rawA = true
        r.frames(1)
        r.engine.input.rawA = false
        r.frames(4)
        assertEquals("title screen must enter the circuit", "Circuit", r.engine.scene.name)
        r.engine.timeScale = 4f // fast-forward the 3-second countdown deterministically
        r.frames(75)
        assertTrue("countdown errors: ${r.errors}", r.errors.isEmpty())
        assertTrue("HUD must go live", r.engine.scene.find("Hud")!!.getAny<com.sengine.engine.core.TextRenderer>()!!.text.contains("LAP"))

        val waypoints = listOf(
            0f to -30f, 24f to -24f, 34f to 0f, 24f to 24f,
            0f to 30f, -24f to 24f, -34f to 0f, -24f to -24f
        )
        // Drive the waypoint circuit for three laps by teleporting the car just
        // before each fixed frame; Race.js still performs all lap/timer logic.
        repeat(24) { lapStep ->
            val w = waypoints[lapStep % waypoints.size]
            r.engine.scene.find("Car")?.let { car -> car.x = w.first; car.z = w.second }
            r.frames(1)
        }
        r.frames(3)
        assertEquals("three laps must load the podium", "Podium", r.engine.scene.name)
        assertEquals(3.0, (r.engine.gameState.load("lap") as Number).toDouble(), 0.0)
        assertTrue("best lap must be recorded", (r.engine.gameState.load("bestLap") as Number).toDouble() > 0.0)
        assertTrue("race errors: ${r.errors}", r.errors.isEmpty())
        r.stop()
        for (s in listOf("Main", "Circuit", "Podium")) assertSceneRuns(p, s, 25)
    }

    @Test
    fun twoYearRoadmapPersistsAndIsVisibleToScripts() {
        val p = newProject("Citadel of Echoes")
        val plan = StudioPlan.load(p)
        assertEquals(2, StudioPlan.YEARS)
        assertEquals(24, StudioPlan.MONTHS)
        assertEquals(24, plan.milestones.size)
        assertEquals(19, plan.completeCount) // engine capabilities shipped in v3
        assertTrue("roadmap must contain two-year plan", StudioPlan.report(plan).contains("Two-year production plan"))
        assertTrue(StudioPlan.markDone(p, "M20", true))
        assertTrue(File(p.dir, "roadmap.json").exists())
        assertEquals(20, StudioPlan.load(p).completeCount)
        assertEquals(24, StudioPlan.load(p).milestones.size) // forward merge keeps full plan

        p.writeAsset("StudioProbe.js", """
function start() {
    log("studio years=" + studio.years() + " months=" + studio.months() +
        " progress=" + studio.progress() + " milestones=" + studio.milestones().length +
        " next=" + studio.next() + " diag=" + (studio.diagnostics().indexOf("Engine") >= 0) +
        " sign=" + studio.complete("M21"));
}
""".trimIndent())
        val scene = p.loadScene("Main")
        scene.create("StudioProbe").add(ScriptComponent().also { it.script = "StudioProbe.js" })
        p.saveScene(scene)
        val r = start(p)
        r.frames(2)
        assertTrue("studio script errors: ${r.errors}", r.errors.isEmpty())
        assertTrue("studio API must be visible: ${r.logs}", r.logs.any {
            it.contains("studio years=2") && it.contains("months=24") &&
                it.contains("milestones=24") && it.contains("diag=true") && it.contains("sign=true")
        })
        assertEquals(21, StudioPlan.load(p).completeCount)
        r.stop()
    }
}
