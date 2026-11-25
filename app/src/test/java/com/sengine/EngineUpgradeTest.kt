package com.sengine

import com.sengine.engine.Audio3DMath
import com.sengine.engine.Engine
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Terrain
import com.sengine.engine.render.Meshes
import com.sengine.project.Project
import com.sengine.project.Templates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Headless tests for the professional upgrade: pooling, steering, raycasts, terrain and the new templates. */
class EngineUpgradeTest {

    private fun newProject(name: String): Project {
        val dir = Files.createTempDirectory("sengine3").toFile()
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
                println("SIM up log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors)
    }

    private fun Run.frames(n: Int, each: (Int) -> Unit = {}) {
        repeat(n) { i -> each(i); synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    // ---------------------------------------------------------------- templates
    @Test
    fun newTemplatesSerializeAndRunClean() {
        for (name in listOf("Flappy Bird", "Chase", "3D Open World")) {
            val p = newProject(name)
            val s = p.loadScene("Main")
            val json = SceneSerializer.toJson(s).toString()
            assertEquals(json, SceneSerializer.toJson(SceneSerializer.fromJson(JSONObject(json))).toString())
            val r = start(p)
            r.frames(90)
            println("SIM template '$name' objects=${s.objects.size} errors=${r.errors.size}")
            assertTrue("script errors in $name: ${r.errors}", r.errors.isEmpty())
        }
    }

    @Test
    fun flappyBirdFlapsAndScores() {
        val p = newProject("Flappy Bird")
        val r = start(p)
        val bird = r.engine.scene.find("Bird")!!
        // start the bird (one flap) so the hover handler stops pinning its y, then score in the gap
        r.frames(10)
        r.frames(1) { r.engine.input.rawA = true }
        r.engine.input.rawA = false
        val zone = r.engine.scene.objects.first { it.name == "ScoreZone" }
        val zw = zone.computeWorld()
        synchronized(r.engine.lock) { bird.x = zw.tx; bird.y = zw.ty }
        r.frames(3)
        val label = r.engine.scene.find("ScoreText")!!.getAny<TextRenderer>()!!.text
        println("SIM flappy label='$label' bird=(${bird.x},${bird.y})")
        assertTrue("score should increase", label.contains("Score: 1"))
        // now flap: the bird stays alive and the pipes keep scrolling left
        val pair = r.engine.scene.objects.first { it.name == "PipePair" }
        val x0 = pair.x
        r.frames(150) { i -> r.engine.input.rawA = i % 25 < 2 }
        r.engine.input.rawA = false
        println("SIM flappy pair x0=$x0 x=${pair.x} birdY=${bird.y}")
        assertTrue("pipes should scroll left", pair.x < x0 - 4f)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun chaseHuntersSeekAndPickupsScore() {
        val p = newProject("Chase")
        val r = start(p)
        val hero = r.engine.scene.find("Hero")!!
        val hunter = r.engine.scene.objects.first { it.name == "Hunter" }
        val d0 = Math.hypot((hunter.x - hero.x).toDouble(), (hunter.y - hero.y).toDouble())
        r.frames(150)
        val d1 = Math.hypot((hunter.x - hero.x).toDouble(), (hunter.y - hero.y).toDouble())
        println("SIM chase dist $d0 -> $d1 hunter=(${hunter.x},${hunter.y})")
        assertTrue("hunter should seek the hero", d1 < d0 - 3.0)
        // collect a pickup
        val pickup = r.engine.scene.objects.first { it.name == "Pickup" }
        synchronized(r.engine.lock) { hero.x = pickup.x; hero.y = pickup.y }
        r.frames(3)
        val label = r.engine.scene.find("ScoreText")!!.getAny<TextRenderer>()!!.text
        println("SIM chase label='$label'")
        assertTrue("pickup should add score", label.contains("Score: 1"))
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun openWorldHeroWalksOnTerrain() {
        val p = newProject("3D Open World")
        val r = start(p)
        val hero = r.engine.scene.find("Hero")!!
        r.frames(90)
        val rb = hero.getAny<Rigidbody3D>()!!
        println("SIM ow y=${hero.y} grounded=${rb.grounded}")
        assertTrue("hero should land on the terrain", rb.grounded)
        val x0 = hero.x
        r.frames(60) { r.engine.input.joyX = 1f }
        r.engine.input.joyX = 0f
        println("SIM ow x0=$x0 x=${hero.x}")
        assertTrue("hero should walk on X", hero.x > x0 + 2f)
        // collect a crystal
        val crystal = r.engine.scene.objects.first { it.tag == "Crystal" }
        synchronized(r.engine.lock) { hero.x = crystal.x; hero.z = crystal.z; hero.y = crystal.y }
        r.frames(3)
        val hud = r.engine.scene.find("Hud")!!.getAny<TextRenderer>()!!.text
        println("SIM ow hud='$hud'")
        assertTrue("crystal should be collected", hud.contains("1"))
        // downward ray away from the hero hits the terrain
        val hit = r.engine.physics3D.raycast(r.engine.scene, 40f, 200f, 40f, 0f, -1f, 0f, 400f)
        println("SIM ow raycast hit=${hit?.name}")
        assertEquals("Terrain", hit?.name)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    // ---------------------------------------------------------------- engine systems
    @Test
    fun poolDefineObtainReleaseReusesInstances() {
        val p = newProject("Chase")
        val r = start(p)
        r.frames(5)
        synchronized(r.engine.lock) {
            val before = r.engine.scene.objects.size
            r.engine.poolDefine("Pickup", 3)
            assertTrue("pool pre-warms clones", r.engine.scene.objects.size >= before + 3)
            val a = r.engine.poolObtain("Pickup", 5f, 5f, null)!!
            assertTrue("obtained instance is active", a.active)
            assertEquals(5f, a.x, 0.001f)
            assertTrue("release deactivates", r.engine.poolRelease(a))
            assertTrue("released instance is inactive", !a.active)
            val b = r.engine.poolObtain("Pickup", 7f, 7f, null)!!
            assertEquals("second obtain reuses the released instance", a.id, b.id)
            assertTrue(b.active)
            assertEquals(7f, b.x, 0.001f)
            r.engine.poolRelease(b)
            println("SIM pool before=$before after=${r.engine.scene.objects.size}")
        }
        r.frames(10)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun raycast2DQueryWorks() {
        val p = newProject("Chase")
        val r = start(p)
        r.frames(5)
        synchronized(r.engine.lock) {
            // from beside the hero straight into the crate at (-10, -10)
            val hit = r.engine.physics.raycast(r.engine.scene, -14f, -10f, 1f, 0f, 60f)
            println("SIM ray2d hit=${hit?.name}")
            assertNotNull("ray should hit the crate", hit)
            assertTrue("ray should hit a crate, got ${hit!!.name}", hit.name.startsWith("Crate"))
            val miss = r.engine.physics.raycast(r.engine.scene, -100f, -100f, 1f, 0f, 40f)
            println("SIM ray2d miss=${miss?.name}")
            assertTrue("ray into empty space misses", miss == null)
        }
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun terrainHeightQueryMatchesHeightField() {
        val p = newProject("3D Open World")
        val r = start(p)
        r.frames(5)
        synchronized(r.engine.lock) {
            val terrain = r.engine.scene.objects.first { it.get<Terrain>() != null }.get<Terrain>()!!
            val h = terrain.heightAtWorld(12f, -7f)
            val h2 = terrain.heightAtWorld(12f, -7f)
            println("SIM terrain h=$h h2=$h2")
            assertNotNull(h)
            assertEquals(h, h2)
            assertTrue("height within amplitude", Math.abs(h!!) <= terrain.amplitude + 1f)
        }
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    @Test
    fun terrainMeshIsDeterministicAndBounded() {
        val t = Terrain().also {
            it.size = 100f; it.resolution = 32; it.amplitude = 12f
            it.frequency = 0.02f; it.octaves = 4; it.seed = 5
        }
        val a = Meshes.terrain(t)
        val b = Meshes.terrain(t)
        assertEquals(a.vertexCount, b.vertexCount)
        assertTrue("buffer populated", a.buffer.capacity() > 0)
        for (i in 0 until a.buffer.capacity()) {
            assertEquals("vertex $i deterministic", a.buffer.get(i), b.buffer.get(i), 0f)
        }
        assertTrue("bounded XZ", a.min[0] >= -50.01f && a.max[0] <= 50.01f && a.min[2] >= -50.01f && a.max[2] <= 50.01f)
        assertTrue("bounded Y", a.min[1] >= -12.01f && a.max[1] <= 12.01f)
        assertTrue("has triangles", a.vertexCount >= 3)
        println("SIM terrainMesh verts=${a.vertexCount} idx=${a.vertexCount * 3} y=[${a.min[1]},${a.max[1]}]")
    }

    @Test
    fun upgradeScriptApiRunsClean() {
        val p = newProject("Empty 2D")
        p.writeAsset("ApiTest.js", """
function start() {
    scene.pool("Dummy", 2);
    var a = scene.obtain("Dummy", 5, 5);
    log("obtained " + (a ? a.name : "null"));
    if (a) scene.release(a);
    var hit = scene.raycast2D(0, 0, 1, 0, 50);
    log("ray2d " + (hit ? hit.name : "miss"));
    var h = scene.terrainHeight(3, 3);
    log("terrainHeight " + h);
    scene.setFog(10, 50);
    scene.setFogColor("#88ccff");
    var moved = moveToward(0, 0, 10, 0, 5);
    log("moveToward " + moved);
    var all = scene.raycastAll2D(0, 0, 1, 0, 100);
    var oc = scene.overlapCircle(0, 0, 5);
    var ocTag = scene.overlapCircle(0, 0, 5, "Nope");
    var os = scene.overlapSphere(0, 0, 0, 5);
    var all3 = scene.raycastAll(0, 10, 0, 0, -1, 0, 50);
    log("queries " + all.length + "/" + oc.length + "/" + ocTag.length + "/" + os.length + "/" + all3.length);
}
function update(dt) { }
""".trimIndent())
        val s = p.loadScene("Main")
        val dummy = s.create("Dummy")
        dummy.x = 100f
        val tester = s.create("ApiTester")
        tester.add(com.sengine.engine.core.ScriptComponent().also { it.script = "ApiTest.js" })
        p.saveScene(s)
        val r = start(p)
        r.frames(30)
        val dummies = r.engine.scene.objects.count { it.name.startsWith("Dummy") }
        println("SIM api dummies=$dummies fog=${r.engine.scene.fog} errors=${r.errors.size}")
        assertTrue("pool should pre-warm Dummy clones", dummies >= 3)
        assertTrue("fog should be enabled by script", r.engine.scene.fog)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    // ---------------------------------------------------------------- script system
    @Test
    fun scriptLifecycleTweenAndModules() {
        val p = newProject("Empty 2D")
        p.writeAsset("MathUtil.js", """
module.exports = {
    double: function (v) { return v * 2; },
    version: "1.0"
};
""".trimIndent())
        p.writeAsset("Life.js", """
var fixed = 0, late = 0, enables = 0, disables = 0;
function start() { refresh(); }
function onFixedUpdate(dt) { fixed++; }
function onLateUpdate(dt) { late++; refresh(); }
function onEnable() { enables++; refresh(); }
function onDisable() { disables++; refresh(); }
function refresh() {
    var s = scene.find("StateA");
    if (s) s.text = "f=" + fixed + " l=" + late + " e=" + enables + " d=" + disables;
}
""".trimIndent())
        p.writeAsset("TweenDemo.js", """
var u = require("MathUtil.js");
var tweened = false;
function start() {
    var s = scene.find("StateB");
    if (s) s.text = "doubled=" + u.double(21) + " ver=" + u.version;
    tween(self, "x", 8, 0.35, "quadOut");
}
function update(dt) {
    if (!tweened && self.x >= 7.5) {
        tweened = true;
        var s = scene.find("StateB");
        if (s) s.text = s.text + " tweened";
    }
}
""".trimIndent())
        val s = p.loadScene("Main")
        val stateA = s.create("StateA")
        stateA.add(com.sengine.engine.core.TextRenderer().also { it.text = "init" })
        val stateB = s.create("StateB")
        stateB.add(com.sengine.engine.core.TextRenderer().also { it.text = "init" })
        val life = s.create("LifeObj")
        life.add(com.sengine.engine.core.ScriptComponent().also { it.script = "Life.js" })
        val tw = s.create("TweenObj")
        tw.x = 0f
        tw.add(com.sengine.engine.core.ScriptComponent().also { it.script = "TweenDemo.js" })
        p.saveScene(s)
        val r = start(p)
        r.frames(80)
        val a = r.engine.scene.find("StateA")!!.getAny<TextRenderer>()!!.text
        val b = r.engine.scene.find("StateB")!!.getAny<TextRenderer>()!!.text
        val twX = r.engine.scene.find("TweenObj")!!.x
        println("SIM life A='$a' B='$b' tweenX=$twX")
        assertTrue("require() module loaded", b.contains("doubled=42"))
        assertTrue("tween completed", b.contains("tweened") && twX >= 7.5f)
        val fixed = a.substringAfter("f=").substringBefore(" ").toInt()
        val late = a.substringAfter(" l=").substringBefore(" ").toInt()
        assertTrue("onFixedUpdate ran ($fixed)", fixed >= 55)
        assertTrue("onLateUpdate ran ($late)", late >= 70)
        assertTrue("initial onEnable fired", a.contains("e=1"))
        // disable / re-enable transitions
        val lifeGo = r.engine.scene.find("LifeObj")!!
        lifeGo.active = false
        r.frames(3)
        val a2 = r.engine.scene.find("StateA")!!.getAny<TextRenderer>()!!.text
        println("SIM life disabled A='$a2'")
        assertTrue("onDisable fired", a2.contains("d=1"))
        lifeGo.active = true
        r.frames(3)
        val a3 = r.engine.scene.find("StateA")!!.getAny<TextRenderer>()!!.text
        println("SIM life re-enabled A='$a3'")
        assertTrue("onEnable re-fired", a3.contains("e=2"))
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    // ---------------------------------------------------------------- physics engine
    @Test
    fun physicsLayersCcdQueriesAndCarry() {
        val p = newProject("Empty 2D")
        val s = p.loadScene("Main")
        fun solid(name: String, x: Float, y: Float, w: Float, h: Float): com.sengine.engine.core.GameObject {
            val go = s.create(name)
            go.x = x; go.y = y
            go.tag = name
            go.add(com.sengine.engine.core.Collider2D().also { it.width = w; it.height = h })
            return go
        }
        fun bullet(name: String, y: Float, ccd: Boolean): com.sengine.engine.core.GameObject {
            val go = s.create(name)
            go.x = 0f; go.y = y
            go.add(com.sengine.engine.core.Collider2D().also { it.width = 1f; it.height = 1f })
            go.add(com.sengine.engine.core.Rigidbody2D().also { it.gravityScale = 0f; it.ccd = ccd })
            return go
        }
        solid("Wall1", 8f, 4f, 0.3f, 4f)
        solid("Wall2", 8f, 10f, 0.3f, 4f)
        val bulletCcd = bullet("BulletCcd", 4f, ccd = true)
        val bulletFast = bullet("BulletFast", 10f, ccd = false)
        // layer test: ghost mask=0 passes everything; solid default stops at its wall
        solid("WallPass", 6f, -1f, 0.3f, 2f)
        solid("WallStop", 6f, -4f, 0.3f, 2f)
        val ghost = bullet("Ghost", -1f, ccd = false)
        ghost.get<com.sengine.engine.core.Collider2D>()!!.mask = 0
        val solidB = bullet("SolidB", -4f, ccd = false)
        // kinematic platform + resting rider
        val platform = s.create("Platform")
        platform.x = -10f; platform.y = 20f
        platform.add(com.sengine.engine.core.Collider2D().also { it.width = 4f; it.height = 0.5f })
        platform.add(com.sengine.engine.core.Rigidbody2D().also { it.bodyType = 1 })
        val rider = s.create("Rider")
        rider.x = -10f; rider.y = 20.45f
        rider.add(com.sengine.engine.core.Collider2D().also { it.width = 1f; it.height = 1f })
        rider.add(com.sengine.engine.core.Rigidbody2D().also { it.gravityScale = 0f })
        p.saveScene(s)

        val r = start(p)
        r.frames(1) // drains the queued play command (beginScene resets runtime state)
        // the engine loads its own copy of the scene: re-resolve live objects
        val sc = r.engine.scene
        val liveCcd = sc.find("BulletCcd")!!
        val liveFast = sc.find("BulletFast")!!
        val liveGhost = sc.find("Ghost")!!
        val liveSolid = sc.find("SolidB")!!
        val livePlat = sc.find("Platform")!!
        val liveRider = sc.find("Rider")!!
        synchronized(r.engine.lock) {
            liveCcd.get<com.sengine.engine.core.Rigidbody2D>()!!.vx = 60f
            liveFast.get<com.sengine.engine.core.Rigidbody2D>()!!.vx = 60f
            liveGhost.get<com.sengine.engine.core.Rigidbody2D>()!!.vx = 25f
            liveSolid.get<com.sengine.engine.core.Rigidbody2D>()!!.vx = 25f
            livePlat.get<com.sengine.engine.core.Rigidbody2D>()!!.vx = 3f
        }
        r.frames(90)
        val ccdX = liveCcd.x
        val fastX = liveFast.x
        val ghostX = liveGhost.x
        val solidX = liveSolid.x
        val platX = livePlat.x
        val riderX = liveRider.x
        println("SIM phys ccd=$ccdX fast=$fastX ghost=$ghostX solid=$solidX plat=$platX rider=$riderX")
        assertTrue("CCD stops before the wall ($ccdX)", ccdX < 7.9f)
        assertTrue("non-CCD bullet gets through ($fastX)", fastX > 8.3f)
        assertTrue("mask=0 ghost passes walls ($ghostX)", ghostX > 10f)
        assertTrue("default mask stops at wall ($solidX)", solidX < 6.4f)
        assertTrue("platform moved ($platX)", platX > -8.5f)
        assertTrue("rider carried by platform ($riderX)", riderX > -8.5f)
        // queries
        synchronized(r.engine.lock) {
            val hits = r.engine.physics.raycastAll(r.engine.scene, -10f, 4f, 1f, 0f, 50f)
            println("SIM rayAll=" + hits.map { "${it.first}:${it.second.name}" })
            assertTrue("raycastAll returns sorted hits", hits.size >= 2 && hits[0].first < hits[1].first)
            assertTrue("nearest hit is the CCD bullet", hits[0].second.name == "BulletCcd")
            val overlap = r.engine.physics.overlapCircle(r.engine.scene, 8f, 4f, 1f)
            assertTrue("overlapCircle finds the wall", overlap.any { it.name == "Wall1" })
            val overlapTag = r.engine.physics.overlapCircle(r.engine.scene, 8f, 4f, 5f, "Wall1")
            assertTrue("tag filter works", overlapTag.size == 1 && overlapTag[0].name == "Wall1")
            val none = r.engine.physics.overlapCircle(r.engine.scene, 100f, 100f, 1f)
            assertTrue("empty overlap", none.isEmpty())
        }
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }

    // ---------------------------------------------------------------- 3d audio
    @Test
    fun audio3dPanningMathAndApiSmoke() {
        // listener at origin, right axis = +X
        fun g(sx: Float, sy: Float, sz: Float, minD: Float = 3f, maxD: Float = 70f) =
            Audio3DMath.gains(0f, 0f, 0f, 1f, 0f, 0f, sx, sy, sz, minD, maxD)
        val rightSide = g(10f, 0f, 0f)
        assertTrue("right side pans right", rightSide[1] > rightSide[0] && rightSide[0] >= 0f)
        val leftSide = g(-10f, 0f, 0f)
        assertTrue("left side pans left", leftSide[0] > leftSide[1])
        val front = g(0f, 0f, -2f)
        assertEquals("near sound is full volume", 1f, front[0], 0.0001f)
        assertEquals("near sound is full volume", 1f, front[1], 0.0001f)
        val far = g(0f, 0f, -100f)
        assertEquals("beyond maxDist is silent", 0f, far[0], 0.0001f)
        assertEquals("beyond maxDist is silent", 0f, far[1], 0.0001f)
        val mid = g(0f, 0f, -36.5f)
        assertEquals("mid distance attenuates linearly", 0.5f, mid[0], 0.01f)
        assertEquals("centered mid is stereo-symmetric", mid[0], mid[1], 0.0001f)
        println("SIM audio right=${rightSide.toList()} left=${leftSide.toList()} mid=${mid.toList()}")

        // API smoke: no audio assets in this project; every call must be a safe no-op
        val p = newProject("Empty 2D")
        p.writeAsset("AudioDemo.js", """
function start() {
    var h = audio.play3d("missing.wav", 1, 2, 3);
    audio.play3dLoop("missing.wav", 4, 5, 6);
    audio.setFalloff(2, 50);
    audio.setSfxVolume(0.8);
    audio.setMusicVolume(0.5);
    audio.playMusic("missing.wav");
    audio.pauseMusic();
    audio.resumeMusic();
    audio.stopMusic();
    scene.broadcast("ping", 7);
}
function ping(n) { log("pinged " + n); }
""".trimIndent())
        val sc = p.loadScene("Main")
        val tester = sc.create("AudioTester")
        tester.add(com.sengine.engine.core.ScriptComponent().also { it.script = "AudioDemo.js" })
        p.saveScene(sc)
        val r = start(p)
        r.frames(30)
        r.engine.audio.setListener(1f, 2f, 3f, 1f, 0f, 0f)
        r.engine.audio.update3d()
        r.engine.audio.move3d(999, 1f, 2f, 3f)
        val handle = r.engine.audio.play3d("missing.wav", 0f, 0f, 0f)
        assertEquals("no sound asset -> handle 0", 0, handle)
        println("SIM audio smoke ok sfx=${r.engine.audio.sfxVolume} music=${r.engine.audio.musicVolume}")
        assertTrue("sfx bus volume applied", r.engine.audio.sfxVolume in 0.79f..0.81f)
        assertTrue("script errors: ${r.errors}", r.errors.isEmpty())
    }
}
