package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.script.ApiReference
import com.sengine.engine.script.SAssets
import com.sengine.engine.script.SAudio
import com.sengine.engine.script.SConsole
import com.sengine.engine.script.SInput
import com.sengine.engine.script.SModules
import com.sengine.engine.script.SObject
import com.sengine.engine.script.SScene
import com.sengine.engine.script.STime
import com.sengine.engine.script.ScriptSystem
import com.sengine.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Rule 6/7 compliance: one API implementation shared by docs, editor and
 * runtime. These tests fail when documentation claims a function that is not
 * implemented, and they exercise every API called out in the master prompt.
 */
class ApiAuditTest {

    private fun project(): Project {
        val dir = Files.createTempDirectory("sengine-api").toFile()
        return Project(File(dir, "Test")).also { it.saveMeta() }
    }

    private class Run(val engine: Engine, val errors: MutableList<String>, val logs: MutableList<String>)

    private fun start(p: Project, scene: String = "Main"): Run {
        val e = Engine(p, p.loadScene(scene))
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

    private fun Run.state(): String =
        engine.scene.find("State")?.getAny<TextRenderer>()?.text ?: ""

    @Test
    fun everyDocumentedFunctionExistsInTheRuntime() {
        val documented = Regex("""\b([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
            .findAll(ApiReference.API_DOC).map { it.groupValues[1] }.toSet()
        val runtime = listOf(
            SObject::class.java, SScene::class.java, SInput::class.java,
            STime::class.java, SAudio::class.java, SConsole::class.java, SModules::class.java,
            SAssets::class.java,
        ).flatMap { it.declaredMethods.map { m -> m.name } }.toSet()
        val prelude = Regex("""function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
            .findAll(ScriptSystem.PRELUDE).map { it.groupValues[1] }.toSet()
        val lifecycle = setOf(
            "start", "update", "onFixedUpdate", "onLateUpdate", "onEnable", "onDisable",
            "onCollision", "onTrigger", "onTriggerExit", "onTap", "onDestroy", "onStop",
            "onUIButtonClick", "onClick",
        )
        val jsBuiltins = setOf(
            "String", "Number", "Boolean", "Object", "Array", "Math", "JSON", "Error",
            "Date", "RegExp", "Function", "parseInt", "parseFloat", "isNaN", "eval",
        )
        val missing = documented.filter {
            it !in runtime && it !in prelude && it !in lifecycle && it !in jsBuiltins
        }
        println("API AUDIT documented=${documented.size} runtime=${runtime.size} prelude=${prelude.size} missing=$missing")
        assertTrue("Documented but not implemented: $missing", missing.isEmpty())
        // Guard against an empty/vacuous audit.
        assertTrue("audit must cover the real API", documented.size > 70)
    }

    @Test
    fun requiredScriptApisPerformRealWork() {
        val p = project()
        val s = p.loadScene("Main")
        val state = s.create("State"); state.add(TextRenderer())

        val probe = s.create("Probe")
        probe.x = 0f; probe.y = 0f
        probe.add(SpriteRenderer())
        probe.add(Rigidbody2D().also { it.bodyType = 2 }) // static: still owns velocity state
        val child: GameObject = s.create("Child", probe)
        child.add(SpriteRenderer())

        val other = s.create("Other"); other.x = 1f; other.y = 0f
        other.add(SpriteRenderer())
        val targetA = s.create("TargetA"); targetA.tag = "Target"
        val targetB = s.create("TargetB"); targetB.tag = "Target"

        val doomed = s.create("Doomed")
        doomed.add(SpriteRenderer())

        // Spawn template with a script that answers send().
        p.writeAsset("Ping.js", "function ping(n) { return n * 2; }\n")
        val template = s.create("Template"); template.x = 20f
        template.add(SpriteRenderer())
        template.add(ScriptComponent().also { it.script = "Ping.js" })

        // Overlap pair: two 1x1 boxes whose centres are 0.5 apart.
        val boxA = s.create("BoxA"); boxA.add(Collider2D())
        val boxB = s.create("BoxB"); boxB.x = 0.5f; boxB.add(Collider2D())

        p.writeAsset("Audit.js", """
var report = [];
function ok(name, cond) { report.push(name + "=" + (cond ? 1 : 0)); }
function write() { var st = scene.find("State"); if (st) st.text = report.join(","); }
function start() {
    ok("self", self !== undefined);
    ok("transform", transform === self);
    ok("gameObject", gameObject === self);
    self.setPosition(2, 3);
    ok("setPosition", self.x === 2 && self.y === 3);
    self.move(1, -1);
    ok("move", self.x === 3 && self.y === 2);
    self.setVelocity(4, 0);
    ok("setVelocity", self.vx === 4);
    self.addForce(2, 0);
    ok("addForce", self.vx > 4);
    var c = self.child("Child");
    ok("child", c !== null);
    ok("parent", c !== null && c.parent.id === self.id);
    ok("find", scene.find("Other") !== null);
    ok("findAll", scene.findAll("Target").length === 2);
    ok("count", scene.count("Target") === 2);
    var spawned = scene.spawn("Template", 5, 6);
    ok("spawn", spawned !== null && spawned.x === 5 && spawned.y === 6);
    ok("send", spawned.send("ping", 9) == 18);
    ok("distanceTo", Math.abs(self.distanceTo(spawned) - Math.sqrt(20)) < 0.001);
    ok("overlaps", scene.find("BoxA").overlaps(scene.find("BoxB")));
    ok("hasComponent", self.hasComponent("Script"));
    var o = scene.find("Other");
    o.setComponentEnabled("SpriteRenderer", false);
    ok("setComponentEnabled", !o.visible);
    ok("random", random() >= 0 && random() < 1);
    var ri = randomInt(3, 5);
    ok("randomInt", ri >= 3 && ri <= 5);
    audio.play("missing.wav");
    ok("audio.play", true);
    var d = scene.find("Doomed"); d.destroy();
    after(0.03, function () { ok("after", true); write(); });
    every(0.03, function () { ok("every", true); write(); });
    write();
}
""".trimIndent())
        probe.add(ScriptComponent().also { it.script = "Audit.js" })
        p.saveScene(s)

        val r = start(p)
        r.frames(20)
        val text = r.state()
        println("API AUDIT state='$text'")
        println("API AUDIT errors=${r.errors}")
        assertTrue("unexpected script errors: ${r.errors}", r.errors.isEmpty())
        val results = text.split(",").filter { it.contains("=") }
            .associate { it.substringBefore("=") to it.substringAfter("=") }
        val required = listOf(
            "self", "transform", "gameObject", "setPosition", "move", "setVelocity",
            "addForce", "child", "parent", "find", "findAll", "count", "spawn", "send",
            "distanceTo", "overlaps", "hasComponent", "setComponentEnabled", "random",
            "randomInt", "audio.play", "after", "every",
        )
        for (key in required) {
            assertEquals("$key must actually work (report=$text)", "1", results[key])
        }
        assertNull("destroy() must remove the object from the scene", r.engine.scene.find("Doomed"))
    }

    @Test
    fun sceneLoadSwitchesToASavedScene() {
        val p = project()
        val main = p.loadScene("Main")
        main.create("MainObject")
        p.writeAsset("Load.js", "function start() { scene.load(\"Level2\"); }\n")
        main.create("Loader").add(ScriptComponent().also { it.script = "Load.js" })
        p.saveScene(main)
        val level2 = p.loadScene("Level2")
        level2.create("Level2Object")
        p.saveScene(level2)

        val r = start(p)
        r.frames(6)
        println("API AUDIT load scene=${r.engine.scene.name} logs=${r.logs}")
        assertEquals("Level2", r.engine.scene.name)
        assertNotNull("Level2 object must be present", r.engine.scene.find("Level2Object"))
        assertNull("Main object must be gone", r.engine.scene.find("MainObject"))
        assertTrue("scene.load must log the transition", r.logs.any { it.contains("Loaded scene Level2") })
        assertTrue("unexpected errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun sceneReloadRestoresThePlaySnapshot() {
        val p = project()
        val s = p.loadScene("Main")
        s.create("Hero")
        s.create("State").add(TextRenderer())
        // Reload only for the first few frames; time/frame is not reset by a
        // scene load, so this terminates even though scripts are re-created.
        p.writeAsset("Reload.js", "function start() { if (time.frame < 3) scene.reload(); }\n")
        s.create("Reloader").add(ScriptComponent().also { it.script = "Reload.js" })
        p.saveScene(s)

        val r = start(p)
        r.frames(10)
        val loads = r.logs.count { it.contains("Loaded scene Main") }
        println("API AUDIT reload loads=$loads objects=${r.engine.scene.objects.size} errors=${r.errors}")
        assertTrue("scene.reload() must actually reload: logs=${r.logs}", loads >= 2)
        assertEquals("Main", r.engine.scene.name)
        assertNotNull("snapshot object must survive reload", r.engine.scene.find("Hero"))
        assertEquals(3, r.engine.scene.objects.size)
        assertTrue("unexpected errors: ${r.errors}", r.errors.isEmpty())
    }
}
