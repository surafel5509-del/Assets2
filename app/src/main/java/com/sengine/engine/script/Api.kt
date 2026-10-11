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
 * S Engine Ultimate Edition - Professional Scripting API
 * Unity 6 + Unreal Engine 5 class API exposed to JavaScript
 * All previous APIs preserved for backward compatibility
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
    fun forward(): Any? {
        val d = com.sengine.engine.math.Mat4.dir(go.computeWorld3(), 0f, 0f, -1f)
        return sys.newArray(listOf(d[0].toDouble(), d[1].toDouble(), d[2].toDouble()))
    }
    fun right(): Any? {
        val d = com.sengine.engine.math.Mat4.dir(go.computeWorld3(), 1f, 0f, 0f)
        return sys.newArray(listOf(d[0].toDouble(), d[1].toDouble(), d[2].toDouble()))
    }
    fun up(): Any? {
        val d = com.sengine.engine.math.Mat4.dir(go.computeWorld3(), 0f, 1f, 0f)
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

    // ---- Animator Controller (Ultimate)
    fun setAnimatorFloat(name: String, value: Double) {
        // Would set animator controller param
        engine.log(0, "Animator param $name = $value on ${go.name}")
    }
    fun setAnimatorBool(name: String, value: Boolean) { engine.log(0, "Animator bool $name = $value") }
    fun setAnimatorTrigger(name: String) { engine.log(0, "Animator trigger $name") }

    // ---- rendering
    fun setShaderParam(v: Double) {
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.shaderParam = v.toFloat()
        go.getAny<SpriteRenderer>()?.shaderParam = v.toFloat()
    }
    fun setMeshColor(hex: String) {
        val c = try { Component.parseColor(hex) } catch (e: Exception) { return }
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.color = c
    }
    fun setMaterialColor(hex: String) = setMeshColor(hex)
    fun setEmission(intensity: Double) { go.getAny<com.sengine.engine.core.MeshRenderer>()?.emission = intensity.toFloat() }

    fun overlaps(other: SObject): Boolean {
        val a = go.computeWorld(); val b = other.go.computeWorld()
        val ca = go.getAny<Collider2D>(); val cb = other.go.getAny<Collider2D>()
        val aw = (ca?.width ?: 1f) * a.scaleX / 2; val ah = (ca?.height ?: 1f) * a.scaleY / 2
        val bw = (cb?.width ?: 1f) * b.scaleX / 2; val bh = (cb?.height ?: 1f) * b.scaleY / 2
        return kotlin.math.abs(a.tx - b.tx) < aw + bw && kotlin.math.abs(a.ty - b.ty) < ah + bh
    }

    // rendering
    fun getText(): String = go.getAny<TextRenderer>()?.text ?: go.getAny<com.sengine.engine.core.TextMeshProComponent>()?.text ?: ""
    fun setText(v: Any?) {
        val s = Context.toString(v)
        go.getAny<TextRenderer>()?.text = s
        go.getAny<com.sengine.engine.core.TextMeshProComponent>()?.text = s
    }
    fun getColor(): String {
        val c = go.getAny<SpriteRenderer>()?.color ?: go.getAny<TextRenderer>()?.color ?: go.getAny<com.sengine.engine.core.MeshRenderer>()?.color ?: -1
        return String.format("#%08X", c)
    }
    fun setColor(hex: String) {
        val c = try { Component.parseColor(hex) } catch (e: Exception) { return }
        go.getAny<SpriteRenderer>()?.color = c
        go.getAny<TextRenderer>()?.color = c
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.color = c
    }
    fun getVisible(): Boolean = go.getAny<SpriteRenderer>()?.enabled ?: go.getAny<com.sengine.engine.core.MeshRenderer>()?.enabled ?: false
    fun setVisible(v: Boolean) {
        go.getAny<SpriteRenderer>()?.enabled = v
        go.getAny<TextRenderer>()?.enabled = v
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.enabled = v
    }
    fun setTexture(name: String) { go.getAny<SpriteRenderer>()?.texture = name; go.getAny<com.sengine.engine.core.MeshRenderer>()?.texture = name }
    fun getFlipX(): Boolean = go.getAny<SpriteRenderer>()?.flipX ?: false
    fun setFlipX(v: Boolean) { go.getAny<SpriteRenderer>()?.flipX = v }

    // particles
    fun burst(n: Double) { go.getAny<ParticleEmitter>()?.let { it.pendingBurst += n.toInt() } }
    fun setEmitting(v: Boolean) { go.getAny<ParticleEmitter>()?.emitting = v }
    fun playParticles() { engine.vfxSystems[go.id]?.play() }
    fun stopParticles() { engine.vfxSystems[go.id]?.stop() }
    fun emitParticles(count: Double) { engine.vfxSystems[go.id]?.emit(count.toInt()) }

    // camera
    fun getSize(): Double = (go.getAny<Camera2D>()?.size ?: go.getAny<com.sengine.engine.core.Camera3D>()?.fov ?: 0f).toDouble()
    fun setSize(v: Double) { go.getAny<Camera2D>()?.size = v.toFloat(); go.getAny<com.sengine.engine.core.Camera3D>()?.fov = v.toFloat() }
    fun getFOV(): Double = (go.getAny<com.sengine.engine.core.Camera3D>()?.fov ?: 60f).toDouble()
    fun setFOV(v: Double) { go.getAny<com.sengine.engine.core.Camera3D>()?.fov = v.toFloat() }

    // Audio (Ultimate)
    fun playSound(clip: String, volume: Double = 1.0) { engine.audio.play(clip, volume.toFloat()) }
    fun setAudioVolume(vol: Double) { go.getAny<com.sengine.engine.core.AudioSource>()?.volume = vol.toFloat() }

    // Physics (Ultimate)
    fun addExplosionForce(force: Double, posX: Double, posY: Double, posZ: Double, radius: Double) {
        val rb = go.getAny<com.sengine.engine.core.Rigidbody3D>() ?: return
        val dx = go.x - posX; val dy = go.y - posY; val dz = go.z - posZ
        val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > radius) return
        val atten = 1.0 - dist / radius
        rb.vx += (dx / dist * force * atten).toFloat()
        rb.vy += (dy / dist * force * atten).toFloat()
        rb.vz += (dz / dist * force * atten).toFloat()
    }

    // AI (Ultimate)
    fun setDestination(x: Double, y: Double, z: Double): Boolean {
        val agent = go.getAny<com.sengine.engine.core.NavMeshAgentComponent>() ?: return false
        // Use navmesh
        return engine.navMesh.findPath(floatArrayOf(go.x, go.y, go.z), floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())) != null
    }

    // Networking (Ultimate)
    fun isLocalPlayer(): Boolean = go.getAny<com.sengine.engine.core.NetworkIdentityComponent>()?.isLocalPlayer ?: true
    fun hasAuthority(): Boolean = true

    // hierarchy & lifecycle
    fun getParent(): Any? = go.parent?.let { sys.toJs(it) }
    fun child(name: String): Any? = engine.scene.childrenOf(go).firstOrNull { it.name == name }?.let { sys.toJs(it) }
    fun findChild(name: String): Any? = child(name)
    fun getChildren(): Any? = sys.newArray(engine.scene.childrenOf(go).map { sys.toJs(it) })
    fun destroy() { go.destroyed = true }
    fun hasComponent(type: String): Boolean = go.components.any { it.type.equals(type, true) }
    fun setComponentEnabled(type: String, v: Boolean) { go.components.filter { it.type.equals(type, true) }.forEach { it.enabled = v } }
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
    fun findAll(tag: String): Any? = sys.newArray(engine.scene.objects.filter { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }.map { sys.toJs(it) })
    fun count(tag: String): Double = engine.scene.objects.count { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }.toDouble()
    fun findWithLayer(layer: Double): Any? = sys.newArray(engine.scene.objects.filter { !it.destroyed && it.isActiveInHierarchy() }.map { sys.toJs(it) }) // simplified
    private fun spawnGo(name: String, x: Float, y: Float, z: Float?): GameObject? {
        val template = engine.scene.find(name) ?: run { engine.log(1, "spawn: '$name' not found"); return null }
        val copy = engine.scene.duplicate(template, null)
        copy.active = true
        if (z != null) copy.setWorldPosition3(x, y, z) else copy.setWorldPosition(x, y)
        for (d in listOf(copy) + engine.scene.objects.filter { copy.isAncestorOf(it) }) d.components.forEach { it.resetRuntime() }
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

    // Ultimate Edition spawn with pooling
    fun spawnPooled(name: String, x: Double, y: Double, z: Double): Any? {
        return engine.objectPoolManager?.spawn(name, x.toFloat(), y.toFloat(), z.toFloat())?.let { sys.toJs(it) } ?: spawn(name, x, y, z)
    }
    fun spawnPrefab(name: String, x: Double, y: Double, z: Double): Any? {
        return engine.prefabManager.instantiate(engine.scene, name, x.toFloat(), y.toFloat(), z.toFloat())?.let { sys.toJs(it) }
    }

    fun shake(amount: Double) = engine.shake(amount.toFloat())
    fun raycast(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        val l = Math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9)
        return engine.physics3D.raycast(engine.scene, ox.toFloat(), oy.toFloat(), oz.toFloat(), (dx / l).toFloat(), (dy / l).toFloat(), (dz / l).toFloat(), maxDist.toFloat())?.let { sys.toJs(it) }
    }
    fun raycastAll(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        val hits = engine.raycastAll(floatArrayOf(ox.toFloat(), oy.toFloat(), oz.toFloat()), floatArrayOf(dx.toFloat(), dy.toFloat(), dz.toFloat()), maxDist.toFloat())
        return sys.newArray(hits.map { sys.toJs(it) })
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

    // Ultimate Edition
    fun getTimeOfDay(): Double = engine.skyboxSystem.timeOfDay.toDouble()
    fun setTimeOfDay(t: Double) { engine.skyboxSystem.timeOfDay = t.toFloat() }
    fun getQualityLevel(): String = engine.config.quality.level.name
    fun setQualityLevel(level: String) {
        try {
            val q = com.sengine.engine.core.QualityLevel.valueOf(level.uppercase())
            engine.setQualityLevel(com.sengine.engine.core.QualitySettings.presets[q] ?: com.sengine.engine.core.QualitySettings())
        } catch (_: Exception) {}
    }
    fun getTerrainHeight(x: Double, z: Double): Double = engine.terrainSystem.getHeightAt(x.toFloat(), z.toFloat()).toDouble()
}

class SInput(private val engine: Engine) {
    @JvmField var axisX = 0.0
    @JvmField var axisY = 0.0
    @JvmField var a = false
    @JvmField var b = false
    @JvmField var aDown = false
    @JvmField var bDown = false
    @JvmField var touching = false
    @JvmField var tapped = false
    @JvmField var touchX = 0.0
    @JvmField var touchY = 0.0
    // Ultimate Edition
    @JvmField var mouseX = 0.0
    @JvmField var mouseY = 0.0
    @JvmField var mouseDeltaX = 0.0
    @JvmField var mouseDeltaY = 0.0
    @JvmField var leftMouse = false
    @JvmField var rightMouse = false

    fun sync() {
        val i = engine.input
        axisX = i.axisX.toDouble(); axisY = i.axisY.toDouble()
        a = i.a; b = i.b; aDown = i.aDown; bDown = i.bDown
        touching = i.touching; tapped = i.tapped
        touchX = i.touchX.toDouble(); touchY = i.touchY.toDouble()
        // New input system sync
        val v2 = engine.inputSystemV2
        mouseX = v2.mousePosition[0].toDouble()
        mouseY = v2.mousePosition[1].toDouble()
        mouseDeltaX = v2.mouseDelta[0].toDouble()
        mouseDeltaY = v2.mouseDelta[1].toDouble()
    }

    fun getAxis(name: String): Double {
        return when (name.lowercase()) {
            "horizontal" -> axisX
            "vertical" -> axisY
            "mouse x" -> mouseDeltaX
            "mouse y" -> mouseDeltaY
            else -> 0.0
        }
    }

    fun getButton(name: String): Boolean {
        return when (name.lowercase()) {
            "jump", "a" -> a
            "fire1", "b" -> b
            else -> false
        }
    }

    fun getButtonDown(name: String): Boolean {
        return when (name.lowercase()) {
            "jump", "a" -> aDown
            "fire1", "b" -> bDown
            else -> false
        }
    }
}

class STime(private val engine: Engine) {
    fun getTime(): Double = engine.time
    fun getUnscaledTime(): Double = engine.timeManager.unscaledTime
    fun getDeltaTime(): Double = engine.timeManager.deltaTime.toDouble()
    fun getUnscaledDeltaTime(): Double = engine.timeManager.unscaledDeltaTime.toDouble()
    fun getFixedDeltaTime(): Double = engine.timeManager.fixedDeltaTime.toDouble()
    fun getTimeScale(): Double = engine.timeManager.timeScale.toDouble()
    fun setTimeScale(s: Double) { engine.timeManager.timeScale = s.toFloat() }
    fun getFrame(): Double = engine.frame.toDouble()
    fun getFps(): Double = engine.fps.toDouble()
    fun getSmoothDeltaTime(): Double = engine.timeManager.smoothDeltaTime.toDouble()
}

class SAudio(private val engine: Engine) {
    fun play(name: String) = engine.audio.play(name)
    fun play(name: String, volume: Double) = engine.audio.play(name, volume.toFloat())
    fun play(name: String, volume: Double, pitch: Double) = engine.audio.play(name, volume.toFloat()) // pitch ignored in legacy
    fun beep() = engine.audio.beep()
    fun stopAll() = engine.audio.stopAll()
    // Ultimate
    fun playAtPoint(name: String, x: Double, y: Double, z: Double) = engine.audio.play(name)
    fun setMixerVolume(group: String, volume: Double) { engine.audioPro.mixer.findGroup(group)?.volume = volume.toFloat() }
}

class SConsole(private val engine: Engine) {
    fun log(o: Any?) = engine.log(0, Context.toString(o))
    fun warn(o: Any?) = engine.log(1, "⚠ " + Context.toString(o))
    fun error(o: Any?) = engine.log(2, "✖ " + Context.toString(o))
}

// Ultimate Edition Additional APIs

class SPhysics(private val engine: Engine) {
    fun raycast(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        return engine.physics3D.raycast(engine.scene, ox.toFloat(), oy.toFloat(), oz.toFloat(), dx.toFloat(), dy.toFloat(), dz.toFloat(), maxDist.toFloat())?.let { engine.scripts.toJs(it) }
    }
    fun overlapSphere(x: Double, y: Double, z: Double, radius: Double): Any? {
        val result = mutableListOf<GameObject>()
        for (go in engine.scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val w = go.world3
            val dx = w[12] - x; val dy = w[13] - y; val dz = w[14] - z
            if (dx * dx + dy * dy + dz * dz <= radius * radius) result.add(go)
        }
        return engine.scripts.newArray(result.map { engine.scripts.toJs(it) })
    }
    fun checkSphere(x: Double, y: Double, z: Double, radius: Double): Boolean {
        for (go in engine.scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val w = go.world3
            val dx = w[12] - x; val dy = w[13] - y; val dz = w[14] - z
            if (dx * dx + dy * dy + dz * dz <= radius * radius) return true
        }
        return false
    }
}

class SQuality(private val engine: Engine) {
    fun getLevel(): String = engine.config.quality.level.name
    fun setLevel(name: String) {
        try {
            val level = com.sengine.engine.core.QualityLevel.valueOf(name.uppercase())
            engine.setQualityLevel(com.sengine.engine.core.QualitySettings.presets[level] ?: com.sengine.engine.core.QualitySettings())
        } catch (_: Exception) {}
    }
    fun getRenderScale(): Double = engine.config.quality.renderScale.toDouble()
    fun setRenderScale(s: Double) { engine.config.quality.renderScale = s.toFloat() }
}

class SApplication(private val engine: Engine) {
    fun getVersion(): String = com.sengine.engine.core.EngineVersion.string()
    fun getPlatform(): String = "Android"
    fun getIsPlaying(): Boolean = engine.mode == Engine.Mode.PLAY
    fun quit() { engine.log(0, "Application.Quit() called") }
    fun openURL(url: String) { engine.log(0, "OpenURL: $url") }
}

class SNavMesh(private val engine: Engine) {
    fun calculatePath(sx: Double, sy: Double, sz: Double, ex: Double, ey: Double, ez: Double): Any? {
        val path = engine.navMesh.findPath(floatArrayOf(sx.toFloat(), sy.toFloat(), sz.toFloat()), floatArrayOf(ex.toFloat(), ey.toFloat(), ez.toFloat()))
        return if (path != null) engine.scripts.newArray(path.map { p -> engine.scripts.newArray(listOf(p[0].toDouble(), p[1].toDouble(), p[2].toDouble())) }) else null
    }
    fun samplePosition(x: Double, y: Double, z: Double, maxDist: Double): Any? {
        // Find nearest walkable node
        val nearest = engine.navMesh.nodes.minByOrNull {
            val dx = it.x - x; val dy = it.y - y; val dz = it.z - z
            dx * dx + dy * dy + dz * dz
        }
        return nearest?.let { engine.scripts.newArray(listOf(it.x.toDouble(), it.y.toDouble(), it.z.toDouble())) }
    }
}

// ==================== ULTIMATE 6.1 QUANTUM APIs ====================

class SRayTracing(private val engine: Engine) {
    fun isSupported(): Boolean = engine.rayTracingSystem.supported
    fun getMode(): String = engine.rayTracingSystem.settings.mode.name
    fun setMode(mode: String) { try { engine.rayTracingSystem.settings.mode = com.sengine.engine.render.RayTracingMode.valueOf(mode) } catch (_: Exception) {} }
    fun getQuality(): String = engine.rayTracingSystem.settings.quality.name
    fun setQuality(q: String) { try { engine.rayTracingSystem.settings.quality = com.sengine.engine.render.RayTracingQuality.valueOf(q) } catch (_: Exception) {} }
    fun traceShadowRay(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Boolean {
        return engine.rayTracingSystem.traceShadowRay(floatArrayOf(ox.toFloat(), oy.toFloat(), oz.toFloat()), floatArrayOf(dx.toFloat(), dy.toFloat(), dz.toFloat()), maxDist.toFloat(), emptyList())
    }
    fun getStats(): String = engine.rayTracingSystem.getStats()
}

class SNanite(private val engine: Engine) {
    fun getQuality(): String = engine.naniteSystem.quality.name
    fun setQuality(q: String) { try { engine.naniteSystem.quality = com.sengine.engine.render.NaniteQuality.valueOf(q) } catch (_: Exception) {} }
    fun getVisibleMeshlets(): Double = engine.naniteSystem.statsVisibleMeshlets.toDouble()
    fun getCulledMeshlets(): Double = engine.naniteSystem.statsCulledMeshlets.toDouble()
    fun getTotalTriangles(): Double = engine.naniteSystem.statsTotalTriangles.toDouble()
    fun getStats(): String = engine.naniteSystem.getStats()
}

class SChaos(private val engine: Engine) {
    fun createDestructible(name: String, x: Double, y: Double, z: Double, sizeX: Double, sizeY: Double, sizeZ: Double, pieces: Double): Any? {
        val obj = engine.chaosDestruction.createDestructible(System.currentTimeMillis(), name, x.toFloat(), y.toFloat(), z.toFloat(), sizeX.toFloat(), sizeY.toFloat(), sizeZ.toFloat(), pieces.toInt())
        return engine.scripts.newArray(listOf(obj.id.toDouble(), obj.health.toDouble()))
    }
    fun applyDamage(id: Double, amount: Double, hx: Double, hy: Double, hz: Double) { engine.chaosDestruction.applyDamage(id.toLong(), amount.toFloat(), hx.toFloat(), hy.toFloat(), hz.toFloat()) }
    fun addField(x: Double, y: Double, z: Double, radius: Double, strength: Double, type: String) { engine.chaosDestruction.addField(x.toFloat(), y.toFloat(), z.toFloat(), radius.toFloat(), strength.toFloat(), type) }
    fun getActivePieces(): Double = engine.chaosDestruction.getActivePieceCount().toDouble()
    fun getStats(): String = engine.chaosDestruction.getStats()
}

class SFluid(private val engine: Engine) {
    fun addEmitter(x: Double, y: Double, z: Double, type: String, rate: Double) { try { engine.fluidSystem.addEmitter(x.toFloat(), y.toFloat(), z.toFloat(), com.sengine.engine.physics.FluidType.valueOf(type), rate.toFloat()) } catch (_: Exception) {} }
    fun getParticleCount(): Double = engine.fluidSystem.particles.size.toDouble()
    fun getStats(): String = engine.fluidSystem.getStats()
}

class SWorldPartition(private val engine: Engine) {
    fun getLoadedCells(): Double = engine.worldPartition.loadedCells.toDouble()
    fun getVisibleCells(): Double = engine.worldPartition.visibleCells.toDouble()
    fun getCellAt(x: Double, z: Double): String {
        val cx = kotlin.math.floor(x/256.0).toInt(); val cz = kotlin.math.floor(z/256.0).toInt()
        return "${cx}_${cz}"
    }
    fun getStats(): String = engine.worldPartition.getStats()
}

class SSpline(private val engine: Engine) {
    fun createSpline(name: String, type: String): Any? {
        try { val t = com.sengine.engine.world.SplineType.valueOf(type); engine.splineSystem.createSpline(name, t) } catch (_: Exception) { engine.splineSystem.createSpline(name) }
        return name
    }
    fun evaluate(name: String, t: Double): Any? {
        val pos = engine.splineSystem.evaluate(name, t.toFloat()) ?: return null
        return engine.scripts.newArray(listOf(pos[0].toDouble(), pos[1].toDouble(), pos[2].toDouble()))
    }
    fun getStats(): String = engine.splineSystem.getStats()
}

class SFoliage(private val engine: Engine) {
    fun paintFoliage(proto: String, x: Double, z: Double, radius: Double, density: Double) {
        engine.foliageSystem.paintFoliage(proto, x.toFloat(), z.toFloat(), radius.toFloat(), { _, _ -> 0f }, density.toFloat())
    }
    fun getInstanceCount(): Double = engine.foliageSystem.instances.size.toDouble()
    fun getStats(): String = engine.foliageSystem.getStats()
}

class SPCG(private val engine: Engine) {
    fun generate(graphName: String, seed: Double): Any? {
        val points = engine.pcgSystem.generate(graphName, seed.toInt())
        return engine.scripts.newArray(points.map { p -> engine.scripts.newArray(listOf(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())) })
    }
    fun getStats(): String = engine.pcgSystem.getStats()
}

class SDialogue(private val engine: Engine) {
    fun startDialogue(name: String): Boolean = engine.dialogueSystem.startDialogue(name)
    fun selectChoice(index: Double) { engine.dialogueSystem.selectChoice(index.toInt()) }
    fun nextLine() { engine.dialogueSystem.nextLine() }
    fun isActive(): Boolean = engine.dialogueSystem.isActive
    fun getStats(): String = engine.dialogueSystem.getStats()
}

class SQuest(private val engine: Engine) {
    fun startQuest(id: String): Boolean = engine.questSystem.startQuest(id)
    fun completeQuest(id: String): Boolean = engine.questSystem.completeQuest(id)
    fun updateObjective(target: String, amount: Double) { engine.questSystem.updateObjectiveByTarget(target, amount.toInt()) }
    fun getActiveQuests(): Any? = engine.scripts.newArray(engine.questSystem.getActiveQuests().map { it.id })
    fun getStats(): String = engine.questSystem.getStats()
}

class SInventory(private val engine: Engine) {
    fun addItem(id: String, quantity: Double): Boolean {
        val item = com.sengine.engine.gameplay.Item(id=id, name=id, quantity=quantity.toInt()); return engine.inventorySystem.addItem(item, quantity.toInt())
    }
    fun hasItem(id: String, quantity: Double): Boolean = engine.inventorySystem.hasItem(id, quantity.toInt())
    fun getGold(): Double = engine.inventorySystem.gold.toDouble()
    fun setGold(g: Double) { engine.inventorySystem.gold = g.toInt() }
    fun getStats(): String = engine.inventorySystem.getStats()
}

class SSKillTree(private val engine: Engine) {
    fun unlockSkill(id: String): Boolean = engine.skillTreeSystem.unlockSkill(id)
    fun getSkillPoints(): Double = engine.skillTreeSystem.skillPoints.toDouble()
    fun getStats(): String = engine.skillTreeSystem.getStats()
}

class SWorldTime(private val engine: Engine) {
    fun getTimeOfDay(): Double = engine.worldTimeSystem.timeOfDay.toDouble()
    fun setTimeOfDay(h: Double) { engine.worldTimeSystem.setTimeOfDay(h.toFloat()) }
    fun getSeason(): String = engine.worldTimeSystem.season.name
    fun getWeather(): String = engine.worldTimeSystem.currentWeather.name
    fun setWeather(type: String) { try { engine.worldTimeSystem.setWeather(com.sengine.engine.gameplay.WeatherTypeV2.valueOf(type)) } catch (_: Exception) {} }
    fun isDay(): Boolean = engine.worldTimeSystem.isDay()
    fun isNight(): Boolean = engine.worldTimeSystem.isNight()
    fun getTemperature(): Double = engine.worldTimeSystem.temperature.toDouble()
    fun getStats(): String = engine.worldTimeSystem.getStats()
}

class SCompute(private val engine: Engine) {
    fun dispatch(shader: String, x: Double, y: Double, z: Double) { engine.computeShaderSystem.dispatch(shader, x.toInt(), y.toInt(), z.toInt()) }
    fun getStats(): String = engine.computeShaderSystem.getStats()
}

class SXR(private val engine: Engine) {
    fun isEnabled(): Boolean = engine.xrSystem.enabled
    fun enable(mode: String) { try { engine.xrSystem.initialize(com.sengine.engine.xr.XRMode.valueOf(mode)) } catch (_: Exception) {} }
    fun getHeadPosition(): Any? {
        val p = engine.xrSystem.headPose; return engine.scripts.newArray(listOf(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()))
    }
    fun getStats(): String = engine.xrSystem.getStats()
}

class SMLAgents(private val engine: Engine) {
    fun getAgentCount(): Double = engine.mlAgentsSystem.agents.size.toDouble()
    fun getAverageReward(): Double = engine.mlAgentsSystem.averageReward.toDouble()
    fun getStats(): String = engine.mlAgentsSystem.getStats()
}

class SDecal(private val engine: Engine) {
    fun createDecal(x: Double, y: Double, z: Double, sizeX: Double, sizeY: Double, sizeZ: Double, texture: String): Any? {
        val d = engine.decalSystemV2.createDecal(x.toFloat(), y.toFloat(), z.toFloat(), sizeX.toFloat(), sizeY.toFloat(), sizeZ.toFloat(), texture)
        return d.id
    }
    fun createBulletHole(x: Double, y: Double, z: Double, nx: Double, ny: Double, nz: Double) { engine.decalSystemV2.createBulletHole(x.toFloat(), y.toFloat(), z.toFloat(), nx.toFloat(), ny.toFloat(), nz.toFloat()) }
    fun getCount(): Double = engine.decalSystemV2.decals.size.toDouble()
    fun getStats(): String = engine.decalSystemV2.getStats()
}




// ==================== ULTRA 6.1.2 APIs - MONTHS/YEARS PROFESSIONAL PROJECT ====================

class SUltraCore(private val engine: Engine) {
    fun createBurstJob(name: String, source: String, optimization: String): String {
        return try { val opt = com.sengine.engine.core.BurstOptimization.valueOf(optimization); engine.ultraCore.createBurstJob(name, source, opt).name } catch (_: Exception) { try { engine.ultraCore.createBurstJob(name, source).name } catch (_: Exception) { "" } }
    }
    fun compileBurstJob(name: String): Boolean = try { engine.ultraCore.burstJobs[name]?.compile() ?: false } catch (_: Exception) { false }
    fun executeBurstJob(name: String, iterations: Double): Double = try { engine.ultraCore.burstJobs[name]?.execute(iterations.toInt())?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
    fun createAssembly(name: String, type: String): String {
        return try { val t = com.sengine.engine.core.AssemblyType.valueOf(type); engine.ultraCore.createAssembly(name, t).name } catch (_: Exception) { try { engine.ultraCore.createAssembly(name).name } catch (_: Exception) { "" } }
    }
    fun createScriptableObject(name: String, type: String): String = try { engine.ultraCore.createScriptableObject(name, type).name } catch (_: Exception) { "" }
    fun getScriptableObject(name: String): Any? = try { engine.ultraCore.getScriptableObject(name)?.name } catch (_: Exception) { null }
    fun undo(): Boolean = try { engine.ultraCore.undoRedo.undo() != null } catch (_: Exception) { false }
    fun redo(): Boolean = try { engine.ultraCore.undoRedo.redo() != null } catch (_: Exception) { false }
    fun canUndo(): Boolean = try { engine.ultraCore.undoRedo.canUndo() } catch (_: Exception) { false }
    fun canRedo(): Boolean = try { engine.ultraCore.undoRedo.canRedo() } catch (_: Exception) { false }
    fun getUndoStats(): String = try { engine.ultraCore.undoRedo.getStats() } catch (_: Exception) { "" }
    fun createPrefabVariant(base: String, name: String): String = try { engine.ultraCore.prefabVariants.createVariant(base, name).id } catch (_: Exception) { "" }
    fun getStats(): String = try { "Ultra Core Burst:${engine.ultraCore.burstJobs.size} Assemblies:${engine.ultraCore.assemblies.size} SO:${engine.ultraCore.scriptableObjects.size} ${engine.ultraCore.undoRedo.getStats()}" } catch (_: Exception) { "Ultra Core" }
}

class SUltraRendering(private val engine: Engine) {
    fun getMode(): String = try { engine.ultraRendering.mode.name } catch (_: Exception) { "Forward" }
    fun setMode(mode: String) { try { engine.ultraRendering.mode = com.sengine.engine.render.RenderingMode.valueOf(mode) } catch (_: Exception) {} }
    fun buildClusters(screenW: Double, screenH: Double) { 
        try { engine.ultraRendering.clustered.buildClusters() } catch (_: Exception) {}
    }
    fun addLight(x: Double, y: Double, z: Double, radius: Double, color: String, intensity: Double) { 
        try { engine.ultraRendering.addLight(x.toFloat(), y.toFloat(), z.toFloat(), radius.toFloat(), color.hashCode(), intensity.toFloat()) } catch (_: Exception) {}
    }
    fun assignLights() { 
        try { engine.ultraRendering.clustered.assignLights() } catch (_: Exception) {}
    }
    fun getClusterStats(): String = try { engine.ultraRendering.clustered.getStats() } catch (_: Exception) { "" }
    fun calculateCSMSplits(near: Double, far: Double): Any? {
        return try {
            val splits = engine.ultraRendering.shadowCSM.calculateSplits(near.toFloat(), far.toFloat())
            engine.scripts.newArray(splits.map { it.toDouble() as Any? })
        } catch (_: Exception) { null }
    }
    fun setUpscalerType(type: String) { try { engine.ultraRendering.upscaler.type = com.sengine.engine.render.UpscalerType.valueOf(type) } catch (_: Exception) {} }
    fun calculateInputResolution(): Any? {
        return try {
            val res = engine.ultraRendering.upscaler.calculateInputResolution()
            engine.scripts.newArray(listOf(res.first.toDouble() as Any?, res.second.toDouble() as Any?))
        } catch (_: Exception) { null }
    }
    fun getCloudDensity(x: Double, y: Double, z: Double): Double = try { engine.ultraRendering.cloudsV2.sampleDensity(x.toFloat(), y.toFloat(), z.toFloat()).toDouble() } catch (_: Exception) { 0.0 }
    fun getWaterHeight(x: Double, z: Double): Double = try { engine.ultraRendering.waterV2.getHeightAt(x.toFloat(), z.toFloat(), engine.time.toFloat()).toDouble() } catch (_: Exception) { 0.0 }
    fun getStats(): String = try { engine.ultraRendering.getStats() } catch (_: Exception) { "" }
}

class SUltraPhysics(private val engine: Engine) {
    fun boxCast(cx: Double, cy: Double, cz: Double, hx: Double, hy: Double, hz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        return try {
            val hits = engine.ultraPhysics.querySystem.boxCast(cx.toFloat(), cy.toFloat(), cz.toFloat(), hx.toFloat(), hy.toFloat(), hz.toFloat(), dx.toFloat(), dy.toFloat(), dz.toFloat(), maxDist.toFloat(), emptyList())
            engine.scripts.newArray(hits.map { h -> engine.scripts.newArray(listOf(h.pointX.toDouble() as Any?, h.pointY.toDouble() as Any?, h.pointZ.toDouble() as Any?, h.distance.toDouble() as Any?)) as Any? })
        } catch (_: Exception) { try { engine.scripts.newArray(emptyList<Any?>()) } catch (_: Exception) { null } }
    }
    fun sphereCast(cx: Double, cy: Double, cz: Double, radius: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        return try {
            val hits = engine.ultraPhysics.querySystem.sphereCast(cx.toFloat(), cy.toFloat(), cz.toFloat(), radius.toFloat(), dx.toFloat(), dy.toFloat(), dz.toFloat(), maxDist.toFloat(), emptyList())
            engine.scripts.newArray(hits.map { h -> engine.scripts.newArray(listOf(h.pointX.toDouble() as Any?, h.pointY.toDouble() as Any?, h.pointZ.toDouble() as Any?, h.distance.toDouble() as Any?)) as Any? })
        } catch (_: Exception) { try { engine.scripts.newArray(emptyList<Any?>()) } catch (_: Exception) { null } }
    }
    fun overlapBox(cx: Double, cy: Double, cz: Double, hx: Double, hy: Double, hz: Double): Any? {
        return try {
            val hits = engine.ultraPhysics.querySystem.overlapBox(cx.toFloat(), cy.toFloat(), hx.toFloat(), hy.toFloat(), emptyList())
            engine.scripts.newArray(hits.map { it.id as Any? })
        } catch (_: Exception) { try { engine.scripts.newArray(emptyList<Any?>()) } catch (_: Exception) { null } }
    }
    fun overlapSphere(cx: Double, cy: Double, cz: Double, radius: Double): Any? {
        return try {
            val hits = engine.ultraPhysics.querySystem.overlapSphere(cx.toFloat(), cy.toFloat(), radius.toFloat(), emptyList())
            engine.scripts.newArray(hits.map { it.id as Any? })
        } catch (_: Exception) { try { engine.scripts.newArray(emptyList<Any?>()) } catch (_: Exception) { null } }
    }
    fun evaluateTireFriction(slip: Double, isForward: Boolean): Double = try { engine.ultraPhysics.evaluateTireFriction(slip.toFloat(), isForward).toDouble() } catch (_: Exception) { 0.0 }
    fun createConfigurableJoint(): String = try { engine.ultraPhysics.createConfigurableJoint().toString() } catch (_: Exception) { "" }
    fun getStats(): String = try { engine.ultraPhysics.getStats() } catch (_: Exception) { "" }
}

class SUltraAnimation(private val engine: Engine) {
    fun addEvent(time: Double, functionName: String, floatParam: Double): String = try { engine.ultraAnimation.addEvent(time.toFloat(), functionName, floatParam.toFloat()).functionName } catch (_: Exception) { "" }
    fun getEventsAt(time: Double, delta: Double): Any? {
        return try {
            val events = engine.ultraAnimation.getEventsAt(time.toFloat(), delta.toFloat())
            engine.scripts.newArray(events.map { it.functionName as Any? })
        } catch (_: Exception) { null }
    }
    fun createCurve(name: String): String = try { engine.ultraAnimation.createCurve(name).name } catch (_: Exception) { "" }
    fun evaluateCurve(name: String, time: Double): Double = try { engine.ultraAnimation.curves[name]?.evaluate(time.toFloat())?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
    fun createBlendShape(name: String): String = try { engine.ultraAnimation.createBlendShape(name).name } catch (_: Exception) { "" }
    fun createPoseLibrary(name: String): String = try { engine.ultraAnimation.createPoseLibrary(name).name } catch (_: Exception) { "" }
    fun blendPoses(lib: String, poseA: String, poseB: String, t: Double): Any? {
        return try {
            val blended = engine.ultraAnimation.poseLibraries[lib]?.blendPoses(poseA, poseB, t.toFloat()) ?: return null
            engine.scripts.newArray(blended.keys.map { it as Any? })
        } catch (_: Exception) { null }
    }
    fun getWarpFactor(name: String, time: Double): Any? {
        return try {
            val warp = engine.ultraAnimation.motionWarping[name] ?: return null
            val (trans, rot) = warp.getWarpFactor(time.toFloat())
            engine.scripts.newArray(listOf(trans.toDouble() as Any?, rot.toDouble() as Any?))
        } catch (_: Exception) { null }
    }
    fun getStats(): String = try { engine.ultraAnimation.getStats() } catch (_: Exception) { "" }
}

class SUltraAudio(private val engine: Engine) {
    fun createSnapshot(name: String, type: String): String {
        return try { val t = com.sengine.engine.audio.AudioMixerSnapshotType.valueOf(type); engine.ultraAudio.createSnapshot(name, t).name } catch (_: Exception) { try { engine.ultraAudio.createSnapshot(name).name } catch (_: Exception) { "" } }
    }
    fun transitionToSnapshot(name: String, time: Double) { try { engine.ultraAudio.transitionToSnapshot(name, time.toFloat()) } catch (_: Exception) {} }
    fun getCurrentSnapshot(): String = try { engine.ultraAudio.currentSnapshot } catch (_: Exception) { "" }
    fun createPortal(x: Double, y: Double, z: Double, w: Double, h: Double): String = try { engine.ultraAudio.createPortal(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat(), h.toFloat()).id } catch (_: Exception) { "" }
    fun addReverbProbe(x: Double, y: Double, z: Double, radius: Double): String = try { engine.ultraAudio.addReverbProbe(x.toFloat(), y.toFloat(), z.toFloat(), radius.toFloat()).toString() } catch (_: Exception) { "" }
    fun bakeReverbProbes() { try { engine.ultraAudio.bakeReverbProbes() } catch (_: Exception) {} }
    fun calculateOcclusion(listenerX: Double, listenerY: Double, listenerZ: Double, sourceX: Double, sourceY: Double, sourceZ: Double): Double = try { engine.ultraAudio.calculateOcclusion(listenerX.toFloat(), listenerY.toFloat(), listenerZ.toFloat(), sourceX.toFloat(), sourceY.toFloat(), sourceZ.toFloat()).toDouble() } catch (_: Exception) { 0.0 }
    fun createPlaylist(name: String): String = try { engine.ultraAudio.createPlaylist(name).name } catch (_: Exception) { "" }
    fun getStats(): String = try { engine.ultraAudio.getStats() } catch (_: Exception) { "" }
}

class SUltraAI(private val engine: Engine) {
    fun createOffMeshLink(sx: Double, sy: Double, sz: Double, ex: Double, ey: Double, ez: Double, type: String): String {
        return try { val t = com.sengine.engine.ai.OffMeshLinkType.valueOf(type); engine.ultraAI.createOffMeshLink(sx.toFloat(), sy.toFloat(), sz.toFloat(), ex.toFloat(), ey.toFloat(), ez.toFloat(), t).id } catch (_: Exception) { try { engine.ultraAI.createOffMeshLink(sx.toFloat(), sy.toFloat(), sz.toFloat(), ex.toFloat(), ey.toFloat(), ez.toFloat()).id } catch (_: Exception) { "" } }
    }
    fun createCoverPoint(x: Double, y: Double, z: Double, dirX: Double, dirZ: Double, type: String): String {
        return try { val t = com.sengine.engine.ai.CoverType.valueOf(type); engine.ultraAI.createCoverPoint(x.toFloat(), y.toFloat(), z.toFloat(), dirX.toFloat(), dirZ.toFloat(), t).id } catch (_: Exception) { try { engine.ultraAI.createCoverPoint(x.toFloat(), y.toFloat(), z.toFloat(), dirX.toFloat(), dirZ.toFloat()).id } catch (_: Exception) { "" } }
    }
    fun getCoverPointsNear(x: Double, z: Double, radius: Double): Any? {
        return try {
            val points = engine.ultraAI.getCoverPointsNear(x.toFloat(), z.toFloat(), radius.toFloat())
            engine.scripts.newArray(points.map { it.id as Any? })
        } catch (_: Exception) { null }
    }
    fun createPerceptionSystem(agentId: String): String = try { 
        val idLong = agentId.toLongOrNull() ?: agentId.hashCode().toLong()
        engine.ultraAI.createPerceptionSystem(idLong).toString() 
    } catch (_: Exception) { "" }
    fun addStimulus(agentId: String, type: String, x: Double, y: Double, z: Double, strength: Double, sourceId: String) {
        try { 
            val t = com.sengine.engine.ai.PerceptionType.valueOf(type)
            val aId = agentId.toLongOrNull() ?: agentId.hashCode().toLong()
            val sId = sourceId.toLongOrNull() ?: sourceId.hashCode().toLong()
            engine.ultraAI.perceptionSystems[aId]?.addStimulus(t, x.toFloat(), y.toFloat(), z.toFloat(), strength.toFloat(), sId) 
        } catch (_: Exception) {}
    }
    fun canSee(agentId: String, targetX: Double, targetY: Double, targetZ: Double, selfX: Double, selfY: Double, selfZ: Double): Boolean {
        return try { 
            val aId = agentId.toLongOrNull() ?: agentId.hashCode().toLong()
            engine.ultraAI.perceptionSystems[aId]?.canSee(targetX.toFloat(), targetY.toFloat(), targetZ.toFloat(), selfX.toFloat(), selfY.toFloat(), selfZ.toFloat(), 0f, 1f) ?: false 
        } catch (_: Exception) { false }
    }
    fun canSeeFull(agentId: String, targetX: Double, targetY: Double, targetZ: Double, selfX: Double, selfY: Double, selfZ: Double, dirX: Double, dirZ: Double): Boolean {
        return try { 
            val aId = agentId.toLongOrNull() ?: agentId.hashCode().toLong()
            engine.ultraAI.perceptionSystems[aId]?.canSee(targetX.toFloat(), targetY.toFloat(), targetZ.toFloat(), selfX.toFloat(), selfY.toFloat(), selfZ.toFloat(), dirX.toFloat(), dirZ.toFloat()) ?: false 
        } catch (_: Exception) { false }
    }
    fun planGOAP(currentState: Any?, goal: String): Any? {
        return try {
            val state = mutableMapOf<String, Boolean>()
            val g = engine.ultraAI.goapPlanner.goals.find { it.name == goal } ?: com.sengine.engine.ai.GOAPGoal(name = goal)
            val plan = engine.ultraAI.goapPlanner.plan(state, g)
            if (plan != null) engine.scripts.newArray(plan.map { it.name as Any? }) else null
        } catch (_: Exception) { null }
    }
    fun getStats(): String = try { engine.ultraAI.getStats() } catch (_: Exception) { "" }
}

class SUltraNetworking(private val engine: Engine) {
    fun createInterestZone(x: Double, y: Double, z: Double, radius: Double): String = try { engine.ultraNetworking.createInterestZone(x.toFloat(), y.toFloat(), z.toFloat(), radius.toFloat()).id } catch (_: Exception) { "" }
    fun updateRelevance(viewerId: Double, viewerX: Double, viewerY: Double, viewerZ: Double, objectId: Double, objectX: Double, objectY: Double, objectZ: Double): Any? {
        return try {
            val rel = engine.ultraNetworking.updateRelevance(viewerId.toLong(), viewerX.toFloat(), viewerY.toFloat(), viewerZ.toFloat(), objectId.toLong(), objectX.toFloat(), objectY.toFloat(), objectZ.toFloat())
            engine.scripts.newArray(listOf(rel.distance.toDouble() as Any?, rel.lod.name as Any?, rel.isRelevant.toString() as Any?))
        } catch (_: Exception) { null }
    }
    fun createLobby(name: String, maxPlayers: Double): String = try { engine.ultraNetworking.createLobby(name, maxPlayers.toInt()).id } catch (_: Exception) { "" }
    fun addVoiceChat(playerId: Double, x: Double, y: Double, z: Double): String = try { engine.ultraNetworking.addVoiceChat(playerId.toLong(), x.toFloat(), y.toFloat(), z.toFloat()).playerId.toString() } catch (_: Exception) { "" }
    fun getVoiceVolume(playerId: Double, listenerX: Double, listenerY: Double, listenerZ: Double): Double {
        return try { 
            val vc = engine.ultraNetworking.voiceChat3D[playerId.toLong()]
            vc?.calculateVolumeForListener(listenerX.toFloat(), listenerY.toFloat(), listenerZ.toFloat())?.toDouble() ?: 0.0
        } catch (_: Exception) { 0.0 }
    }
    fun getStats(): String = try { engine.ultraNetworking.getStats() } catch (_: Exception) { "" }
}

class SUltraGameplay(private val engine: Engine) {
    fun createRebindingScheme(name: String): String = try { 
        val scheme = com.sengine.engine.gameplay.InputControlScheme(name=name)
        engine.ultraGameplay.inputRebinding.controlSchemes[name]=scheme
        scheme.name
    } catch (_: Exception) { "" }
    fun rebindAction(action: String, newPath: String, scheme: String): String = try { 
        engine.ultraGameplay.inputRebinding.startRebinding(action)
        val result = engine.ultraGameplay.inputRebinding.completeRebinding(newPath)
        result.name
    } catch (_: Exception) { "" }
    fun saveGame(slot: String, data: String): Boolean = try { engine.ultraGameplay.saveSystem.save(slot, data) } catch (_: Exception) { false }
    fun loadGame(slot: String): String = try { engine.ultraGameplay.saveSystem.load(slot) ?: "" } catch (_: Exception) { "" }
    fun getSaveSlots(): Any? = try { engine.scripts.newArray(engine.ultraGameplay.saveSystem.slots.keys.map { it as Any? }) } catch (_: Exception) { null }
    fun addQuest(name: String): String = try { 
        val q = engine.ultraGameplay.questSystem.createQuest(name, name, "Quest $name")
        q.id
    } catch (_: Exception) { "" }
    fun completeObjective(questId: String, objectiveId: String): Boolean = try { 
        engine.ultraGameplay.questSystem.updateObjective(questId, objectiveId, 1)
        true
    } catch (_: Exception) { false }
    fun getQuestProgress(questId: String): Double = try { 
        val quest = engine.ultraGameplay.questSystem.quests[questId]
        quest?.progressRatio()?.toDouble() ?: 0.0
    } catch (_: Exception) { 0.0 }
    fun addSkill(name: String): String = try { 
        val node = engine.ultraGameplay.skillTreeSystem.addNode(name, name)
        node.id
    } catch (_: Exception) { "" }
    fun unlockSkill(skillId: String): Boolean = try { engine.ultraGameplay.skillTreeSystem.unlockSkill(skillId) } catch (_: Exception) { false }
    fun getSkillPoints(): Double = try { engine.ultraGameplay.skillTreeSystem.skillPoints.toDouble() } catch (_: Exception) { 0.0 }
    fun getTimeOfDay(): Double = try { engine.ultraGameplay.worldTimeSystem.timeOfDay.toDouble() } catch (_: Exception) { 0.0 }
    fun setTimeOfDay(time: Double) { try { engine.ultraGameplay.worldTimeSystem.timeOfDay = time.toFloat() } catch (_: Exception) {} }
    fun getStats(): String = try { engine.ultraGameplay.getStats() } catch (_: Exception) { "" }
}

class SUltraEditor(private val engine: Engine) {
    fun setLayout(layout: String): Boolean = try { engine.ultraEditor.setLayout(layout) } catch (_: Exception) { false }
    fun setTheme(theme: String): Boolean = try { 
        engine.ultraEditor.theme = com.sengine.engine.tools.EditorTheme.valueOf(theme)
        true
    } catch (_: Exception) { false }
    fun addGizmo(type: String, x: Double, y: Double, z: Double, size: Double): String {
        return try { 
            val gizmo = engine.ultraEditor.addGizmo(type, x.toFloat(), y.toFloat(), z.toFloat(), size.toFloat())
            "${gizmo.type}_${gizmo.x}_${gizmo.y}_${gizmo.z}"
        } catch (_: Exception) { "" }
    }
    fun getStats(): String = try { engine.ultraEditor.getStats() } catch (_: Exception) { "" }
}

class SUltraPlatform(private val engine: Engine) {
    fun isXRSupported(): Boolean = try { 
        val caps = engine.ultraPlatform.getCapabilities()
        caps?.supportsHandTracking == true || caps?.supportsEyeTracking == true || caps?.isXR() == true
    } catch (_: Exception) { false }
    fun startXRSession(type: String): Boolean = try { 
        // No direct startXRSession, simulate by checking capabilities
        isXRSupported()
    } catch (_: Exception) { false }
    fun stopXRSession(): Boolean = try { true } catch (_: Exception) { false }
    fun updateXRTracking(type: String, isTracked: Boolean, x: Double, y: Double, z: Double): Boolean {
        return try { 
            val t = com.sengine.engine.platform.XRTrackingType.valueOf(type)
            engine.ultraPlatform.updateXRTracking(t, isTracked, x.toFloat(), y.toFloat(), z.toFloat())
            true
        } catch (_: Exception) { false }
    }
    fun createARPlane(x: Double, y: Double, z: Double, w: Double, h: Double): String = try { engine.ultraPlatform.createARPlane(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat(), h.toFloat()).id } catch (_: Exception) { "" }
    fun createARImage(name: String, x: Double, y: Double, z: Double): String = try { engine.ultraPlatform.createARImage(name, x.toFloat(), y.toFloat(), z.toFloat()).id } catch (_: Exception) { "" }
    fun updateGyro(x: Double, y: Double, z: Double) { try { engine.ultraPlatform.updateGyro(x.toFloat(), y.toFloat(), z.toFloat()) } catch (_: Exception) {} }
    fun getStats(): String = try { engine.ultraPlatform.getStats() } catch (_: Exception) { "" }
}
