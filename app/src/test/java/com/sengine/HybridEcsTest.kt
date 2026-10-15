package com.sengine

import com.sengine.engine.core.GameObject
import com.sengine.engine.ecs.*
import org.junit.Assert.*
import org.junit.Test

class HybridEcsTest {
    @Test fun hybridSyncDoesNotRewindPhysicsOrScriptTransforms() {
        val ecs = EntityManager(); val go = GameObject(1, "Body")
        ecs.createFromGameObject(go)
        go.x = 42f; go.y = -7f; go.rotY = 90f; go.scaleZ = 2f
        ecs.update(1f / 60f)
        assertEquals(42f, go.x, 0f); assertEquals(-7f, go.y, 0f)
        assertEquals(90f, go.rotY, 0f); assertEquals(2f, go.scaleZ, 0f)
    }
    @Test fun ecsSystemsCanStillWriteHybridTransforms() {
        val ecs = EntityManager(); val go = GameObject(1, "Body")
        ecs.createFromGameObject(go)
        ecs.world.addSystem(object : SystemBase() {
            override fun onCreate() {}
            override fun onUpdate(dt: Float) {
                for (chunk in world.query(Query(all = setOf(Translation::class.java)))) {
                    chunk.getComponents(Translation::class.java).forEach { it.x += 2f }
                }
            }
        })
        go.x = 3f; ecs.update(0.1f)
        assertEquals(5f, go.x, 0f)
    }
    @Test fun duplicateRegistrationAndDestroyedObjectsDoNotLeakEntities() {
        val ecs = EntityManager(); val go = GameObject(1, "Body")
        val first = ecs.createFromGameObject(go)
        assertEquals(first, ecs.createFromGameObject(go))
        assertEquals(1, ecs.world.entityCount())
        go.destroyed = true; ecs.update(0.1f)
        assertEquals(0, ecs.world.entityCount())
    }
    @Test fun newSceneDoesNotReuseStaleEntityHandles() {
        val ecs = EntityManager()
        val previous = ecs.createFromGameObject(GameObject(1, "Old scene"))
        ecs.clear()
        val next = ecs.createFromGameObject(GameObject(1, "New scene"))
        assertNotEquals(previous, next)
        ecs.world.destroyEntity(previous)
        assertEquals(1, ecs.world.entityCount())
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateComponentTypesCannotMisalignChunks() {
        World().createEntity(Translation(0f, 0f, 0f), Translation(1f, 1f, 1f))
    }
}
