package com.sengine.engine.core

/**
 * Unity-like Layer & Tag system with 32 layers and collision matrix.
 */
object LayerMask {
    const val MAX_LAYERS = 32
    val names = Array(MAX_LAYERS) { i ->
        when (i) {
            0 -> "Default"
            1 -> "TransparentFX"
            2 -> "Ignore Raycast"
            4 -> "Water"
            5 -> "UI"
            8 -> "Ground"
            9 -> "Player"
            10 -> "Enemy"
            11 -> "Projectile"
            12 -> "Pickup"
            13 -> "Trigger"
            14 -> "Vehicle"
            15 -> "Destructible"
            else -> "Layer $i"
        }
    }

    fun nameToLayer(name: String): Int = names.indexOf(name).takeIf { it >= 0 } ?: 0
    fun layerToName(layer: Int): String = if (layer in 0 until MAX_LAYERS) names[layer] else "Unknown"

    fun getMask(vararg layers: Int): Int {
        var mask = 0
        for (l in layers) if (l in 0 until MAX_LAYERS) mask = mask or (1 shl l)
        return mask
    }

    fun hasLayer(mask: Int, layer: Int): Boolean = (mask and (1 shl layer)) != 0
}

object TagManager {
    val tags = mutableListOf(
        "Untagged", "Player", "MainCamera", "GameController", "Enemy", "Finish",
        "EditorOnly", "Respawn", "Coin", "Projectile", "Pickup", "Ground",
        "UI", "Trigger", "Obstacle", "Vehicle", "Weapon", "Destructible"
    )

    fun addTag(tag: String) { if (tag !in tags) tags.add(tag) }
    fun isValid(tag: String) = tag in tags
}

class CollisionMatrix {
    // 32x32 matrix, true = can collide
    private val matrix = Array(LayerMask.MAX_LAYERS) { BooleanArray(LayerMask.MAX_LAYERS) { true } }

    init {
        // sensible defaults
        setCollision(LayerMask.nameToLayer("Ignore Raycast"), LayerMask.nameToLayer("Ignore Raycast"), false)
        setCollision(LayerMask.nameToLayer("UI"), LayerMask.nameToLayer("UI"), false)
    }

    fun setCollision(a: Int, b: Int, canCollide: Boolean) {
        if (a in 0 until LayerMask.MAX_LAYERS && b in 0 until LayerMask.MAX_LAYERS) {
            matrix[a][b] = canCollide
            matrix[b][a] = canCollide
        }
    }

    fun canCollide(a: Int, b: Int): Boolean {
        if (a !in 0 until LayerMask.MAX_LAYERS || b !in 0 until LayerMask.MAX_LAYERS) return true
        return matrix[a][b]
    }

    fun toJson(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        for (i in 0 until LayerMask.MAX_LAYERS) {
            var bits = 0
            for (j in 0 until LayerMask.MAX_LAYERS) if (matrix[i][j]) bits = bits or (1 shl j)
            arr.put(bits)
        }
        return arr
    }

    fun fromJson(arr: org.json.JSONArray) {
        for (i in 0 until minOf(arr.length(), LayerMask.MAX_LAYERS)) {
            val bits = arr.getInt(i)
            for (j in 0 until LayerMask.MAX_LAYERS) matrix[i][j] = (bits and (1 shl j)) != 0
        }
    }
}
