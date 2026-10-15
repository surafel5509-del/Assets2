package com.sengine

import com.sengine.engine.core.*
import com.sengine.project.Project
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SceneSafetyTest {
    @Test fun unknownComponentsRoundTripWithoutLosingPluginData() {
        val scene = Scene("Main")
        val obj = scene.create("Plugin Object")
        val raw = JSONObject().put("type", "Plugin.Custom").put("enabled", false).put("nested", JSONObject().put("value", 42))
        obj.add(MissingComponent(raw))
        val loaded = SceneSerializer.fromJson(SceneSerializer.toJson(scene))
        val saved = loaded.objects.single().components.single().toJson()
        assertEquals(42, saved.getJSONObject("nested").getInt("value"))
        assertFalse(saved.getBoolean("enabled"))
        assertTrue(SceneDiagnostics.inspect(loaded).any { it.message.contains("Unavailable component") })
    }
    @Test fun legacyScenesMigrateToVersionedFormat() {
        val old = JSONObject().put("objects", JSONArray().put(JSONObject().put("id", 1).put("name", "Old object")))
        val scene = SceneSerializer.fromJson(old)
        assertEquals("Old object", scene.objects.single().name)
        assertEquals(SceneSerializer.SCHEMA_VERSION, SceneSerializer.toJson(scene).getInt("schemaVersion"))
    }
    @Test(expected = IllegalArgumentException::class) fun futureSchemaIsRejected() {
        SceneSerializer.fromJson(JSONObject().put("schemaVersion", 999))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsAreRejected() {
        SceneSerializer.fromJson(JSONObject().put("objects", JSONArray().put(JSONObject().put("id", 1)).put(JSONObject().put("id", 1))))
    }
    @Test(expected = IllegalArgumentException::class) fun cyclicParentingIsRejected() {
        val scene = Scene("Main"); val parent = scene.create("Parent"); val child = scene.create("Child", parent)
        parent.parent = child
    }
    @Test(expected = IllegalArgumentException::class) fun missingSerializedParentsAreRejected() {
        SceneSerializer.fromJson(JSONObject().put("objects", JSONArray().put(JSONObject().put("id", 1).put("parent", 2))))
    }
    @Test(expected = IllegalArgumentException::class) fun crossSceneParentingIsRejectedOnCreation() {
        Scene("Main").create("Child", Scene("Main").create("Foreign parent"))
    }
    @Test fun deepHierarchyTraversesTransformsDuplicatesAndDeletesWithoutRecursion() {
        val scene = Scene("Main")
        val root = scene.create("Root")
        var parent = root
        repeat(2000) { parent = scene.create("Node $it", parent).apply { x = 1f } }
        scene.updateTransforms()
        assertEquals(2001, scene.hierarchy().size)
        assertEquals(2000f, parent.computeWorld().tx, 0.01f)
        assertTrue(parent.isActiveInHierarchy())
        val copy = scene.duplicate(root)
        assertEquals(4002, scene.objects.size)
        assertEquals("Root (1)", copy.name)
        scene.remove(root)
        assertEquals(2001, scene.objects.size)
        assertFalse(copy.destroyed)
    }
    @Test fun diagnosticsFindsMissingAssetsAndInvalidTransforms() {
        val scene = Scene("Main"); scene.create("Camera").add(Camera2D())
        scene.create("Sprite").apply { x = Float.NaN; add(SpriteRenderer().apply { texture = "missing.png" }) }
        val issues = SceneDiagnostics.inspect(scene) { false }
        assertTrue(issues.any { it.severity == SceneDiagnostics.Severity.ERROR })
        assertTrue(issues.any { it.message.contains("missing.png") })
        assertFalse(issues.any { it.message.contains("No active camera") })
    }
    @Test fun savingDoesNotReplaceLiveSceneWithRecoveryAndPreservesBackup() {
        val dir = Files.createTempDirectory("recovery-engine").toFile()
        try {
            val project = Project(dir)
            val scene = Scene("Main"); scene.create("Original")
            project.saveScene(scene)
            scene.create("Unsaved")
            project.saveRecovery(scene)
            assertEquals(1, project.loadScene("Main").objects.size)
            assertEquals(2, project.loadRecovery("Main").objects.size)
            project.saveScene(scene)
            assertEquals(2, project.loadScene("Main").objects.size)
            assertEquals(1, project.loadBackup("Main").objects.size)
            assertFalse(project.hasRecovery("Main"))
        } finally { dir.deleteRecursively() }
    }
}
