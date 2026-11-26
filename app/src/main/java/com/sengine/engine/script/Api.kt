package com.sengine.engine.script

import com.sengine.engine.Engine
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Component
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import org.mozilla.javascript.Context
import kotlin.math.sqrt

/*
 * Objects exposed to JavaScript. Getter/setter pairs become JS properties
 * (e.g. getX()/setX() -> transform.x).
 */

class SObject(private val go: GameObject, private val engine: Engine, private val sys: ScriptSystem) {
    fun getId(): Double = go.id.toDouble()
    fun getName(): String = go.name
    fun setName(v: String) { go.name = v }
    fun getTag(): String = go.tag
    fun setTag(v: String) { go.tag = v }
    fun getActive(): Boolean = go.active
    fun setActive(v: Boolean) { go.active = v }
    fun getOrder(): Double = go.order.toDouble()
    fun setOrder(v: Double) { go.order = v.toInt() }

    fun getX(): Double = go.x.toDouble()
    fun setX(v: Double) { go.x = v.toFloat() }
    fun getY(): Double = go.y.toDouble()
    fun setY(v: Double) { go.y = v.toFloat() }
    fun getRotation(): Double = go.rotation.toDouble()
    fun setRotation(v: Double) { go.rotation = v.toFloat() }
    fun getScaleX(): Double = go.scaleX.toDouble()
    fun setScaleX(v: Double) { go.scaleX = v.toFloat() }
    fun getScaleY(): Double = go.scaleY.toDouble()
    fun setScaleY(v: Double) { go.scaleY = v.toFloat() }
    fun getWorldX(): Double = go.computeWorld().tx.toDouble()
    fun getWorldY(): Double = go.computeWorld().ty.toDouble()

    fun setPosition(x: Double, y: Double) { go.x = x.toFloat(); go.y = y.toFloat() }
    fun setWorldPosition(x: Double, y: Double) { go.setWorldPosition(x.toFloat(), y.toFloat()) }
    fun move(dx: Double, dy: Double) { go.x += dx.toFloat(); go.y += dy.toFloat() }
    fun rotate(deg: Double) { go.rotation += deg.toFloat() }

    // physics
    private val rb3 get() = go.getAny<com.sengine.engine.core.Rigidbody3D>()
    fun getVx(): Double = (rb3?.vx ?: go.getAny<Rigidbody2D>()?.vx ?: 0f).toDouble()
    fun setVx(v: Double) { rb3?.let { it.vx = v.toFloat(); return }; go.getAny<Rigidbody2D>()?.vx = v.toFloat() }
    fun getVy(): Double = (rb3?.vy ?: go.getAny<Rigidbody2D>()?.vy ?: 0f).toDouble()
    fun setVy(v: Double) { rb3?.let { it.vy = v.toFloat(); return }; go.getAny<Rigidbody2D>()?.vy = v.toFloat() }
    fun getVz(): Double = (rb3?.vz ?: 0f).toDouble()
    fun setVz(v: Double) { rb3?.vz = v.toFloat() }
    fun isGrounded(): Boolean = rb3?.grounded ?: go.getAny<Rigidbody2D>()?.grounded ?: false
    fun addForce(fx: Double, fy: Double) {
        rb3?.let { it.vx += (fx / it.mass).toFloat(); it.vy += (fy / it.mass).toFloat(); return }
        val rb = go.getAny<Rigidbody2D>() ?: return
        rb.vx += (fx / rb.mass).toFloat(); rb.vy += (fy / rb.mass).toFloat()
    }
    fun addForce(fx: Double, fy: Double, fz: Double) {
        val rb = rb3 ?: return addForce(fx, fy)
        rb.vx += (fx / rb.mass).toFloat(); rb.vy += (fy / rb.mass).toFloat(); rb.vz += (fz / rb.mass).toFloat()
    }
    /** Instant velocity change (kg*m/s divided by mass) — punches, bullets, knocks. */
    fun addImpulse(fx: Double, fy: Double) {
        rb3?.let { it.vx += (fx / it.mass).toFloat(); it.vy += (fy / it.mass).toFloat(); return }
        val rb = go.getAny<Rigidbody2D>() ?: return
        rb.vx += (fx / rb.mass).toFloat(); rb.vy += (fy / rb.mass).toFloat()
    }
    fun addImpulse(fx: Double, fy: Double, fz: Double) {
        val rb = rb3 ?: return addImpulse(fx, fy)
        rb.vx += (fx / rb.mass).toFloat(); rb.vy += (fy / rb.mass).toFloat(); rb.vz += (fz / rb.mass).toFloat()
    }
    /** Normal of the surface under this 3D body ([x,y,z], up when airborne). */
    fun getGroundNormal(): Any? {
        val rb = rb3 ?: return sys.newArray(listOf(0.0, 1.0, 0.0))
        return sys.newArray(listOf(rb.groundNx.toDouble(), rb.groundNy.toDouble(), rb.groundNz.toDouble()))
    }
    /** Sets the collision layer (0..15) of this object's collider. */
    fun setLayer(layer: Int) {
        go.getAny<Collider2D>()?.layer = layer
        go.getAny<com.sengine.engine.core.Collider3D>()?.layer = layer
    }
    /** Sets the collision mask bits of this object's collider. */
    fun setMask(mask: Int) {
        go.getAny<Collider2D>()?.mask = mask and 0xFFFF
        go.getAny<com.sengine.engine.core.Collider3D>()?.mask = mask and 0xFFFF
    }
    fun setVelocity(vx: Double, vy: Double) {
        rb3?.let { it.vx = vx.toFloat(); it.vy = vy.toFloat(); return }
        val rb = go.getAny<Rigidbody2D>() ?: return
        rb.vx = vx.toFloat(); rb.vy = vy.toFloat()
    }
    fun setVelocity(vx: Double, vy: Double, vz: Double) {
        val rb = rb3 ?: return setVelocity(vx, vy)
        rb.vx = vx.toFloat(); rb.vy = vy.toFloat(); rb.vz = vz.toFloat()
    }

    // ---- 3D transform
    fun getZ(): Double = go.z.toDouble()
    fun setZ(v: Double) { go.z = v.toFloat() }
    fun getRotX(): Double = go.rotX.toDouble()
    fun setRotX(v: Double) { go.rotX = v.toFloat() }
    fun getRotY(): Double = go.rotY.toDouble()
    fun setRotY(v: Double) { go.rotY = v.toFloat() }
    fun getRotZ(): Double = go.rotation.toDouble()
    fun setRotZ(v: Double) { go.rotation = v.toFloat() }
    fun getScaleZ(): Double = go.scaleZ.toDouble()
    fun setScaleZ(v: Double) { go.scaleZ = v.toFloat() }
    fun getWorldZ(): Double = go.computeWorld3()[14].toDouble()
    fun setPosition(x: Double, y: Double, z: Double) { go.x = x.toFloat(); go.y = y.toFloat(); go.z = z.toFloat() }
    fun move(dx: Double, dy: Double, dz: Double) { go.x += dx.toFloat(); go.y += dy.toFloat(); go.z += dz.toFloat() }
    fun rotate(rx: Double, ry: Double, rz: Double) { go.rotX += rx.toFloat(); go.rotY += ry.toFloat(); go.rotation += rz.toFloat() }
    fun distanceTo3(o: SObject): Double {
        val a = go.computeWorld3(); val b = o.go.computeWorld3()
        val dx = a[12] - b[12]; val dy = a[13] - b[13]; val dz = a[14] - b[14]
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }
    /** Local forward (-Z) direction in world space as [x, y, z]. */
    fun forward(): Any? {
        val d = com.sengine.engine.math.Mat4.dir(go.computeWorld3(), 0f, 0f, -1f)
        return sys.newArray(listOf(d[0].toDouble(), d[1].toDouble(), d[2].toDouble()))
    }

    // ---- animation
    fun play(clip: String) {
        val a = go.getAny<com.sengine.engine.core.Animator>() ?: return
        if (a.current == clip && a.playing && !a.finished) return
        a.current = clip; a.time = 0f; a.frame = 0; a.finished = false; a.playing = true
    }
    fun stopAnimation() { go.getAny<com.sengine.engine.core.Animator>()?.playing = false }
    fun getAnimation(): String = go.getAny<com.sengine.engine.core.Animator>()?.current ?: ""
    fun isAnimationFinished(): Boolean = go.getAny<com.sengine.engine.core.Animator>()?.finished ?: true
    fun setAnimSpeed(v: Double) { go.getAny<com.sengine.engine.core.Animator>()?.speed = v.toFloat() }

    // ---- rendering
    fun setShaderParam(v: Double) {
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.shaderParam = v.toFloat()
        go.getAny<SpriteRenderer>()?.shaderParam = v.toFloat()
    }
    fun setMeshColor(hex: String) {
        val c = try { Component.parseColor(hex) } catch (e: Exception) { return }
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.color = c
    }
    /** True when the two objects' real colliders intersect (box/circle, offsets, scale). */
    fun overlaps(other: SObject): Boolean = engine.physics.overlaps(go, other.go)

    // rendering
    fun getText(): String = go.getAny<TextRenderer>()?.text ?: ""
    fun setText(v: Any?) { go.getAny<TextRenderer>()?.text = Context.toString(v) }
    fun getColor(): String {
        val c = go.getAny<SpriteRenderer>()?.color ?: go.getAny<TextRenderer>()?.color
            ?: go.getAny<com.sengine.engine.core.Light>()?.color ?: -1
        return String.format("#%08X", c)
    }
    fun setColor(hex: String) {
        val c = try { Component.parseColor(hex) } catch (e: Exception) { return }
        go.getAny<SpriteRenderer>()?.color = c
        go.getAny<TextRenderer>()?.color = c
        go.getAny<com.sengine.engine.core.Light>()?.color = c
    }
    /** Light intensity (0..4 typical). */
    fun setIntensity(v: Double) { go.getAny<com.sengine.engine.core.Light>()?.intensity = v.toFloat() }
    /** Camera3D sky gradient colors, e.g. self.setSky("#FF2A6FD4", "#FFBFD8F0"). */
    fun setSky(top: Any?, horizon: Any?) {
        val cam = go.getAny<com.sengine.engine.core.Camera3D>() ?: return
        try { cam.skyTop = Component.parseColor(Context.toString(top)) } catch (_: Exception) {}
        try { cam.skyHorizon = Component.parseColor(Context.toString(horizon)) } catch (_: Exception) {}
    }
    fun getVisible(): Boolean = go.getAny<SpriteRenderer>()?.enabled ?: false
    fun setVisible(v: Boolean) {
        go.getAny<SpriteRenderer>()?.enabled = v
        go.getAny<TextRenderer>()?.enabled = v
    }
    fun setTexture(name: String) { go.getAny<SpriteRenderer>()?.texture = name }
    fun getFlipX(): Boolean = go.getAny<SpriteRenderer>()?.flipX ?: false
    fun setFlipX(v: Boolean) { go.getAny<SpriteRenderer>()?.flipX = v }

    // particles
    fun burst(n: Double) { go.getAny<ParticleEmitter>()?.let { it.pendingBurst += n.toInt() } }
    fun setEmitting(v: Boolean) { go.getAny<ParticleEmitter>()?.emitting = v }

    // camera
    fun getSize(): Double = (go.getAny<Camera2D>()?.size ?: 0f).toDouble()
    fun setSize(v: Double) { go.getAny<Camera2D>()?.size = v.toFloat() }

    // hierarchy & lifecycle
    fun getParent(): Any? = go.parent?.let { sys.toJs(it) }
    fun child(name: String): Any? = engine.scene.childrenOf(go).firstOrNull { it.name == name }?.let { sys.toJs(it) }
    fun destroy() { go.destroyed = true }
    /** Raw engine object — advanced use (pooling, engine helpers). */
    fun unwrap(): GameObject = go
    fun hasComponent(type: String): Boolean = go.components.any { it.type.equals(type, true) }
    fun setComponentEnabled(type: String, v: Boolean) {
        go.components.filter { it.type.equals(type, true) }.forEach { it.enabled = v }
    }
    fun distanceTo(o: SObject): Double {
        val a = go.computeWorld(); val b = o.go.computeWorld()
        val dx = a.tx - b.tx; val dy = a.ty - b.ty
        return sqrt((dx * dx + dy * dy).toDouble())
    }
    fun send(fn: String, arg: Any?): Any? = sys.sendMessage(go, fn, arg)
    fun send(fn: String): Any? = sys.sendMessage(go, fn, null)
    fun `is`(o: SObject?): Boolean = o != null && o.go === go
    override fun toString() = "GameObject(${go.name})"
}

class SScene(private val engine: Engine, private val sys: ScriptSystem) {
    fun getName(): String = engine.scene.name
    fun find(name: String): Any? = engine.scene.find(name)?.let { sys.toJs(it) }
    fun findAll(tag: String): Any? =
        sys.newArray(engine.scene.objects.filter { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }.map { sys.toJs(it) })
    fun count(tag: String): Double =
        engine.scene.objects.count { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }.toDouble()
    private fun spawnGo(name: String, x: Float, y: Float, z: Float?): GameObject? {
        val template = engine.scene.find(name) ?: run { engine.log(1, "spawn: '$name' not found"); return null }
        val copy = engine.scene.duplicate(template, null)
        copy.active = true
        if (z != null) copy.setWorldPosition3(x, y, z) else copy.setWorldPosition(x, y)
        for (d in listOf(copy) + engine.scene.objects.filter { copy.isAncestorOf(it) }) {
            d.components.forEach { it.resetRuntime() }
        }
        engine.scene.updateTransforms()
        sys.attach(copy)
        return copy
    }
    fun spawn(name: String, x: Double, y: Double): Any? = spawnGo(name, x.toFloat(), y.toFloat(), null)?.let { sys.toJs(it) }
    fun spawn(name: String): Any? {
        val t = engine.scene.find(name) ?: return null
        val w = t.computeWorld()
        return spawn(name, w.tx.toDouble(), w.ty.toDouble())
    }
    fun spawn(name: String, x: Double, y: Double, z: Double): Any? = spawnGo(name, x.toFloat(), y.toFloat(), z.toFloat())?.let { sys.toJs(it) }
    fun shake(amount: Double) = engine.shake(amount.toFloat())
    /** Calls `fn(arg)` on every object's scripts (event-bus style messaging). */
    fun broadcast(fn: String, arg: Any?): Any? {
        var result: Any? = null
        for (go in engine.scene.objects) result = sys.sendMessage(go, fn, arg) ?: result
        return result
    }
    fun broadcast(fn: String): Any? = broadcast(fn, null)

    // ---- object pooling: pool("Enemy", 16) once, then obtain()/release()
    fun pool(name: String, size: Double) = engine.poolDefine(name, size.toInt())
    fun obtain(name: String, x: Double, y: Double): Any? =
        engine.poolObtain(name, x.toFloat(), y.toFloat(), null)?.let { sys.toJs(it) }
    fun obtain(name: String, x: Double, y: Double, z: Double): Any? =
        engine.poolObtain(name, x.toFloat(), y.toFloat(), z.toFloat())?.let { sys.toJs(it) }
    fun release(o: Any?): Boolean {
        val go = (o as? SObject)?.unwrap() ?: return false
        return engine.poolRelease(go)
    }

    // ---- queries
    /** Sorted multi-hit 2D ray cast: every collider along the ray, nearest first. */
    fun raycastAll2D(ox: Double, oy: Double, dx: Double, dy: Double, maxDist: Double): Any? =
        sys.newArray(engine.physics.raycastAll(engine.scene, ox.toFloat(), oy.toFloat(), dx.toFloat(), dy.toFloat(), maxDist.toFloat())
            .map { sys.toJs(it.second) })
    /** Sorted multi-hit 3D ray cast (colliders + terrain), nearest first. */
    fun raycastAll(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? =
        sys.newArray(engine.physics3D.raycastAll(engine.scene, ox.toFloat(), oy.toFloat(), oz.toFloat(),
            dx.toFloat(), dy.toFloat(), dz.toFloat(), maxDist.toFloat()).map { sys.toJs(it.second) })
    /** All colliders overlapping a world-space circle (optional tag filter). */
    fun overlapCircle(x: Double, y: Double, r: Double): Any? =
        sys.newArray(engine.physics.overlapCircle(engine.scene, x.toFloat(), y.toFloat(), r.toFloat()).map { sys.toJs(it) })
    fun overlapCircle(x: Double, y: Double, r: Double, tag: String): Any? =
        sys.newArray(engine.physics.overlapCircle(engine.scene, x.toFloat(), y.toFloat(), r.toFloat(), tag).map { sys.toJs(it) })
    /** All colliders overlapping a world-space sphere (optional tag filter). */
    fun overlapSphere(x: Double, y: Double, z: Double, r: Double): Any? =
        sys.newArray(engine.physics3D.overlapSphere(engine.scene, x.toFloat(), y.toFloat(), z.toFloat(), r.toFloat()).map { sys.toJs(it) })
    fun overlapSphere(x: Double, y: Double, z: Double, r: Double, tag: String): Any? =
        sys.newArray(engine.physics3D.overlapSphere(engine.scene, x.toFloat(), y.toFloat(), z.toFloat(), r.toFloat(), tag).map { sys.toJs(it) })

    /** 2D ray cast against colliders; returns the first object hit or null. */
    fun raycast2D(ox: Double, oy: Double, dx: Double, dy: Double, maxDist: Double): Any? {
        val l = Math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9)
        return engine.physics.raycast(engine.scene, ox.toFloat(), oy.toFloat(),
            (dx / l).toFloat(), (dy / l).toFloat(), maxDist.toFloat())?.let { sys.toJs(it) }
    }
    /** World height of the first Terrain at (x, z); 0 when there is none. */
    fun terrainHeight(x: Double, z: Double): Double {
        for (go in engine.scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val t = go.get<com.sengine.engine.core.Terrain>() ?: continue
            if (!t.enabled) continue
            val h = t.heightAtWorld(x.toFloat(), z.toFloat()) ?: continue
            return h.toDouble()
        }
        return 0.0
    }
    /** Enables distance fog (start, end in world units). */
    fun setFog(start: Double, end: Double) {
        engine.scene.fog = true
        engine.scene.fogStart = start.toFloat()
        engine.scene.fogEnd = end.toFloat()
    }
    fun setFogColor(hex: String) {
        try { engine.scene.fogColor = Component.parseColor(hex) } catch (_: Exception) {}
    }
    /** 3D raycast against Collider3D objects; returns the first object hit or null. */
    fun raycast(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        val l = Math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9)
        return engine.physics3D.raycast(engine.scene, ox.toFloat(), oy.toFloat(), oz.toFloat(),
            (dx / l).toFloat(), (dy / l).toFloat(), (dz / l).toFloat(), maxDist.toFloat())?.let { sys.toJs(it) }
    }
    fun getCamera3D(): Any? = engine.mainCamera3D()?.let { sys.toJs(it) }
    fun getGravity3D(): Double = engine.scene.gravity3D.toDouble()
    fun setGravity3D(v: Double) { engine.scene.gravity3D = v.toFloat() }
    fun load(sceneName: String) = engine.requestLoadScene(sceneName)
    fun reload() = engine.requestLoadScene(engine.scene.name)
    fun getCamera(): Any? = engine.mainCamera()?.let { sys.toJs(it) }
    fun getGravityX(): Double = engine.scene.gravityX.toDouble()
    fun setGravityX(v: Double) { engine.scene.gravityX = v.toFloat() }
    fun getGravityY(): Double = engine.scene.gravityY.toDouble()
    fun setGravityY(v: Double) { engine.scene.gravityY = v.toFloat() }
}

/** Plain public fields (Rhino exposes them with their exact names, e.g. input.aDown). */
class SInput(private val engine: Engine) {
    @JvmField var axisX = 0.0
    @JvmField var axisY = 0.0
    @JvmField var a = false
    @JvmField var b = false
    @JvmField var aDown = false
    @JvmField var bDown = false
    /** True while the given key code (android.view.KeyEvent.KEYCODE_*) is held. */
    fun key(code: Double): Boolean = engine.input.keys.contains(code.toInt())
    /** True while W (or D-pad up) is held — convenience for movement. */
    @JvmField var up = false
    @JvmField var down = false
    @JvmField var left = false
    @JvmField var right = false
    @JvmField var touching = false
    @JvmField var tapped = false
    @JvmField var touchX = 0.0
    @JvmField var touchY = 0.0

    fun sync() {
        val i = engine.input
        axisX = i.axisX.toDouble(); axisY = i.axisY.toDouble()
        a = i.a; b = i.b; aDown = i.aDown; bDown = i.bDown
        up = i.axisY > 0.5f; down = i.axisY < -0.5f
        left = i.axisX < -0.5f; right = i.axisX > 0.5f
        touching = i.touching; tapped = i.tapped
        touchX = i.touchX.toDouble(); touchY = i.touchY.toDouble()
    }
}

class STime(private val engine: Engine) {
    fun getTime(): Double = engine.time
    fun getFrame(): Double = engine.frame.toDouble()
    fun getFps(): Double = engine.fps.toDouble()
    /** Seconds since the previous frame (the dt passed to update). */
    fun getDelta(): Double = engine.lastDt.toDouble()
    /** Global time scale (0 = paused, 0.5 = slow motion, 2 = fast forward). */
    fun getScale(): Double = engine.timeScale.toDouble()
    fun setScale(v: Double) { engine.timeScale = v.toFloat().coerceIn(0f, 4f) }
}

class SAudio(private val engine: Engine) {
    fun play(name: String) = engine.audio.play(name)
    fun play(name: String, volume: Double) = engine.audio.play(name, volume.toFloat())
    fun beep() = engine.audio.beep()
    fun stopAll() = engine.audio.stopAll()

    // ---- 3D positional audio
    /** Plays a sound at a world position; returns a handle for stop3d/move3d. */
    fun play3d(name: String, x: Double, y: Double, z: Double): Any? =
        engine.audio.play3d(name, x.toFloat(), y.toFloat(), z.toFloat()).toDouble()
    fun play3d(name: String, x: Double, y: Double, z: Double, volume: Double): Any? =
        engine.audio.play3d(name, x.toFloat(), y.toFloat(), z.toFloat(), volume.toFloat()).toDouble()
    fun play3dLoop(name: String, x: Double, y: Double, z: Double): Any? =
        engine.audio.play3d(name, x.toFloat(), y.toFloat(), z.toFloat(), 1f, true).toDouble()
    fun stop3d(handle: Double) = engine.audio.stop3d(handle.toInt())
    fun move3d(handle: Double, x: Double, y: Double, z: Double) =
        engine.audio.move3d(handle.toInt(), x.toFloat(), y.toFloat(), z.toFloat())
    /** Manual listener override (normally the engine follows the camera). */
    fun setListener(x: Double, y: Double, z: Double) =
        engine.audio.setListener(x.toFloat(), y.toFloat(), z.toFloat(), 1f, 0f, 0f)
    fun setFalloff(minDist: Double, maxDist: Double) {
        engine.audio.minDist3 = minDist.toFloat()
        engine.audio.maxDist3 = maxDist.toFloat()
    }

    // ---- music + buses
    fun playMusic(name: String) = engine.audio.playMusic(name)
    fun playMusic(name: String, loop: Boolean) = engine.audio.playMusic(name, loop)
    fun pauseMusic() = engine.audio.pauseMusic()
    fun resumeMusic() = engine.audio.resumeMusic()
    fun stopMusic() = engine.audio.stopMusic()
    fun setMusicVolume(v: Double) { engine.audio.musicVolume = v.toFloat() }
    fun setSfxVolume(v: Double) { engine.audio.sfxVolume = v.toFloat() }
}

class SConsole(private val engine: Engine) {
    fun log(o: Any?) = engine.log(0, Context.toString(o))
    fun warn(o: Any?) = engine.log(1, "⚠ " + Context.toString(o))
    fun error(o: Any?) = engine.log(2, "✖ " + Context.toString(o))
}
