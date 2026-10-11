package com.sengine.engine.render.`2d`

/**
 * S Engine Ultimate 6.1.1 [Titan] - Sorting System & 2D Lighting V2
 * Sorting Layers, Sorting Groups, Order in Layer, 2D Lighting with Normal Maps
 */
enum class SortingLayerType { Default, Background, Ground, Player, Enemy, Foreground, UI, Custom }

data class SortingLayer(
    var id: Int = 0,
    var name: String = "Default",
    var value: Int = 0,
    var isDefault: Boolean = false
)

data class SortingGroup(
    var id: String = "group_0",
    var sortingLayer: Int = 0,
    var sortingOrder: Int = 0,
    var sortAtRoot: Boolean = true,
    var objects: MutableList<Long> = mutableListOf()
) {
    fun addObject(objId: Long) { if (objId !in objects) objects.add(objId) }
    fun removeObject(objId: Long) { objects.remove(objId) }
    fun getSortingValue(): Int = sortingLayer*1000 + sortingOrder
}

data class OrderInLayer(
    var objectId: Long = 0L,
    var layer: Int = 0,
    var order: Int = 0
) {
    fun getSortingValue(): Int = layer*1000 + order
}

class SortingSystem {
    var layers = mutableMapOf<Int, SortingLayer>()
    var groups = mutableMapOf<String, SortingGroup>()
    var orders = mutableMapOf<Long, OrderInLayer>()
    var nextLayerId: Int = 0
    var nextGroupId: Int = 0

    init {
        // Default layers
        createLayer("Default", 0, true)
        createLayer("Background", -100)
        createLayer("Ground", -10)
        createLayer("Player", 0)
        createLayer("Enemy", 10)
        createLayer("Foreground", 100)
        createLayer("UI", 1000)
    }

    fun createLayer(name: String, value: Int, isDefault: Boolean = false): SortingLayer {
        val id = nextLayerId++
        val layer = SortingLayer(id=id, name=name, value=value, isDefault=isDefault)
        layers[id]=layer; return layer
    }

    fun createGroup(sortingLayer: Int = 0, sortingOrder: Int = 0): SortingGroup {
        val id = "group_${nextGroupId++}"
        val group = SortingGroup(id=id, sortingLayer=sortingLayer, sortingOrder=sortingOrder)
        groups[id]=group; return group
    }

    fun setOrder(objectId: Long, layer: Int, order: Int) {
        orders[objectId]=OrderInLayer(objectId=objectId, layer=layer, order=order)
    }

    fun getOrder(objectId: Long): OrderInLayer? = orders[objectId]

    fun getSortedObjects(objectIds: List<Long>): List<Long> {
        return objectIds.sortedBy { id ->
            val order = orders[id]
            val group = groups.values.find { id in it.objects }
            when {
                group != null -> group.getSortingValue()
                order != null -> order.getSortingValue()
                else -> 0
            }
        }
    }

    fun getLayerByName(name: String): SortingLayer? = layers.values.find { it.name == name }
    fun getLayerByValue(value: Int): SortingLayer? = layers.values.find { it.value == value }

    fun getStats(): String = "Sorting Layers:${layers.size} Groups:${groups.size} Orders:${orders.size}"
}

// 2D Lighting with Normal Maps
data class NormalMapData(
    var texture: String = "normal.png",
    var strength: Float = 1f,
    var flipX: Boolean = false,
    var flipY: Boolean = false
)

data class Light2DData(
    var x: Float = 0f, var y: Float = 0f,
    var color: Int = 0xFFFFFFFF.toInt(),
    var intensity: Float = 1f,
    var radius: Float = 5f,
    var normalMapInfluence: Float = 1f
) {
    fun calculateWithNormal(px: Float, py: Float, normalX: Float, normalY: Float, normalZ: Float): Int {
        val dx = x-px; val dy = y-py
        val dist = kotlin.math.sqrt(dx*dx+dy*dy)
        if (dist > radius) return 0
        val atten = 1f - dist/radius
        // Dot with normal for normal map influence
        val len = kotlin.math.sqrt(dx*dx+dy*dy+1f)
        val lightDirX = dx/len; val lightDirY = dy/len; val lightDirZ = 1f/len
        val dot = lightDirX*normalX + lightDirY*normalY + lightDirZ*normalZ
        val normalFactor = (dot*0.5f+0.5f)*normalMapInfluence + (1f-normalMapInfluence)
        val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
        val nr = (r*atten*intensity*normalFactor).toInt().coerceIn(0,255)
        val ng = (g*atten*intensity*normalFactor).toInt().coerceIn(0,255)
        val nb = (b*atten*intensity*normalFactor).toInt().coerceIn(0,255)
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
}

class Lighting2DSystemV2 {
    var lights = mutableListOf<Light2DData>()
    var normalMaps = mutableMapOf<String, NormalMapData>()
    var ambientColor: Int = 0xFF404040.toInt()
    var ambientIntensity: Float = 0.3f
    var useNormalMaps: Boolean = true
    var maxLights: Int = 16

    fun addLight(x: Float, y: Float, color: Int, intensity: Float, radius: Float): Light2DData {
        val light = Light2DData(x=x, y=y, color=color, intensity=intensity, radius=radius)
        lights.add(light); return light
    }

    fun addNormalMap(texture: String, strength: Float = 1f): NormalMapData {
        val nm = NormalMapData(texture=texture, strength=strength); normalMaps[texture]=nm; return nm
    }

    fun calculateLighting(px: Float, py: Float, normalX: Float = 0f, normalY: Float = 0f, normalZ: Float = 1f): Int {
        var r = (ambientColor shr 16) and 0xFF; var g = (ambientColor shr 8) and 0xFF; var b = ambientColor and 0xFF
        r = (r*ambientIntensity).toInt(); g = (g*ambientIntensity).toInt(); b = (b*ambientIntensity).toInt()
        for (light in lights.take(maxLights)) {
            val col = if (useNormalMaps) light.calculateWithNormal(px, py, normalX, normalY, normalZ) else {
                val dx = light.x-px; val dy = light.y-py
                val dist = kotlin.math.sqrt(dx*dx+dy*dy)
                if (dist > light.radius) 0 else {
                    val atten = 1f-dist/light.radius
                    val lr = (light.color shr 16) and 0xFF; val lg = (light.color shr 8) and 0xFF; val lb = light.color and 0xFF
                    (0xFF shl 24) or ((lr*atten*light.intensity).toInt().coerceIn(0,255) shl 16) or ((lg*atten*light.intensity).toInt().coerceIn(0,255) shl 8) or (lb*atten*light.intensity).toInt().coerceIn(0,255)
                }
            }
            if (col != 0) {
                val lr = (col shr 16) and 0xFF; val lg = (col shr 8) and 0xFF; val lb = col and 0xFF
                r+=lr; g+=lg; b+=lb
            }
        }
        r=r.coerceIn(0,255); g=g.coerceIn(0,255); b=b.coerceIn(0,255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun getStats(): String = "2D Lighting Lights:${lights.size}/$maxLights NormalMaps:${normalMaps.size} Ambient:${ambientIntensity} UseNormal:$useNormalMaps"
}
