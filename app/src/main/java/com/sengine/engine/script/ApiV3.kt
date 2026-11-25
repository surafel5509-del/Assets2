package com.sengine.engine.script

import com.sengine.engine.Engine
import com.sengine.project.EngineInfo
import com.sengine.project.StudioPlan
import org.mozilla.javascript.Scriptable

/* ---------------------------------------------------------------------------
 * v3 script API: events, deterministic RNG, state machines, camera director,
 * advanced physics queries / constraints / soft bodies, and durable saves.
 * -------------------------------------------------------------------------*/

/** Bridge for the Kotlin [EventBus] (`events.on/once/off/emit/last/count/clear`). */
class SEvents(private val engine: Engine, private val sys: ScriptSystem) {
    fun on(name: String, fn: Any?): Double =
        sys.eventBus.on(name) { arg -> sys.invokeFunction(fn, arg) }.toDouble()

    fun once(name: String, fn: Any?): Double =
        sys.eventBus.once(name) { arg -> sys.invokeFunction(fn, arg) }.toDouble()

    fun off(id: Double): Boolean = sys.eventBus.off(id.toInt())
    fun clear(name: String?) { sys.eventBus.clear(name) }
    fun count(name: String): Double = sys.eventBus.listenerCount(name).toDouble()
    fun last(name: String): Any? = sys.eventBus.last(name)
    fun emit(name: String, arg: Any?): Double = sys.emitEvent(name, arg).toDouble()
    fun emit(name: String): Double = emit(name, null)
}

/** `random()` in JS reads this seeded generator; `setRandomSeed` re-seeds it. */
class SRandomBridge(private val engine: Engine) {
    fun next(): Double = engine.scriptRng.nextDouble()
    fun seed(v: Double): Double {
        engine.scriptRng.reset(v.toInt())
        return v
    }
    fun getSeed(): Double = engine.scriptRng.seed.toDouble()
}

/** `state.set("chase")` / `state.update(dt)` dispatch hooks on the calling script scope. */
class SState(private val sys: ScriptSystem) {
    fun getState(): String = sys.fsmState()
    fun setState(name: String) = sys.fsmSet(name)
    fun isState(name: String): Boolean = sys.fsmIs(name)
    fun getPrevious(): String = sys.fsmPrevious()
    fun getElapsed(): Double = sys.fsmElapsed()
    fun update(dt: Double) = sys.fsmUpdate(dt)
}

/** Professional camera API: bounds, dead zones, shake trauma, blends and dolly paths. */
class SCamera(private val engine: Engine, private val sys: ScriptSystem) {
    fun getX(): Double = engine.cameraPose()[0].toDouble()
    fun getY(): Double = engine.cameraPose()[1].toDouble()
    fun getZ(): Double = engine.cameraPose()[2].toDouble()
    fun getZoom(): Double = engine.cameraPose()[3].toDouble()
    fun getFov(): Double = engine.cameraPose()[4].toDouble()
    fun getPriority(): Double = engine.activeCameraPriority().toDouble()
    fun isBlending(): Boolean = engine.cameraDirector.isBlending
    fun isCinematic(): Boolean = engine.cameraDirector.isCinematic

    fun shake(amount: Double) = engine.cameraShake(amount.toFloat())

    @JvmOverloads
    fun setBounds(minX: Double, minY: Double, maxX: Double, maxY: Double, minZ: Double = minY, maxZ: Double = maxY) =
        engine.cameraSetBounds(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat(), minZ.toFloat(), maxZ.toFloat())

    fun clearBounds() = engine.cameraClearBounds()
    fun setDeadZone(w: Double, h: Double) = engine.cameraSetDeadZone(w.toFloat(), h.toFloat())
    fun setLookAhead(v: Double) = engine.cameraSetLookAhead(v.toFloat())
    fun zoomTo(z: Double) = engine.cameraZoomTo(z.toFloat())
    fun setFov(v: Double) = engine.cameraSetFov(v.toFloat())
    fun snap() = engine.cameraSnap()
    fun panTo(x: Double, y: Double, duration: Double) = engine.cameraPanTo(x.toFloat(), y.toFloat(), duration.toFloat())
    fun setPriority(v: Double) = engine.cameraSetPriority(v.toInt())
    fun activate(name: String, duration: Double) = engine.cameraActivate(name, duration.toFloat())
    fun activate(name: String) = engine.cameraActivate(name, engine.settings.cameraBlend)
    fun stopPath() = engine.cameraStopPath()

    /** `camera.playPath([[0,0,0],[10,4,5],[20,0,0]], 4)` — Catmull–Rom dolly. */
    fun playPath(points: Any?, duration: Double): Boolean =
        engine.cameraPlayPath(jsPoints(points), duration.toFloat())
}

/** Advanced physics: swept shape casts, area forces, joints and solver introspection. */
class SPhysics(private val engine: Engine, private val sys: ScriptSystem) {
    private fun record(hit: com.sengine.engine.physics.PhysicsWorld.CastResult?): Any? {
        engine.lastCastDistance = hit?.distance ?: 0f
        return hit?.let { sys.toJs(it.go) }
    }
    private fun record3(hit: com.sengine.engine.physics.PhysicsWorld3D.CastResult?): Any? {
        engine.lastCastDistance = hit?.distance ?: 0f
        return hit?.let { sys.toJs(it.go) }
    }

    @JvmOverloads
    fun circleCast(ox: Double, oy: Double, dx: Double, dy: Double, radius: Double, maxDist: Double, tag: String = ""): Any? =
        record(engine.physics.circleCast(engine.scene, ox.toFloat(), oy.toFloat(), dx.toFloat(), dy.toFloat(),
            radius.toFloat(), maxDist.toFloat(), tag))

    @JvmOverloads
    fun boxCast(ox: Double, oy: Double, dx: Double, dy: Double, hw: Double, hh: Double, maxDist: Double, tag: String = ""): Any? =
        record(engine.physics.boxCast(engine.scene, ox.toFloat(), oy.toFloat(), dx.toFloat(), dy.toFloat(),
            hw.toFloat(), hh.toFloat(), maxDist.toFloat(), tag))

    @JvmOverloads
    fun sphereCast(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double,
                   radius: Double, maxDist: Double, tag: String = ""): Any? =
        record3(engine.physics3D.sphereCast(engine.scene, ox.toFloat(), oy.toFloat(), oz.toFloat(),
            dx.toFloat(), dy.toFloat(), dz.toFloat(), radius.toFloat(), maxDist.toFloat(), tag))

    /** Distance of the most recent cast (0 when it missed). */
    fun hitDistance(): Double = engine.lastCastDistance.toDouble()

    @JvmOverloads
    fun explode(x: Double, y: Double, radius: Double, force: Double, tag: String = ""): Double =
        engine.physics.explode(engine.scene, x.toFloat(), y.toFloat(), radius.toFloat(), force.toFloat(), tag).toDouble()

    @JvmOverloads
    fun explode3(x: Double, y: Double, z: Double, radius: Double, force: Double, tag: String = ""): Double =
        engine.physics3D.explode(engine.scene, x.toFloat(), y.toFloat(), z.toFloat(), radius.toFloat(), force.toFloat(), tag).toDouble()

    @JvmOverloads
    fun attract(x: Double, y: Double, radius: Double, force: Double, tag: String = ""): Double =
        engine.physics.attract(engine.scene, x.toFloat(), y.toFloat(), radius.toFloat(), force.toFloat(), tag).toDouble()

    @JvmOverloads
    fun wind(fx: Double, fy: Double, dt: Double, tag: String = ""): Double =
        engine.physics.wind(engine.scene, fx.toFloat(), fy.toFloat(), dt.toFloat(), tag).toDouble()

    @JvmOverloads
    fun joint(a: Any?, b: Any?, mode: Double, rest: Double, stiffness: Double, damping: Double, breakForce: Double = 0.0): Boolean {
        val ga = (a as? SObject)?.unwrap() ?: return false
        val gb = (b as? SObject)?.unwrap() ?: return false
        return engine.joints.add2(ga, gb, mode.toInt(), rest.toFloat(), stiffness.toFloat(), damping.toFloat(), breakForce.toFloat())
    }

    @JvmOverloads
    fun joint3(a: Any?, b: Any?, mode: Double, rest: Double, stiffness: Double, damping: Double, breakForce: Double = 0.0): Boolean {
        val ga = (a as? SObject)?.unwrap() ?: return false
        val gb = (b as? SObject)?.unwrap() ?: return false
        return engine.joints.add3(ga, gb, mode.toInt(), rest.toFloat(), stiffness.toFloat(), damping.toFloat(), breakForce.toFloat())
    }

    fun getIterations(): Double = engine.physics.solverIterations.toDouble()
    fun setIterations(v: Double) { engine.physics.solverIterations = v.toInt().coerceIn(1, 8) }
    fun getJointIterations(): Double = engine.settings.jointIterations.toDouble()
    fun getBroadphasePairs(): Double = engine.physics.lastBroadphasePairs.toDouble()
    fun getBruteForcePairs(): Double = engine.physics.lastBruteForcePairs.toDouble()
    fun getFixedStep(): Double = engine.physics.clock.fixedDt.toDouble()
    fun getAlpha(): Double = engine.physics.clock.alpha.toDouble()
    fun getSteps(): Double = engine.physics.clock.totalSteps.toDouble()
    fun getSeed(): Double = engine.settings.deterministicSeed.toDouble()
    fun getJointCount(): Double = engine.joints.lastActive.toDouble()
}

/** Verlet soft-body ropes / cloth strips, driven by `soft.step` or automatically. */
class SSoft(private val engine: Engine, private val sys: ScriptSystem) {
    @JvmOverloads
    fun rope(name: String, x: Double, y: Double, segments: Double, spacing: Double,
             dirX: Double = 1.0, dirY: Double = 0.0, z: Double = 0.0): Boolean {
        engine.softBodies.createRope(name, x.toFloat(), y.toFloat(), z.toFloat(),
            segments.toInt(), spacing.toFloat(), dirX.toFloat(), dirY.toFloat())
        return true
    }

    @JvmOverloads
    fun cloth(name: String, x: Double, y: Double, cols: Double, rows: Double, spacing: Double, z: Double = 0.0): Boolean {
        engine.softBodies.createCloth(name, x.toFloat(), y.toFloat(), z.toFloat(),
            cols.toInt(), rows.toInt(), spacing.toFloat())
        return true
    }

    fun point(name: String, i: Double): Any? {
        val p = engine.softBodies.get(name)?.points?.getOrNull(i.toInt()) ?: return null
        return sys.newArray(listOf(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()))
    }

    fun setPoint(name: String, i: Double, x: Double, y: Double, z: Double): Boolean {
        val p = engine.softBodies.get(name)?.points?.getOrNull(i.toInt()) ?: return false
        p.teleport(x.toFloat(), y.toFloat(), z.toFloat())
        return true
    }

    fun pin(name: String, i: Double, value: Boolean): Boolean {
        val b = engine.softBodies.get(name) ?: return false
        b.pin(i.toInt(), value); return true
    }

    @JvmOverloads
    fun force(name: String, fx: Double, fy: Double, fz: Double = 0.0, index: Double = -1.0): Boolean {
        val b = engine.softBodies.get(name) ?: return false
        b.addForce(fx.toFloat(), fy.toFloat(), fz.toFloat(), index.toInt()); return true
    }

    fun wind(name: String, fx: Double, fy: Double): Boolean {
        val b = engine.softBodies.get(name) ?: return false
        b.windX = fx.toFloat(); b.windY = fy.toFloat(); return true
    }

    fun explode(name: String, x: Double, y: Double, radius: Double, force: Double): Boolean {
        val b = engine.softBodies.get(name) ?: return false
        b.radialImpulse(x.toFloat(), y.toFloat(), 0f, radius.toFloat(), force.toFloat()); return true
    }

    fun step(name: String, dt: Double): Boolean {
        val b = engine.softBodies.get(name) ?: return false
        b.step(dt.toFloat()); return true
    }

    fun remove(name: String): Boolean { engine.softBodies.remove(name); return true }
    fun count(): Double = engine.softBodies.size.toDouble()
    fun names(): Any? = sys.newArray(engine.softBodies.names())
    fun getAutoStep(): Boolean = engine.softBodies.autoStep
    fun setAutoStep(v: Boolean) { engine.softBodies.autoStep = v }
}

/** Durable progression + engine metadata for full games. */
class SGame(private val engine: Engine, private val sys: ScriptSystem) {
    fun save(key: String, value: Any?) = engine.gameState.save(key, value)
    fun load(key: String): Any? = engine.gameState.load(key)
    fun load(key: String, fallback: Any?): Any? = engine.gameState.load(key, fallback)
    fun has(key: String): Boolean = engine.gameState.has(key)
    fun clear() = engine.gameState.clear()
    fun keys(): Any? = sys.newArray(engine.gameState.keys())
    fun emit(name: String, arg: Any?): Double = sys.emitEvent(name, arg).toDouble()
    fun emit(name: String): Double = emit(name, null)
    fun getVersion(): String = EngineInfo.VERSION
    fun getEdition(): String = EngineInfo.EDITION
    fun getSchema(): Double = EngineInfo.SCHEMA_VERSION.toDouble()
    fun fixedStep(): Double = engine.physics.clock.fixedDt.toDouble()
    fun instructions(): Double = sys.frameInstructions.toDouble()
    fun diagnostics(): String = com.sengine.project.ProjectDiagnostics.report(engine.project)
}

/** `studio` API: two-year roadmap, progress, milestone sign-off and project diagnostics. */
class SStudio(private val engine: Engine, private val sys: ScriptSystem) {
    private fun plan() = StudioPlan.load(engine.project)
    fun report(): String = StudioPlan.report(plan())
    fun brief(): String = StudioPlan.brief()
    fun progress(): Double = plan().progress.toDouble()
    fun years(): Double = StudioPlan.YEARS.toDouble()
    fun months(): Double = StudioPlan.MONTHS.toDouble()
    fun next(): String = plan().next?.let { "${it.id}|${it.month}|${it.phase}|${it.title}" } ?: "complete"
    fun milestones(): Any? {
        val p = plan()
        return sys.newArray(p.milestones.map {
            "${it.id}|${it.month}|${it.phase}|${it.title}|${if (it.complete) "complete" else "planned"}|${p.dueDate(it)}"
        })
    }
    fun complete(id: String): Boolean = StudioPlan.markDone(engine.project, id, true)
    fun reset(): String { StudioPlan.reset(engine.project); return "roadmap reset" }
    fun diagnostics(): String = com.sengine.project.ProjectDiagnostics.report(engine.project)
}

/** Converts a JS array of [x,y,z] triples into spline control points. */
internal fun jsPoints(o: Any?): List<FloatArray> {
    val out = ArrayList<FloatArray>()
    if (o !is Scriptable) return out
    val len = (o.get("length", o) as? Number)?.toInt() ?: return out
    for (i in 0 until len) {
        val e = o.get(i, o)
        if (e is Scriptable) {
            out.add(floatArrayOf(jsNum(e.get(0, e)), jsNum(e.get(1, e)), jsNum(e.get(2, e))))
        } else if (e is Number) {
            // flat [x,y,z, x,y,z, ...] form
            if (i + 2 < len) {
                out.add(floatArrayOf(jsNum(e), jsNum(o.get(i + 1, o)), jsNum(o.get(i + 2, o))))
            }
        }
    }
    return out
}

private fun jsNum(v: Any?): Float = when (v) {
    is Number -> v.toFloat()
    is String -> v.toFloatOrNull() ?: 0f
    else -> 0f
}
