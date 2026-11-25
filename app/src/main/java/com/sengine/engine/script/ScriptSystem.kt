package com.sengine.engine.script

import com.sengine.engine.Engine
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.physics.PhysicsWorld
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Thrown by the instruction observer when a script callback runs too long. */
class ScriptBudgetExceeded(val limit: Int) : Error("Script instruction budget exceeded ($limit instructions)")

/**
 * Rhino factory that puts a hard instruction cap on every script callback.
 * A runaway `while(true)` is stopped instead of freezing the game thread;
 * the offending script is disabled and reported in the console.
 */
class ScriptBudgetFactory : ContextFactory() {
    private class Frame(val limit: Int) { var used = 0 }
    private val frames = ArrayList<Frame>()

    @Volatile var frameInstructions = 0
        private set
    /** Used when a Rhino context is reused without an explicit call frame. */
    @Volatile var defaultLimit = 250_000

    override fun makeContext(): Context {
        val c = super.makeContext()
        c.instructionObserverThreshold = 1000
        return c
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        frameInstructions += instructionCount
        val f = frames.lastOrNull()
        if (f != null) {
            f.used += instructionCount
            if (f.used > f.limit) {
                f.used = 0
                throw ScriptBudgetExceeded(f.limit)
            }
        } else if (frameInstructions > defaultLimit * 4L) {
            // Safety net: a reused/external context still cannot freeze the game.
            frameInstructions = 0
            throw ScriptBudgetExceeded(defaultLimit)
        }
    }

    /**
     * Enters a context owned by this factory. Rhino associates one Context per
     * thread; if another factory's context is still attached (e.g. a test used
     * Context.enter() directly), release it first so the budget cannot be
     * bypassed.
     */
    fun ensureContext(): Context {
        var guard = 0
        while (guard++ < 8) {
            val current = Context.getCurrentContext() ?: break
            if (current.factory === this) break
            try { Context.exit() } catch (_: Throwable) { break }
        }
        val c = enterContext()
        if (c.instructionObserverThreshold <= 0) c.instructionObserverThreshold = 1000
        return c
    }

    fun enterCall(limit: Int) { frames.add(Frame(limit.coerceAtLeast(1000))) }
    fun exitCall() { if (frames.isNotEmpty()) frames.removeAt(frames.size - 1) }
    fun resetFrame() { frameInstructions = 0 }
}

/** One shared factory for every engine instance on a thread. */
object ScriptBudget {
    val factory = ScriptBudgetFactory()
}

/**
 * JavaScript behaviour runtime backed by Mozilla Rhino (interpreted mode).
 * Every Script component gets its own scope whose prototype is the shared global scope.
 */
class ScriptSystem(val engine: Engine) : PhysicsWorld.Listener {

    private class Instance(val go: GameObject, val comp: ScriptComponent, val scope: Scriptable) {
        var started = false
        var failed = false
        var lastActive = false
        var fixedAcc = 0f
    }

    private var cx: Context? = null
    private var global: ScriptableObject? = null
    private var ownerThread: Thread? = null
    private val instances = ArrayList<Instance>()
    private val wrappers = HashMap<Long, SObject>()
    private val compiled = HashMap<String, Script>()
    private val moduleCache = HashMap<String, Any?>()
    private val inputApi = SInput(engine)
    private val budgetFactory = ScriptBudget.factory

    /** Decoupled event bus shared by scripts and engine systems. */
    val eventBus = EventBus()
    /** Smoothed milliseconds spent in each script asset (profiler). */
    val timings = LinkedHashMap<String, Float>()
    /** Scope of the script callback currently executing (state-machine dispatch). */
    var currentScope: Scriptable? = null
        private set
    private class Fsm(var state: String, var previous: String, var elapsed: Float)
    private val fsms = HashMap<Scriptable, Fsm>()

    val frameInstructions: Int get() = budgetFactory.frameInstructions
    val isRunning get() = cx != null

    fun begin() {
        end()
        budgetFactory.defaultLimit = engine.settings.scriptBudget
        val c = budgetFactory.ensureContext()
        c.optimizationLevel = -1
        c.languageVersion = Context.VERSION_ES6
        // return Java strings / numbers / booleans as native JS values
        c.wrapFactory.isJavaPrimitiveWrap = false
        cx = c
        ownerThread = Thread.currentThread()
        val g = c.initStandardObjects()
        global = g
        inputApi.sync()
        put(g, "input", inputApi)
        put(g, "time", STime(engine))
        put(g, "scene", SScene(engine, this))
        put(g, "audio", SAudio(engine))
        put(g, "console", SConsole(engine))
        put(g, "__modules", SModules(this))
        put(g, "__events", SEvents(engine, this))
        put(g, "__rng", SRandomBridge(engine))
        put(g, "__state", SState(this))
        put(g, "camera", SCamera(engine, this))
        put(g, "physics", SPhysics(engine, this))
        put(g, "soft", SSoft(engine, this))
        put(g, "game", SGame(engine, this))
        put(g, "__studio", SStudio(engine, this))
        budgetFactory.enterCall(2_000_000)
        try { c.evaluateString(g, PRELUDE, "prelude", 1, null) }
        catch (e: Throwable) { engine.log(2, "prelude: ${e.message}") }
        finally { budgetFactory.exitCall() }
        compiled.clear()
        moduleCache.clear()
        eventBus.clear()
        timings.clear()
        fsms.clear()
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
        currentScope = null
        instances.clear()
        wrappers.clear()
        compiled.clear()
        moduleCache.clear()
        eventBus.clear()
        fsms.clear()
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
    fun attach(go: GameObject) {
        val c = cx ?: return
        val g = global ?: return
        for (comp in go.components) {
            if (comp !is ScriptComponent || comp.script.isBlank()) continue
            val script = compiled[comp.script] ?: compileScript(comp.script) ?: continue
            val scope = c.newObject(g)
            scope.prototype = g
            scope.parentScope = null
            val self = Context.javaToJS(wrap(go), g)
            ScriptableObject.putProperty(scope, "self", self)
            ScriptableObject.putProperty(scope, "transform", self)
            // per-instance durable bag that survives hot reload
            ScriptableObject.putProperty(scope, "persist", c.newObject(g))
            ScriptableObject.putProperty(scope, "gameObject", self)
            applyParams(scope, comp.params)
            val inst = Instance(go, comp, scope)
            budgetFactory.enterCall(engine.settings.scriptBudget)
            try {
                script.exec(c, scope)
                // Inspector params override top-level defaults like `var speed = 5;`
                applyParams(scope, comp.params)
                instances.add(inst)
            } catch (e: ScriptBudgetExceeded) {
                engine.log(2, "${comp.script}: ${e.message} — script disabled")
                inst.failed = true
            } catch (e: RhinoException) {
                engine.log(2, "${comp.script}:${e.lineNumber()} ${e.details()}")
            } catch (e: Exception) {
                engine.log(2, "${comp.script}: ${e.message}")
            } finally {
                budgetFactory.exitCall()
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
    fun requireModule(name: String): Any? {
        val c = cx ?: return null
        val g = global ?: return null
        moduleCache[name]?.let { return it }
        val raw = engine.project.readAsset(name)
        val src = if (raw != null && name.endsWith(".bp")) {
            try { com.sengine.engine.blueprint.BlueprintCompiler.compile(com.sengine.engine.blueprint.Blueprint.parse(raw)) }
            catch (e: Exception) { engine.log(2, "Blueprint $name: ${e.message}"); null }
        } else raw
        if (src == null) { engine.log(2, "require: module not found: $name"); return null }
        val scope = c.newObject(g)
        scope.prototype = g
        scope.parentScope = null
        val exports = c.newObject(g)
        val module = c.newObject(g)
        ScriptableObject.putProperty(module, "exports", exports)
        ScriptableObject.putProperty(scope, "module", module)
        ScriptableObject.putProperty(scope, "exports", exports)
        budgetFactory.enterCall(engine.settings.scriptBudget)
        return try {
            c.compileString(src, name, 1, null).exec(c, scope)
            val result = ScriptableObject.getProperty(module, "exports")
            moduleCache[name] = result
            result
        } catch (e: RhinoException) {
            engine.log(2, "require $name:${e.lineNumber()} ${e.details()}")
            null
        } catch (e: Exception) {
            engine.log(2, "require $name: ${e.message}")
            null
        } finally {
            budgetFactory.exitCall()
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

    fun update(dt: Float) {
        if (cx == null) return
        budgetFactory.resetFrame()
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
            budgetFactory.enterCall(engine.settings.scriptBudget)
            try { tick.call(cx, g, g, emptyArray()) }
            catch (e: ScriptBudgetExceeded) { engine.log(2, "timers/coroutines: ${e.message}") }
            catch (e: RhinoException) { engine.log(2, "timer: line ${e.lineNumber()} ${e.details()}") }
            catch (e: Throwable) { engine.log(2, "timer: ${e.message}") }
            finally { budgetFactory.exitCall() }
        }
    }

    private fun call(inst: Instance, fname: String, vararg args: Any?): Any? {
        val f = inst.scope.get(fname, inst.scope)
        if (f !is Function) return null
        val t0 = System.nanoTime()
        val prevScope = currentScope
        currentScope = inst.scope
        budgetFactory.enterCall(engine.settings.scriptBudget)
        return try {
            f.call(cx, inst.scope, inst.scope, arrayOf(*args))
        } catch (e: ScriptBudgetExceeded) {
            engine.log(2, "${inst.comp.script} in $fname(): ${e.message} — script disabled")
            inst.failed = true
            null
        } catch (e: RhinoException) {
            engine.log(2, "${inst.comp.script}:${e.lineNumber()} in $fname(): ${e.details()}")
            inst.failed = true
            null
        } catch (e: Throwable) {
            engine.log(2, "${inst.comp.script} in $fname(): ${e.message}")
            inst.failed = true
            null
        } finally {
            budgetFactory.exitCall()
            currentScope = prevScope
            val ms = (System.nanoTime() - t0) / 1e6f
            val key = inst.comp.script
            timings[key] = (timings[key] ?: 0f) * 0.9f + ms * 0.1f
        }
    }

    /** Invokes an arbitrary JS function (event handlers, coroutine predicates). */
    fun invokeFunction(fn: Any?, vararg args: Any?): Any? {
        if (fn !is Function) return null
        val c = cx ?: return null
        val g = global ?: return null
        budgetFactory.enterCall(engine.settings.scriptBudget)
        return try {
            fn.call(c, g, currentScope ?: g, arrayOf(*args))
        } catch (e: ScriptBudgetExceeded) {
            engine.log(2, "event handler: ${e.message}"); null
        } catch (e: RhinoException) {
            engine.log(2, "event handler: line ${e.lineNumber()} ${e.details()}"); null
        } catch (e: Throwable) {
            engine.log(2, "event handler: ${e.message}"); null
        } finally { budgetFactory.exitCall() }
    }

    /** Engine-side event emission (also wakes coroutines waiting for the event). */
    fun emitEvent(name: String, arg: Any? = null): Int {
        val g = global
        if (g != null) {
            val sig = g.get("__fiber_signal", g)
            if (sig is Function) invokeFunction(sig, name, arg)
        }
        return eventBus.emit(name, arg)
    }

    // ---------------------------------------------------------------- state machines
    fun fsmState(): String = currentScope?.let { fsms[it]?.state } ?: ""
    fun fsmPrevious(): String = currentScope?.let { fsms[it]?.previous } ?: ""
    fun fsmElapsed(): Double = (currentScope?.let { fsms[it]?.elapsed } ?: 0f).toDouble()
    fun fsmIs(name: String): Boolean = fsmState() == name
    fun fsmSet(name: String) {
        val scope = currentScope ?: return
        val fsm = fsms.getOrPut(scope) { Fsm("", "", 0f) }
        if (fsm.state == name) return
        val old = scope.get(fsm.state + "_exit", scope)
        if (old is Function) invokeFunction(old)
        fsm.previous = fsm.state
        fsm.state = name
        fsm.elapsed = 0f
        val enter = scope.get(name + "_enter", scope)
        if (enter is Function) invokeFunction(enter)
    }
    fun fsmUpdate(dt: Double) {
        val scope = currentScope ?: return
        val fsm = fsms.getOrPut(scope) { Fsm("", "", 0f) }
        fsm.elapsed += dt.toFloat()
        val fn = scope.get(fsm.state + "_update", scope)
        if (fn is Function) invokeFunction(fn, dt)
    }

    // ---------------------------------------------------------------- hot reload
    /**
     * Recompiles every script asset in place while play mode keeps running.
     * A script's `persist` object is carried over, so health / score / state
     * survive a reload; `onHotReload` is called instead of `start` for scripts
     * that were already running. Returns the number of attached instances.
     */
    fun hotReload(): Int {
        if (cx == null || global == null) return 0
        data class Saved(val scope: Scriptable, val persist: Any?, val started: Boolean, val active: Boolean, val key: String)
        val saved = instances.map { inst ->
            val p = inst.scope.get("persist", inst.scope)
            Saved(inst.scope, if (p is Scriptable && p !== Scriptable.NOT_FOUND) p else null,
                inst.started, inst.lastActive, "${inst.go.id}:${inst.comp.script}")
        }
        compiled.clear()
        moduleCache.clear()
        instances.clear()
        for (go in engine.scene.objects.toList()) attach(go)
        for (inst in instances) {
            val key = "${inst.go.id}:${inst.comp.script}"
            val old = saved.firstOrNull { it.key == key } ?: continue
            if (old.persist != null) ScriptableObject.putProperty(inst.scope, "persist", old.persist)
            if (old.started) {
                inst.started = true
                inst.lastActive = old.active
                call(inst, "onHotReload")
            }
        }
        startPending()
        engine.log(0, "↻ Hot reload: ${instances.size} script instance(s)")
        return instances.size
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
// Deterministic RNG (seeded from Project Settings -> replay-safe simulation)
function random(a, b) { var r = __rng.next(); if (a === undefined) return r; return a + r * (b - a); }
function randomInt(a, b) { return Math.floor(random(a, b + 1)); }
function setRandomSeed(seed) { return __rng.seed(seed); }
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
function randomSign() { return random() < 0.5 ? -1 : 1; }
function randomPick(arr) { return arr[randomInt(0, arr.length - 1)]; }
function assert(cond, msg) { if (!cond) throw new Error(msg || "assertion failed"); }

// ---------------------------------------------------------------- event bus
// events.on(name, fn) / once / off(id) / emit(name, data) / last(name) / clear(name)
var events = {
    on: function (name, fn) { return __events.on(name, fn); },
    once: function (name, fn) { return __events.once(name, fn); },
    off: function (id) { return __events.off(id); },
    clear: function (name) { return __events.clear(name === undefined ? null : name); },
    count: function (name) { return __events.count(name); },
    last: function (name) { return __events.last(name); },
    emit: function (name, arg) {
        var a = arg === undefined ? null : arg;
        return __events.emit(name, a);
    }
};

// ---------------------------------------------------------------- coroutines (ES6 generators)
// coroutine(function* () { ... yield waitSec(1); yield waitUntil(fn);
//                           yield waitFrames(3); yield waitFor("event"); ... })
function waitSec(s) { return { __wait: Math.max(0, s || 0) }; }
function waitFrames(n) { return { __frames: n || 1 }; }
function waitUntil(fn) { return { __until: fn }; }
function waitFor(name) { return { __event: name }; }
var __fibers = [];
var __fiberSeq = 1;
function coroutine(gen) {
    var it = (typeof gen === 'function') ? gen() : gen;
    var f = { id: __fiberSeq++, it: it, wait: null, t: 0, frames: 0,
              done: false, signal: null, arg: null };
    __fibers.push(f);
    __fiber_resume(f);
    return f.id;
}
function cancelCoroutine(id) {
    for (var i = 0; i < __fibers.length; i++) if (__fibers[i].id === id) { __fibers[i].done = true; return true; }
    return false;
}
function cancelCoroutines() { for (var i = 0; i < __fibers.length; i++) __fibers[i].done = true; }
function activeCoroutines() { var n = 0; for (var i = 0; i < __fibers.length; i++) if (!__fibers[i].done) n++; return n; }
function __fiber_resume(f, arg) {
    var r;
    try { r = f.it.next(arg); }
    catch (err) { error('coroutine: ' + err); f.it = null; f.done = true; return; }
    if (r.done) { f.it = null; f.done = true; return; }
    f.wait = r.value || null; f.t = 0; f.frames = 0; f.arg = null;
}
function __fiber_signal(name, arg) {
    for (var i = 0; i < __fibers.length; i++) {
        var f = __fibers[i];
        if (!f.done && f.wait && f.wait.__event === name) { f.signal = name; f.arg = arg; }
    }
}
function __fiber_tick(dt) {
    for (var i = __fibers.length - 1; i >= 0; i--) {
        var f = __fibers[i];
        if (f.done) { __fibers.splice(i, 1); continue; }
        var w = f.wait, ready = false;
        if (w === null || w === undefined) ready = true;
        else if (w.__wait !== undefined) { f.t += dt; ready = f.t >= w.__wait; }
        else if (w.__frames !== undefined) { f.frames += 1; ready = f.frames >= w.__frames; }
        else if (typeof w.__until === 'function') {
            try { ready = !!w.__until(); } catch (err) { error('coroutine waitUntil: ' + err); f.done = true; continue; }
        }
        else if (w.__event !== undefined) ready = f.signal === w.__event;
        else ready = true;
        if (ready) { var a = f.arg; f.signal = null; f.arg = null; __fiber_resume(f, a); }
    }
}

// ---------------------------------------------------------------- state machines
// state.set("chase") calls chase_enter()/idle_exit(); state.update(dt) calls
// <state>_update(dt). Hooks live in the same script as the behaviour.
var state = {
    get: function () { return __state.getState(); },
    set: function (s) { __state.setState(s); },
    is: function (s) { return __state.isState(s); },
    elapsed: function () { return __state.getElapsed(); },
    update: function (dt) { __state.update(dt || 0); }
};

// ---------------------------------------------------------------- studio plan
// studio.report() / progress() / milestones() / next() / complete(id) / diagnostics()
var studio = {
    report: function () { return __studio.report(); },
    brief: function () { return __studio.brief(); },
    progress: function () { return __studio.progress(); },
    years: function () { return __studio.years(); },
    months: function () { return __studio.months(); },
    next: function () { return __studio.next(); },
    milestones: function () { return __studio.milestones(); },
    complete: function (id) { return __studio.complete(id); },
    reset: function () { return __studio.reset(); },
    diagnostics: function () { return __studio.diagnostics(); }
};

// ---------------------------------------------------------------- timers
var __timers = [];
function after(sec, fn) { __timers.push({ t: time.time + sec, f: fn, every: 0 }); }
function every(sec, fn) { __timers.push({ t: time.time + sec, f: fn, every: sec }); }
function wait(sec, fn) { after(sec, fn); }
function cancelTimers() { __timers.length = 0; }

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
  __fiber_tick(dt);
  for (var i = __timers.length - 1; i >= 0; i--) {
    var tm = __timers[i];
    if (now >= tm.t) { if (tm.every > 0) tm.t += tm.every; else __timers.splice(i, 1); tm.f(); }
  }
  for (var j = __tweens.length - 1; j >= 0; j--) {
    var tw = __tweens[j];
    if (tw.dead || tw.obj === undefined) { __tweens.splice(j, 1); continue; }
    tw.t += dt;
    var k = Math.min(tw.t / tw.dur, 1);
    var e = tw.fn(k);
    tw.obj[tw.prop] = tw.from + (tw.to - tw.from) * e;
    if (k >= 1) {
      __tweens.splice(j, 1);
      if (tw.onDone) { try { tw.onDone(); } catch (err) { error(err); } }
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
