package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.AssetValidator
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Joint2D
import com.sengine.engine.core.Light
import com.sengine.engine.core.PhysicsMaterial2D
import com.sengine.engine.core.PrefabAsset
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.Tilemap
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.UIProgressBar
import com.sengine.engine.core.UIRect
import com.sengine.engine.core.UIText
import com.sengine.engine.physics.PhysicsWorld
import com.sengine.engine.render.Renderer3D
import com.sengine.engine.render.ShaderLibrary
import com.sengine.engine.render.View3D
import com.sengine.project.AssetLibrary
import com.sengine.project.AtlasCache
import com.sengine.project.BigEditionAssets
import com.sengine.project.MaterialCache
import com.sengine.project.Project
import com.sengine.project.Templates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.max

/** Headless acceptance tests for the Phase 2 "Big Edition" expansion. */
class BigEditionTest {

    private fun project(): Project {
        val dir = Files.createTempDirectory("sengine-big").toFile()
        return Project(File(dir, "Test")).also { it.saveMeta() }
    }

    private class Run(val engine: Engine, val errors: MutableList<String>, val logs: MutableList<String>)

    private fun start(p: Project, scene: Scene): Run {
        val e = Engine(p, scene)
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = ArrayList<String>()
        val logs = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                logs.add(message)
                if (level >= 2) errors.add(message)
                println("BIG log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors, logs)
    }

    private fun Run.frame(each: (Engine) -> Unit = {}) {
        each(engine)
        synchronized(engine.lock) { engine.tick(1f / 60f) }
    }

    private fun Run.finish() {
        engine.release()
        synchronized(engine.lock) { engine.tick(0f) }
    }

    @Test
    fun bigEditionDataTemplatesAreRecognisedAndValidated() {
        val samples = mapOf(
            "Starter.prefab" to BigEditionAssets.PREFAB,
            "Teal.mat" to BigEditionAssets.MATERIAL,
            "Hero.atlas" to BigEditionAssets.ATLAS,
            "Grass.tilemap" to BigEditionAssets.TILEMAP,
            "Snapshot.scene" to BigEditionAssets.SCENE,
            "Notes.txt" to BigEditionAssets.TEXT,
        )
        for ((name, text) in samples) assertNull("$name should validate", AssetValidator.validate(name, text))
        assertEquals(AssetKind.MATERIAL, AssetKind.of("shiny.mat"))
        assertEquals(AssetKind.FONT, AssetKind.of("MyFont.ttf"))
        assertEquals(AssetKind.TILEMAP, AssetKind.ofName("tilemap"))
        assertEquals("prefab", AssetKind.PREFAB.defaultExtension)
        assertTrue("prefabs are not binary", !AssetKind.PREFAB.binary)
        assertTrue("fonts are binary", AssetKind.FONT.binary)
        assertNotNull("broken atlas rejected", AssetValidator.validate("Broken.atlas", "{\"frames\":{}}"))
        assertNotNull("empty tilemap rejected", AssetValidator.validate("Empty.tilemap", "{\"map\":\"-1\"}"))
        assertNotNull("broken prefab rejected", AssetValidator.validate("Broken.prefab", "{\"objects\":[]}"))
    }

    @Test
    fun materialAndAtlasCachesReloadAfterExternalEdits() {
        val p = project()
        p.writeAsset("Live.mat", BigEditionAssets.MATERIAL)
        p.writeAsset("Hero.atlas", BigEditionAssets.ATLAS)
        val materials = MaterialCache(p)
        val atlases = AtlasCache(p)
        assertEquals(0.65f, materials.get("Live.mat")!!.specular, 0.001f)
        assertEquals(listOf("fall", "idle", "jump", "step"), atlases.frameNames("Hero.atlas"))
        val out = FloatArray(4)
        assertTrue("named frame uv", atlases.uv("Hero.atlas", "step", out))
        assertEquals(0.25f, out[0], 0.001f)
        assertEquals(0.5f, out[2], 0.001f)

        val updated = JSONObject(BigEditionAssets.MATERIAL).put("specular", 1.5).toString(2)
        p.writeAsset("Live.mat", updated)
        p.assetFile("Live.mat").setLastModified(System.currentTimeMillis() + 5000L)
        assertEquals(1.5f, materials.get("Live.mat")!!.specular, 0.001f)
    }

    @Test
    fun prefabCaptureKeepsHierarchyAndInstantiatesAtAnOffset() {
        val p = project()
        val source = Scene("PrefabSource")
        val root = source.create("Enemy")
        root.x = 2f; root.y = -1f
        root.add(SpriteRenderer().also { it.material = "Shiny.mat"; it.texture = "Hero.png" })
        val child = source.create("Hat", root)
        child.x = 1f; child.y = 2f
        child.add(UIText().also { it.text = "Hat Label" })
        val json = PrefabAsset.capture(source, root).toString(2)
        assertNull("captured prefab should validate", AssetValidator.validate("Enemy.prefab", json))
        p.writeAsset("Enemy.prefab", json)

        val target = Scene("Main")
        val copy = PrefabAsset.instantiate(target, json, 10f, 5f)
        assertNotNull("prefab instantiated", copy)
        assertEquals("Enemy", copy!!.name)
        assertEquals(12f, copy.x, 0.001f)
        assertEquals(4f, copy.y, 0.001f)
        assertEquals(1, target.childrenOf(copy).size)
        assertEquals("Hat", target.childrenOf(copy)[0].name)
        assertEquals("Shiny.mat", copy.get<SpriteRenderer>()!!.material)
        assertEquals("Hat Label", target.childrenOf(copy)[0].get<UIText>()!!.text)
    }

    @Test
    fun tilemapSolidsCollideAndAnswerQueries() {
        val scene = Scene("Main")
        scene.gravityY = -9.81f
        val tiles = scene.create("Ground")
        tiles.add(Tilemap().also {
            it.map = "0,0,0"; it.tileSize = 1f; it.collide = true
        })
        val world = PhysicsWorld()
        scene.updateTransforms()
        assertEquals("downward ray hits tilemap", tiles, world.raycast(scene, 0.5f, 3f, 0f, -1f, 10f))
        assertNotNull("tile centre is solid", world.overlapPoint(scene, 0.5f, 0.5f))
        assertNull("outside map is empty", world.overlapPoint(scene, 8f, 3f))

        val ball = scene.create("Ball")
        ball.x = 0.5f; ball.y = 2f
        ball.add(Collider2D().also { it.shape = 1; it.radius = 0.4f })
        val rb = Rigidbody2D().also { it.gravityScale = 1f; it.friction = 0.2f }
        ball.add(rb)
        repeat(180) {
            world.step(scene, 1f / 60f)
            scene.updateTransforms()
        }
        println("BIG tilemap ball y=${ball.y} grounded=${rb.grounded}")
        assertTrue("ball rests on tile top", ball.y > 1.2f && ball.y < 1.8f)
        assertTrue("dynamic body is grounded", rb.grounded)
    }

    @Test
    fun convexPolygonCollidersUseHullsForRayAndPointQueries() {
        val scene = Scene("Polygons")
        val triangle = scene.create("Triangle")
        triangle.add(Collider2D().also {
            it.shape = 2
            it.vertices = "-1,-1; 1,-1; 0,1"
        })
        scene.updateTransforms()
        val world = PhysicsWorld()
        assertEquals("polygon centre", triangle, world.overlapPoint(scene, 0f, 0f))
        assertNull("point outside polygon", world.overlapPoint(scene, 0f, 1.4f))
        assertEquals("ray hits triangle", triangle, world.raycast(scene, 0f, 3f, 0f, -1f, 10f))

        val hull = Collider2D.polygonPoints("0,0; 1,0; 0,1; 0.2,0.2")
        assertTrue("hull keeps convex corners", hull.size >= 3 && hull.size <= 4)
        var area = 0f
        for (i in hull.indices) {
            val a = hull[i]; val b = hull[(i + 1) % hull.size]
            area += a[0] * b[1] - b[0] * a[1]
        }
        assertTrue("hull is CCW", area > 0f)
    }

    @Test
    fun physicsMaterialsBounceAndDistanceJointsConstrainBodies() {
        val bounce = Scene("Bounce")
        bounce.gravityY = -9.81f
        val floor = bounce.create("Floor")
        floor.y = -2f
        floor.add(Collider2D().also { it.width = 10f; it.height = 1f })
        val ball = bounce.create("Ball")
        ball.add(Collider2D().also { it.shape = 1; it.radius = 0.5f })
        val rb = Rigidbody2D()
        ball.add(rb)
        ball.add(PhysicsMaterial2D().also { it.bounciness = 0.9f; it.friction = 0f; it.density = 1f })
        val physics = PhysicsWorld()
        var peakVy = Float.NEGATIVE_INFINITY
        repeat(60) {
            physics.step(bounce, 1f / 60f)
            bounce.updateTransforms()
            peakVy = max(peakVy, rb.vy)
        }
        println("BIG material bounce peak=$peakVy ballY=${ball.y}")
        assertTrue("material restitution produces upward velocity", peakVy > 1f)

        val joints = Scene("Joints")
        joints.gravityY = 0f
        val a = joints.create("A")
        a.add(Rigidbody2D())
        a.add(Joint2D().also { it.target = "B"; it.length = 2f; it.mode = 0 })
        val b = joints.create("B")
        b.x = 5f
        b.add(Rigidbody2D())
        val jp = PhysicsWorld()
        repeat(20) {
            jp.step(joints, 1f / 60f)
            joints.updateTransforms()
        }
        val distance = abs(b.x - a.x)
        println("BIG joint distance=$distance broken=${a.get<Joint2D>()!!.broken}")
        assertTrue("distance joint pulls bodies together", distance < 2.2f)
        assertFalse("joint stays enabled", a.get<Joint2D>()!!.broken)
    }

    @Test
    fun canvasUiTapInvokesTheScriptAndUpdatesProgress() {
        val p = project()
        p.writeAsset("UI.js", """function onClick(label) {
    log("ui:" + label);
    self.uiProgress = 1;
}""")
        val scene = Scene("Main")
        val button = scene.create("Button")
        button.add(UIRect())
        button.add(UIButton().also { it.label = "GO" })
        button.add(UIProgressBar().also { it.value = 0f })
        button.add(ScriptComponent().also { it.script = "UI.js" })

        val run = start(p, scene)
        run.frame() // process the queued play command and attach scripts
        run.frame {
            it.input.tapPending = true
            it.input.rawTouching = true
            it.input.rawTouchSX = 800f
            it.input.rawTouchSY = 450f
        }
        assertEquals(1f, button.get<UIProgressBar>()!!.value, 0.001f)
        assertTrue("button is marked clicked", button.get<UIButton>()!!.clicked)
        assertTrue("callback logged", run.logs.any { it.contains("ui:GO") })
        assertTrue("no runtime errors: ${run.errors}", run.errors.isEmpty())
        run.finish()
    }

    @Test
    fun assetsBridgeAndPrefabSpawnWorkFromJavaScriptWithoutEscapingTheProject() {
        val p = project()
        p.writeAsset("Notes.txt", "hello big edition")
        p.writeAsset("Test.prefab", BigEditionAssets.PREFAB)
        p.writeAsset("Bridge.js", """function start() {
    assets.writeText("Reply.txt", "ok");
    log("asset:" + assets.readText("Notes.txt"));
    log("prefabs:" + assets.list("PREFAB").length);
    log("binary:" + assets.writeText("bad.png", "blocked"));
    var o = scene.spawnPrefab("Test.prefab", 3, 2);
    log("prefab:" + (o ? o.name : "none"));
}""")
        val scene = Scene("Main")
        scene.create("Runner").add(ScriptComponent().also { it.script = "Bridge.js" })
        val run = start(p, scene)
        run.frame()
        assertEquals("ok", p.readAsset("Reply.txt"))
        assertNull("binary assets cannot be written by scripts", p.readAsset("bad.png"))
        assertTrue("text asset read", run.logs.any { it.contains("asset:hello big edition") })
        assertTrue("prefab asset listed", run.logs.any { it.contains("prefabs:1") })
        assertTrue("binary write denied", run.logs.any { it.contains("binary:false") })
        assertTrue("prefab spawned", run.logs.any { it.contains("prefab:StarterPrefab") })
        assertTrue("no runtime errors: ${run.errors}", run.errors.isEmpty())
        run.finish()
    }

    @Test
    fun orthographicProjectionAndSpotLightShaderAreActuallyWired() {
        val v = View3D()
        v.widthPx = 1600; v.heightPx = 900
        v.projection = 0
        v.update()
        assertEquals(0f, v.proj[15], 0.0001f) // perspective divides by -z
        v.projection = 1
        v.orthoSize = 6f
        v.update()
        assertEquals(1f, v.proj[15], 0.0001f) // orthographic has w = 1
        assertEquals(1f / (6f * 1600f / 900f), v.proj[0], 0.0001f)

        val fs = ShaderLibrary.meshFs("")
        assertTrue("mesh shader declares spot arrays", fs.contains("uniform vec4 uSpotPos[2];"))
        assertTrue("mesh shader loops spot lights", fs.contains("for (int j = 0; j < 2; j++)"))
        assertTrue("mesh shader uses cone falloff", fs.contains("smoothstep(uSpotParams[j].y"))

        val lights = Scene("Lights")
        val point = lights.create("Point")
        point.z = 2f
        point.add(Light().also { it.kind = 1 })
        repeat(3) { i ->
            val spot = lights.create("Spot$i")
            spot.z = 1f + i * 4f
            spot.add(Light().also { it.kind = 2; it.coneAngle = 35f; it.range = 20f })
        }
        lights.updateTransforms()
        v.eye[0] = 0f; v.eye[1] = 0f; v.eye[2] = 20f
        val renderer = Renderer3D()
        renderer.setupLights(lights, v)
        assertEquals(1, renderer.activePoints)
        assertEquals(2, renderer.activeSpots)
    }

    @Test
    fun bigEditionShowcaseTemplateRoundTripsInstallsDataAssetsAndRuns() {
        val p = project()
        val template = Templates.all.first { it.name == "Big Edition Showcase" }
        template.build(p)
        p.saveMeta()
        val scene = p.loadScene("Main")
        val json = SceneSerializer.toJson(scene).toString()
        assertEquals("showcase serializes exactly", json,
            SceneSerializer.toJson(SceneSerializer.fromJson(JSONObject(json))).toString())
        for (name in p.listAssets()) {
            if (!AssetValidator.supports(name)) continue
            val text = p.readAsset(name)!!
            assertNull("$name must be valid", AssetValidator.validate(name, text))
        }
        assertTrue("store has data category", AssetLibrary.categories.contains("Data Assets"))
        assertTrue("store has data items", AssetLibrary.items.any { it.category == "Data Assets" })

        val run = start(p, scene)
        repeat(6) { run.frame() }
        run.frame {
            it.input.tapPending = true
            it.input.rawTouching = true
            it.input.rawTouchSX = 800f
            it.input.rawTouchSY = (1f - 0.42f) * 900f
        }
        assertTrue("button callback ran", run.logs.any { it.contains("UI: SPAWN") })
        assertTrue("prefab spawned from UI", run.logs.any { it.contains("spawned Polygon Hero") })
        assertTrue("no showcase runtime errors: ${run.errors}", run.errors.isEmpty())
        println("BIG showcase objects=${scene.objects.size} assets=${p.listAssets().size}")
        run.finish()
    }
}
