package com.sengine.engine

import com.sengine.engine.core.AudioSource
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Component
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.Steering
import com.sengine.engine.physics.PhysicsWorld
import com.sengine.engine.render.View2D
import com.sengine.engine.script.ScriptBudgetException
import com.sengine.engine.script.ScriptSystem
import com.sengine.project.Project
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The runtime core. All scene mutation happens while holding [lock];
 * simulation runs on the GL thread via [tick].
 */
class Engine(val project: Project, initialScene: Scene) {

    enum class Mode { EDIT, PLAY, PAUSED }

    interface Listener {
        fun onLog(level: Int, message: String) {}
        fun onModeChanged(mode: Mode) {}
        fun onSceneReplaced() {}
    }

    val lock = Any()
    @Volatile var scene: Scene = initialScene
        private set
    @Volatile var mode = Mode.EDIT
        private set

    val input = Input()
    val physics = PhysicsWorld()
    val physics3D = com.sengine.engine.physics.PhysicsWorld3D()
    val animation = com.sengine.engine.anim.AnimationSystem(project)
    private val modelClipCache = HashMap<String, Pair<Long, com.sengine.engine.anim.Animation3DClip?>>()
    val scripts = ScriptSystem(this)
    val ui = UISystem(this)
    val audio = AudioSystem(project)
    val gameView = View2D()

    var time = 0.0; private set
    var frame = 0L; private set
    /** Seconds of the most recent frame (after time scale). */
    var lastDt = 1f / 60f; private set
    /** Global time scale: 0 pauses, 0.5 slows down, 2 speeds up. */
    var timeScale = 1f
    @Volatile var fps = 0f; private set
    // profiler (milliseconds, smoothed)
    @Volatile var scriptMs = 0f
    @Volatile var physicsMs = 0f
    @Volatile var renderMs = 0f
    @Volatile var drawCalls = 0
    private var fpsAcc = 0f
    private var fpsFrames = 0

    val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private var snapshot: String? = null
    private var snapshotScene = ""
    private var pendingSceneLoad: String? = null
    @Volatile private var released = false

    val logs = ArrayDeque<String>()

    // object pools: templates pre-cloned once, reused via scene.obtain()/release()
    private val pools = HashMap<String, ArrayList<GameObject>>()
    private val poolOwner = HashMap<Long, String>()
    private val poolAttached = HashSet<Long>()

    init {
        physics.listener = scripts
        physics3D.listener = scripts
    }

    // ---------------------------------------------------------------- commands
    fun post(cmd: () -> Unit) { commands.add(cmd) }

    fun play() = post {
        when (mode) {
            Mode.EDIT -> startPlay()
            Mode.PAUSED -> setMode(Mode.PLAY)
            else -> {}
        }
    }

    fun pause() = post { if (mode == Mode.PLAY) setMode(Mode.PAUSED) }
    fun stop() = post { if (mode != Mode.EDIT) stopPlay() }
    fun stepFrame() = post { if (mode == Mode.PAUSED) runFrame(1f / 60f) }

    /** Replace the edited scene (editor only, call while holding lock). */
    fun replaceScene(s: Scene) {
        scene = s
        listeners.forEach { it.onSceneReplaced() }
    }

    fun requestLoadScene(name: String) { pendingSceneLoad = name }

    fun log(level: Int, msg: String) {
        synchronized(logs) {
            logs.addLast(msg)
            while (logs.size > 300) logs.removeFirst()
        }
        listeners.forEach { it.onLog(level, msg) }
    }

    private fun setMode(m: Mode) {
        mode = m
        listeners.forEach { it.onModeChanged(m) }
    }

    // ---------------------------------------------------------------- play mode
    private fun startPlay() {
        resetRuntimeState()
        snapshot = SceneSerializer.toJson(scene).toString()
        snapshotScene = scene.name
        setMode(Mode.PLAY)
        beginScene()
        log(0, "▶ Play: ${scene.name}")
    }

    /**
     * Clears every piece of mutable runtime state that a previous play session
     * could leave behind (time scale, profilers, physics accumulators, pending
     * scene loads, input, pools). Without this, a script that sets
     * `time.scale = 0` or queues `scene.load()` makes the *next* Play unusable.
     */
    private fun resetRuntimeState() {
        time = 0.0
        frame = 0
        timeScale = 1f
        lastDt = 1f / 60f
        fps = 0f
        fpsAcc = 0f
        fpsFrames = 0
        scriptMs = 0f
        physicsMs = 0f
        renderMs = 0f
        drawCalls = 0
        pendingSceneLoad = null
        input.clear()
        physics.reset()
        physics3D.reset()
        ui.reset()
        clearPools()
    }

    private fun beginScene() {
        clearPools()
        for (go in scene.objects) for (c in go.components) c.resetRuntime()
        physics.reset()
        physics3D.reset()
        audio.start()
        scene.updateTransforms()
        snapCameraToTarget()
        updateCamera3DFollow(10f)
        scene.updateTransforms()
        updateGameView()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            go.get<AudioSource>()?.let { if (it.playOnStart) audio.play(it.clip, it.volume, it.loop) }
        }
        scripts.begin()
    }

    private fun endScene() {
        scripts.end()
        audio.stop()
    }

    private fun stopPlay() {
        endScene()
        val snap = snapshot
        if (snap != null) scene = SceneSerializer.fromJson(JSONObject(snap))
        snapshot = null
        snapshotScene = ""
        resetRuntimeState()
        setMode(Mode.EDIT)
        listeners.forEach { it.onSceneReplaced() }
        log(0, "■ Stopped")
    }

    // ---------------------------------------------------------------- loop
    /** Called on the GL thread with [lock] held. */
    fun tick(dt: Float) {
        while (true) {
            val c = commands.poll() ?: break
            try { c() }
            catch (e: ScriptBudgetException) { log(2, "Engine error: ${e.message}") }
            catch (e: Exception) { log(2, "Engine error: ${e.message}") }
        }
        fpsAcc += dt; fpsFrames++
        if (fpsAcc >= 0.5f) { fps = fpsFrames / fpsAcc; fpsAcc = 0f; fpsFrames = 0 }

        when (mode) {
            Mode.PLAY -> runFrame(dt)
            Mode.EDIT -> { scene.updateTransforms(); updateParticles(dt); updateAnimators(dt, false); updateModelAnimators(dt, false) }
            Mode.PAUSED -> scene.updateTransforms()
        }
    }

    private fun runFrame(dt0: Float) {
        val dt = (dt0 * timeScale).coerceAtMost(0.1f)
        lastDt = dt
        time += dt; frame++
        scene.updateTransforms()
        updateGameView()
        input.beginFrame(gameView)
        val t0 = System.nanoTime()
        updateSteering(dt)
        try { ui.update() }
        catch (e: ScriptBudgetException) { log(2, "UI script aborted: ${e.message}") }
        scripts.update(dt)
        val t1 = System.nanoTime()
        physics.step(scene, dt)
        physics3D.step(scene, dt)
        val t2 = System.nanoTime()
        scriptMs = scriptMs * 0.9f + (t1 - t0) / 1e6f * 0.1f
        physicsMs = physicsMs * 0.9f + (t2 - t1) / 1e6f * 0.1f
        cleanupDestroyed()
        scene.updateTransforms()
        updateCameraFollow(dt)
        updateCamera3DFollow(dt)
        updateAudioListener()
        updateGameView()
        updateParticles(dt)
        updateAnimators(dt, true)
        updateModelAnimators(dt, true)
        decayShake(dt)

        val load = pendingSceneLoad
        if (load != null) {
            pendingSceneLoad = null
            val snap = snapshot
            val fromSnapshot = snap != null && load == snapshotScene
            if (fromSnapshot || project.sceneExists(load)) {
                endScene()
                scene = if (fromSnapshot) SceneSerializer.fromJson(JSONObject(snap)) else project.loadScene(load)
                listeners.forEach { it.onSceneReplaced() }
                beginScene()
                log(0, "Loaded scene $load")
            } else log(2, "Scene not found: $load")
        }
    }

    /** Positions the 3D-audio listener on the active camera (right vector from its world matrix). */
    private fun updateAudioListener() {
        try {
            val cam3 = mainCamera3D()
            if (cam3 != null) {
                val w = cam3.world3
                val rx = w[0]; val ry = w[1]; val rz = w[2]
                audio.setListener(w[12], w[13], w[14], rx, ry, rz)
            } else {
                val cam2 = mainCamera()
                if (cam2 != null) {
                    val w = cam2.computeWorld()
                    audio.setListener(w.tx, w.ty, 0f, 1f, 0f, 0f)
                }
            }
            audio.update3d()
        } catch (_: Throwable) {
        }
    }

    private fun cleanupDestroyed() {
        if (scene.objects.none { it.destroyed }) return
        val dead = scene.objects.filter { it.destroyed || isUnderDestroyed(it) }
        for (d in dead) { d.destroyed = true; scripts.onDestroyed(d) }
        scene.objects.removeAll(dead.toSet())
        listeners.forEach { it.onSceneReplaced() }
    }

    private fun isUnderDestroyed(go: GameObject): Boolean {
        var p = go.parent
        while (p != null) { if (p.destroyed) return true; p = p.parent }
        return false
    }

    fun mainCamera3D(): GameObject? = scene.objects.firstOrNull { it.isActiveInHierarchy() && it.get<com.sengine.engine.core.Camera3D>() != null }

    // ---------------------------------------------------------------- pooling
    fun clearPools() {
        pools.clear(); poolOwner.clear(); poolAttached.clear()
    }

    /** Pre-creates [size] inactive clones of the template named [name]. */
    fun poolDefine(name: String, size: Int) {
        val template = scene.find(name) ?: run { log(1, "pool: template '$name' not found"); return }
        val list = pools.getOrPut(name) { ArrayList() }
        list.removeAll { it.destroyed }
        var n = size - list.size
        while (n-- > 0) {
            val copy = scene.duplicate(template, null)
            copy.active = false
            poolOwner[copy.id] = name
            list.add(copy)
        }
    }

    /** Grabs an object from the pool (grows it when empty) and places it at the given position. */
    fun poolObtain(name: String, x: Float, y: Float, z: Float?): GameObject? {
        val template = scene.find(name)
            ?: run { log(1, "pool: template '$name' not found"); return null }
        val list = pools.getOrPut(name) { ArrayList() }
        list.removeAll { it.destroyed }
        var go = list.firstOrNull { !it.active }
        var fresh = false
        if (go == null) {
            go = scene.duplicate(template, null)
            poolOwner[go.id] = name
            list.add(go)
            fresh = true
        }
        val target = go
        target.active = true
        if (z != null) target.setWorldPosition3(x, y, z) else target.setWorldPosition(x, y)
        for (d in listOf(target) + scene.objects.filter { target.isAncestorOf(it) }) {
            d.components.forEach { it.resetRuntime() }
        }
        scene.updateTransforms()
        if (fresh || target.id !in poolAttached) {
            poolAttached.add(target.id)
            scripts.attach(target)
        }
        return target
    }

    /** Returns a pooled object for reuse (deactivates it). */
    fun poolRelease(go: GameObject): Boolean {
        if (poolOwner[go.id] == null) return false
        go.active = false
        return true
    }

    // ---------------------------------------------------------------- steering
    private fun updateSteering(dt: Float) {
        if (dt <= 0f) return
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val st = go.getAny<Steering>() ?: continue
            if (!st.enabled) continue
            val rb3 = go.getAny<com.sengine.engine.core.Rigidbody3D>()
            val rb2 = go.getAny<com.sengine.engine.core.Rigidbody2D>()
            if (rb3 == null && rb2 == null) continue
            val threeD = rb3 != null
            if (!st.initialized) {
                st.initialized = true
                st.startX = go.x
                st.startZ = go.z
                st.wanderAngle = Random.nextFloat() * 360f
            }
            // plane coordinates: XZ for 3D, XY for 2D
            val w3 = if (threeD) go.computeWorld3() else null
            val w2 = if (threeD) null else go.computeWorld()
            val px = w3?.get(12) ?: w2!!.tx
            val pz = w3?.get(14) ?: w2!!.ty
            val cvx = rb3?.vx ?: rb2?.vx ?: 0f
            val cvp = if (threeD) rb3.vz else rb2?.vy ?: 0f
            // 2D: only steer vertically when gravity is off (top-down style)
            val steerY = !threeD && (scene.gravityY == 0f || (rb2?.gravityScale ?: 1f) == 0f)

            var dvx = 0f
            var dvp = 0f
            var arrived = false
            when (st.mode) {
                0, 2 -> { // seek / pursue
                    val t = if (st.target.isNotBlank()) scene.find(st.target) else null
                    if (t != null) {
                        val tw3 = if (threeD) t.computeWorld3() else null
                        val tw2 = if (threeD) null else t.computeWorld()
                        var tx = tw3?.get(12) ?: tw2!!.tx
                        var tp = tw3?.get(14) ?: tw2!!.ty
                        if (st.mode == 2) { // pursue: lead the target
                            val tvx = t.getAny<com.sengine.engine.core.Rigidbody3D>()?.vx
                                ?: t.getAny<com.sengine.engine.core.Rigidbody2D>()?.vx ?: 0f
                            val tvp = if (threeD) t.getAny<com.sengine.engine.core.Rigidbody3D>()?.vz ?: 0f
                                else t.getAny<com.sengine.engine.core.Rigidbody2D>()?.vy ?: 0f
                            val dx0 = tx - px; val dp0 = tp - pz
                            val dist0 = kotlin.math.sqrt(dx0 * dx0 + dp0 * dp0)
                            val lead = if (st.speed > 0.1f) dist0 / st.speed else 0f
                            tx += tvx * lead; tp += tvp * lead
                        }
                        val dx = tx - px; val dp = tp - pz
                        val dist = kotlin.math.sqrt(dx * dx + dp * dp)
                        if (st.mode == 0 && dist < 0.4f) {
                            arrived = true
                        } else if (dist > 1e-5f) {
                            var scale = st.speed
                            if (st.mode == 0 && st.slowingRadius > 0f && dist < st.slowingRadius) {
                                scale *= dist / st.slowingRadius
                            }
                            dvx = dx / dist * scale
                            dvp = dp / dist * scale
                        }
                    }
                }
                1 -> { // flee
                    val t = if (st.target.isNotBlank()) scene.find(st.target) else null
                    if (t != null) {
                        val tw3 = if (threeD) t.computeWorld3() else null
                        val tw2 = if (threeD) null else t.computeWorld()
                        val tx = tw3?.get(12) ?: tw2!!.tx
                        val tp = tw3?.get(14) ?: tw2!!.ty
                        val dx = px - tx; val dp = pz - tp
                        val dist = kotlin.math.sqrt(dx * dx + dp * dp)
                        if (dist > 1e-5f) { dvx = dx / dist * st.speed; dvp = dp / dist * st.speed }
                    }
                }
                3 -> { // wander
                    val turn = st.wanderTurn * st.wanderStrength * dt
                    st.wanderAngle += (Random.nextFloat() - 0.5f) * 2f * turn
                    val a = Math.toRadians(st.wanderAngle.toDouble())
                    dvx = (Math.cos(a) * st.speed).toFloat()
                    dvp = (Math.sin(a) * st.speed).toFloat()
                }
                4 -> { // patrol between start ± distance on X
                    if (px > st.startX + st.patrolDistance) st.patrolDir = -1
                    else if (px < st.startX - st.patrolDistance) st.patrolDir = 1
                    dvx = st.patrolDir * st.speed
                }
            }
            st.arrived = arrived

            // steer: accelerate the current velocity toward the desired one
            var nvx = cvx
            var nvp = cvp
            val ax = st.acceleration * dt
            val dvxd = dvx - cvx
            val dvdp = if (steerY || threeD) dvp - cvp else 0f
            val dl = kotlin.math.sqrt(dvxd * dvxd + dvdp * dvdp)
            if (dl > ax && dl > 1e-6f) {
                nvx += dvxd / dl * ax
                if (steerY || threeD) nvp += dvdp / dl * ax
            } else {
                nvx = dvx
                if (steerY || threeD) nvp = dvp
            }
            if (threeD) {
                rb3.vx = nvx; rb3.vz = nvp
            } else {
                rb2!!.vx = nvx
                if (steerY) rb2.vy = nvp
            }

            if (st.faceMovement && (dvx * dvx + dvp * dvp) > 1e-4f) {
                if (threeD) {
                    go.rotY = Math.toDegrees(Math.atan2(-dvx.toDouble(), -dvp.toDouble())).toFloat()
                } else {
                    go.rotation = Math.toDegrees(Math.atan2(dvp.toDouble(), dvx.toDouble())).toFloat() - 90f
                }
            }
        }
    }

    private fun updateAnimators(dt: Float, playing: Boolean) {
        for (go in scene.objects) {
            val a = go.getAny<com.sengine.engine.core.Animator>() ?: continue
            val sr = go.getAny<com.sengine.engine.core.SpriteRenderer>() ?: continue
            if (playing && !go.isActiveInHierarchy()) continue
            animation.update(a, sr, dt, playing)
        }
    }

    private fun loadModelClip(name: String): com.sengine.engine.anim.Animation3DClip? {
        if (name.isBlank()) return null
        val f = project.assetFile(name)
        val stamp = f.lastModified()
        modelClipCache[name]?.let { if (it.first == stamp) return it.second }
        val clip = try {
            val text = f.readText()
            com.sengine.engine.anim.Animation3DImporters.parse(name, text)
                ?: com.sengine.engine.anim.Animation3DClip.fromJson(JSONObject(text))
        } catch (_: Exception) { null }
        val valid = clip?.takeIf { it.validate() == null }
        modelClipCache[name] = stamp to valid
        return valid
    }

    private fun updateModelAnimators(dt: Float, playing: Boolean) {
        for (go in scene.objects) {
            val a = go.getAny<com.sengine.engine.core.ModelAnimator>() ?: continue
            if (playing && (!a.playing || !go.isActiveInHierarchy())) continue
            val clip = loadModelClip(a.clip) ?: continue
            if (playing) a.time += dt * a.speed
            val pose = clip.sample(a.time, a.loop)
            for ((target, value) in pose) {
                val node = animationTarget(go, target) ?: continue
                val (p, r, s) = value
                node.x = p[0]; node.y = p[1]; node.z = p[2]
                node.rotX = r[0]; node.rotY = r[1]; node.rotation = r[2]
                node.scaleX = s[0]; node.scaleY = s[1]; node.scaleZ = s[2]
            }
        }
    }

    private fun animationTarget(owner: GameObject, name: String): GameObject? {
        if (name.isBlank() || name.equals("root", ignoreCase = true) || name == owner.name) return owner
        return scene.objects.firstOrNull { it.parent === owner && it.name == name } ?: scene.find(name)
    }

    private fun updateCamera3DFollow(dt: Float) {
        val camGo = mainCamera3D() ?: return
        val cam = camGo.get<com.sengine.engine.core.Camera3D>()!!
        if (cam.follow.isBlank()) return
        val t = scene.find(cam.follow) ?: return
        val tw = t.world3
        val w = camGo.computeWorld3()
        val k = if (cam.smoothing <= 0f) 1f else (1f - exp(-cam.smoothing * dt))
        val gx = tw[12] + cam.offsetX; val gy = tw[13] + cam.offsetY; val gz = tw[14] + cam.offsetZ
        camGo.setWorldPosition3(w[12] + (gx - w[12]) * k, w[13] + (gy - w[13]) * k, w[14] + (gz - w[14]) * k)
    }

    fun shake(amount: Float) {
        mainCamera()?.getAny<Camera2D>()?.let { it.shake = maxOf(it.shake, amount) }
        mainCamera3D()?.getAny<com.sengine.engine.core.Camera3D>()?.let { it.shake = maxOf(it.shake, amount) }
    }

    private fun decayShake(dt: Float) {
        mainCamera()?.getAny<Camera2D>()?.let { it.shake = maxOf(0f, it.shake - dt * 2f * maxOf(1f, it.shake)) }
        mainCamera3D()?.getAny<com.sengine.engine.core.Camera3D>()?.let { it.shake = maxOf(0f, it.shake - dt * 2f * maxOf(1f, it.shake)) }
    }

    fun mainCamera(): GameObject? = scene.objects.firstOrNull { it.isActiveInHierarchy() && it.get<Camera2D>() != null }

    private fun snapCameraToTarget() {
        val camGo = mainCamera() ?: return
        val cam = camGo.get<Camera2D>()!!
        if (cam.follow.isBlank()) return
        val t = scene.find(cam.follow) ?: return
        camGo.setWorldPosition(t.world.tx, t.world.ty)
    }

    private fun updateCameraFollow(dt: Float) {
        val camGo = mainCamera() ?: return
        val cam = camGo.get<Camera2D>()!!
        if (cam.follow.isBlank()) return
        val t = scene.find(cam.follow) ?: return
        val w = camGo.computeWorld()
        val k = if (cam.smoothing <= 0f) 1f else (1f - exp(-cam.smoothing * dt))
        camGo.setWorldPosition(w.tx + (t.world.tx - w.tx) * k, w.ty + (t.world.ty - w.ty) * k)
    }

    fun updateGameView() {
        val camGo = mainCamera()
        if (camGo == null) {
            gameView.cx = 0f; gameView.cy = 0f; gameView.size = 5f
            return
        }
        val w = camGo.computeWorld()
        gameView.cx = w.tx; gameView.cy = w.ty
        gameView.size = camGo.get<Camera2D>()!!.size
    }

    fun backgroundColor(): Int = mainCamera()?.get<Camera2D>()?.background ?: 0xFF1B2533.toInt()

    // ---------------------------------------------------------------- particles
    private fun updateParticles(dt: Float) {
        for (go in scene.objects) {
            val pe = go.getAny<ParticleEmitter>() ?: continue
            val alive = go.isActiveInHierarchy() && pe.enabled
            val w = go.world
            if (alive && pe.emitting) pe.accumulator += pe.rate * dt
            var toEmit = pe.accumulator.toInt() + pe.pendingBurst
            pe.accumulator -= pe.accumulator.toInt()
            pe.pendingBurst = 0
            if (!alive) toEmit = 0
            while (toEmit-- > 0 && pe.particles.size < pe.maxParticles) {
                val ang = Math.toRadians((pe.direction + w.rotationDeg + (Random.nextFloat() - 0.5f) * pe.spread).toDouble())
                val sp = pe.speed * (0.6f + Random.nextFloat() * 0.8f)
                pe.particles.add(
                    ParticleEmitter.Particle(
                        w.tx, w.ty, (cos(ang) * sp).toFloat(), (sin(ang) * sp).toFloat(),
                        0f, pe.lifetime * (0.7f + Random.nextFloat() * 0.6f)
                    )
                )
            }
            val it = pe.particles.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.age += dt
                if (p.age >= p.life) { it.remove(); continue }
                p.vy += pe.gravity * dt
                p.x += p.vx * dt; p.y += p.vy * dt
            }
        }
    }

    fun findComponentOwner(c: Component): GameObject? = scene.objects.firstOrNull { c in it.components }

    /**
     * Shuts the engine down. The Rhino context is owned by the GL thread, so
     * teardown (and therefore `onStop()` callbacks) must be executed there.
     * [release] may be called from any thread: the work is queued for the next
     * [tick], while audio - which is thread-safe - is stopped immediately too.
     */
    fun release() {
        if (released) return
        released = true
        post {
            if (mode != Mode.EDIT) stopPlay() else endScene()
            audio.stop()
            listeners.clear()
        }
        audio.stop()
    }
}
