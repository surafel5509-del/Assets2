package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.*
import com.sengine.project.Project
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RuntimeLifecycleTest {
    @Test fun timeScalePauseStepAndStopRespectSceneLifecycle() {
        val dir = Files.createTempDirectory("runtime-lifecycle").toFile()
        val scene = Scene("Lifecycle")
        scene.create("Camera").add(Camera2D())
        val body = scene.create("Body").apply { add(Rigidbody2D()) }
        val engine = Engine(Project(dir), scene)
        try {
            engine.timeManager.timeScale = 0f
            engine.play(); engine.tick(1f / 60f)
            assertEquals(0f, body.y, 0f)
            assertEquals(0.0, engine.time, 0.0)
            engine.timeManager.timeScale = 1f
            engine.tick(1f / 60f)
            assertTrue(body.y < 0f)
            engine.pause(); engine.tick(1f / 60f)
            val paused = body.y
            engine.tick(1f / 60f)
            assertEquals(paused, body.y, 0f)
            engine.stepFrame(); engine.tick(1f / 60f)
            assertTrue(body.y < paused)
            engine.stop(); engine.tick(1f / 60f)
            assertEquals(0f, engine.scene.find("Body")!!.y, 0f)
            assertEquals(0, engine.ecs.world.entityCount())
            repeat(3) {
                engine.play(); engine.tick(1f / 60f)
                assertEquals(2, engine.ecs.world.entityCount())
                engine.stop(); engine.tick(1f / 60f)
            }
        } finally { engine.release(); dir.deleteRecursively() }
    }
}
