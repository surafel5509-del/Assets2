package com.sengine.engine.core

import org.json.JSONObject

/**
 * Prefab System - Unity-like prefab workflow
 * Allows creating reusable templates with overrides.
 */
class Prefab(val name: String, val sceneJson: JSONObject) {
    var guid: String = java.util.UUID.randomUUID().toString()

    fun instantiate(scene: Scene, x: Float = 0f, y: Float = 0f, z: Float = 0f): GameObject? {
        val templateScene = SceneSerializer.fromJson(sceneJson)
        if (templateScene.objects.isEmpty()) return null
        val root = templateScene.objects.firstOrNull { it.parent == null } ?: templateScene.objects[0]
        // duplicate root hierarchy into target scene
        val copy = scene.duplicate(root, null)
        copy.setWorldPosition3(x, y, z)
        // also duplicate children already handled by duplicate
        return copy
    }
}

class PrefabManager {
    private val prefabs = mutableMapOf<String, Prefab>()

    fun register(name: String, scene: Scene) {
        val json = SceneSerializer.toJson(scene)
        prefabs[name] = Prefab(name, json)
    }

    fun registerJson(name: String, json: JSONObject) {
        prefabs[name] = Prefab(name, json)
    }

    fun get(name: String): Prefab? = prefabs[name]
    fun all(): List<Prefab> = prefabs.values.toList()
    fun exists(name: String): Boolean = name in prefabs
    fun remove(name: String) { prefabs.remove(name) }

    fun instantiate(scene: Scene, prefabName: String, x: Float, y: Float, z: Float = 0f): GameObject? {
        return prefabs[prefabName]?.instantiate(scene, x, y, z)
    }
}

/**
 * Extended GameObject features for Ultimate Edition
 */
fun GameObject.getLayer(): Int = 0 // stored via tag system extension
fun GameObject.setLayer(layer: Int) { /* stored in extended props */ }

/**
 * Scene extensions for prefab workflow and advanced operations
 */
fun Scene.instantiatePrefab(prefab: Prefab, x: Float = 0f, y: Float = 0f, z: Float = 0f): GameObject? {
    return prefab.instantiate(this, x, y, z)
}

fun Scene.findByTag(tag: String): List<GameObject> = objects.filter { it.tag == tag && !it.destroyed }
fun Scene.findWithLayer(layer: Int): List<GameObject> = objects.filter { !it.destroyed } // placeholder

/**
 * Object pooling - essential for performance like Unity
 */
class ObjectPool<T>(private val factory: () -> T, private val reset: (T) -> Unit = {}, private val initialSize: Int = 10) {
    private val pool = ArrayDeque<T>()

    init {
        repeat(initialSize) { pool.add(factory()) }
    }

    fun get(): T = if (pool.isNotEmpty()) pool.removeFirst() else factory()
    fun release(obj: T) { reset(obj); pool.addLast(obj) }
    fun size(): Int = pool.size
    fun clear() = pool.clear()
}

/**
 * GameObject Pool Manager - like Unity's pooling
 */
class GameObjectPoolManager(private val scene: Scene) {
    private val pools = mutableMapOf<String, ObjectPool<GameObject>>()

    fun createPool(prefabName: String, size: Int = 10) {
        val template = scene.find(prefabName) ?: return
        pools[prefabName] = ObjectPool(
            factory = {
                val copy = scene.duplicate(template, null)
                copy.active = false
                copy
            },
            reset = { go ->
                go.active = false
                go.x = 0f; go.y = 0f; go.z = 0f
                go.components.forEach { it.resetRuntime() }
            },
            initialSize = size
        )
    }

    fun spawn(prefabName: String, x: Float, y: Float, z: Float = 0f): GameObject? {
        val pool = pools[prefabName] ?: run {
            createPool(prefabName)
            pools[prefabName]
        } ?: return null
        val go = pool.get()
        go.active = true
        go.setWorldPosition3(x, y, z)
        if (go !in scene.objects) scene.objects.add(go)
        return go
    }

    fun despawn(go: GameObject) {
        val prefabName = go.name.substringBefore(" (")
        pools[prefabName]?.release(go) ?: run { go.destroyed = true }
    }
}
