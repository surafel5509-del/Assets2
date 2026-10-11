package com.sengine.engine

import com.sengine.engine.core.AudioSource
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Component
import com.sengine.engine.core.EngineConfig
import com.sengine.engine.core.EngineVersion
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.QualitySettings
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.TimeManager
import com.sengine.engine.physics.PhysicsWorld
import com.sengine.engine.render.View2D
import com.sengine.engine.script.ScriptSystem
import com.sengine.project.Project
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * S Engine Ultimate Edition - Professional Game Engine Core
 * Unity 6 + Unreal Engine 5 class architecture
 * 
 * Features:
 * - Universal Render Pipeline + HDRP + Forward/Deferred
 * - Advanced Physics with joints, vehicles, character controller, cloth
 * - DOTS-like ECS + Job System
 * - Animator Controller with state machines, blend trees, IK
 * - NavMesh + Behavior Trees + Crowd
 * - Audio Mixer + Spatial Audio + Reverb Zones
 * - VFX Graph + Advanced Particle System
 * - Terrain System + Water + Skybox + Volumetric Fog
 * - Input System v2 (action-based)
 * - Networking (Netcode)
 * - Profiling + Frame Debugger
 * - Prefab System + Object Pooling
 * - Material System (PBR)
 * - Post Processing Stack v3
 */

class Engine(val project: Project, initialScene: Scene) {

    enum class Mode { EDIT, PLAY, PAUSED }

    interface Listener {
        fun onLog(level: Int, message: String) {}
        fun onModeChanged(mode: Mode) {}
        fun onSceneReplaced() {}
        fun onProfilerUpdate() {}
    }

    val lock = Any()
    @Volatile var scene: Scene = initialScene
        private set
    @Volatile var mode = Mode.EDIT
        private set

    // ==================== Core Systems ====================
    val input = Input()
    val inputSystemV2 = com.sengine.engine.input.InputSystemV2()
    val physics = PhysicsWorld()
    val physics3D = com.sengine.engine.physics.PhysicsWorld3D()
    val physicsAdvanced = com.sengine.engine.physics.advanced.JointSystem()
    val broadphase = com.sengine.engine.physics.advanced.BroadphaseSAP()
    val animation = com.sengine.engine.anim.AnimationSystem(project)
    val animatorControllerSystem = com.sengine.engine.anim.advanced.AnimatorStateMachine()
    val scripts = ScriptSystem(this)
    val audio = AudioSystem(project)
    val audioPro = com.sengine.engine.audio.AudioSystemPro()
    val gameView = View2D()

    // ==================== Ultimate Edition Systems ====================
    val config = EngineConfig()
    val timeManager = TimeManager()
    val ecs = com.sengine.engine.ecs.EntityManager()
    val jobSystem = com.sengine.engine.jobs.JobSystem.main
    val materialLibrary = com.sengine.engine.render.MaterialLibrary()
    val pipelineManager = com.sengine.engine.render.pipeline.PipelineManager()
    val lightingSystem = com.sengine.engine.render.LightingSystem()
    val lightProbeSystem = com.sengine.engine.render.LightProbeGroup()
    val reflectionProbeSystem = com.sengine.engine.render.ReflectionProbeSystem()
    val postProcessStack = com.sengine.engine.render.PostProcessStack()
    val skyboxSystem = com.sengine.engine.render.SkyboxSystem()
    val terrainSystem = com.sengine.engine.terrain.TerrainSystem()
    val navMesh = com.sengine.engine.ai.NavMesh()
    val crowdSystem = com.sengine.engine.ai.CrowdSimulation(navMesh)
    val profiler = com.sengine.engine.profiling.ProfilerSystem()
    val memoryProfiler = com.sengine.engine.profiling.MemoryProfiler()
    val frameDebugger = com.sengine.engine.profiling.FrameDebugger()
    val networkManager = com.sengine.engine.net.NetworkManager()
    val prefabManager = com.sengine.engine.core.PrefabManager()
    var objectPoolManager: com.sengine.engine.core.GameObjectPoolManager? = null

    // VFX
    val vfxSystems = mutableMapOf<Long, com.sengine.engine.vfx.ParticleSystemV2>()

    // Physics Materials
    val physicsMaterials = mutableMapOf<String, com.sengine.engine.physics.advanced.PhysicsMaterial>()

    // AI
    val behaviorTrees = mutableMapOf<Long, com.sengine.engine.ai.BehaviorTree>()

    // ==================== Ultimate 6.1 Quantum Systems ====================
    // Rendering Quantum
    val rayTracingSystem = com.sengine.engine.render.RayTracingSystem()
    val giSystem = com.sengine.engine.render.GlobalIlluminationSystem()
    val naniteSystem = com.sengine.engine.render.VirtualizedGeometrySystem()
    val virtualTextureSystem = com.sengine.engine.render.VirtualTextureSystem()
    val decalSystemV2 = com.sengine.engine.render.DecalSystemV2()
    val lightmappingSystem = com.sengine.engine.render.LightmappingSystem()
    val hairSystem = com.sengine.engine.render.HairSystem()

    // Physics Quantum
    val chaosDestruction = com.sengine.engine.physics.ChaosDestructionSystem()
    val fluidSystem = com.sengine.engine.physics.SPHFluidSystem()
    val softBodySystem = com.sengine.engine.physics.SoftBodySystem()

    // World Quantum
    val worldPartition = com.sengine.engine.world.WorldPartitionSystem()
    val splineSystem = com.sengine.engine.world.SplineSystem()
    val foliageSystem = com.sengine.engine.world.FoliageSystem()
    val pcgSystem = com.sengine.engine.world.PCGSystem()

    // Animation Quantum
    val controlRigSystem = com.sengine.engine.anim.advanced.ControlRigSystem()
    val motionMatchingSystem = com.sengine.engine.anim.advanced.MotionMatchingSystem()

    // XR Quantum
    val xrSystem = com.sengine.engine.xr.XRSystem()

    // AI Quantum
    val eqsSystem = com.sengine.engine.ai.EQSSystem()
    val utilityAI = com.sengine.engine.ai.UtilityAI()
    val mlAgentsSystem = com.sengine.engine.ai.MLAgentsSystem()

    // Gameplay Quantum
    val dialogueSystem = com.sengine.engine.gameplay.DialogueSystem()
    val questSystem = com.sengine.engine.gameplay.QuestSystemV2()
    val inventorySystem = com.sengine.engine.gameplay.InventorySystemV2()
    val skillTreeSystem = com.sengine.engine.gameplay.SkillTreeSystem()
    val worldTimeSystem = com.sengine.engine.gameplay.WorldTimeSystem()
    val oceanSystem = com.sengine.engine.gameplay.OceanFFTSystem()

    // Core Quantum
    val computeShaderSystem = com.sengine.engine.core.ComputeShaderSystem()
    val moddingSystem = com.sengine.engine.core.ModdingSystem()

    // Tools Quantum
    val levelEditorTools = com.sengine.engine.tools.LevelEditorTools()

    // Networking Quantum
    val rollbackNetcode = com.sengine.engine.net.RollbackNetcode()
    val lagCompensation = com.sengine.engine.net.LagCompensationSystem()
    val antiCheatSystem = com.sengine.engine.net.AntiCheatSystem()

    // ==================== Ultimate 6.1.1 Titan - Unity 2D & Most Powerful ====================
    // 2D Systems
    val renderer2DSystem = com.sengine.engine.render.`2d`.Renderer2DSystem()
    val spriteSystemV2 = com.sengine.engine.render.`2d`.SpriteSystemV2()
    val physics2DV2 = com.sengine.engine.physics.`2d`.Physics2DV2()
    val tilemapSystemV2 = com.sengine.engine.tilemap.TilemapSystemV2()
    val spriteAnimationSystem = com.sengine.engine.anim.`2d`.SpriteAnimationSystem()
    val sortingSystem = com.sengine.engine.render.`2d`.SortingSystem()
    val lighting2DSystemV2 = com.sengine.engine.render.`2d`.Lighting2DSystemV2()
    val particleSystem2D = com.sengine.engine.vfx.ParticleSystem2D()
    val shaderGraph2D = com.sengine.engine.vfx.ShaderGraph2D()
    val cinemachine2DSystem = com.sengine.engine.cinemachine.Cinemachine2DSystem()

    // Most Powerful Tools
    val fullControlSystem = com.sengine.engine.core.FullControlSystem()
    val packageManagerSystem = com.sengine.engine.core.PackageManagerSystem()
    val hotReloadSystem = com.sengine.engine.core.HotReloadSystem()
    val profilerV2System = com.sengine.engine.profiling.ProfilerV2System()
    val memoryControlSystem = com.sengine.engine.profiling.MemoryControlSystem()
    val graphicsControlSystem = com.sengine.engine.profiling.GraphicsControlSystem()
    val spriteEditorTools = com.sengine.engine.tools.SpriteEditorTools()
    val tilemapEditorTools = com.sengine.engine.tools.TilemapEditorTools()

    // Time & Frame
    var time = 0.0; private set
    var frame = 0L; private set
    @Volatile var fps = 0f; private set
    @Volatile var scriptMs = 0f
    @Volatile var physicsMs = 0f
    @Volatile var renderMs = 0f
    @Volatile var animationMs = 0f
    @Volatile var vfxMs = 0f
    @Volatile var aiMs = 0f
    @Volatile var cullingMs = 0f
    @Volatile var drawCalls = 0
    @Volatile var triangles = 0
    @Volatile var batches = 0
    private var fpsAcc = 0f
    private var fpsFrames = 0

    val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private var snapshot: String? = null
    private var snapshotScene = ""
    private var pendingSceneLoad: String? = null

    val logs = ArrayDeque<String>()

    init {
        physics.listener = scripts
        physics3D.listener = scripts
        objectPoolManager = com.sengine.engine.core.GameObjectPoolManager(scene)
        // Bake navmesh for default scene
        try { navMesh.bake(0f, scene.objects.filter { it.tag == "Obstacle" || it.name.contains("Wall") }) } catch (_: Exception) {}
        // Initialize Quantum systems
        try {
            rayTracingSystem.initialize()
            giSystem.addLightmap("Default", 512, 512)
            worldPartition.initialize()
            pcgSystem.createDefaultGraphs()
            motionMatchingSystem.createLocomotionClips()
            questSystem.createSampleQuests()
            skillTreeSystem.createWarriorTree()
            computeShaderSystem.createDefaultShaders()
            oceanSystem.createDefaultOcean()
            dialogueSystem.createSampleDialogue()
            foliageSystem.addPrototype("Grass", com.sengine.engine.world.FoliageType.Grass, 200f)
            foliageSystem.addPrototype("Tree", com.sengine.engine.world.FoliageType.Tree, 5f)
        } catch (_: Exception) {}
        // Initialize Titan 2D & Most Powerful systems
        try {
            renderer2DSystem.createGlobalLight(0xFFAAAAAA.toInt(), 0.5f)
            spriteSystemV2.createAtlas("MainAtlas", 2048, 2048)
            tilemapSystemV2.createLayer("Ground", 100, 100)
            tilemapSystemV2.createLayer("Walls", 100, 100)
            spriteAnimationSystem.createLibrary("MainLibrary")
            sortingSystem.createLayer("Default", 0, true)
            lighting2DSystemV2.addLight(0f, 0f, 0xFFFFFFFF.toInt(), 1f, 10f)
            fullControlSystem.addHierarchyNode("Main Camera")
            fullControlSystem.addProjectAsset("Assets/Textures", "MainAtlas", "Texture", true)
            profilerV2System.enabled = true
            memoryControlSystem.createPool("GameObjectPool", 100)
            graphicsControlSystem.setQualityLevel(3)
            spriteEditorTools.createPalette("MainPalette")
            tilemapEditorTools.createBrush("DefaultBrush", 1)
            cinemachine2DSystem.createCamera("MainCamera", "Player", 10)
        } catch (_: Exception) {}
        log(0, "S Engine Most Powerful Edition v${EngineVersion.MAJOR}.${EngineVersion.MINOR}.${EngineVersion.PATCH} [${EngineVersion.CODENAME}] Initialized - TITAN")
        log(0, "Render Pipeline: ${config.pipeline} | Color Space: ${config.colorSpace} | Quality: ${config.quality.level} | 2D URP: Enabled")
        log(0, "Quantum Systems: RayTracing=${config.rayTracing} Nanite=${config.nanite} Lumen=${config.lumen} VT=${config.virtualTexture} Chaos=${config.chaosDestruction} WorldPartition=${config.worldPartition} XR=${config.xrSupport}")
        log(0, "Titan 2D Systems: Renderer2D=${renderer2DSystem.lights.size} Sprites=${spriteSystemV2.sprites.size} Tilemap=${tilemapSystemV2.layers.size} Physics2D=${physics2DV2.bodies.size} Sorting=${sortingSystem.layers.size}")
        log(0, "Most Powerful Tools: FullControl=${fullControlSystem.hierarchy.size} Packages=${packageManagerSystem.packages.size} ProfilerV2=${profilerV2System.frames.size} HotReload=${hotReloadSystem.watchedFiles.size}")
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

    fun replaceScene(s: Scene) {
        scene = s
        objectPoolManager = com.sengine.engine.core.GameObjectPoolManager(scene)
        listeners.forEach { it.onSceneReplaced() }
    }

    fun requestLoadScene(name: String) { pendingSceneLoad = name }

    fun log(level: Int, msg: String) {
        synchronized(logs) {
            logs.addLast(msg)
            while (logs.size > 500) logs.removeFirst()
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
        timeManager.reset()
        input.clear()
        setMode(Mode.PLAY)
        beginScene()
        log(0, "▶ Play: ${scene.name} | Pipeline: ${pipelineManager.active?.name} | Quality: ${config.quality.level}")
    }

    private fun beginScene() {
        profiler.beginSample("BeginScene")
        for (go in scene.objects) for (c in go.components) c.resetRuntime()
        physics.reset()
        physics3D.reset()
        audio.start()
        audioPro.listenerPos = floatArrayOf(0f, 0f, 0f)
        scene.updateTransforms()
        snapCameraToTarget()
        updateCamera3DFollow(10f)
        scene.updateTransforms()
        updateGameView()

        // Initialize ECS
        for (go in scene.objects) if (go.isActiveInHierarchy()) ecs.createFromGameObject(go)

        // Initialize VFX
        vfxSystems.clear()
        for (go in scene.objects) {
            go.get<com.sengine.engine.core.ParticleSystemComponent>()?.let { comp ->
                val ps = com.sengine.engine.vfx.ParticleSystemV2()
                ps.main.startLifetime.constant = comp.startLifetime
                ps.main.startSpeed.constant = comp.startSpeed
                ps.main.startSize.constant = comp.startSize
                ps.main.maxParticles = comp.maxParticles
                ps.emission.rateOverTime.constant = comp.emissionRate
                ps.main.loop = comp.loop
                vfxSystems[go.id] = ps
            }
        }

        // Initialize Behavior Trees
        behaviorTrees.clear()

        // Lighting setup
        lightingSystem.cullAndSetup(scene, com.sengine.engine.render.View3D().apply {
            widthPx = gameView.widthPx; heightPx = gameView.heightPx
            fov = 60f; near = 0.1f; far = 500f
        })

        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            go.get<AudioSource>()?.let { if (it.playOnStart) audio.play(it.clip, it.volume, it.loop) }
        }
        scripts.begin()
        profiler.endSample("BeginScene")
    }

    private fun endScene() {
        profiler.beginSample("EndScene")
        scripts.end()
        audio.stop()
        vfxSystems.clear()
        behaviorTrees.clear()
        profiler.endSample("EndScene")
    }

    private fun stopPlay() {
        endScene()
        val snap = snapshot
        if (snap != null) scene = SceneSerializer.fromJson(JSONObject(snap))
        snapshot = null
        input.clear()
        setMode(Mode.EDIT)
        listeners.forEach { it.onSceneReplaced() }
        log(0, "■ Stopped | Avg Frame: ${profiler.getAverageFrameTimeMs()}ms | FPS: ${profiler.fps}")
        log(0, profiler.dump())
    }

    // ---------------------------------------------------------------- loop
    fun tick(dt: Float) {
        profiler.beginSample("Engine.Tick")
        while (true) {
            val c = commands.poll() ?: break
            try { c() } catch (e: Exception) { log(2, "Engine error: ${e.message}") }
        }
        fpsAcc += dt; fpsFrames++
        if (fpsAcc >= 0.5f) { fps = fpsFrames / fpsAcc; fpsAcc = 0f; fpsFrames = 0 }

        when (mode) {
            Mode.PLAY -> runFrame(dt)
            Mode.EDIT -> {
                profiler.beginSample("EditMode")
                scene.updateTransforms()
                updateParticles(dt)
                updateAnimators(dt, false)
                updateVFX(dt, false)
                profiler.endSample("EditMode")
            }
            Mode.PAUSED -> scene.updateTransforms()
        }
        profiler.endSample("Engine.Tick")
        profiler.endFrame(dt)
        listeners.forEach { it.onProfilerUpdate() }
    }

    private fun runFrame(dt0: Float) {
        val dt = dt0.coerceAtMost(timeManager.maximumDeltaTime)
        timeManager.update(dt)
        time += dt; frame++
        scene.updateTransforms()
        updateGameView()
        input.beginFrame(gameView)
        inputSystemV2.update()

        // Scripting
        profiler.beginSample("Scripting")
        val t0 = System.nanoTime()
        scripts.update(dt)
        val t1 = System.nanoTime()
        profiler.endSample("Scripting")

        // Physics
        profiler.beginSample("Physics")
        physics.step(scene, dt)
        physics3D.step(scene, dt)
        physicsAdvanced.update(scene, dt)
        // Character controllers
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            go.get<com.sengine.engine.core.CharacterControllerComponent>()?.let { cc ->
                // Character controller handled via physics
            }
            go.get<com.sengine.engine.core.WheelColliderComponent>()?.let { wc ->
                // Wheel collider update
            }
        }
        val t2 = System.nanoTime()
        profiler.endSample("Physics")

        // AI
        profiler.beginSample("AI")
        val tAI0 = System.nanoTime()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            go.get<com.sengine.engine.core.NavMeshAgentComponent>()?.let { agentComp ->
                val agent = com.sengine.engine.ai.NavMeshAgent().apply {
                    speed = agentComp.speed
                    angularSpeed = agentComp.angularSpeed
                    acceleration = agentComp.acceleration
                    stoppingDistance = agentComp.stoppingDistance
                }
                agent.update(go, dt)
            }
        }
        crowdSystem.update(dt)
        for ((_, bt) in behaviorTrees) bt.tick(dt)
        val tAI1 = System.nanoTime()
        profiler.endSample("AI")

        scriptMs = scriptMs * 0.9f + (t1 - t0) / 1e6f * 0.1f
        physicsMs = physicsMs * 0.9f + (t2 - t1) / 1e6f * 0.1f
        aiMs = aiMs * 0.9f + (tAI1 - tAI0) / 1e6f * 0.1f

        cleanupDestroyed()
        scene.updateTransforms()
        ecs.update(dt)

        // Camera
        profiler.beginSample("Camera")
        updateCameraFollow(dt)
        updateCamera3DFollow(dt)
        updateGameView()
        // Update audio listener to main camera
        mainCamera3D()?.let { cam -> audioPro.updateListener(cam) }
            ?: mainCamera()?.let { cam -> /* 2D listener */ }
        profiler.endSample("Camera")

        // Animation
        profiler.beginSample("Animation")
        val tAnim0 = System.nanoTime()
        updateAnimators(dt, true)
        updateAnimatorControllers(dt)
        val tAnim1 = System.nanoTime()
        animationMs = animationMs * 0.9f + (tAnim1 - tAnim0) / 1e6f * 0.1f
        profiler.endSample("Animation")

        // VFX
        profiler.beginSample("VFX")
        val tVFX0 = System.nanoTime()
        updateParticles(dt)
        updateVFX(dt, true)
        val tVFX1 = System.nanoTime()
        vfxMs = vfxMs * 0.9f + (tVFX1 - tVFX0) / 1e6f * 0.1f
        profiler.endSample("VFX")

        // Culling & Lighting
        profiler.beginSample("Culling")
        val tCull0 = System.nanoTime()
        // Frustum culling, occlusion culling, LOD
        updateLOD()
        lightingSystem.cullAndSetup(scene, com.sengine.engine.render.View3D().apply {
            widthPx = gameView.widthPx; heightPx = gameView.heightPx
        })
        val tCull1 = System.nanoTime()
        cullingMs = cullingMs * 0.9f + (tCull1 - tCull0) / 1e6f * 0.1f
        profiler.endSample("Culling")

        // Quantum Systems Update
        profiler.beginSample("Quantum")
        try {
            // Ray Tracing
            if (config.rayTracing && config.quality.rayTracing != com.sengine.engine.core.RayTracingQuality.Off) {
                rayTracingSystem.frameRayCount = 0
            }
            // GI
            giSystem.updateProbes(floatArrayOf(0.5f,0.5f,0.6f))
            // Nanite
            val camPos = mainCamera3D()?.world3 ?: floatArrayOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,1f)
            val camPosArr = floatArrayOf(camPos[12], camPos[13], camPos[14])
            val camDir = floatArrayOf(0f,0f,-1f)
            val frustum = Array(6){ floatArrayOf(0f,0f,0f,1f) }
            naniteSystem.cullMeshlets(camPosArr, camDir, frustum)
            // Virtual Textures
            virtualTextureSystem.requestTilesForView(camPosArr[0], camPosArr[1], camPosArr[2], 100f, 100f)
            virtualTextureSystem.updateStreaming()
            // Chaos Destruction
            chaosDestruction.update(dt)
            // Fluid
            fluidSystem.update(dt)
            softBodySystem.update(dt)
            // World Partition
            worldPartition.updatePlayerPosition(camPosArr[0], camPosArr[1], camPosArr[2])
            worldPartition.updateStreaming()
            // Foliage
            foliageSystem.update(dt, camPosArr[0], camPosArr[2])
            // Control Rig & Motion Matching
            controlRigSystem.evaluateAll()
            motionMatchingSystem.update(dt)
            // Hair
            hairSystem.update(dt)
            // XR
            if (config.xrSupport && xrSystem.enabled) { /* XR update */ }
            // AI Quantum
            mlAgentsSystem.update(dt)
            // Gameplay Quantum
            worldTimeSystem.update(dt)
            oceanSystem.update(dt)
            questSystem.update(dt)
            // Compute
            computeShaderSystem.executeQueue()
            // Modding
            moddingSystem.updatePlugins(dt)
            // Decals
            decalSystemV2.update(dt, camPosArr[0], camPosArr[2])
            // Rollback Netcode
            if (rollbackNetcode.players.isNotEmpty()) rollbackNetcode.update()
        } catch (_: Exception) {}
        profiler.endSample("Quantum")

        decayShake(dt)

        // Networking
        if (networkManager.isRunning) {
            val msgs = networkManager.receiveMessages()
            // handle messages
        }

        val load = pendingSceneLoad
        if (load != null) {
            pendingSceneLoad = null
            val snap = snapshot
            val fromSnapshot = snap != null && load == snapshotScene
            if (fromSnapshot || project.sceneExists(load)) {
                endScene()
                scene = if (fromSnapshot) SceneSerializer.fromJson(JSONObject(snap!!)) else project.loadScene(load)
                objectPoolManager = com.sengine.engine.core.GameObjectPoolManager(scene)
                listeners.forEach { it.onSceneReplaced() }
                beginScene()
                log(0, "Loaded scene $load")
            } else log(2, "Scene not found: $load")
        }
    }

    private fun cleanupDestroyed() {
        if (scene.objects.none { it.destroyed }) return
        val dead = scene.objects.filter { it.destroyed || isUnderDestroyed(it) }
        for (d in dead) {
            d.destroyed = true
            scripts.onDestroyed(d)
            vfxSystems.remove(d.id)
            behaviorTrees.remove(d.id)
            physicsAdvanced.removeJoints(d)
        }
        scene.objects.removeAll(dead.toSet())
        listeners.forEach { it.onSceneReplaced() }
    }

    private fun isUnderDestroyed(go: GameObject): Boolean {
        var p = go.parent
        while (p != null) { if (p.destroyed) return true; p = p.parent }
        return false
    }

    fun mainCamera3D(): GameObject? = scene.objects.firstOrNull { it.isActiveInHierarchy() && it.get<com.sengine.engine.core.Camera3D>() != null }
    fun mainCamera(): GameObject? = scene.objects.firstOrNull { it.isActiveInHierarchy() && it.get<Camera2D>() != null }

    private fun updateAnimators(dt: Float, playing: Boolean) {
        for (go in scene.objects) {
            val a = go.getAny<com.sengine.engine.core.Animator>() ?: continue
            val sr = go.getAny<com.sengine.engine.core.SpriteRenderer>() ?: continue
            if (playing && !go.isActiveInHierarchy()) continue
            animation.update(a, sr, dt, playing)
        }
    }

    private fun updateAnimatorControllers(dt: Float) {
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val ctrlComp = go.get<com.sengine.engine.core.AnimatorControllerComponent>() ?: continue
            // Update state machine
            // This would load controller asset and tick
        }
    }

    private fun updateVFX(dt: Float, playing: Boolean) {
        for ((_, ps) in vfxSystems) if (playing) ps.update(dt)
    }

    private fun updateLOD() {
        val camPos = mainCamera3D()?.world3 ?: mainCamera()?.let { go ->
            floatArrayOf(go.world.tx, go.world.ty, 0f).let {
                val m = com.sengine.engine.math.Mat4.identity()
                m[12] = it[0]; m[13] = it[1]; m[14] = it[2]
                m
            }
        } ?: com.sengine.engine.math.Mat4.identity()

        val camX = camPos[12]; val camY = camPos[13]; val camZ = camPos[14]

        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            go.get<com.sengine.engine.core.LODGroup>()?.let { lodGroup ->
                val w = go.world3
                val dx = w[12] - camX; val dy = w[13] - camY; val dz = w[14] - camZ
                val dist = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                // LOD selection based on distance and quality bias
                val bias = config.quality.lodBias
                // Simplified LOD - would switch meshes
            }
        }
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

    fun release() {
        synchronized(lock) {
            if (mode != Mode.EDIT) endScene()
            audio.stop()
            jobSystem.shutdown()
        }
    }

    // ==================== Ultimate Edition API ====================

    fun setQualityLevel(level: QualitySettings) {
        config.quality = level
        log(0, "Quality set to ${level.level}: renderScale=${level.renderScale} shadows=${level.shadowQuality} MSAA=${level.msaa}")
    }

    fun getProfilerData(): String = profiler.dump()

    fun raycastAll(origin: FloatArray, direction: FloatArray, maxDistance: Float): List<GameObject> {
        val hits = mutableListOf<Pair<Float, GameObject>>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<com.sengine.engine.core.Collider3D>() ?: continue
            // simplified raycast
            val t = physics3D.raycast(scene, origin[0], origin[1], origin[2], direction[0], direction[1], direction[2], maxDistance)
            if (t != null) {
                val w = go.world3
                val dx = w[12] - origin[0]; val dy = w[13] - origin[1]; val dz = w[14] - origin[2]
                val dist = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                hits.add(dist to go)
            }
        }
        return hits.sortedBy { it.first }.map { it.second }
    }
}
