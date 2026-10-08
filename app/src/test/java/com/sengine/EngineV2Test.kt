package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.anim.AnimationClip
import com.sengine.engine.blueprint.Blueprint
import com.sengine.engine.blueprint.BlueprintCompiler
import com.sengine.engine.blueprint.BlueprintNodes
import com.sengine.engine.core.Animator
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.project.AssetLibrary
import com.sengine.project.Project
import com.sengine.project.Templates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import java.io.File
import java.nio.file.Files

/**
 * Headless tests for the Ultimate Edition systems: 3D physics, animation,
 * blueprints and asset store content.
 *
 * Templates are looked up by name via [Templates.byName]. The animation tests no
 * longer depend on a template at all -- the "Animated Platformer" template was
 * removed (Platformer demos are explicitly out of scope for this engine), so they
 * now build their own scene and drive [AnimationSystem] through the real engine
 * loop. That keeps the coverage and removes the dependency on template contents.
 */
class EngineV2Test {

    private fun newProject(name: String): Project {
        val dir = Files.createTempDirectory("sengine2").toFile()
        val p = Project(File(dir, "Test"))
        p.saveMeta()
        val t = Templates.byName(name) ?: error("no template named '$name'")
        t.build(p)
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
                println("SIM v2 log[$level]: $message")
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

    /** A project holding one hero sprite driven by a hand-written .anim clip. */
    private fun animProject(clip: AnimationClip, playOnStart: Boolean = true, active: Boolean = true): Pair<Project, String> {
        val dir = Files.createTempDirectory("sengineAnim").toFile()
        val p = Project(File(dir, "AnimTest"))
        p.saveMeta()
        p.writeAsset("HeroRun.anim", clip.toJson().toString())
        val s = Scene("Main")
        val cam = s.create("Main Camera")
        cam.add(Camera2D().also { it.size = 5f })
        val hero = s.create("Hero")
        hero.add(SpriteRenderer())
        hero.add(Animator().also { it.clip = "HeroRun.anim"; it.playOnStart = playOnStart })
        hero.active = active
        p.saveScene(s)
        p.startScene = "Main"
        p.saveMeta()
        return p to "Hero"
    }

    @Test
    fun demo3dPhysicsAndPickups() {
        val p = newProject("3D Demo")
        val r = start(p)
        val player = r.engine.scene.find("Player")!!
        r.frames(90)
        val rb = player.getAny<Rigidbody3D>()!!
        println("SIM 3d settled y=${player.y} grounded=${rb.grounded}")
        assertTrue("3D player should land on the ground", rb.grounded)
        assertTrue("3D player should rest above ground", player.y > 0f && player.y < 2f)
        val x0 = player.x
        r.frames(60) { r.engine.input.joyX = 1f }
        r.engine.input.joyX = 0f
        println("SIM 3d moved x0=$x0 x=${player.x}")
        assertTrue("3D player should move on X", player.x > x0 + 2f)
        // jump
        var maxY = player.y
        r.frames(40) { i -> r.engine.input.rawA = i < 3; maxY = maxOf(maxY, player.y) }
        r.engine.input.rawA = false
        println("SIM 3d jump maxY=$maxY")
        assertTrue("3D player should jump", maxY > 1.5f)
        // crates should fall and rest (stack stays above ground)
        val crates = r.engine.scene.objects.filter { it.name == "Crate" }
        assertTrue("crates resting", crates.all { it.y > 0.2f && it.y < 3f })
        // pickup
        val coin = r.engine.scene.objects.first { it.tag == "Coin" }
        val before = r.engine.scene.objects.count { it.tag == "Coin" }
        r.frames(60)
        synchronized(r.engine.lock) { player.x = coin.x; player.z = coin.z; player.y = coin.y }
        r.frames(3)
        val after = r.engine.scene.objects.count { it.tag == "Coin" }
        val label = r.engine.scene.find("ScoreText")!!.getAny<TextRenderer>()!!.text
        println("SIM 3d coins $before -> $after label='$label'")
        assertEquals(before - 1, after)
        assertEquals("Coins left: $after", label)
        // raycast straight down from above the ground hits the ground
        val hit = r.engine.physics3D.raycast(r.engine.scene, 15f, 10f, 15f, 0f, -1f, 0f, 50f)
        println("SIM 3d raycast hit=${hit?.name}")
        assertEquals("Ground", hit?.name)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    /**
     * A looping clip must start on play, advance with time, sample the UV rect of
     * the cell it is on, and wrap around the sheet.  Eight frames at 10 fps over a
     * 4x2 sheet, so a full loop is 0.8s and 200 frames covers four of them.
     */
    @Test
    fun loopingClipAdvancesAndWraps() {
        val clip = AnimationClip("hero.png", 4, 2, (0..7).toMutableList(), 10f, true)
        val (p, heroName) = animProject(clip)
        val r = start(p)
        val hero = r.engine.scene.find(heroName)!!
        val anim = hero.getAny<Animator>()!!
        val sr = hero.getAny<SpriteRenderer>()!!

        assertTrue("playOnStart should start the clip", anim.playing)
        assertEquals("HeroRun.anim", anim.current)

        // frames() invokes the callback *before* it ticks, so on the very first
        // iteration the sprite's UV rect is still its identity default -- the
        // animation system has not run yet.  Warm up one frame so the sampling
        // assertions below compare against a rect AnimationSystem actually wrote.
        r.frames(1)

        val seen = LinkedHashSet<Int>()
        r.frames(200) {
            seen.add(anim.frame)
            // The UV rect must always describe the cell the current frame names.
            val want = FloatArray(4)
            clip.cellUv(clip.frames[anim.frame], want)
            assertEquals("u0 for frame ${anim.frame}", want[0], sr.uv[0], 1e-6f)
            assertEquals("u1 for frame ${anim.frame}", want[2], sr.uv[2], 1e-6f)
            assertEquals("vBottom for frame ${anim.frame}", want[1], sr.uv[1], 1e-6f)
            assertEquals("vTop for frame ${anim.frame}", want[3], sr.uv[3], 1e-6f)
        }
        println("SIM anim frames seen=$seen texture=${sr.animTexture}")
        assertEquals("a looping 8-frame clip should visit every frame", (0..7).toSet(), seen)
        assertEquals("clip texture should be applied to the sprite", "hero.png", sr.animTexture)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    /** A non-looping clip must hold its last frame and report finished. */
    @Test
    fun nonLoopingClipFinishesOnLastFrame() {
        val clip = AnimationClip("hero.png", 4, 1, (0..3).toMutableList(), 10f, false)
        val (p, heroName) = animProject(clip)
        val r = start(p)
        val anim = r.engine.scene.find(heroName)!!.getAny<Animator>()!!
        r.frames(180) // 3s: well past the 0.4s clip
        println("SIM nonloop frame=${anim.frame} finished=${anim.finished} playing=${anim.playing}")
        assertEquals("should hold the final frame", 3, anim.frame)
        assertTrue("a non-looping clip should report finished", anim.finished)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    /** An inactive object must not animate in play mode, and playOnStart=false must not autoplay. */
    @Test
    fun inactiveOrDisabledAnimatorsDoNotAdvance() {
        val clip = AnimationClip("hero.png", 4, 1, (0..3).toMutableList(), 10f, true)

        val (p1, h1) = animProject(clip, active = false)
        val r1 = start(p1)
        r1.frames(120)
        val a1 = r1.engine.scene.find(h1)!!.getAny<Animator>()!!
        println("SIM inactive frame=${a1.frame}")
        assertEquals("an inactive object must not advance its clip", 0, a1.frame)

        val (p2, h2) = animProject(clip, playOnStart = false)
        val r2 = start(p2)
        r2.frames(120)
        val a2 = r2.engine.scene.find(h2)!!.getAny<Animator>()!!
        println("SIM playOnStart=false frame=${a2.frame} playing=${a2.playing}")
        assertTrue("playOnStart=false should not autoplay", !a2.playing)
        assertEquals(0, a2.frame)
    }

    @Test
    fun blueprintDemoRunsGraphs() {
        val p = newProject("Blueprint Demo")
        val r = start(p)
        val player = r.engine.scene.find("Player")!!
        r.frames(60)
        val x0 = player.x
        r.frames(40) { r.engine.input.joyX = -1f }
        r.engine.input.joyX = 0f
        println("SIM bp player x0=$x0 x=${player.x}")
        assertTrue("blueprint Platformer node should move the player", player.x < x0 - 1.5f)
        val spinner = r.engine.scene.find("Spinner")!!
        println("SIM bp spinner rotation=${spinner.rotation}")
        assertTrue("RotatorBP should spin", spinner.rotation != 0f)
        val gems = r.engine.scene.objects.count { it.name == "Gem" }
        val gem = r.engine.scene.objects.first { it.name == "Gem" }
        synchronized(r.engine.lock) { player.x = gem.x; player.y = gem.y }
        r.frames(3)
        val after = r.engine.scene.objects.count { it.name == "Gem" }
        println("SIM bp gems $gems -> $after")
        assertEquals(gems - 1, after)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    /**
     * Every shipping template must survive a serialize/deserialize round trip.
     * Iterating [Templates.all] rather than a hard-coded list means this cannot
     * go stale when a template is added or removed -- which is exactly how the
     * previous version broke when "Animated Platformer" was retired.
     */
    @Test
    fun everyTemplateSerializesRoundTrip() {
        for (t in Templates.all) {
            val p = newProject(t.name)
            val s = p.loadScene("Main")
            val json = SceneSerializer.toJson(s).toString()
            assertEquals("template '${t.name}' round trip",
                json, SceneSerializer.toJson(SceneSerializer.fromJson(JSONObject(json))).toString())
            println("SIM template '${t.name}' objects=${s.objects.size}")
        }
    }

    @Test
    fun everyBlueprintNodeCompilesToValidJs() {
        val bp = Blueprint()
        var prev: Int? = null
        // one chain per event with every action node attached
        for ((i, def) in BlueprintNodes.all.withIndex()) {
            val n = bp.add(def.type, i * 50f, 0f)
            if (!def.hasIn) { prev = n.id; continue }
            prev?.let { bp.connect(it, "out", n.id) }
            if (def.outs.contains("out")) prev = n.id
        }
        val js = BlueprintCompiler.compile(bp)
        println("SIM bp all-nodes js length=${js.length}")
        compiles(js, "all.bp")
        // JSON round trip
        val bp2 = Blueprint.parse(bp.toJson().toString())
        assertEquals(bp.nodes.size, bp2.nodes.size)
        assertEquals(bp.links.size, bp2.links.size)
        assertEquals(js, BlueprintCompiler.compile(bp2))
        compiles(BlueprintCompiler.compile(Blueprint.defaultGraph()), "default.bp")
    }

    @Test
    fun storeScriptsAndBlueprintsAreValid() {
        for ((name, _, code) in AssetLibrary.Scripts.all) compiles(code, name)
        val dir = Files.createTempDirectory("store").toFile()
        val p = Project(File(dir, "Store")); p.saveMeta()
        for (item in AssetLibrary.items.filter { it.category == "Blueprints" || it.category == "3D Models" || it.category == "Sounds" }) {
            item.install(p)
            assertTrue("${item.title} installed", item.installed(p))
        }
        for (bpName in p.listAssets().filter { it.endsWith(".bp") }) compiles(BlueprintCompiler.compile(Blueprint.parse(p.readAsset(bpName)!!)), bpName)
        val wav = p.assetFile("coin.wav").readBytes()
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4))
        val obj = p.readAsset("Tree.obj")!!
        assertTrue(obj.lines().count { it.startsWith("v ") } > 20 && obj.lines().count { it.startsWith("f ") } > 20)
        println("SIM store items=${AssetLibrary.items.size}")
    }
}
