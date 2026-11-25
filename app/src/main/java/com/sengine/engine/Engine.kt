package com.sengine.engine

import com.sengine.engine.camera.CameraDirector
import com.sengine.engine.camera.CameraPath
import com.sengine.engine.camera.CameraPose
import com.sengine.engine.camera.CameraRig
import com.sengine.engine.core.AudioSource
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Component
import com.sengine.engine.core.DeterministicRandom
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.Steering
import com.sengine.engine.physics.JointSystem
import com.sengine.engine.physics.PhysicsWorld
import com.sengine.engine.physics.SoftBodyWorld
import com.sengine.engine.render.View2D
import com.sengine.engine.script.ScriptSystem
import com.sengine.project.GameStateStore
import com.sengine.project.Project
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

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
    val scripts = ScriptSystem(this)
    val audio = AudioSystem(project)
    val gameView = View2D()

    // ---------------------------------------------------------------- v3 systems
    /** Editable simulation/quality settings from project.json. */
    val settings get() = project.settings
    val joints = JointSystem()
    val softBodies = SoftBodyWorld()
    val gameState = GameStateStore(project.dir)
    val cameraDirector = CameraDirector()
    /** Seeded PRNG used by script `random()` — replay deterministic. */
    val scriptRng = DeterministicRandom(project.settings.deterministicSeed)
    private val simRng = DeterministicRandom(project.settings.deterministicSeed xor 0x5f3a7c1)
    var lastCastDistance = 0f
    private val camBaseSize = HashMap<Long, Float>()
    private val camBaseFov = HashMap<Long, Float>()
    private var panFrom: CameraPose? = null
    private var panTarget: CameraPose? = null
    private var panTime = 0f
    private var panDuration = 0f

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

    val logs = ArrayDeque<String>()

    // object pools: templates pre-cloned once, reused via scene.obtain()/release()
    private val pools = HashMap<String, ArrayList<GameObject>>()
    private val poolOwner = HashMap<Long, String>()
    private val poolAttached = HashSet<Long>()

    init {
        physics.listener = scripts
        physics3D.listener = scripts
        applySettings()
    }

    /** Applies project settings to both physics worlds and the joint solver. */
    fun applySettings() {
        val s = settings
        physics.clock.fixedDt = 1f / s.fixedHz
        physics.clock.maxSubSteps = s.maxSubSteps
        physics3D.clock.fixedDt = 1f / s.fixedHz
        physics3D.clock.maxSubSteps = s.maxSubSteps
        physics.solverIterations = s.solverIterations
        physics3D.solverIterations = s.solverIterations
        physics.broadphase = s.broadphase
        physics3D.broadphase = s.broadphase
        physics.cellSize = s.broadphaseCell
        physics3D.cellSize = s.broadphaseCell
        physics.onPostStep = { scene, dt ->
            joints.step(scene, dt, s.jointIterations)
            softBodies.step(dt)
        }
    }

    fun resetRandom() {
        scriptRng.reset(settings.deterministicSeed)
        simRng.reset(settings.deterministicSeed xor 0x5f3a7c1)
    }

    /** Re-attaches every script without leaving play mode (editor action). */
    fun hotReloadScripts() = post { scripts.hotReload() }

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
        snapshot = SceneSerializer.toJson(scene).toString()
        snapshotScene = scene.name
        time = 0.0; frame = 0
        input.clear()
        setMode(Mode.PLAY)
        beginScene()
        log(0, "▶ Play: ${scene.name}")
    }

    private fun beginScene() {
        applySettings()
        resetRandom()
        clearPools()
        joints.clear()
        softBodies.clear()
        for (go in scene.objects) for (c in go.components) c.resetRuntime()
        physics.reset()
        physics3D.reset()
        audio.start()
        scene.updateTransforms()
        snapCameraToTarget()
        updateCamera3DFollow(10f)
        scene.updateTransforms()
        updateGameView()
        initCameraRigs()
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
        input.clear()
        cameraDirector.clear()
        camBaseSize.clear(); camBaseFov.clear()
        panFrom = null; panTarget = null
        joints.clear(); softBodies.clear()
        setMode(Mode.EDIT)
        listeners.forEach { it.onSceneReplaced() }
        log(0, "■ Stopped")
    }

    // ---------------------------------------------------------------- loop
    /** Called on the GL thread with [lock] held. */
    fun tick(dt: Float) {
        while (true) {
            val c = commands.poll() ?: break
            try { c() } catch (e: Exception) { log(2, "Engine error: ${e.message}") }
        }
        fpsAcc += dt; fpsFrames++
        if (fpsAcc >= 0.5f) { fps = fpsFrames / fpsAcc; fpsAcc = 0f; fpsFrames = 0 }

        when (mode) {
            Mode.PLAY -> runFrame(dt)
            Mode.EDIT -> { scene.updateTransforms(); updateParticles(dt); updateAnimators(dt, false) }
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
        scripts.update(dt)
        val t1 = System.nanoTime()
        physics.step(scene, dt)
        physics3D.step(scene, dt)
        val t2 = System.nanoTime()
        scriptMs = scriptMs * 0.9f + (t1 - t0) / 1e6f * 0.1f
        physicsMs = physicsMs * 0.9f + (t2 - t1) / 1e6f * 0.1f
        cleanupDestroyed()
        scene.updateTransforms()
        updateCameraSystem(dt)
        updateAudioListener()
        updateGameView()
        updateParticles(dt)
        updateAnimators(dt, true)
        decayShake(dt)

        val load = pendingSceneLoad
        if (load != null) {
            pendingSceneLoad = null
            val snap = snapshot
            val fromSnapshot = snap != null && load == snapshotScene
            if (fromSnapshot || project.sceneExists(load)) {
                endScene()
                scene = if (fromSnapshot) SceneSerializer.fromJson(JSONObject(snap!!)) else project.loadScene(load)
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
                st.wanderAngle = simRng.nextFloat() * 360f
            }
            // plane coordinates: XZ for 3D, XY for 2D
            val w3 = if (threeD) go.computeWorld3() else null
            val w2 = if (threeD) null else go.computeWorld()
            val px = w3?.get(12) ?: w2!!.tx
            val pz = w3?.get(14) ?: w2!!.ty
            val cvx = rb3?.vx ?: rb2?.vx ?: 0f
            val cvp = if (threeD) rb3!!.vz else rb2?.vy ?: 0f
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
                    st.wanderAngle += (simRng.nextFloat() - 0.5f) * 2f * turn
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
                rb3!!.vx = nvx; rb3.vz = nvp
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

    // ================================================================ v3 camera system
    private fun cameras2D(): List<GameObject> =
        scene.objects.filter { it.isActiveInHierarchy() && it.get<Camera2D>() != null }
    private fun cameras3D(): List<GameObject> =
        scene.objects.filter { it.isActiveInHierarchy() && it.get<com.sengine.engine.core.Camera3D>() != null }

    /** Highest-priority active 3D camera; [CameraDirector.activeId] can override. */
    fun mainCamera3D(): GameObject? {
        val cams = cameras3D()
        if (cams.isEmpty()) return null
        cameraDirector.activeId?.let { id -> cams.firstOrNull { it.id == id }?.let { return it } }
        return cams.maxByOrNull { cameraDirector.rig(it.id).priority } ?: cams.first()
    }

    /** Highest-priority active 2D camera; falls back to the first one. */
    fun mainCamera(): GameObject? {
        val cams = cameras2D()
        if (cams.isEmpty()) return null
        cameraDirector.activeId?.let { id -> cams.firstOrNull { it.id == id }?.let { return it } }
        return cams.maxByOrNull { cameraDirector.rig(it.id).priority } ?: cams.first()
    }

    private fun syncRig2(go: GameObject, cam: Camera2D): CameraRig {
        val r = cameraDirector.rig(go.id)
        r.priority = cam.priority
        r.smoothing = cam.smoothing
        r.deadZoneW = cam.deadZoneW
        r.deadZoneH = cam.deadZoneH
        r.lookAhead = cam.lookAhead
        r.minZoom = cam.minimumZoom
        r.maxZoom = cam.maximumZoom
        r.traumaDecay = cam.traumaDecay
        r.shakeScale = cam.shakeScale
        r.bounds.apply {
            enabled = cam.boundsEnabled
            minX = cam.boundMinX; maxX = cam.boundMaxX
            minY = cam.boundMinY; maxY = cam.boundMaxY
            minZ = cam.boundMinY; maxZ = cam.boundMaxY
            viewWidth = 2f * cam.size * gameView.aspect
            viewHeight = 2f * cam.size
        }
        return r
    }

    private fun syncRig3(go: GameObject, cam: com.sengine.engine.core.Camera3D): CameraRig {
        val r = cameraDirector.rig(go.id)
        r.priority = cam.priority
        r.smoothing = cam.smoothing
        r.deadZoneW = cam.deadZoneW
        r.lookAhead = cam.lookAhead
        r.traumaDecay = cam.traumaDecay
        r.shakeScale = cam.shakeScale
        r.bounds.apply {
            enabled = cam.boundsEnabled
            minX = cam.boundMinX; maxX = cam.boundMaxX
            minY = -10000f; maxY = 10000f
            minZ = cam.boundMinZ; maxZ = cam.boundMaxZ
            viewWidth = 0f; viewHeight = 0f; viewDepth = 0f
        }
        return r
    }

    private fun initCameraRigs() {
        cameraDirector.clear()
        camBaseSize.clear(); camBaseFov.clear()
        panFrom = null; panTarget = null
        for (go in scene.objects) {
            val c2 = go.get<Camera2D>()
            if (c2 != null) {
                val w = go.computeWorld()
                syncRig2(go, c2).snap(w.tx, w.ty, 0f, 1f)
                camBaseSize[go.id] = c2.size
            }
            val c3 = go.get<com.sengine.engine.core.Camera3D>()
            if (c3 != null) {
                val w = go.computeWorld3()
                syncRig3(go, c3).snap(w[12], w[13], w[14], 1f)
                camBaseFov[go.id] = c3.fov
            }
        }
    }

    /** Runs cinematics, blends and follow logic once per frame. */
    private fun updateCameraSystem(dt: Float) {
        val progress = cameraDirector.update(dt)
        val path = cameraDirector.cinematicPath
        if (path != null && progress != null) {
            val p = path.sample(progress)
            applyCameraPose(p[0], p[1], p[2])
            return
        }
        if (panFrom != null && panTarget != null) {
            panTime += dt
            val t = if (panDuration <= 0f) 1f else (panTime / panDuration).coerceIn(0f, 1f)
            val blended = CameraRig.blend(panFrom!!, panTarget!!, t)
            applyCameraPose(blended.x, blended.y, blended.z)
            if (t >= 1f) {
                mainCamera()?.let { cameraDirector.rig(it.id).snap(blended.x, blended.y, blended.z) }
                mainCamera3D()?.let { cameraDirector.rig(it.id).snap(blended.x, blended.y, blended.z) }
                panFrom = null; panTarget = null
            }
            return
        }
        updateCameraFollow(dt)
        updateCamera3DFollow(dt)
        if (cameraDirector.isBlending) {
            val fromId = cameraDirector.blendFromId ?: return
            val active = mainCamera3D() ?: mainCamera() ?: return
            val from = cameraDirector.rig(fromId)
            val to = cameraDirector.rig(active.id)
            val p = CameraRig.blend(from.pose, to.pose, cameraDirector.blendProgress)
            applyCameraPose(p.x, p.y, p.z)
        }
    }

    private fun applyCameraPose(x: Float, y: Float, z: Float) {
        val c3 = mainCamera3D()
        if (c3 != null) { c3.setWorldPosition3(x, y, z); return }
        mainCamera()?.setWorldPosition(x, y)
    }

    private fun updateCamera3DFollow(dt: Float) {
        val camGo = mainCamera3D() ?: return
        val cam = camGo.get<com.sengine.engine.core.Camera3D>() ?: return
        val rig = syncRig3(camGo, cam)
        if (cam.follow.isBlank()) {
            rig.follow(rig.pose.x, rig.pose.y, rig.pose.z, 0f, 0f, dt)
            return
        }
        val t = scene.find(cam.follow) ?: return
        val tw = t.world3
        val rb = t.getAny<com.sengine.engine.core.Rigidbody3D>()
        val p = rig.follow(tw[12] + cam.offsetX, tw[13] + cam.offsetY, tw[14] + cam.offsetZ,
            rb?.vx ?: 0f, rb?.vy ?: 0f, dt, rb?.vz ?: 0f)
        camGo.setWorldPosition3(p.x + rig.shakeX, p.y + rig.shakeY, p.z)
        val base = camBaseFov[camGo.id] ?: cam.fov
        cam.fov = (base / p.zoom).coerceIn(10f, 150f)
    }

    private fun updateCameraFollow(dt: Float) {
        val camGo = mainCamera() ?: return
        val cam = camGo.get<Camera2D>() ?: return
        val rig = syncRig2(camGo, cam)
        if (cam.follow.isBlank()) {
            rig.follow(rig.pose.x, rig.pose.y, rig.pose.z, 0f, 0f, dt)
            return
        }
        val t = scene.find(cam.follow) ?: return
        val rb = t.getAny<com.sengine.engine.core.Rigidbody2D>()
        val p = rig.follow(t.world.tx, t.world.ty, rig.pose.z, rb?.vx ?: 0f, rb?.vy ?: 0f, dt)
        camGo.setWorldPosition(p.x + rig.shakeX, p.y + rig.shakeY)
        val base = camBaseSize[camGo.id] ?: cam.size
        cam.size = (base / p.zoom).coerceAtLeast(0.1f)
    }

    fun shake(amount: Float) = cameraShake(amount)

    /** Adds trauma to every active rig (quadratic shake) plus the legacy renderer jitter. */
    fun cameraShake(amount: Float) {
        for (go in cameras2D()) {
            syncRig2(go, go.get<Camera2D>()!!).addTrauma(amount)
            go.get<Camera2D>()?.shake = maxOf(go.get<Camera2D>()!!.shake, amount)
        }
        for (go in cameras3D()) {
            syncRig3(go, go.get<com.sengine.engine.core.Camera3D>()!!).addTrauma(amount)
            go.get<com.sengine.engine.core.Camera3D>()?.shake = maxOf(go.get<com.sengine.engine.core.Camera3D>()!!.shake, amount)
        }
    }

    private fun decayShake(dt: Float) {
        mainCamera()?.getAny<Camera2D>()?.let { it.shake = maxOf(0f, it.shake - dt * 2f * maxOf(1f, it.shake)) }
        mainCamera3D()?.getAny<com.sengine.engine.core.Camera3D>()?.let { it.shake = maxOf(0f, it.shake - dt * 2f * maxOf(1f, it.shake)) }
    }

    private fun snapCameraToTarget() {
        val camGo = mainCamera() ?: return
        val cam = camGo.get<Camera2D>() ?: return
        if (cam.follow.isBlank()) return
        val t = scene.find(cam.follow) ?: return
        camGo.setWorldPosition(t.world.tx, t.world.ty)
    }

    // ---------------------------------------------------------------- script camera API
    fun activeCamera2D(): GameObject? = mainCamera()
    fun activeCamera3D(): GameObject? = mainCamera3D()

    fun cameraPose(): FloatArray {
        val c3 = mainCamera3D()
        if (c3 != null) {
            val w = c3.computeWorld3()
            return floatArrayOf(w[12], w[13], w[14], cameraDirector.rig(c3.id).pose.zoom,
                c3.get<com.sengine.engine.core.Camera3D>()?.fov ?: 60f)
        }
        val c2 = mainCamera()
        if (c2 != null) {
            val w = c2.computeWorld()
            return floatArrayOf(w.tx, w.ty, 0f, cameraDirector.rig(c2.id).pose.zoom,
                c2.get<Camera2D>()?.size ?: 5f)
        }
        return floatArrayOf(0f, 0f, 0f, 1f, 60f)
    }

    fun activeCameraPriority(): Int {
        val go = mainCamera3D() ?: mainCamera() ?: return 0
        return cameraDirector.rig(go.id).priority
    }

    fun cameraSetBounds(minX: Float, minY: Float, maxX: Float, maxY: Float, minZ: Float, maxZ: Float) {
        for (go in cameras2D()) {
            val c = go.get<Camera2D>() ?: continue
            c.boundsEnabled = true
            c.boundMinX = minX; c.boundMaxX = maxX; c.boundMinY = minY; c.boundMaxY = maxY
            syncRig2(go, c)
        }
        for (go in cameras3D()) {
            val c = go.get<com.sengine.engine.core.Camera3D>() ?: continue
            c.boundsEnabled = true
            c.boundMinX = minX; c.boundMaxX = maxX; c.boundMinZ = minZ; c.boundMaxZ = maxZ
            syncRig3(go, c)
        }
    }

    fun cameraClearBounds() {
        for (go in cameras2D()) { val c = go.get<Camera2D>() ?: continue; c.boundsEnabled = false; syncRig2(go, c) }
        for (go in cameras3D()) { val c = go.get<com.sengine.engine.core.Camera3D>() ?: continue; c.boundsEnabled = false; syncRig3(go, c) }
    }

    fun cameraSetDeadZone(w: Float, h: Float) {
        for (go in cameras2D()) { val c = go.get<Camera2D>() ?: continue; c.deadZoneW = w; c.deadZoneH = h; syncRig2(go, c) }
        for (go in cameras3D()) { val c = go.get<com.sengine.engine.core.Camera3D>() ?: continue; c.deadZoneW = w; syncRig3(go, c) }
    }

    fun cameraSetLookAhead(v: Float) {
        for (go in cameras2D()) { val c = go.get<Camera2D>() ?: continue; c.lookAhead = v.coerceIn(0f, 2f); syncRig2(go, c) }
        for (go in cameras3D()) { val c = go.get<com.sengine.engine.core.Camera3D>() ?: continue; c.lookAhead = v.coerceIn(0f, 2f); syncRig3(go, c) }
    }

    fun cameraZoomTo(z: Float) {
        (mainCamera3D() ?: mainCamera())?.let { cameraDirector.rig(it.id).zoomTo(z) }
    }

    fun cameraSetFov(v: Float) { mainCamera3D()?.get<com.sengine.engine.core.Camera3D>()?.fov = v.coerceIn(10f, 150f) }

    fun cameraSnap() {
        val c2 = mainCamera()
        if (c2 != null) {
            val cam = c2.get<Camera2D>() ?: return
            val t = if (cam.follow.isNotBlank()) scene.find(cam.follow) else null
            val x = t?.world?.tx ?: c2.computeWorld().tx
            val y = t?.world?.ty ?: c2.computeWorld().ty
            c2.setWorldPosition(x, y)
            syncRig2(c2, cam).snap(x, y, 0f, cameraDirector.rig(c2.id).pose.zoom)
        }
        val c3 = mainCamera3D()
        if (c3 != null) {
            val w = c3.computeWorld3()
            syncRig3(c3, c3.get<com.sengine.engine.core.Camera3D>()!!).snap(w[12], w[13], w[14])
        }
    }

    fun cameraPanTo(x: Float, y: Float, duration: Float) {
        val p = cameraPose()
        panFrom = CameraPose(p[0], p[1], p[2], p[3])
        panTarget = CameraPose(x, y, p[2], p[3])
        panTime = 0f
        panDuration = duration.coerceAtLeast(0f)
    }

    fun cameraSetPriority(v: Int) {
        (mainCamera3D() ?: mainCamera())?.let { go ->
            go.get<Camera2D>()?.priority = v
            go.get<com.sengine.engine.core.Camera3D>()?.priority = v
            cameraDirector.rig(go.id).priority = v
        }
        cameraDirector.activeId = null
    }

    fun cameraActivate(name: String, duration: Float) {
        val go = scene.objects.firstOrNull { it.name == name && !it.destroyed &&
            (it.get<Camera2D>() != null || it.get<com.sengine.engine.core.Camera3D>() != null) } ?: return
        val previous = cameraDirector.activeId
        if (previous != null) {
            scene.findById(previous)?.let { from ->
                val w = from.computeWorld3()
                cameraDirector.rig(from.id).pose.x = w[12]
                cameraDirector.rig(from.id).pose.y = w[13]
                cameraDirector.rig(from.id).pose.z = w[14]
            }
        }
        cameraDirector.switchTo(go.id, duration)
    }

    fun cameraPlayPath(points: List<FloatArray>, duration: Float): Boolean {
        if (points.size < 2) return false
        cameraDirector.playPath(CameraPath(points), duration)
        return true
    }

    fun cameraStopPath() = cameraDirector.stopPath()

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
                val ang = Math.toRadians((pe.direction + w.rotationDeg + (simRng.nextFloat() - 0.5f) * pe.spread).toDouble())
                val sp = pe.speed * (0.6f + simRng.nextFloat() * 0.8f)
                pe.particles.add(
                    ParticleEmitter.Particle(
                        w.tx, w.ty, (cos(ang) * sp).toFloat(), (sin(ang) * sp).toFloat(),
                        0f, pe.lifetime * (0.7f + simRng.nextFloat() * 0.6f)
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

    fun release() {
        synchronized(lock) {
            if (mode != Mode.EDIT) endScene()
            audio.stop()
        }
    }
}
