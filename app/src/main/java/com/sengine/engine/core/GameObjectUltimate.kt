package com.sengine.engine.core

/**
 * Ultimate Edition GameObject Extensions - Unity 6 / UE5 class features
 * Tags, Layers, Static flags, Prefab, LOD, etc.
 */

enum class StaticEditorFlags(val value: Int) {
    Nothing(0),
    Everything(1),
    LightmapStatic(2),
    OccluderStatic(4),
    BatchingStatic(8),
    NavigationStatic(16),
    OccludeeStatic(32),
    OffMeshLinkGeneration(64),
    ReflectionProbeStatic(128)
}

class GameObjectUltimateExtensions {
    // This file extends GameObject functionality via extension properties stored in a map
    companion object {
        private val layerMap = mutableMapOf<Long, Int>()
        private val staticFlagsMap = mutableMapOf<Long, Int>()
        private val prefabMap = mutableMapOf<Long, String>()
        private val isStaticMap = mutableMapOf<Long, Boolean>()

        fun getLayer(go: GameObject): Int = layerMap[go.id] ?: 0
        fun setLayer(go: GameObject, layer: Int) { layerMap[go.id] = layer.coerceIn(0, 31) }

        fun getStaticFlags(go: GameObject): Int = staticFlagsMap[go.id] ?: 0
        fun setStaticFlags(go: GameObject, flags: Int) { staticFlagsMap[go.id] = flags }

        fun isStatic(go: GameObject): Boolean = isStaticMap[go.id] ?: false
        fun setStatic(go: GameObject, isStatic: Boolean) { isStaticMap[go.id] = isStatic }

        fun getPrefabGuid(go: GameObject): String? = prefabMap[go.id]
        fun setPrefabGuid(go: GameObject, guid: String) { prefabMap[go.id] = guid }

        fun clear(go: GameObject) {
            layerMap.remove(go.id)
            staticFlagsMap.remove(go.id)
            prefabMap.remove(go.id)
            isStaticMap.remove(go.id)
        }
    }
}

// Extension functions for easier access
fun GameObject.getLayerPro(): Int = GameObjectUltimateExtensions.getLayer(this)
fun GameObject.setLayerPro(layer: Int) = GameObjectUltimateExtensions.setLayer(this, layer)
fun GameObject.isStaticPro(): Boolean = GameObjectUltimateExtensions.isStatic(this)
fun GameObject.setStaticPro(isStatic: Boolean) = GameObjectUltimateExtensions.setStatic(this, isStatic)

/**
 * Scene culling and optimization data
 */
class SceneCullingData {
    var occluders = mutableListOf<GameObject>()
    var occludees = mutableListOf<GameObject>()
    var staticObjects = mutableListOf<GameObject>()
    var dynamicObjects = mutableListOf<GameObject>()

    fun update(scene: Scene) {
        occluders.clear(); occludees.clear(); staticObjects.clear(); dynamicObjects.clear()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            if (go.isStaticPro()) staticObjects.add(go) else dynamicObjects.add(go)
            if (go.get<OcclusionAreaComponent>() != null) occluders.add(go)
            if (go.get<MeshRenderer>() != null) occludees.add(go)
        }
    }
}

/**
 * Transform utilities - Unity-like
 */
object TransformUtils {
    fun lookAt(go: GameObject, target: GameObject) {
        val w = go.world3
        val tw = target.world3
        val dx = tw[12] - w[12]
        val dy = tw[13] - w[13]
        val dz = tw[14] - w[14]
        val yaw = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()
        val distXZ = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        val pitch = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), distXZ.toDouble())).toFloat()
        go.rotY = yaw
        go.rotX = -pitch
    }

    fun rotateAround(go: GameObject, point: FloatArray, axis: FloatArray, angle: Float) {
        // Simplified rotation around point
        val px = go.x - point[0]
        val py = go.y - point[1]
        val pz = go.z - point[2]
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val cos = kotlin.math.cos(rad); val sin = kotlin.math.sin(rad)
        // Rotate around Y axis for simplicity
        go.x = point[0] + px * cos - pz * sin
        go.z = point[2] + px * sin + pz * cos
    }

    fun translateInWorldSpace(go: GameObject, translation: FloatArray) {
        go.x += translation[0]
        go.y += translation[1]
        go.z += translation[2]
    }
}

/**
 * Component utilities
 */
object ComponentUtils {
    inline fun <reified T : Component> requireComponent(go: GameObject): T {
        return go.getAny<T>() ?: go.add(T::class.java.getDeclaredConstructor().newInstance() as T)
    }

    fun getComponentsInChildren(go: GameObject, scene: Scene): List<Component> {
        val result = mutableListOf<Component>()
        result.addAll(go.components)
        for (child in scene.childrenOf(go)) result.addAll(getComponentsInChildren(child, scene))
        return result
    }

    fun getComponentsInParent(go: GameObject): List<Component> {
        val result = mutableListOf<Component>()
        var current = go.parent
        while (current != null) {
            result.addAll(current.components)
            current = current.parent
        }
        return result
    }
}
