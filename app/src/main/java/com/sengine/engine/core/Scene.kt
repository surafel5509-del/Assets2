package com.sengine.engine.core

import org.json.JSONArray
import org.json.JSONObject

class Scene(var name: String) {
    val objects = mutableListOf<GameObject>()
    var gravityX = 0f
    var gravityY = -9.81f
    var gravityZ = 0f
    /** Vertical gravity used by 3D physics. */
    var gravity3D = -9.81f
    var ambient = 0xFF4A505A.toInt()
    var fog = false
    var fogColor = 0xFF9DB4CF.toInt()
    var fogStart = 20f
    var fogEnd = 80f
    var nextId = 1L

    fun create(name: String, parent: GameObject? = null): GameObject {
        require(parent == null || parent in objects) { "Parent is not in this scene" }
        val go = GameObject(nextId++, uniqueName(name))
        go.parent = parent
        objects.add(go)
        return go
    }

    fun uniqueName(base: String): String {
        if (objects.none { it.name == base }) return base
        val stem = base.replace(Regex(" \\(\\d+\\)$"), "")
        var i = 1
        while (objects.any { it.name == "$stem ($i)" }) i++
        return "$stem ($i)"
    }

    fun findById(id: Long) = objects.firstOrNull { it.id == id }
    fun find(name: String) = objects.firstOrNull { it.name == name && !it.destroyed }

    fun childrenOf(go: GameObject?) = objects.filter { it.parent === go }

    /** Linear-time, iterative traversal: safe even for deeply nested imported scenes. */
    fun hierarchy(): List<Pair<GameObject, Int>> {
        val children = objects.groupBy { it.parent }
        val out = ArrayList<Pair<GameObject, Int>>(objects.size)
        val stack = ArrayDeque<Pair<GameObject, Int>>()
        val visited = HashSet<GameObject>()
        fun walk(root: GameObject) {
            stack.addLast(root to 0)
            while (stack.isNotEmpty()) {
                val (go, depth) = stack.removeLast()
                if (!visited.add(go)) continue
                out.add(go to depth)
                for (child in children[go].orEmpty().asReversed()) stack.addLast(child to depth + 1)
            }
        }
        for (root in children[null].orEmpty()) walk(root)
        // Keep orphaned objects visible so diagnostics can report them.
        for (go in objects) if (go !in visited) walk(go)
        return out
    }

    fun remove(go: GameObject) {
        val children = objects.groupBy { it.parent }
        val pending = ArrayDeque<GameObject>()
        val removed = HashSet<GameObject>()
        pending.addLast(go)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (!removed.add(current)) continue
            current.destroyed = true
            children[current].orEmpty().forEach { pending.addLast(it) }
        }
        objects.removeAll(removed)
    }

    private val tmp3 = FloatArray(16)

    fun updateTransforms() {
        for ((go, _) in hierarchy()) {
            val p = go.parent
            if (p == null) {
                go.localMatrix(go.world)
                go.localMatrix3(go.world3)
            } else {
                go.world.setMul(p.world, go.localMatrix())
                com.sengine.engine.math.Mat4.mul(go.world3, p.world3, go.localMatrix3(tmp3))
            }
        }
    }

    /** Deep copy of an object (and its children) placed next to the original. */
    fun duplicate(src: GameObject, newParent: GameObject? = src.parent): GameObject {
        require(src in objects) { "Object is not in this scene" }
        require(newParent == null || newParent in objects) { "Parent is not in this scene" }
        require(newParent !== src && (newParent == null || !src.isAncestorOf(newParent))) { "Cannot duplicate into own subtree" }
        val descendants = objects.groupBy { it.parent }
        fun clone(original: GameObject, parent: GameObject?): GameObject {
            val copy = create(original.name, parent)
            SceneSerializer.applyObjectJson(copy, SceneSerializer.objectToJson(original))
            return copy
        }
        val root = clone(src, newParent)
        val pending = ArrayDeque<Pair<GameObject, GameObject>>()
        pending.addLast(src to root)
        while (pending.isNotEmpty()) {
            val (original, copy) = pending.removeLast()
            for (child in descendants[original].orEmpty()) pending.addLast(child to clone(child, copy))
        }
        return root
    }

    fun moveInOrder(go: GameObject, delta: Int) {
        val siblings = objects.filter { it.parent === go.parent }
        val i = siblings.indexOf(go)
        val j = (i + delta).coerceIn(0, siblings.size - 1)
        if (i == j) return
        val other = siblings[j]
        val a = objects.indexOf(go)
        val b = objects.indexOf(other)
        objects[a] = other
        objects[b] = go
    }
}

object SceneSerializer {
    const val SCHEMA_VERSION = 1
    fun objectToJson(go: GameObject): JSONObject {
        val o = JSONObject()
        o.put("id", go.id)
        o.put("name", go.name)
        o.put("tag", go.tag)
        o.put("active", go.active)
        o.put("order", go.order)
        o.put("x", go.x.toDouble()); o.put("y", go.y.toDouble())
        o.put("rotation", go.rotation.toDouble())
        o.put("scaleX", go.scaleX.toDouble()); o.put("scaleY", go.scaleY.toDouble())
        if (go.z != 0f) o.put("z", go.z.toDouble())
        if (go.rotX != 0f) o.put("rotX", go.rotX.toDouble())
        if (go.rotY != 0f) o.put("rotY", go.rotY.toDouble())
        if (go.scaleZ != 1f) o.put("scaleZ", go.scaleZ.toDouble())
        go.parent?.let { o.put("parent", it.id) }
        val comps = JSONArray()
        for (c in go.components) comps.put(c.toJson())
        o.put("components", comps)
        return o
    }

    /** Applies everything except id and parent. */
    fun applyObjectJson(go: GameObject, o: JSONObject) {
        go.tag = o.optString("tag", "Untagged")
        go.active = o.optBoolean("active", true)
        go.order = o.optInt("order", 0)
        go.x = o.optDouble("x", 0.0).toFloat()
        go.y = o.optDouble("y", 0.0).toFloat()
        go.rotation = o.optDouble("rotation", 0.0).toFloat()
        go.scaleX = o.optDouble("scaleX", 1.0).toFloat()
        go.scaleY = o.optDouble("scaleY", 1.0).toFloat()
        go.z = o.optDouble("z", 0.0).toFloat()
        go.rotX = o.optDouble("rotX", 0.0).toFloat()
        go.rotY = o.optDouble("rotY", 0.0).toFloat()
        go.scaleZ = o.optDouble("scaleZ", 1.0).toFloat()
        go.components.clear()
        val comps = o.optJSONArray("components") ?: JSONArray()
        for (i in 0 until comps.length()) {
            val cj = comps.getJSONObject(i)
            val c = ComponentRegistry.create(cj.optString("type")) ?: MissingComponent(cj)
            c.fromJson(cj)
            go.add(c)
        }
    }

    fun toJson(scene: Scene): JSONObject {
        val o = JSONObject()
        o.put("schemaVersion", SCHEMA_VERSION)
        o.put("name", scene.name)
        o.put("gravityX", scene.gravityX.toDouble())
        o.put("gravityY", scene.gravityY.toDouble())
        o.put("gravityZ", scene.gravityZ.toDouble())
        o.put("gravity3D", scene.gravity3D.toDouble())
        o.put("ambient", String.format("#%08X", scene.ambient))
        o.put("fogColor", String.format("#%08X", scene.fogColor))
        o.put("fogStart", scene.fogStart.toDouble()); o.put("fogEnd", scene.fogEnd.toDouble())
        o.put("fog", scene.fog)
        o.put("nextId", scene.nextId)
        val arr = JSONArray()
        for (go in scene.objects) arr.put(objectToJson(go))
        o.put("objects", arr)
        return o
    }

    fun fromJson(o: JSONObject): Scene {
        val version = o.optInt("schemaVersion", 0)
        require(version in 0..SCHEMA_VERSION) { "Scene schema $version is not supported by this editor" }
        val s = Scene(o.optString("name", "Main"))
        s.gravityX = o.optDouble("gravityX", 0.0).toFloat()
        s.gravityY = o.optDouble("gravityY", -9.81).toFloat()
        s.gravityZ = o.optDouble("gravityZ", 0.0).toFloat()
        s.gravity3D = o.optDouble("gravity3D", -9.81).toFloat()
        try { s.ambient = Component.parseColor(o.optString("ambient", "#FF4A505A")) } catch (_: Exception) {}
        try { s.fogColor = Component.parseColor(o.optString("fogColor", "#FF9DB4CF")) } catch (_: Exception) {}
        s.fogStart = o.optDouble("fogStart", 20.0).toFloat()
        s.fogEnd = o.optDouble("fogEnd", 80.0).toFloat()
        s.fog = o.optBoolean("fog", false)
        val arr = o.optJSONArray("objects") ?: JSONArray()
        val parents = HashMap<GameObject, Long>()
        var maxId = 0L
        val byId = HashMap<Long, GameObject>()
        for (i in 0 until arr.length()) {
            val oj = arr.getJSONObject(i)
            val go = GameObject(oj.optLong("id"), oj.optString("name", "GameObject"))
            require(go.id > 0 && go.id < Long.MAX_VALUE && !byId.containsKey(go.id)) { "Invalid or duplicate object ID ${go.id}" }
            byId[go.id] = go
            applyObjectJson(go, oj)
            if (oj.has("parent")) parents[go] = oj.getLong("parent")
            s.objects.add(go)
            maxId = maxOf(maxId, go.id)
        }
        for ((go, pid) in parents) go.parent = byId[pid] ?: throw IllegalArgumentException("Missing parent $pid for ${go.name}")
        s.nextId = maxOf(o.optLong("nextId", 1L), maxId + 1)
        return s
    }
}
