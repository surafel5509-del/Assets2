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
 * JavaScript behaviour runtime backed by Mozilla Rhino (interpreted mode).
 * Every Script component gets its own scope whose prototype is the shared global scope.
 *
 * Phase 1: all JS execution goes through [ScriptContextFactory], which denies
 * LiveConnect access to host Java classes and enforces a per-frame instruction
 * / time budget. Errors are reported to the console; they never escape into
 * the render loop.
 */
class ScriptSystem(val engine: Engine) : PhysicsWorld.Listener {

    private class Instance(val go: GameObject, val comp: ScriptComponent, val scope: Scriptable) {
        var started = false
        var failed = false
        var lastActive = false
        var fixedAcc = 0f
    }

    private val factory = ScriptContextFactory()
    private var budgetDepth = 0
    private var cx: Context? = null
    private var global: ScriptableObject? = null
    private var ownerThread: Thread? = null
    private val instances = ArrayList<Instance>()
    private val wrappers = HashMap<Long, SObject>()
    private val compiled = HashMap<String, Script>()
    private val moduleCache = HashMap<String, Any?>()
    private val inputApi = SInput(engine)

    val isRunning get() = cx != null

    /** Nests safely: only the outermost call starts a fresh per-frame budget. */
    private inline fun <T> withBudget(block: () -> T): T {
        if (budgetDepth++ == 0) factory.beginBudget()
        try {
            return block()
        } finally {
            if (--budgetDepth == 0) factory.endBudget()
        }
    }

    fun begin() {
        end()
        // Rhino associates Contexts with threads. `enterContext()` returns an
        // already-associated Context without calling our factory's
        // onContextCreated(), which would silently disable the sandbox and the
        // instruction budget. A previous Engine (or a compile-only helper) may
        // have left one on this thread, so detach stale contexts first.
        while (Context.getCurrentContext() != null) {
            try { Context.exit() } catch (e: Exception) { break }
        }
        val c = factory.enterContext()
        cx = c
        ownerThread = Thread.currentThread()
        // Safe standard objects omit Packages/java/com/... and JavaAdapter entirely.
        // Together with ScriptContextFactory's allow-list shutter this closes
        // Rhino's LiveConnect escape hatches to the host file system / classes.
        val g = c.initSafeStandardObjects()
        global = g
        inputApi.sync()
        put(g, "input", inputApi)
        put(g, "time", STime(engine))
        put(g, "scene", SScene(engine, this))
        put(g, "audio", SAudio(engine))
        put(g, "console", SConsole(engine))
        put(g, "__modules", SModules(this))
        withBudget {
            try { c.evaluateString(g, PRELUDE, "prelude", 1, null) }
            catch (e: ScriptBudgetException) { engine.log(2, "prelude: ${e.message}") }
        }
        compiled.clear()
        moduleCache.clear()
        withBudget {
            for (go in engine.scene.objects.toList()) attach(go)
            startPending()
        }
    }

    fun end() {
        val c = cx
        val sameThread = c == null || Thread.currentThread() === ownerThread
        if (c != null && !sameThread) {
            // A Rhino Context is thread-local. It cannot be exited from another
            // thread; keep the state intact so the next begin()/release() on the
            // owner (GL) thread performs the real teardown and fires onStop().
            return
        }
        if (c != null) {
            val current = Context.getCurrentContext()
            if (current === c) {
                withBudget {
                    for (inst in instances.toList()) if (inst.started && !inst.failed) call(inst, "onStop")
                }
                try { Context.exit() } catch (_: Exception) {}
            } else {
                // The context was detached by a newer Engine on this thread;
                // calling Context.exit() here would tear down the wrong one.
                engine.log(1, "Script context already detached; onStop skipped")
            }
        }
        cx = null
        global = null
        ownerThread = null
        instances.clear()
        wrappers.clear()
        compiled.clear()
        moduleCache.clear()
        factory.endBudget()
        budgetDepth = 0
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

    /** Create script instances for a (newly spawned) object and its children. */
    fun attach(go: GameObject): Unit = withBudget {
        val c = cx ?: return@withBudget
        val g = global ?: return@withBudget
        for (comp in go.components) {
            if (comp !is ScriptComponent || comp.script.isBlank()) continue
            val script = compiled[comp.script] ?: compileScript(comp.script) ?: continue
            val scope = c.newObject(g)
            scope.prototype = g
            scope.parentScope = null
            val self = Context.javaToJS(wrap(go), g)
            ScriptableObject.putProperty(scope, "self", self)
            ScriptableObject.putProperty(scope, "transform", self)
            ScriptableObject.putProperty(scope, "gameObject", self)
            applyParams(scope, comp.params)
            val inst = Instance(go, comp, scope)
            try {
                script.exec(c, scope)
                // Inspector params override top-level defaults like `var speed = 5;`
                applyParams(scope, comp.params)
                instances.add(inst)
            } catch (e: ScriptBudgetException) {
                engine.log(2, "${comp.script}: ${e.message}")
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

    /** Loads and compiles a script/blueprint asset by file name (null when missing or broken). */
    private fun compileScript(name: String): Script? {
        val c = cx ?: return null
        val raw = engine.project.readAsset(name)
        val src = if (raw != null && name.endsWith(".bp")) {
            try { com.sengine.engine.blueprint.BlueprintCompiler.compile(com.sengine.engine.blueprint.Blueprint.parse(raw)) }
            catch (e: Exception) { engine.log(2, "Blueprint $name: ${e.message}"); null }
        } else raw
        if (src == null) {
            engine.log(2, "Script not found: $name")
            return null
        }
        return try {
            c.compileString(src, name, 1, null).also { compiled[name] = it }
        } catch (e: RhinoException) {
            engine.log(2, "$name:${e.lineNumber()} ${e.details()}")
            null
        }
    }

    /**
     * CommonJS-style module loader: `var utils = require("Utils.js")`.
     * The file runs once in its own scope with `module`/`exports`; the resulting
     * `module.exports` is cached and shared with every later require.
     */
    fun requireModule(name: String): Any? = withBudget {
        val c = cx ?: return@withBudget null
        val g = global ?: return@withBudget null
        moduleCache[name]?.let { return@withBudget it }
        val raw = engine.project.readAsset(name)
        val src = if (raw != null && name.endsWith(".bp")) {
            try { com.sengine.engine.blueprint.BlueprintCompiler.compile(com.sengine.engine.blueprint.Blueprint.parse(raw)) }
            catch (e: Exception) { engine.log(2, "Blueprint $name: ${e.message}"); null }
        } else raw
        if (src == null) { engine.log(2, "require: module not found: $name"); return@withBudget null }
        val scope = c.newObject(g)
        scope.prototype = g
        scope.parentScope = null
        val exports = c.newObject(g)
        val module = c.newObject(g)
        ScriptableObject.putProperty(module, "exports", exports)
        ScriptableObject.putProperty(scope, "module", module)
        ScriptableObject.putProperty(scope, "exports", exports)
        return@withBudget try {
            c.compileString(src, name, 1, null).exec(c, scope)
            val result = ScriptableObject.getProperty(module, "exports")
            moduleCache[name] = result
            result
        } catch (e: ScriptBudgetException) {
            engine.log(2, "require $name: ${e.message}")
            null
        } catch (e: RhinoException) {
            engine.log(2, "require $name:${e.lineNumber()} ${e.details()}")
            null
        } catch (e: Exception) {
            engine.log(2, "require $name: ${e.message}")
            null
        }
    }

    private fun startPending() {
        for (inst in instances.toList()) {
            if (!inst.started && inst.go.isActiveInHierarchy() && inst.comp.enabled) {
                inst.started = true
                inst.lastActive = true
                call(inst, "onEnable")
                call(inst, "start")
            }
        }
    }

    fun update(dt: Float): Unit = withBudget {
        try { updateInternal(dt) }
        catch (e: ScriptBudgetException) { engine.log(2, "script frame aborted: ${e.message}") }
    }

    private fun updateInternal(dt: Float) {
        if (cx == null) return
        inputApi.sync()
        startPending()
        val dtArg = dt.toDouble()
        val fixedDt = (1f / 60f).toDouble()
        val input = engine.input
        var tapTarget: GameObject? = null
        if (input.tapped) tapTarget = engine.physics.overlapPoint(engine.scene, input.touchX, input.touchY)
        for (inst in instances.toList()) {
            if (inst.failed || !inst.started || inst.go.destroyed) continue
            val active = inst.go.isActiveInHierarchy() && inst.comp.enabled
            // enable / disable transitions (checked even while inactive)
            if (inst.lastActive && !active) {
                inst.lastActive = false
                call(inst, "onDisable")
                continue
            }
            if (!inst.lastActive && active) {
                inst.lastActive = true
                call(inst, "onEnable")
            }
            if (!active) continue
            call(inst, "update", dtArg)
            if (tapTarget != null && tapTarget === inst.go) call(inst, "onTap")
            // fixed-rate hook (1/60 s steps, same cadence as the physics worlds)
            inst.fixedAcc += dt
            var steps = 0
            while (inst.fixedAcc >= 1f / 60f && steps < 3) {
                inst.fixedAcc -= 1f / 60f
                steps++
                call(inst, "onFixedUpdate", fixedDt)
                if (inst.failed) break
            }
        }
        // late update runs after every update (camera work, follow-ups)
        for (inst in instances.toList()) {
            if (inst.failed || !inst.started || inst.go.destroyed) continue
            if (!inst.lastActive) continue
            call(inst, "onLateUpdate", dtArg)
        }
        val g = global ?: return
        val tick = g.get("__tick", g)
        if (tick is Function) {
            try { tick.call(cx, g, g, emptyArray()) }
            catch (e: ScriptBudgetException) { engine.log(2, "timer: ${e.message}") }
            catch (e: RhinoException) {
                engine.log(2, "timer: line ${e.lineNumber()} ${e.details()}")
            } catch (e: Exception) { engine.log(2, "timer: ${e.message}") }
        }
    }

    private fun call(inst: Instance, fname: String, vararg args: Any?): Any? = withBudget {
        val f = inst.scope.get(fname, inst.scope)
        if (f !is Function) return@withBudget null
        return@withBudget try {
            f.call(cx, inst.scope, inst.scope, arrayOf(*args))
        } catch (e: ScriptBudgetException) {
            engine.log(2, "${inst.comp.script} in $fname(): ${e.message}")
            inst.failed = true
            null
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

    fun sendMessage(go: GameObject, fname: String, arg: Any?): Any? = withBudget {
        var result: Any? = null
        for (inst in instances.toList()) {
            if (inst.go === go && !inst.failed) result = call(inst, fname, arg) ?: result
        }
        return@withBudget result
    }

    private fun dispatch(go: GameObject, fname: String, other: GameObject): Unit = withBudget {
        if (cx == null) return@withBudget
        val o = toJs(other)
        for (inst in instances.toList()) {
            if (inst.go === go && inst.started && !inst.failed && inst.comp.enabled) call(inst, fname, o)
        }
    }

    fun onDestroyed(go: GameObject): Unit = withBudget {
        for (inst in instances.toList()) if (inst.go === go) {
            if (inst.started && !inst.failed) call(inst, "onDestroy")
            instances.remove(inst)
        }
        wrappers.remove(go.id)
    }

    override fun onCollisionEnter(a: GameObject, b: GameObject) {
        dispatch(a, "onCollision", b); dispatch(b, "onCollision", a)
    }

    override fun onTriggerEnter(a: GameObject, b: GameObject) {
        dispatch(a, "onTrigger", b); dispatch(b, "onTrigger", a)
    }

    override fun onTriggerExit(a: GameObject, b: GameObject) {
        dispatch(a, "onTriggerExit", b); dispatch(b, "onTriggerExit", a)
    }

    companion object {
        const val PRELUDE = """
function log() { var s = []; for (var i = 0; i < arguments.length; i++) s.push(String(arguments[i])); console.log(s.join(' ')); }
function warn(m) { console.warn(String(m)); }
function error(m) { console.error(String(m)); }
function random(a, b) {
    if (a === undefined) return Math.random();
    if (b === undefined) { b = a; a = 0; }
    return a + Math.random() * (b - a);
}
function randomInt(a, b) {
    if (a === undefined) return Math.floor(Math.random() * 2);
    if (b === undefined) { b = a; a = 0; }
    return Math.floor(a + Math.random() * (b - a + 1));
}
function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function lerp(a, b, t) { return a + (b - a) * t; }
function moveToward(cur, target, maxDelta) { if (Math.abs(target - cur) <= maxDelta) return target; return cur + (target > cur ? 1 : -1) * maxDelta; }
function sign(x) { return x > 0 ? 1 : (x < 0 ? -1 : 0); }
function choose() { return arguments[randomInt(0, arguments.length - 1)]; }
function angleDeg(dx, dy) { return Math.atan2(dy, dx) * 180 / Math.PI; }
function require(name) { return __modules.require(name); }

// ---------------------------------------------------------------- math toolkit
function dist(x1, y1, x2, y2) { var dx = x2 - x1, dy = y2 - y1; return Math.sqrt(dx * dx + dy * dy); }
function dist3(x1, y1, z1, x2, y2, z2) { var dx = x2 - x1, dy = y2 - y1, dz = z2 - z1; return Math.sqrt(dx * dx + dy * dy + dz * dz); }
function smoothstep(a, b, x) { var t = clamp((x - a) / (b - a || 1e-9), 0, 1); return t * t * (3 - 2 * t); }
function mapRange(v, inLo, inHi, outLo, outHi) { return outLo + (outHi - outLo) * ((v - inLo) / ((inHi - inLo) || 1e-9)); }
function wrap(v, lo, hi) { var r = hi - lo; return lo + (((v - lo) % r) + r) % r; }
function damp(a, b, lambda, dt) { return b + (a - b) * Math.exp(-lambda * dt); }
function lerpAngle(a, b, t) {
    var d = ((b - a) % 360 + 540) % 360 - 180;
    return a + d * t;
}
function rotateToward(cur, target, maxDelta) {
    var d = ((target - cur) % 360 + 540) % 360 - 180;
    if (Math.abs(d) <= maxDelta) return target;
    return cur + (d > 0 ? maxDelta : -maxDelta);
}
function randomSign() { return Math.random() < 0.5 ? -1 : 1; }
function randomPick(arr) { return arr[randomInt(0, arr.length - 1)]; }
function assert(cond, msg) { if (!cond) throw new Error(msg || "assertion failed"); }

// ---------------------------------------------------------------- timers
var __timers = [];
function after(sec, fn) { __timers.push({ t: time.time + sec, f: fn, every: 0 }); }
function every(sec, fn) { __timers.push({ t: time.time + sec, f: fn, every: sec }); }
function wait(sec, fn) { after(sec, fn); }
function cancelTimers() { __timers.length = 0; }
/** Runs one callback without letting a broken timer/tween kill the whole tick. */
function __safeCall(fn, label) {
    try { fn(); }
    catch (err) { error(label + ': ' + (err && err.message ? err.message : String(err))); }
}

// ---------------------------------------------------------------- tween engine
var __tweens = [];
var __EASE = {
    linear: function (t) { return t; },
    quadIn: function (t) { return t * t; },
    quadOut: function (t) { return t * (2 - t); },
    quadInOut: function (t) { return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t; },
    cubicOut: function (t) { var f = t - 1; return f * f * f + 1; },
    sineInOut: function (t) { return 0.5 - 0.5 * Math.cos(Math.PI * t); },
    backOut: function (t) { var s = 1.70158; var f = t - 1; return f * f * ((s + 1) * f + s) + 1; },
    bounceOut: function (t) {
        if (t < 1 / 2.75) return 7.5625 * t * t;
        if (t < 2 / 2.75) { t -= 1.5 / 2.75; return 7.5625 * t * t + 0.75; }
        if (t < 2.5 / 2.75) { t -= 2.25 / 2.75; return 7.5625 * t * t + 0.9375; }
        t -= 2.625 / 2.75; return 7.5625 * t * t + 0.984375;
    }
};
/**
 * tween(obj, "x", 10, 0.5, "quadOut", onDone) - animates a numeric property of
 * a scene object (x, y, z, rotation, scaleX, ...) over dur seconds.
 * Returns a handle with .cancel(). Ease: linear, quadIn/Out/InOut, cubicOut,
 * sineInOut, backOut, bounceOut.
 */
function tween(obj, prop, to, dur, ease, onDone) {
    var fn = __EASE[ease || "linear"] || __EASE.linear;
    var from = obj[prop];
    var h = { obj: obj, prop: prop, from: from, to: to, dur: Math.max(dur || 0.0001, 0.0001), t: 0, fn: fn, onDone: onDone, dead: false,
              cancel: function () { this.dead = true; } };
    __tweens.push(h);
    return h;
}
function cancelTweens(obj) {
    for (var i = __tweens.length - 1; i >= 0; i--) {
        if (obj === undefined || __tweens[i].obj === obj) __tweens.splice(i, 1);
    }
}
function __tick() {
  var now = time.time;
  var dt = now - (__tick.last === undefined ? now : __tick.last);
  __tick.last = now;
  for (var i = __timers.length - 1; i >= 0; i--) {
    var tm = __timers[i];
    if (now >= tm.t) {
      if (tm.every > 0) tm.t = now + tm.every; else __timers.splice(i, 1);
      __safeCall(tm.f, 'timer');
    }
  }
  for (var j = __tweens.length - 1; j >= 0; j--) {
    var tw = __tweens[j];
    if (tw.dead || tw.obj === undefined) { __tweens.splice(j, 1); continue; }
    try {
      tw.t += dt;
      var k = Math.min(tw.t / tw.dur, 1);
      var e = tw.fn(k);
      tw.obj[tw.prop] = tw.from + (tw.to - tw.from) * e;
      if (k >= 1) __tweens.splice(j, 1);
      else continue;
      if (tw.onDone) __safeCall(tw.onDone, 'tween callback');
    } catch (err) {
      __tweens.splice(j, 1);
      error('tween: ' + (err && err.message ? err.message : String(err)));
    }
  }
}
"""
    }
}

/** JS bridge for the CommonJS-style module loader (`require("File.js")`). */
class SModules(private val sys: ScriptSystem) {
    fun require(name: String): Any? = sys.requireModule(name)
}
