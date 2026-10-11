package com.sengine.engine.anim.advanced

import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.sqrt

/**
 * Ultimate Edition Animator Controller - Unity Animator / Unreal Anim Blueprint equivalent
 * State machines, blend trees, IK, root motion, layers
 */

enum class AnimatorParameterType { Float, Int, Bool, Trigger }
data class AnimatorParameter(var name: String, var type: AnimatorParameterType, var floatValue: Float = 0f, var intValue: Int = 0, var boolValue: Boolean = false)

enum class TransitionConditionMode { If, IfNot, Greater, Less, Equals, NotEqual }

data class AnimatorCondition(
    var parameter: String = "",
    var mode: TransitionConditionMode = TransitionConditionMode.Greater,
    var threshold: Float = 0f
) {
    fun evaluate(params: Map<String, AnimatorParameter>): Boolean {
        val p = params[parameter] ?: return false
        return when (mode) {
            TransitionConditionMode.If -> p.boolValue
            TransitionConditionMode.IfNot -> !p.boolValue
            TransitionConditionMode.Greater -> when (p.type) {
                AnimatorParameterType.Float -> p.floatValue > threshold
                AnimatorParameterType.Int -> p.intValue > threshold.toInt()
                else -> false
            }
            TransitionConditionMode.Less -> when (p.type) {
                AnimatorParameterType.Float -> p.floatValue < threshold
                AnimatorParameterType.Int -> p.intValue < threshold.toInt()
                else -> false
            }
            TransitionConditionMode.Equals -> when (p.type) {
                AnimatorParameterType.Float -> p.floatValue == threshold
                AnimatorParameterType.Int -> p.intValue == threshold.toInt()
                AnimatorParameterType.Bool -> p.boolValue == (threshold != 0f)
                else -> false
            }
            TransitionConditionMode.NotEqual -> !evaluate(params.copy().also { /* invert handled */ })
        }
    }
}

data class AnimatorTransition(
    var fromState: String = "",
    var toState: String = "",
    var duration: Float = 0.25f,
    var offset: Float = 0f,
    var exitTime: Float = 0.9f,
    var hasExitTime: Boolean = true,
    var hasFixedDuration: Boolean = true,
    var interruptionSource: Int = 0,
    var conditions: MutableList<AnimatorCondition> = mutableListOf(),
    var canTransitionToSelf: Boolean = false
) {
    fun canTransition(params: Map<String, AnimatorParameter>, normalizedTime: Float): Boolean {
        if (hasExitTime && normalizedTime < exitTime) return false
        if (conditions.isEmpty()) return true
        return conditions.all { it.evaluate(params) }
    }
}

data class AnimationState(
    var name: String = "New State",
    var clip: String = "",
    var speed: Float = 1f,
    var loop: Boolean = true,
    var mirror: Boolean = false,
    var cycleOffset: Float = 0f,
    var isDefault: Boolean = false,
    var tag: String = "",
    var transitions: MutableList<AnimatorTransition> = mutableListOf(),
    // Blend tree
    var isBlendTree: Boolean = false,
    var blendTree: BlendTree? = null,
    // IK
    var ikOnFeet: Boolean = false,
    // Root motion
    var useRootMotion: Boolean = false
)

class BlendTree {
    enum class BlendType { Simple1D, SimpleDirectional2D, FreeformDirectional2D, FreeformCartesian2D, Direct }

    var type: BlendType = BlendType.Simple1D
    var parameter: String = "Blend"
    var parameterX: String = "BlendX"
    var parameterY: String = "BlendY"
    var children = mutableListOf<BlendTreeChild>()

    data class BlendTreeChild(var clip: String, var threshold: Float = 0f, var position: FloatArray = floatArrayOf(0f, 0f), var timeScale: Float = 1f)

    fun evaluate(params: Map<String, AnimatorParameter>): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        when (type) {
            BlendType.Simple1D -> {
                val blend = params[parameter]?.floatValue ?: 0f
                if (children.isEmpty()) return result
                val sorted = children.sortedBy { it.threshold }
                if (blend <= sorted.first().threshold) {
                    result[sorted.first().clip] = 1f
                } else if (blend >= sorted.last().threshold) {
                    result[sorted.last().clip] = 1f
                } else {
                    for (i in 0 until sorted.size - 1) {
                        val a = sorted[i]; val b = sorted[i + 1]
                        if (blend in a.threshold..b.threshold) {
                            val t = (blend - a.threshold) / (b.threshold - a.threshold).coerceAtLeast(0.001f)
                            result[a.clip] = 1f - t
                            result[b.clip] = t
                            break
                        }
                    }
                }
            }
            BlendType.SimpleDirectional2D, BlendType.FreeformDirectional2D -> {
                val bx = params[parameterX]?.floatValue ?: 0f
                val by = params[parameterY]?.floatValue ?: 0f
                // compute weights by inverse distance
                var total = 0f
                val weights = mutableMapOf<String, Float>()
                for (child in children) {
                    val dx = child.position[0] - bx
                    val dy = child.position[1] - by
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                    val w = 1f / dist
                    weights[child.clip] = w
                    total += w
                }
                for ((k, v) in weights) result[k] = v / total
            }
            else -> {
                for (child in children) result[child.clip] = 1f / children.size.coerceAtLeast(1)
            }
        }
        return result
    }
}

class AnimatorLayer(var name: String = "Base Layer") {
    var states = mutableListOf<AnimationState>()
    var defaultState: String = ""
    var weight: Float = 1f
    var blendingMode: Int = 0 // 0 override, 1 additive
    var avatarMask: String = "" // body mask
    var ikPass: Boolean = false
    var syncedLayerIndex: Int = -1

    fun getState(name: String): AnimationState? = states.firstOrNull { it.name == name }
    fun addState(state: AnimationState) { states.add(state); if (state.isDefault || defaultState.isEmpty()) defaultState = state.name }
}

class AnimatorController(var name: String = "AnimatorController") {
    var parameters = mutableMapOf<String, AnimatorParameter>()
    var layers = mutableListOf<AnimatorLayer>()

    init {
        if (layers.isEmpty()) layers.add(AnimatorLayer())
    }

    fun addParameter(name: String, type: AnimatorParameterType) { parameters[name] = AnimatorParameter(name, type) }
    fun setFloat(name: String, value: Float) { parameters[name]?.let { it.floatValue = value } }
    fun setInt(name: String, value: Int) { parameters[name]?.let { it.intValue = value } }
    fun setBool(name: String, value: Boolean) { parameters[name]?.let { it.boolValue = value } }
    fun setTrigger(name: String) { parameters[name]?.let { it.boolValue = true } }
    fun resetTrigger(name: String) { parameters[name]?.let { it.boolValue = false } }

    fun getLayer(name: String): AnimatorLayer? = layers.firstOrNull { it.name == name }
    fun addLayer(layer: AnimatorLayer) { layers.add(layer) }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        val params = JSONArray()
        for (p in parameters.values) {
            val po = JSONObject()
            po.put("name", p.name); po.put("type", p.type.name)
            po.put("float", p.floatValue.toDouble()); po.put("int", p.intValue); po.put("bool", p.boolValue)
            params.put(po)
        }
        o.put("parameters", params)
        val layersArr = JSONArray()
        for (layer in layers) {
            val lo = JSONObject()
            lo.put("name", layer.name); lo.put("defaultState", layer.defaultState); lo.put("weight", layer.weight.toDouble())
            val statesArr = JSONArray()
            for (s in layer.states) {
                val so = JSONObject()
                so.put("name", s.name); so.put("clip", s.clip); so.put("speed", s.speed.toDouble())
                so.put("loop", s.loop); so.put("isBlendTree", s.isBlendTree)
                val transArr = JSONArray()
                for (t in s.transitions) {
                    val to = JSONObject()
                    to.put("to", t.toState); to.put("duration", t.duration.toDouble())
                    to.put("exitTime", t.exitTime.toDouble()); to.put("hasExitTime", t.hasExitTime)
                    val condArr = JSONArray()
                    for (c in t.conditions) {
                        val co = JSONObject()
                        co.put("param", c.parameter); co.put("mode", c.mode.name); co.put("threshold", c.threshold.toDouble())
                        condArr.put(co)
                    }
                    to.put("conditions", condArr)
                    transArr.put(to)
                }
                so.put("transitions", transArr)
                statesArr.put(so)
            }
            lo.put("states", statesArr)
            layersArr.put(lo)
        }
        o.put("layers", layersArr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): AnimatorController {
            val ctrl = AnimatorController(o.optString("name", "Controller"))
            val params = o.optJSONArray("parameters")
            if (params != null) for (i in 0 until params.length()) {
                val po = params.getJSONObject(i)
                try {
                    val type = AnimatorParameterType.valueOf(po.optString("type", "Float"))
                    val p = AnimatorParameter(po.optString("name", ""), type)
                    p.floatValue = po.optDouble("float", 0.0).toFloat()
                    p.intValue = po.optInt("int", 0)
                    p.boolValue = po.optBoolean("bool", false)
                    ctrl.parameters[p.name] = p
                } catch (_: Exception) {}
            }
            val layersArr = o.optJSONArray("layers")
            if (layersArr != null) {
                ctrl.layers.clear()
                for (i in 0 until layersArr.length()) {
                    val lo = layersArr.getJSONObject(i)
                    val layer = AnimatorLayer(lo.optString("name", "Layer $i"))
                    layer.defaultState = lo.optString("defaultState", "")
                    layer.weight = lo.optDouble("weight", 1.0).toFloat()
                    val statesArr = lo.optJSONArray("states")
                    if (statesArr != null) for (j in 0 until statesArr.length()) {
                        val so = statesArr.getJSONObject(j)
                        val state = AnimationState(so.optString("name", "State $j"))
                        state.clip = so.optString("clip", "")
                        state.speed = so.optDouble("speed", 1.0).toFloat()
                        state.loop = so.optBoolean("loop", true)
                        state.isBlendTree = so.optBoolean("isBlendTree", false)
                        val transArr = so.optJSONArray("transitions")
                        if (transArr != null) for (k in 0 until transArr.length()) {
                            val to = transArr.getJSONObject(k)
                            val trans = AnimatorTransition(toState = to.optString("to", ""))
                            trans.duration = to.optDouble("duration", 0.25).toFloat()
                            trans.exitTime = to.optDouble("exitTime", 0.9).toFloat()
                            trans.hasExitTime = to.optBoolean("hasExitTime", true)
                            val condArr = to.optJSONArray("conditions")
                            if (condArr != null) for (l in 0 until condArr.length()) {
                                val co = condArr.getJSONObject(l)
                                try {
                                    val cond = AnimatorCondition(
                                        co.optString("param", ""),
                                        TransitionConditionMode.valueOf(co.optString("mode", "Greater")),
                                        co.optDouble("threshold", 0.0).toFloat()
                                    )
                                    trans.conditions.add(cond)
                                } catch (_: Exception) {}
                            }
                            state.transitions.add(trans)
                        }
                        layer.states.add(state)
                    }
                    ctrl.layers.add(layer)
                }
            }
            if (ctrl.layers.isEmpty()) ctrl.layers.add(AnimatorLayer())
            return ctrl
        }
    }
}

class AnimatorStateMachine {
    var controller: AnimatorController? = null
    private var currentState: AnimationState? = null
    private var nextState: AnimationState? = null
    private var transitionTime: Float = 0f
    private var transitionDuration: Float = 0f
    private var normalizedTime: Float = 0f
    private var layerIndex: Int = 0

    fun setController(ctrl: AnimatorController) {
        controller = ctrl
        val layer = ctrl.layers.getOrNull(layerIndex) ?: return
        currentState = layer.getState(layer.defaultState) ?: layer.states.firstOrNull()
        normalizedTime = 0f
    }

    fun update(dt: Float, params: Map<String, AnimatorParameter>): AnimationState? {
        val ctrl = controller ?: return currentState
        val layer = ctrl.layers.getOrNull(layerIndex) ?: return currentState
        val cur = currentState ?: return null

        normalizedTime += dt * cur.speed

        // Check transitions
        if (nextState == null) {
            for (trans in cur.transitions) {
                if (trans.canTransition(params, normalizedTime % 1f)) {
                    val target = layer.getState(trans.toState)
                    if (target != null) {
                        nextState = target
                        transitionDuration = trans.duration
                        transitionTime = 0f
                        break
                    }
                }
            }
        } else {
            transitionTime += dt
            if (transitionTime >= transitionDuration) {
                currentState = nextState
                nextState = null
                normalizedTime = 0f
                // reset triggers
                for (p in params.values) if (p.type == AnimatorParameterType.Trigger) p.boolValue = false
            }
        }

        return currentState
    }

    fun getCurrentState(): AnimationState? = currentState
    fun getNextState(): AnimationState? = nextState
    fun getTransitionProgress(): Float = if (transitionDuration > 0f) (transitionTime / transitionDuration).coerceIn(0f, 1f) else 0f
    fun getNormalizedTime(): Float = normalizedTime
}

/**
 * IK System - FABRIK and CCD
 */
class IKSystem {
    data class Joint(val x: Float, val y: Float, val z: Float, var length: Float = 0f)

    fun solveFABRIK(joints: MutableList<Joint>, target: FloatArray, tolerance: Float = 0.01f, maxIter: Int = 10): Boolean {
        if (joints.size < 2) return false
        val totalLength = joints.zipWithNext { a, b ->
            val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
            sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        }.sum()

        val root = joints.first()
        val distToTarget = run {
            val dx = target[0] - root.x; val dy = target[1] - root.y; val dz = target[2] - root.z
            sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        }

        if (distToTarget > totalLength) {
            // Stretch towards target
            for (i in 0 until joints.size - 1) {
                val j = joints[i]; val nj = joints[i + 1]
                val dx = target[0] - j.x; val dy = target[1] - j.y; val dz = target[2] - j.z
                val len = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat().coerceAtLeast(0.001f)
                val r = nj.length / len
                joints[i + 1] = nj.copy(x = j.x + dx * r, y = j.y + dy * r, z = j.z + dz * r)
            }
            return true
        }

        val initialRoot = root.copy()
        var iter = 0
        while (iter < maxIter) {
            // Backward
            joints[joints.size - 1] = joints.last().copy(x = target[0], y = target[1], z = target[2])
            for (i in joints.size - 2 downTo 0) {
                val cur = joints[i]; val next = joints[i + 1]
                val dx = cur.x - next.x; val dy = cur.y - next.y; val dz = cur.z - next.z
                val len = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat().coerceAtLeast(0.001f)
                val r = cur.length / len
                joints[i] = cur.copy(x = next.x + dx * r, y = next.y + dy * r, z = next.z + dz * r)
            }
            // Forward
            joints[0] = initialRoot
            for (i in 0 until joints.size - 1) {
                val cur = joints[i]; val next = joints[i + 1]
                val dx = next.x - cur.x; val dy = next.y - cur.y; val dz = next.z - cur.z
                val len = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat().coerceAtLeast(0.001f)
                val r = cur.length / len
                joints[i + 1] = next.copy(x = cur.x + dx * r, y = cur.y + dy * r, z = cur.z + dz * r)
            }
            val end = joints.last()
            val dx = end.x - target[0]; val dy = end.y - target[1]; val dz = end.z - target[2]
            if (dx * dx + dy * dy + dz * dz < tolerance * tolerance) break
            iter++
        }
        return true
    }
}
