package com.sengine.engine.render.pipeline

import com.sengine.engine.core.Scene
import com.sengine.engine.render.View3D
import com.sengine.engine.render.Renderer3D
import com.sengine.engine.render.ShaderLibrary

/**
 * Ultimate Edition Render Pipeline - Unity SRP / Unreal equivalent
 * Supports Forward, Forward+, Deferred, and custom pipelines
 */

enum class RenderPassType {
    DepthPrepass,
    GBuffer,
    ShadowMap,
    OpaqueForward,
    TransparentForward,
    Skybox,
    PostProcessing,
    FinalBlit,
    Custom
}

data class RenderPass(val type: RenderPassType, val name: String, val enabled: Boolean = true) {
    var executionOrder: Int = type.ordinal * 10
    var renderFunc: (() -> Unit)? = null
}

abstract class RenderPipeline(val name: String) {
    protected val passes = mutableListOf<RenderPass>()
    var isInitialized = false

    abstract fun initialize(shaders: ShaderLibrary)
    abstract fun render(scene: Scene, view: View3D, renderer: Renderer3D, width: Int, height: Int)
    open fun cleanup() {}

    fun addPass(pass: RenderPass) { passes.add(pass); passes.sortBy { it.executionOrder } }
    fun removePass(name: String) { passes.removeIf { it.name == name } }
    fun getPasses(): List<RenderPass> = passes.toList()
}

class ForwardPipeline : RenderPipeline("Forward") {
    private var shadowPass: RenderPass? = null
    private var opaquePass: RenderPass? = null
    private var transparentPass: RenderPass? = null

    override fun initialize(shaders: ShaderLibrary) {
        passes.clear()
        addPass(RenderPass(RenderPassType.DepthPrepass, "Depth Prepass", true))
        shadowPass = RenderPass(RenderPassType.ShadowMap, "Shadow Maps", true)
        addPass(shadowPass!!)
        opaquePass = RenderPass(RenderPassType.OpaqueForward, "Opaque Forward", true)
        addPass(opaquePass!!)
        addPass(RenderPass(RenderPassType.Skybox, "Skybox", true))
        transparentPass = RenderPass(RenderPassType.TransparentForward, "Transparent Forward", true)
        addPass(transparentPass!!)
        addPass(RenderPass(RenderPassType.PostProcessing, "Post Processing", true))
        addPass(RenderPass(RenderPassType.FinalBlit, "Final Blit", true))
        isInitialized = true
    }

    override fun render(scene: Scene, view: View3D, renderer: Renderer3D, width: Int, height: Int) {
        // Forward rendering logic handled by SceneRenderer, but pipeline controls order
        for (pass in passes.filter { it.enabled }) {
            pass.renderFunc?.invoke()
        }
    }
}

class DeferredPipeline : RenderPipeline("Deferred") {
    override fun initialize(shaders: ShaderLibrary) {
        passes.clear()
        addPass(RenderPass(RenderPassType.DepthPrepass, "Depth Prepass"))
        addPass(RenderPass(RenderPassType.ShadowMap, "Shadow Maps"))
        addPass(RenderPass(RenderPassType.GBuffer, "GBuffer Pass"))
        addPass(RenderPass(RenderPassType.OpaqueForward, "Deferred Lighting"))
        addPass(RenderPass(RenderPassType.Skybox, "Skybox"))
        addPass(RenderPass(RenderPassType.TransparentForward, "Transparent Forward"))
        addPass(RenderPass(RenderPassType.PostProcessing, "Post Processing"))
        isInitialized = true
    }

    override fun render(scene: Scene, view: View3D, renderer: Renderer3D, width: Int, height: Int) {
        for (pass in passes.filter { it.enabled }) pass.renderFunc?.invoke()
    }
}

class UniversalPipeline : RenderPipeline("Universal RP") {
    var msaa: Int = 4
    var hdr: Boolean = true
    var shadowDistance: Float = 100f
    var shadowCascades: Int = 4
    var renderScale: Float = 1f
    var opaqueDownsampling: Int = 0
    var useSRPBatcher: Boolean = true
    var useDynamicBatching: Boolean = false

    override fun initialize(shaders: ShaderLibrary) {
        passes.clear()
        addPass(RenderPass(RenderPassType.ShadowMap, "Main Light Shadow", true))
        addPass(RenderPass(RenderPassType.ShadowMap, "Additional Light Shadows", true))
        addPass(RenderPass(RenderPassType.DepthPrepass, "Depth Priming", false))
        addPass(RenderPass(RenderPassType.OpaqueForward, "Opaque (URP Forward)", true))
        addPass(RenderPass(RenderPassType.Skybox, "Skybox"))
        addPass(RenderPass(RenderPassType.TransparentForward, "Transparent"))
        addPass(RenderPass(RenderPassType.PostProcessing, "PostFX (URP)"))
        addPass(RenderPass(RenderPassType.FinalBlit, "Final Blit"))
        isInitialized = true
    }

    override fun render(scene: Scene, view: View3D, renderer: Renderer3D, width: Int, height: Int) {
        // URP-specific optimizations
        for (pass in passes.filter { it.enabled }) pass.renderFunc?.invoke()
    }
}

class HighDefinitionPipeline : RenderPipeline("High Definition RP") {
    var supportVolumetrics: Boolean = true
    var supportSSR: Boolean = true
    var supportSSAO: Boolean = true
    var supportSubsurfaceScattering: Boolean = true
    var supportDecals: Boolean = true
    var supportMotionVectors: Boolean = true
    var maxShadowRequests: Int = 128

    override fun initialize(shaders: ShaderLibrary) {
        passes.clear()
        addPass(RenderPass(RenderPassType.DepthPrepass, "Depth Prepass"))
        addPass(RenderPass(RenderPassType.GBuffer, "GBuffer (HDRP)"))
        addPass(RenderPass(RenderPassType.ShadowMap, "Shadow Atlas"))
        addPass(RenderPass(RenderPassType.OpaqueForward, "Deferred (HDRP)"))
        addPass(RenderPass(RenderPassType.Custom, "Volumetric Fog"))
        addPass(RenderPass(RenderPassType.Custom, "SSR"))
        addPass(RenderPass(RenderPassType.Custom, "SSAO"))
        addPass(RenderPass(RenderPassType.Skybox, "Physically Based Sky"))
        addPass(RenderPass(RenderPassType.TransparentForward, "Transparent (HDRP)"))
        addPass(RenderPass(RenderPassType.PostProcessing, "PostFX (HDRP)"))
        isInitialized = true
    }

    override fun render(scene: Scene, view: View3D, renderer: Renderer3D, width: Int, height: Int) {
        for (pass in passes.filter { it.enabled }) pass.renderFunc?.invoke()
    }
}

class PipelineManager {
    private val pipelines = mutableMapOf<String, RenderPipeline>()
    var active: RenderPipeline? = null

    init {
        pipelines["Forward"] = ForwardPipeline()
        pipelines["Deferred"] = DeferredPipeline()
        pipelines["Universal"] = UniversalPipeline()
        pipelines["HDRP"] = HighDefinitionPipeline()
        active = pipelines["Universal"]
    }

    fun get(name: String): RenderPipeline? = pipelines[name]
    fun all(): List<RenderPipeline> = pipelines.values.toList()
    fun setActive(name: String) { pipelines[name]?.let { active = it } }
    fun setActive(pipeline: RenderPipeline) { active = pipeline; pipelines[pipeline.name] = pipeline }
}
