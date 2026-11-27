package com.sengine.engine.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Row/column helpers shared by tilemap rendering, collision and script APIs. */
object TilemapData {
    /** Semicolon or newline separated rows; comma or whitespace separated tile ids. -1 is empty. */
    fun rows(map: String): List<List<Int>> = map
        .split(';', '\n')
        .map { line ->
            line.trim().split(Regex("[,\\s]+"))
                .filter { it.isNotEmpty() }
                .map { it.trim().toIntOrNull() ?: -1 }
        }
        .filter { it.isNotEmpty() }

    fun format(rows: List<List<Int>>): String =
        rows.joinToString(";") { row -> row.joinToString(",") }

    fun paletteColors(palette: String): IntArray = palette
        .split(',')
        .mapNotNull { part ->
            val s = part.trim()
            if (s.isEmpty()) null else try { Component.parseColor(s) } catch (_: Exception) { null }
        }
        .toIntArray()

    fun isSolid(id: Int): Boolean = id >= 0

    fun colorFor(palette: String, id: Int, fallback: Int): Int {
        val colors = paletteColors(palette)
        return if (id in colors.indices) colors[id] else fallback
    }
}

/** Serializable .mat asset. Applied to a renderer copy, never to the scene object itself. */
data class MaterialAsset(
    var color: Int = 0xFFFFFFFF.toInt(),
    var texture: String = "",
    var specular: Float = 0.35f,
    var shininess: Float = 32f,
    var emission: Float = 0f,
    var tiling: Float = 1f,
    var shader: String = "",
    var shaderParam: Float = 1f,
    var unlit: Boolean = false,
) {
    fun applyTo(mr: MeshRenderer) {
        mr.color = color
        mr.texture = texture
        mr.specular = specular
        mr.shininess = shininess
        mr.emission = emission
        mr.tiling = tiling
        mr.shader = shader
        mr.shaderParam = shaderParam
        mr.unlit = unlit
    }

    fun applyTo(sr: SpriteRenderer) {
        sr.color = color
        sr.texture = texture
        sr.shader = shader
        sr.shaderParam = shaderParam
    }

    fun toJson(): JSONObject = JSONObject()
        .put("color", String.format("#%08X", color))
        .put("texture", texture)
        .put("specular", specular.toDouble())
        .put("shininess", shininess.toDouble())
        .put("emission", emission.toDouble())
        .put("tiling", tiling.toDouble())
        .put("shader", shader)
        .put("shaderParam", shaderParam.toDouble())
        .put("unlit", unlit)

    companion object {
        fun fromJson(o: JSONObject): MaterialAsset {
            val color = try { Component.parseColor(o.optString("color", "#FFFFFFFF")) } catch (_: Exception) { 0xFFFFFFFF.toInt() }
            return MaterialAsset(
                color,
                o.optString("texture", ""),
                o.optDouble("specular", 0.35).toFloat().coerceIn(0f, 2f),
                o.optDouble("shininess", 32.0).toFloat().coerceIn(1f, 256f),
                o.optDouble("emission", 0.0).toFloat().coerceIn(0f, 4f),
                o.optDouble("tiling", 1.0).toFloat().coerceAtLeast(0.01f),
                o.optString("shader", ""),
                o.optDouble("shaderParam", 1.0).toFloat(),
                o.optBoolean("unlit", false),
            )
        }
    }
}

/** Named UV rectangles inside a sprite sheet. UV order matches SpriteRenderer.uv. */
data class SpriteAtlas(
    var texture: String = "",
    var columns: Int = 1,
    var rows: Int = 1,
    var frames: LinkedHashMap<String, FloatArray> = linkedMapOf(),
) {
    val frameNames: List<String> get() = frames.keys.sorted()

    fun uv(name: String, out: FloatArray): Boolean {
        val key = if (name.isBlank()) frameNames.firstOrNull() ?: return false else name
        val f = frames[key] ?: return false
        out[0] = f[0]; out[1] = f[1]; out[2] = f[2]; out[3] = f[3]
        return true
    }

    fun toJson(): JSONObject {
        val f = JSONObject()
        for ((name, uv) in frames) f.put(name, JSONArray().apply { uv.forEach { put(it.toDouble()) } })
        return JSONObject().put("texture", texture).put("columns", columns).put("rows", rows).put("frames", f)
    }

    companion object {
        fun fromJson(o: JSONObject): SpriteAtlas {
            val out = LinkedHashMap<String, FloatArray>()
            val f = o.optJSONObject("frames")
            if (f != null) {
                val keys = f.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val a = f.optJSONArray(name)
                    if (a != null && a.length() >= 4) {
                        out[name] = floatArrayOf(
                            a.optDouble(0, 0.0).toFloat(), a.optDouble(1, 0.0).toFloat(),
                            a.optDouble(2, 1.0).toFloat(), a.optDouble(3, 1.0).toFloat(),
                        )
                    } else {
                        val rect = f.optJSONObject(name)
                        if (rect != null) out[name] = floatArrayOf(
                            rect.optDouble("u0", 0.0).toFloat(), rect.optDouble("v0", 0.0).toFloat(),
                            rect.optDouble("u1", 1.0).toFloat(), rect.optDouble("v1", 1.0).toFloat(),
                        )
                    }
                }
            }
            return SpriteAtlas(
                o.optString("texture", ""),
                o.optInt("columns", 1).coerceAtLeast(1),
                o.optInt("rows", 1).coerceAtLeast(1),
                out,
            )
        }
    }
}

/** .prefab files are scene-object subtrees with local transforms and relative parent ids. */
object PrefabAsset {
    const val FORMAT = "S Engine Prefab"

    fun capture(scene: Scene, root: GameObject): JSONObject {
        val list = ArrayList<GameObject>()
        fun walk(go: GameObject) {
            list.add(go)
            for (child in scene.childrenOf(go)) walk(child)
        }
        walk(root)
        val objects = JSONArray()
        for (go in list) {
            val o = SceneSerializer.objectToJson(go)
            if (go === root) o.remove("parent")
            objects.put(o)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("name", root.name)
            .put("objects", objects)
    }

    fun parse(text: String): JSONObject? = try {
        val o = JSONObject(text)
        val arr = o.optJSONArray("objects")
        if (o.optString("format") != FORMAT || o.optInt("version", 1) > 1 || arr == null || arr.length() == 0) null else o
    } catch (_: Exception) { null }

    /** Instantiates all roots from [text] into [scene], offset from their prefab-local positions. */
    fun instantiate(scene: Scene, text: String, x: Float, y: Float, z: Float? = null): GameObject? {
        val o = parse(text) ?: return null
        val tmp = try {
            SceneSerializer.fromJson(JSONObject().put("objects", o.getJSONArray("objects")))
        } catch (_: Exception) { return null }
        tmp.updateTransforms()
        // Duplicate from the *temporary* scene: Scene.duplicate() looks up children
        // in its own object list, so calling it on the destination would lose the
        // prefab's hierarchy.
        fun copyTree(src: GameObject, newParent: GameObject?): GameObject {
            val json = SceneSerializer.objectToJson(src)
            val copy = scene.create(src.name, newParent)
            SceneSerializer.applyObjectJson(copy, json)
            copy.parent = newParent
            for (child in tmp.childrenOf(src).toList()) copyTree(child, copy)
            return copy
        }
        val roots = tmp.objects.filter { it.parent == null }
        var first: GameObject? = null
        for (root in roots) {
            val copy = copyTree(root, null)
            if (first == null) first = copy
            val w2 = copy.computeWorld()
            val w3 = copy.computeWorld3()
            if (z != null) copy.setWorldPosition3(x + w3[12], y + w3[13], z + w3[14])
            else copy.setWorldPosition(x + w2.tx, y + w2.ty)
            copy.active = root.active
            for (d in allDescendants(scene, copy)) d.components.forEach { it.resetRuntime() }
        }
        scene.updateTransforms()
        return first
    }

    private fun allDescendants(scene: Scene, root: GameObject): List<GameObject> {
        val out = ArrayList<GameObject>()
        fun walk(go: GameObject) {
            out.add(go)
            for (child in scene.childrenOf(go)) walk(child)
        }
        walk(root)
        return out
    }
}

/** Lightweight validation for the new text data assets, shared by editor and tests. */
object AssetValidator {
    private val SUPPORTED = setOf(
        AssetKind.PREFAB, AssetKind.MATERIAL, AssetKind.ATLAS,
        AssetKind.TILEMAP, AssetKind.SCENE, AssetKind.TEXT, AssetKind.ANIMATION,
    )

    fun supports(name: String): Boolean = AssetKind.of(name) in SUPPORTED

    fun label(name: String): String = when (AssetKind.of(name)) {
        AssetKind.PREFAB -> "Prefab JSON"
        AssetKind.MATERIAL -> "Material JSON"
        AssetKind.ATLAS -> "Sprite Atlas JSON"
        AssetKind.TILEMAP -> "Tilemap JSON"
        AssetKind.SCENE -> "Scene Snapshot JSON"
        AssetKind.ANIMATION -> "Animation JSON"
        AssetKind.TEXT -> "Text Data"
        else -> "Text"
    }

    fun validate(name: String, text: String): String? {
        val kind = AssetKind.of(name) ?: return null
        if (kind.binary || !supports(name)) return null
        return try {
            when (kind) {
                AssetKind.MATERIAL -> {
                    val o = JSONObject(text)
                    if (!o.has("color")) return "missing color"
                    val m = MaterialAsset.fromJson(o)
                    if (m.shininess < 1f || m.tiling <= 0f) "invalid material range" else null
                }
                AssetKind.ATLAS -> {
                    val a = SpriteAtlas.fromJson(JSONObject(text))
                    if (a.columns < 1 || a.rows < 1) return "invalid atlas grid"
                    if (a.frames.isEmpty()) return "atlas has no frames"
                    for ((frame, uv) in a.frames) {
                        if (uv.size != 4 || uv[0] >= uv[2] || uv[1] <= uv[3]) return "invalid UV for frame $frame"
                        if (uv.any { it < -0.001f || it > 1.001f }) return "UV outside 0..1 for frame $frame"
                    }
                    null
                }
                AssetKind.TILEMAP -> {
                    val t = Tilemap.fromAsset(text)
                    val rows = TilemapData.rows(t.map)
                    if (t.columns < 1 || t.rows < 1 || t.tileSize <= 0f) "invalid tilemap grid"
                    else if (rows.isEmpty() || rows.none { row -> row.any(TilemapData::isSolid) }) "tilemap has no solid tiles"
                    else null
                }
                AssetKind.PREFAB -> {
                    val o = PrefabAsset.parse(text) ?: return "invalid prefab header"
                    val scene = SceneSerializer.fromJson(JSONObject().put("objects", o.getJSONArray("objects")))
                    if (scene.objects.none { it.parent == null }) "prefab has no root object" else null
                }
                AssetKind.SCENE -> {
                    val o = JSONObject(text)
                    if (!o.has("objects")) return "missing objects"
                    SceneSerializer.fromJson(o)
                    null
                }
                AssetKind.ANIMATION -> {
                    val o = JSONObject(text)
                    if (!o.has("frames") || !o.has("fps")) "missing frames or fps" else null
                }
                AssetKind.TEXT -> null
                else -> null
            }
        } catch (e: Exception) {
            e.message ?: "Invalid ${label(name)}"
        }
    }
}
