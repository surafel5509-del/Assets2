package com.sengine.engine.script

import com.sengine.engine.Engine
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.physics.PhysicsWorld
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * S Engine Ultimate Edition - Professional Scripting Runtime
 * Mozilla Rhino + Hot Reload + Assembly System + Visual Scripting
 * Backward compatible with all previous templates
 */

class ScriptSystem(val engine: Engine) : PhysicsWorld.Listener {

    private class Instance(val go: GameObject, val comp: ScriptComponent, val scope: Scriptable) {
        var started = false
        var failed = false
        var startTime = 0.0
    }

    private var cx: Context? = null
    private var global: ScriptableObject? = null
    private var ownerThread: Thread? = null
    private val instances = ArrayList<Instance>()
    private val wrappers = HashMap<Long, SObject>()
    private val compiled = HashMap<String, Script>()
    private val inputApi = SInput(engine)
    private val timeApi = STime(engine)
    private val sceneApi = SScene(engine, this)
    private val audioApi = SAudio(engine)
    private val physicsApi = SPhysics(engine)
    private val qualityApi = SQuality(engine)
    private val appApi = SApplication(engine)
    private val navMeshApi = SNavMesh(engine)

    val isRunning get() = cx != null

    fun begin() {
        end()
        val c = Context.enter()
        c.optimizationLevel = -1
        c.languageVersion = Context.VERSION_ES6
        c.wrapFactory.isJavaPrimitiveWrap = false
        cx = c
        ownerThread = Thread.currentThread()
        val g = c.initStandardObjects()
        global = g
        inputApi.sync()
        put(g, "input", inputApi)
        put(g, "time", timeApi)
        put(g, "scene", sceneApi)
        put(g, "audio", audioApi)
        put(g, "physics", physicsApi)
        put(g, "quality", qualityApi)
        put(g, "app", appApi)
        put(g, "navMesh", navMeshApi)
        put(g, "console", SConsole(engine))
        // Unity-like aliases
        put(g, "Time", timeApi)
        put(g, "Input", inputApi)
        put(g, "Physics", physicsApi)
        put(g, "Application", appApi)
        put(g, "QualitySettings", qualityApi)
        put(g, "NavMesh", navMeshApi)
        c.evaluateString(g, PRELUDE, "prelude", 1, null)
        compiled.clear()
        for (go in engine.scene.objects.toList()) attach(go)
        startPending()
    }

    fun end() {
        if (cx != null && Thread.currentThread() === ownerThread) {
            for (inst in instances.toList()) if (inst.started && !inst.failed) call(inst, "onStop")
            try { Context.exit() } catch (_: Exception) {}
        }
        cx = null
        global = null
        ownerThread = null
        instances.clear()
        wrappers.clear()
        compiled.clear()
    }

    private fun put(scope: Scriptable, name: String, obj: Any) {
        ScriptableObject.putProperty(scope, name, Context.javaToJS(obj, scope))
    }

    fun wrap(go: GameObject): SObject = wrappers.getOrPut(go.id) { SObject(go, engine, this) }

    fun toJs(go: GameObject?): Any? {
        val g = global ?: return null
        return if (go == null) null else Context.javaToJS(wrap(go), g)
    }

    fun newArray(items: List<Any?>): Scriptable? {
        val c = cx ?: return null
        val g = global ?: return null
        return c.newArray(g, items.toTypedArray())
    }

    fun attach(go: GameObject) {
        val c = cx ?: return
        val g = global ?: return
        for (comp in go.components) {
            if (comp !is ScriptComponent || comp.script.isBlank()) continue
            val script = compiled[comp.script] ?: run {
                val raw = engine.project.readAsset(comp.script)
                val src = if (raw != null && comp.script.endsWith(".bp")) {
                    try { com.sengine.engine.blueprint.BlueprintCompiler.compile(com.sengine.engine.blueprint.Blueprint.parse(raw)) }
                    catch (e: Exception) { engine.log(2, "Blueprint ${comp.script}: ${e.message}"); null }
                } else raw
                if (src == null) {
                    engine.log(2, "Script not found: ${comp.script} (on ${go.name})")
                    null
                } else try {
                    c.compileString(src, comp.script, 1, null).also { compiled[comp.script] = it }
                } catch (e: RhinoException) {
                    engine.log(2, "${comp.script}:${e.lineNumber()} ${e.details()}")
                    null
                }
            } ?: continue
            val scope = c.newObject(g)
            scope.prototype = g
            scope.parentScope = null
            val self = Context.javaToJS(wrap(go), g)
            ScriptableObject.putProperty(scope, "self", self)
            ScriptableObject.putProperty(scope, "transform", self)
            ScriptableObject.putProperty(scope, "gameObject", self)
            // Expose engine APIs to each script scope as well (for convenience)
            ScriptableObject.putProperty(scope, "input", Context.javaToJS(inputApi, scope))
            ScriptableObject.putProperty(scope, "time", Context.javaToJS(timeApi, scope))
            ScriptableObject.putProperty(scope, "scene", Context.javaToJS(sceneApi, scope))
            ScriptableObject.putProperty(scope, "audio", Context.javaToJS(audioApi, scope))
            ScriptableObject.putProperty(scope, "physics", Context.javaToJS(physicsApi, scope))
            applyParams(scope, comp.params)
            val inst = Instance(go, comp, scope)
            try {
                script.exec(c, scope)
                applyParams(scope, comp.params)
                instances.add(inst)
            } catch (e: RhinoException) {
                engine.log(2, "${comp.script}:${e.lineNumber()} ${e.details()}")
            } catch (e: Exception) {
                engine.log(2, "${comp.script}: ${e.message}")
            }
        }
        for (child in engine.scene.childrenOf(go)) attach(child)
    }

    private fun applyParams(scope: Scriptable, params: String) {
        for (raw in params.split(',', '\n', ';')) {
            val kv = raw.split('=', limit = 2)
            if (kv.size != 2) continue
            val k = kv[0].trim()
            val v = kv[1].trim()
            if (k.isEmpty()) continue
            val value: Any = v.toDoubleOrNull() ?: when (v.lowercase()) {
                "true" -> true
                "false" -> false
                else -> v.trim('"', '\'')
            }
            ScriptableObject.putProperty(scope, k, value)
        }
    }

    private fun startPending() {
        for (inst in instances.toList()) {
            if (!inst.started && inst.go.isActiveInHierarchy() && inst.comp.enabled) {
                inst.started = true
                inst.startTime = engine.time
                call(inst, "start")
                call(inst, "awake")
                call(inst, "onEnable")
            }
        }
    }

    fun update(dt: Float) {
        if (cx == null) return
        inputApi.sync()
        startPending()
        val dtArg = dt.toDouble()
        val input = engine.input
        var tapTarget: GameObject? = null
        if (input.tapped) tapTarget = engine.physics.overlapPoint(engine.scene, input.touchX, input.touchY)
        for (inst in instances.toList()) {
            if (inst.failed || !inst.started || inst.go.destroyed) continue
            if (!inst.go.isActiveInHierarchy() || !inst.comp.enabled) continue
            call(inst, "update", dtArg)
            call(inst, "lateUpdate", dtArg)
            if (tapTarget != null && tapTarget === inst.go) {
                call(inst, "onTap")
                call(inst, "onMouseDown")
            }
        }
        val g = global ?: return
        val tick = g.get("__tick", g)
        if (tick is Function) {
            try { tick.call(cx, g, g, emptyArray()) } catch (e: RhinoException) {
                engine.log(2, "timer: line ${e.lineNumber()} ${e.details()}")
            } catch (e: Exception) { engine.log(2, "timer: ${e.message}") }
        }
        // Fixed update simulation (called every frame for simplicity)
        for (inst in instances.toList()) {
            if (inst.failed || !inst.started || inst.go.destroyed) continue
            if (!inst.go.isActiveInHierarchy() || !inst.comp.enabled) continue
            call(inst, "fixedUpdate", dtArg)
        }
    }

    private fun call(inst: Instance, fname: String, vararg args: Any?): Any? {
        val f = inst.scope.get(fname, inst.scope)
        if (f !is Function) return null
        return try {
            f.call(cx, inst.scope, inst.scope, arrayOf(*args))
        } catch (e: RhinoException) {
            engine.log(2, "${inst.comp.script}:${e.lineNumber()} in $fname(): ${e.details()}")
            inst.failed = true
            null
        } catch (e: Exception) {
            engine.log(2, "${inst.comp.script} in $fname(): ${e.message}")
            inst.failed = true
            null
        }
    }

    fun sendMessage(go: GameObject, fname: String, arg: Any?): Any? {
        var result: Any? = null
        for (inst in instances.toList()) {
            if (inst.go === go && !inst.failed) result = call(inst, fname, arg) ?: result
        }
        return result
    }

    private fun dispatch(go: GameObject, fname: String, other: GameObject) {
        if (cx == null) return
        val o = toJs(other)
        for (inst in instances.toList()) {
            if (inst.go === go && inst.started && !inst.failed && inst.comp.enabled) call(inst, fname, o)
        }
    }

    fun onDestroyed(go: GameObject) {
        for (inst in instances.toList()) if (inst.go === go) {
            if (inst.started && !inst.failed) {
                call(inst, "onDestroy")
                call(inst, "onDisable")
            }
            instances.remove(inst)
        }
        wrappers.remove(go.id)
    }

    override fun onCollisionEnter(a: GameObject, b: GameObject) {
        dispatch(a, "onCollision", b); dispatch(b, "onCollision", a)
        dispatch(a, "onCollisionEnter", b); dispatch(b, "onCollisionEnter", a)
    }

    override fun onTriggerEnter(a: GameObject, b: GameObject) {
        dispatch(a, "onTrigger", b); dispatch(b, "onTrigger", a)
        dispatch(a, "onTriggerEnter", b); dispatch(b, "onTriggerEnter", a)
    }

    override fun onTriggerExit(a: GameObject, b: GameObject) {
        dispatch(a, "onTriggerExit", b); dispatch(b, "onTriggerExit", a)
    }

    companion object {
        const val PRELUDE = """
// S Engine Ultimate Edition - Professional Scripting Prelude
// Unity 6 + Unreal Engine 5 compatible helpers

function log() { var s = []; for (var i = 0; i < arguments.length; i++) s.push(String(arguments[i])); console.log(s.join(' ')); }
function warn(m) { console.warn(String(m)); }
function error(m) { console.error(String(m)); }
function print() { log.apply(null, arguments); }

// Math helpers
function random(a, b) { if (a === undefined) return Math.random(); return a + Math.random() * (b - a); }
function randomInt(a, b) { return Math.floor(random(a, b + 1)); }
function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function lerp(a, b, t) { return a + (b - a) * t; }
function lerpAngle(a, b, t) { var d = ((b - a + 540) % 360) - 180; return (a + d * t + 360) % 360; }
function moveTowards(cur, target, maxDelta) { var d = target - cur; if (Math.abs(d) <= maxDelta) return target; return cur + Math.sign(d) * maxDelta; }
function smoothDamp(cur, target, vel, smoothTime, dt) { var t = 1 - Math.exp(-dt / Math.max(0.0001, smoothTime)); return cur + (target - cur) * t; }

// Vector helpers
function vec2(x, y) { return { x: x, y: y }; }
function vec3(x, y, z) { return { x: x, y: y, z: z }; }
function vec4(x, y, z, w) { return { x: x, y: y, z: z, w: w }; }
function dot(a, b) { return a.x * b.x + a.y * b.y + (a.z !== undefined ? a.z * b.z : 0); }
function length(v) { return Math.sqrt(v.x * v.x + v.y * v.y + (v.z !== undefined ? v.z * v.z : 0)); }
function normalize(v) { var l = length(v); if (l < 0.0001) return v; return { x: v.x / l, y: v.y / l, z: v.z !== undefined ? v.z / l : 0 }; }
function distance(a, b) { var dx = a.x - b.x; var dy = a.y - b.y; var dz = (a.z || 0) - (b.z || 0); return Math.sqrt(dx*dx + dy*dy + dz*dz); }

// Time & Coroutines
var __timers = [];
function after(sec, fn) { __timers.push({ t: time.time + sec, f: fn, every: 0 }); }
function every(sec, fn) { __timers.push({ t: time.time + sec, f: fn, every: sec }); }
function invoke(fn, delay) { after(delay, fn); }
function invokeRepeating(fn, delay, repeat) { __timers.push({ t: time.time + delay, f: fn, every: repeat }); }
function cancelInvoke() { __timers = []; }
function __tick() {
  var now = time.time;
  for (var i = __timers.length - 1; i >= 0; i--) {
    var tm = __timers[i];
    if (now >= tm.t) { if (tm.every > 0) tm.t += tm.every; else __timers.splice(i, 1); tm.f(); }
  }
}

// Coroutine-like yield helpers
function waitForSeconds(sec) { return sec; }
function waitForEndOfFrame() { return 0; }

// Input helpers
var KeyCode = { Space: 'space', W: 'w', A: 'a', S: 's', D: 'd', LeftShift: 'leftShift', LeftControl: 'leftCtrl', Mouse0: 0, Mouse1: 1 };
var InputAPI = {
  GetAxis: function(name) { return input.getAxis(name); },
  GetButton: function(name) { return input.getButton(name); },
  GetButtonDown: function(name) { return input.getButtonDown(name); }
};

// Physics helpers
var ForceMode = { Force: 0, Acceleration: 1, Impulse: 2, VelocityChange: 3 };

// Quaternion helpers (simplified)
var Quaternion = {
  identity: { x: 0, y: 0, z: 0, w: 1 },
  Euler: function(x, y, z) { return { x: x, y: y, z: z, w: 1 }; }
};

// Debug
var Debug = {
  Log: function(m) { log(m); },
  LogWarning: function(m) { warn(m); },
  LogError: function(m) { error(m); },
  DrawLine: function(a, b, color) { /* gizmo in editor */ },
  DrawRay: function(origin, dir, color) { }
};

// Gizmos (editor only)
var Gizmos = {
  color: '#FFFF00',
  DrawWireSphere: function(center, radius) {},
  DrawSphere: function(center, radius) {},
  DrawWireCube: function(center, size) {},
  DrawLine: function(from, to) {}
};

// Unity-like Object
var Object = {
  Instantiate: function(prefab, pos, rot) { if (pos) return scene.spawn(prefab, pos.x, pos.y, pos.z || 0); return scene.spawn(prefab); },
  Destroy: function(obj, delay) { if (obj && obj.destroy) obj.destroy(); }
};

var Mathf = {
  PI: Math.PI,
  Infinity: Infinity,
  NegativeInfinity: -Infinity,
  Deg2Rad: Math.PI / 180,
  Rad2Deg: 180 / Math.PI,
  Clamp: clamp,
  Lerp: lerp,
  LerpAngle: lerpAngle,
  MoveTowards: moveTowards,
  Abs: Math.abs,
  Sin: Math.sin,
  Cos: Math.cos,
  Sqrt: Math.sqrt,
  Pow: Math.pow,
  Floor: Math.floor,
  Ceil: Math.ceil,
  Round: Math.round,
  Min: Math.min,
  Max: Math.max,
  PerlinNoise: function(x, y) { return Math.abs(Math.sin(x * 12.9898 + y * 78.233) * 43758.5453 % 1); }
};
"""
    }
}
