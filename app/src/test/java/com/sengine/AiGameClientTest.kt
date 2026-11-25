package com.sengine

import com.sengine.ai.AiGameClient
import com.sengine.engine.core.ScriptComponent
import com.sengine.project.Project
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AiGameClientTest {
    private val script = listOf(
        "function start() { state.set('score', 0); }",
        "function update(dt) {",
        "  var dx = input.axisX * 4 * dt;",
        "  self.move(dx, 0);",
        "  if (input.tapped) { self.addForce(0, 3); }",
        "}",
        "function onTrigger(other) { state.update('score', 1); audio.beep(); }"
    ).joinToString("\n")

    private val camera2d = JSONObject()
        .put("type", "Camera").put("enabled", true).put("Size", 9.0)
        .put("Background", "#FF101827").put("Priority", 10)

    private val hud = JSONObject()
        .put("type", "TextRenderer").put("enabled", true).put("Text", "Score 0")
        .put("Size", 0.5).put("Color", "#FFFFFFFF").put("Align", "Center")
        .put("Screen Space UI", true)

    private val sprite = JSONObject()
        .put("type", "SpriteRenderer").put("enabled", true).put("Shape", "Square")
        .put("Color", "#FF41D6A6").put("Texture", "")

    private val body2d = JSONObject()
        .put("type", "Rigidbody2D").put("enabled", true).put("Body Type", "Dynamic")
        .put("Mass", 1.0).put("Gravity Scale", 1.0)

    private val collider2d = JSONObject()
        .put("type", "Collider2D").put("enabled", true).put("Shape", "Box")
        .put("Width", 1.0).put("Height", 1.0).put("Is Trigger", false)

    private val scriptComponent = JSONObject()
        .put("type", "Script").put("enabled", true).put("Script", "Controller.js")
        .put("Params", "")

    private val camera3d = JSONObject()
        .put("type", "Camera3D").put("enabled", true).put("Field of View", 60.0)
        .put("Priority", 10).put("Sky Top", "#FF3B7BD4")

    private val mesh = JSONObject()
        .put("type", "MeshRenderer").put("enabled", true).put("Mesh", "Cube")
        .put("Color", "#FF41D6A6").put("Texture", "")

    private val light = JSONObject()
        .put("type", "Light").put("enabled", true).put("Type", "Directional")
        .put("Intensity", 1.0)

    private val body3d = JSONObject()
        .put("type", "Rigidbody3D").put("enabled", true).put("Body Type", "Dynamic")
        .put("Mass", 1.0)

    private val collider3d = JSONObject()
        .put("type", "Collider3D").put("enabled", true).put("Shape", "Box")
        .put("Size X", 1.0).put("Size Y", 1.0).put("Size Z", 1.0)

    private fun obj(id: Long, name: String, vararg components: JSONObject): JSONObject =
        JSONObject().put("id", id).put("name", name).put("tag", "Untagged")
            .put("active", true).put("x", 0.0).put("y", 0.0)
            .put("components", JSONArray(components.toList()))

    private fun payload(
        assets: JSONArray = JSONArray().put(JSONObject().put("name", "Controller.js").put("content", script)),
        objects: JSONArray = JSONArray()
            .put(obj(1L, "Main Camera", camera2d))
            .put(obj(2L, "Player", sprite, body2d, collider2d, scriptComponent))
            .put(obj(3L, "HUD", hud)),
        projectName: String = "AI Runner",
        startScene: String = "Main"
    ): JSONObject {
        val scene = JSONObject().put("name", "Main").put("gravityX", 0.0)
            .put("gravityY", -9.81).put("nextId", 4L).put("objects", objects)
        val projectPayload = JSONObject()
            .put("format", "s-engine-project").put("formatVersion", 1)
            .put("project", JSONObject().put("name", projectName).put("startScene", startScene).put("orientation", 0))
            .put("scenes", JSONArray().put(scene))
            .put("assets", assets)
            .put("notes", JSONArray().put("Built-in shapes only"))
        return JSONObject()
            .put("id", "test-id")
            .put("agent", JSONObject().put("qaSummary", "QA passed").put("repairs", 0))
            .put("project", projectPayload)
    }

    private fun expectInvalid(block: () -> Unit, expected: String? = null) {
        try {
            block()
            fail("Expected invalid response to be rejected")
        } catch (e: IllegalArgumentException) {
            if (expected != null) assertTrue("Expected '$expected' in '${e.message}'", e.message?.contains(expected) == true)
        }
    }

    @Test
    fun parsesValid2DAnd3DProjectsWithExactEngineFields() {
        val twoD = AiGameClient.parseGenerateResponse(payload())
        assertEquals("AI Runner", twoD.name)
        assertEquals("Main", twoD.startScene)
        assertEquals(1, twoD.scenes.size)
        assertEquals(3, twoD.scenes[0].objects.size)

        val objects3d = JSONArray()
            .put(obj(1L, "Main Camera", camera3d))
            .put(obj(2L, "Player", mesh, body3d, collider3d, light, scriptComponent))
            .put(obj(3L, "HUD", hud))
        val threeD = AiGameClient.parseGenerateResponse(payload(objects = objects3d, projectName = "3D Explorer"))
        assertEquals("3D Explorer", threeD.name)
        assertTrue(threeD.scenes[0].objects.any { go -> go.components.any { it.type == "Camera3D" } })
        assertTrue(threeD.scenes[0].objects.any { go -> go.components.any { it.type == "MeshRenderer" } })
    }

    @Test
    fun rejectsMalformedResponsesAndEngineContractViolations() {
        expectInvalid({ AiGameClient.parseGenerateResponse(JSONObject()) }, "no project payload")
        expectInvalid({ AiGameClient.parseGenerateResponse(JSONObject().put("project", JSONObject())) }, "format")
        val noScenes = payload().getJSONObject("project")
        noScenes.remove("scenes")
        expectInvalid({ AiGameClient.parseGenerateResponse(JSONObject().put("project", noScenes)) }, "scenes")
        val unknownProject = payload()
        unknownProject.getJSONObject("project").getJSONObject("project").put("unityVersion", "2022")
        expectInvalid({ AiGameClient.parseGenerateResponse(unknownProject) }, "unsupported field")
        val unknownScene = payload()
        unknownScene.getJSONObject("project").getJSONArray("scenes").getJSONObject(0).put("physicsEngine", "Box2D")
        expectInvalid({ AiGameClient.parseGenerateResponse(unknownScene) }, "unsupported field")
        val unknownObject = payload()
        unknownObject.getJSONObject("project").getJSONArray("scenes").getJSONObject(0).getJSONArray("objects").getJSONObject(1).put("prefab", "Player")
        expectInvalid({ AiGameClient.parseGenerateResponse(unknownObject) }, "unsupported field")
        val unknownAsset = payload()
        unknownAsset.getJSONObject("project").getJSONArray("assets").getJSONObject(0).put("mimeType", "text/javascript")
        expectInvalid({ AiGameClient.parseGenerateResponse(unknownAsset) }, "unsupported field")
        expectInvalid({
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray()
                .put(obj(1L, "Camera", camera2d))
                .put(obj(2L, "Bad", JSONObject().put("type", "NetworkTransform")))))
        }, "unsupported component")
        expectInvalid({
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray()
                .put(obj(1L, "Camera", camera2d))
                .put(obj(1L, "Player", scriptComponent))))
        }, "duplicated")
        expectInvalid({
            val player = obj(2L, "Player", scriptComponent).put("parent", 999L)
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray().put(obj(1L, "Camera", camera2d)).put(player)))
        }, "missing parent")
        expectInvalid({
            val badScript = JSONObject().put("type", "Script").put("Script", "Controller.js").put("script", "lowercase-alias")
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray()
                .put(obj(1L, "Camera", camera2d))
                .put(obj(2L, "Player", badScript))))
        }, "unsupported field")
        expectInvalid({
            val bad = JSONObject().put("type", "Script").put("Script", "Controller.js")
            val unsafeAssets = JSONArray().put(JSONObject().put("name", "../Controller.js").put("content", script))
            AiGameClient.parseGenerateResponse(payload(assets = unsafeAssets, objects = JSONArray()
                .put(obj(1L, "Camera", camera2d)).put(obj(2L, "Player", bad))))
        }, "Unsafe generated asset path")
        expectInvalid({
            val invented = script + "\nfunction bad() { self.teleport(1, 2); }"
            val assets = JSONArray().put(JSONObject().put("name", "Controller.js").put("content", invented))
            AiGameClient.parseGenerateResponse(payload(assets = assets))
        }, "unsupported self.")
        expectInvalid({
            val invalidJs = "function start() {"
            val assets = JSONArray().put(JSONObject().put("name", "Controller.js").put("content", invalidJs))
            AiGameClient.parseGenerateResponse(payload(assets = assets))
        }, "not valid Rhino JavaScript")
        expectInvalid({
            val badColor = JSONObject(sprite.toString()).put("Color", "not-a-color")
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray()
                .put(obj(1L, "Camera", camera2d))
                .put(obj(2L, "Player", badColor, scriptComponent))))
        }, "must be #RRGGBB")
        expectInvalid({
            val badChoice = JSONObject(body2d.toString()).put("Body Type", "Teleporting")
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray()
                .put(obj(1L, "Camera", camera2d))
                .put(obj(2L, "Player", badChoice, scriptComponent))))
        }, "must be one of")
        expectInvalid({
            val missing = JSONObject().put("type", "Script").put("Script", "Missing.js")
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray()
                .put(obj(1L, "Camera", camera2d)).put(obj(2L, "Player", missing))))
        }, "missing asset")
        expectInvalid({
            val a = obj(2L, "A", scriptComponent).put("parent", 3L)
            val b = obj(3L, "B", scriptComponent).put("parent", 2L)
            AiGameClient.parseGenerateResponse(payload(objects = JSONArray().put(obj(1L, "Camera", camera2d)).put(a).put(b)))
        }, "parent cycle")
    }

    @Test
    fun supportsValidBlueprintScriptAssetsButRejectsArbitraryCodeNodes() {
        val bp = JSONObject().put("format", "sengine-blueprint-1")
            .put("nodes", JSONArray()
                .put(JSONObject().put("id", 1).put("type", "OnStart").put("params", JSONObject()))
                .put(JSONObject().put("id", 2).put("type", "Log").put("params", JSONObject().put("message", "\"ready\""))))
            .put("links", JSONArray().put(JSONObject().put("from", 1).put("pin", "out").put("to", 2)))
        val assets = JSONArray().put(JSONObject().put("name", "Controller.bp").put("content", bp.toString()))
        val bpComponent = JSONObject().put("type", "Script").put("Script", "Controller.bp")
        val objects = JSONArray().put(obj(1L, "Camera", camera2d)).put(obj(2L, "Player", bpComponent))
        val parsed = AiGameClient.parseGenerateResponse(payload(assets = assets, objects = objects))
        assertEquals("Controller.bp", parsed.assets.single().name)

        val badBp = JSONObject().put("format", "sengine-blueprint-1")
            .put("nodes", JSONArray().put(JSONObject().put("id", 1).put("type", "Code")))
            .put("links", JSONArray())
        expectInvalid({
            AiGameClient.parseGenerateResponse(payload(
                assets = JSONArray().put(JSONObject().put("name", "Controller.bp").put("content", badBp.toString())),
                objects = objects
            ))
        }, "Code")
    }

    @Test
    fun importsAndOpensProjectUsingRealProjectAndSceneSerializers() {
        val root = Files.createTempDirectory("sengine-ai-import").toFile()
        try {
            val project = AiGameClient.importProject(root, payload())
            assertEquals("AI Runner", project.name)
            assertEquals("Main", project.startScene)
            assertEquals(0, project.orientation)
            assertTrue(project.sceneExists("Main"))
            assertEquals(script, project.readAsset("Controller.js"))
            val scene = project.loadScene("Main")
            assertEquals(3, scene.objects.size)
            val attached = scene.objects.flatMap { it.components }.filterIsInstance<ScriptComponent>()
            assertEquals(1, attached.size)
            assertEquals("Controller.js", attached.single().script)
            assertTrue(File(root, "AI Runner/project.json").isFile)
            assertTrue(File(root, "AI Runner/scenes/Main.scene.json").isFile)
            assertTrue(File(root, "AI Runner/assets/Controller.js").isFile)
            assertTrue(root.listFiles()?.none { it.name.startsWith(".ai-import") } == true)
            val reopened = Project(File(root, "AI Runner"))
            assertEquals("Main", reopened.startScene)
            assertEquals(3, reopened.loadScene("Main").objects.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedOrCancelledImportsLeaveNoPartialProject() {
        val root = Files.createTempDirectory("sengine-ai-failure").toFile()
        try {
            val unsafeAssets = JSONArray().put(JSONObject().put("name", "../Controller.js").put("content", script))
            expectInvalid({ AiGameClient.importProject(root, payload(assets = unsafeAssets)) }, "Unsafe generated asset path")
            assertEquals(0, root.listFiles()?.count { it.isDirectory && !it.name.startsWith(".") } ?: -1)
            val cancellation = AiGameClient.Cancellation()
            cancellation.cancel()
            expectInvalid({ AiGameClient.importProject(root, payload(), cancellation) }, "cancelled")
            assertTrue(root.listFiles()?.none { it.name.startsWith(".ai-import") } == true)
        } finally {
            root.deleteRecursively()
        }
    }
}
