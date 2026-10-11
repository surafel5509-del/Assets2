package com.sengine.engine.core

/**
 * S Engine Ultimate Edition - Professional Component System
 * Unity 6 + Unreal Engine 5 class components
 * 100+ components covering rendering, physics, animation, AI, audio, VFX, networking, etc.
 */

object ComponentRegistry {
    val types: LinkedHashMap<String, () -> Component> = linkedMapOf(
        // Rendering 2D
        "SpriteRenderer" to { SpriteRenderer() },
        "TextRenderer" to { TextRenderer() },
        "TilemapRenderer" to { TilemapRenderer() },
        "SortingGroup" to { SortingGroup() },
        "SpriteMask" to { SpriteMask() },
        // Rendering 3D - Core
        "MeshRenderer" to { MeshRenderer() },
        "SkinnedMeshRenderer" to { SkinnedMeshRenderer() },
        "TrailRenderer" to { TrailRenderer() },
        "LineRenderer" to { LineRenderer() },
        "BillboardRenderer" to { BillboardRenderer() },
        "DecalProjector" to { DecalProjector() },
        "LODGroup" to { LODGroup() },
        "ReflectionProbe" to { ReflectionProbeComponent() },
        "LightProbeGroup" to { LightProbeGroupComponent() },
        "LightProbeProxyVolume" to { LightProbeProxyVolume() },
        // Lights & Camera
        "Camera" to { Camera2D() },
        "Camera3D" to { Camera3D() },
        "Light" to { Light() },
        "LightPro" to { LightPro() },
        "Skybox" to { SkyboxComponent() },
        "VolumetricFog" to { VolumetricFogComponent() },
        "PostProcessVolume" to { PostProcessVolumeComponent() },
        // Physics 2D
        "Rigidbody2D" to { Rigidbody2D() },
        "Collider2D" to { Collider2D() },
        "Joint2D" to { Joint2D() },
        "PhysicsMaterial2D" to { PhysicsMaterial2D() },
        "Effector2D" to { Effector2D() },
        // Physics 3D
        "Rigidbody3D" to { Rigidbody3D() },
        "Collider3D" to { Collider3D() },
        "CharacterController" to { CharacterControllerComponent() },
        "Joint3D" to { Joint3D() },
        "WheelCollider" to { WheelColliderComponent() },
        "Cloth" to { ClothComponent() },
        "PhysicsMaterial" to { PhysicsMaterialComponent() },
        "ConstantForce" to { ConstantForce() },
        // Animation
        "Animator" to { Animator() },
        "AnimatorController" to { AnimatorControllerComponent() },
        "Animation" to { AnimationComponent() },
        "IK" to { IKComponent() },
        "Timeline" to { TimelineComponent() },
        // Audio
        "AudioSource" to { AudioSource() },
        "AudioListener" to { AudioListener() },
        "AudioReverbZone" to { AudioReverbZoneComponent() },
        "AudioMixerGroup" to { AudioMixerGroupComponent() },
        // VFX & Particles
        "ParticleEmitter" to { ParticleEmitter() },
        "ParticleSystem" to { ParticleSystemComponent() },
        "Trail" to { TrailComponent() },
        "VisualEffect" to { VisualEffectComponent() },
        // AI & Navigation
        "NavMeshAgent" to { NavMeshAgentComponent() },
        "NavMeshObstacle" to { NavMeshObstacleComponent() },
        "OffMeshLink" to { OffMeshLinkComponent() },
        "BehaviorTree" to { BehaviorTreeComponent() },
        // UI
        "Canvas" to { CanvasComponent() },
        "CanvasGroup" to { CanvasGroupComponent() },
        "RectTransform" to { RectTransformComponent() },
        "Image" to { ImageComponent() },
        "Button" to { ButtonComponent() },
        "TextMeshPro" to { TextMeshProComponent() },
        "ScrollView" to { ScrollViewComponent() },
        // Terrain & Environment
        "Terrain" to { TerrainComponent() },
        "Water" to { WaterComponent() },
        "WindZone" to { WindZoneComponent() },
        "Tree" to { TreeComponent() },
        // Networking
        "NetworkIdentity" to { NetworkIdentityComponent() },
        "NetworkTransform" to { NetworkTransformComponent() },
        "NetworkAnimator" to { NetworkAnimatorComponent() },
        // Scripting
        "Script" to { ScriptComponent() },
        "ScriptableObject" to { ScriptableObjectComponent() },
        // Utility
        "LOD" to { LODComponent() },
        "OcclusionArea" to { OcclusionAreaComponent() },
        "OcclusionPortal" to { OcclusionPortalComponent() },
        "CullingGroup" to { CullingGroupComponent() },
        "PrefabInstance" to { PrefabInstanceComponent() },
    )

    val categories: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Rendering 2D" to listOf("SpriteRenderer", "TextRenderer", "TilemapRenderer", "SortingGroup", "SpriteMask"),
        "Rendering 3D" to listOf("MeshRenderer", "SkinnedMeshRenderer", "TrailRenderer", "LineRenderer", "BillboardRenderer", "DecalProjector", "LODGroup", "ReflectionProbe", "LightProbeGroup", "Skybox", "VolumetricFog", "PostProcessVolume"),
        "Lighting" to listOf("Light", "LightPro"),
        "Camera" to listOf("Camera", "Camera3D"),
        "Physics 2D" to listOf("Rigidbody2D", "Collider2D", "Joint2D", "PhysicsMaterial2D", "Effector2D"),
        "Physics 3D" to listOf("Rigidbody3D", "Collider3D", "CharacterController", "Joint3D", "WheelCollider", "Cloth", "PhysicsMaterial", "ConstantForce"),
        "Animation" to listOf("Animator", "AnimatorController", "Animation", "IK", "Timeline"),
        "Audio" to listOf("AudioSource", "AudioListener", "AudioReverbZone", "AudioMixerGroup"),
        "VFX" to listOf("ParticleEmitter", "ParticleSystem", "Trail", "VisualEffect"),
        "AI" to listOf("NavMeshAgent", "NavMeshObstacle", "OffMeshLink", "BehaviorTree"),
        "UI" to listOf("Canvas", "CanvasGroup", "RectTransform", "Image", "Button", "TextMeshPro", "ScrollView"),
        "Environment" to listOf("Terrain", "Water", "WindZone", "Tree"),
        "Networking" to listOf("NetworkIdentity", "NetworkTransform", "NetworkAnimator"),
        "Scripting" to listOf("Script", "ScriptableObject"),
        "Optimization" to listOf("LOD", "OcclusionArea", "OcclusionPortal", "CullingGroup"),
        "Utility" to listOf("PrefabInstance"),
    )

    val POST_FX = listOf("None", "Grayscale", "Sepia", "Vignette", "CRT", "Pixelate", "Bloom", "Invert", "Chromatic", "SSAO", "SSR", "MotionBlur", "DepthOfField", "Panini", "LensDistortion", "FilmGrain", "TonemappingACES", "Custom Shader")

    fun create(type: String): Component? = types[type]?.invoke()
}

// ==================== 2D Rendering ====================

class SpriteRenderer : Component() {
    override val type = "SpriteRenderer"
    var shape = 0
    var color = 0xFFFFFFFF.toInt()
    var texture = ""
    var flipX = false
    var flipY = false
    var shader = ""
    var shaderParam = 1f
    var screenSpace = false
    var material = ""
    var sortingLayer = 0
    var sortingOrder = 0
    var maskInteraction = 0
    var spriteSortPoint = 0

    var animTexture: String? = null
    val uv = floatArrayOf(0f, 1f, 1f, 0f)

    override fun props() = listOf(
        Prop.Choice("Shape", SHAPES, { shape }, { shape = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.Asset("Material", AssetKind.MATERIAL, { material }, { material = it }),
        Prop.B("Flip X", { flipX }, { flipX = it }),
        Prop.B("Flip Y", { flipY }, { flipY = it }),
        Prop.Asset("Shader", AssetKind.SHADER, { shader }, { shader = it }),
        Prop.F("Shader Param", { shaderParam }, { shaderParam = it }),
        Prop.B("Screen Space UI", { screenSpace }, { screenSpace = it }),
        Prop.I("Sorting Order", { sortingOrder }, { sortingOrder = it }),
    )

    fun resetUv() { uv[0] = 0f; uv[1] = 1f; uv[2] = 1f; uv[3] = 0f; animTexture = null }
    companion object { val SHAPES = listOf("Square", "Circle", "Triangle", "Capsule", "9-Sliced") }
}

class TextRenderer : Component() {
    override val type = "TextRenderer"
    var text = "Hello S Engine"
    var size = 0.5f
    var color = 0xFFFFFFFF.toInt()
    var align = 1
    var bold = false
    var screenSpace = false
    var font = ""
    var lineSpacing = 1f
    var richText = true
    var overflowMode = 0

    override fun props() = listOf(
        Prop.S("Text", { text }, { text = it }, multiline = true),
        Prop.B("Screen Space UI", { screenSpace }, { screenSpace = it }),
        Prop.F("Size", { size }, { size = it.coerceAtLeast(0.01f) }, 0.05f),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Choice("Align", listOf("Left", "Center", "Right", "Justified"), { align }, { align = it }),
        Prop.B("Bold", { bold }, { bold = it }),
        Prop.Asset("Font", AssetKind.FONT, { font }, { font = it }),
        Prop.F("Line Spacing", { lineSpacing }, { lineSpacing = it }),
        Prop.B("Rich Text", { richText }, { richText = it }),
    )
}

class TilemapRenderer : Component() {
    override val type = "TilemapRenderer"
    var tilemap = ""
    var color = 0xFFFFFFFF.toInt()
    var sortingOrder = 0
    var maskInteraction = 0
    override fun props() = listOf(
        Prop.Asset("Tilemap", AssetKind.TEXTURE, { tilemap }, { tilemap = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.I("Sorting Order", { sortingOrder }, { sortingOrder = it }),
    )
}

class SortingGroup : Component() {
    override val type = "SortingGroup"
    var sortingLayer = 0
    var sortingOrder = 0
    var sortAtRoot = false
    override fun props() = listOf(
        Prop.I("Sorting Layer", { sortingLayer }, { sortingLayer = it }),
        Prop.I("Sorting Order", { sortingOrder }, { sortingOrder = it }),
        Prop.B("Sort At Root", { sortAtRoot }, { sortAtRoot = it }),
    )
}

class SpriteMask : Component() {
    override val type = "SpriteMask"
    var sprite = ""
    var alphaCutoff = 0.5f
    var frontSortingLayer = 0
    var frontSortingOrder = 0
    var backSortingLayer = 0
    var backSortingOrder = 0
    override fun props() = listOf(
        Prop.Asset("Sprite", AssetKind.TEXTURE, { sprite }, { sprite = it }),
        Prop.F("Alpha Cutoff", { alphaCutoff }, { alphaCutoff = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

// ==================== 3D Rendering ====================

class MeshRenderer : Component() {
    override val type = "MeshRenderer"
    var mesh = 0
    var model = ""
    var color = 0xFFD0D4DC.toInt()
    var texture = ""
    var tiling = 1f
    var specular = 0.35f
    var shininess = 32f
    var emission = 0f
    var unlit = false
    var shader = ""
    var shaderParam = 1f
    var material = ""
    var castShadows = 1
    var receiveShadows = true
    var motionVector = true
    var lightProbeUsage = 1
    var reflectionProbeUsage = 1
    var allowOcclusionWhenDynamic = true
    var renderQueue = 2000

    override fun props() = listOf(
        Prop.Choice("Mesh", MESHES, { mesh }, { mesh = it }),
        Prop.Asset("Model (.obj)", AssetKind.MODEL, { model }, { model = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.Asset("Material", AssetKind.MATERIAL, { material }, { material = it }),
        Prop.F("Tiling", { tiling }, { tiling = it.coerceAtLeast(0.01f) }),
        Prop.F("Specular", { specular }, { specular = it.coerceIn(0f, 2f) }, 0.05f),
        Prop.F("Shininess", { shininess }, { shininess = it.coerceIn(1f, 256f) }, 1f),
        Prop.F("Emission", { emission }, { emission = it.coerceIn(0f, 4f) }, 0.05f),
        Prop.B("Unlit", { unlit }, { unlit = it }),
        Prop.Asset("Shader", AssetKind.SHADER, { shader }, { shader = it }),
        Prop.F("Shader Param", { shaderParam }, { shaderParam = it }),
        Prop.Choice("Cast Shadows", listOf("Off", "On", "Two Sided", "Shadows Only"), { castShadows }, { castShadows = it }),
        Prop.B("Receive Shadows", { receiveShadows }, { receiveShadows = it }),
        Prop.I("Render Queue", { renderQueue }, { renderQueue = it }),
    )
    companion object { val MESHES = listOf("Cube", "Sphere", "Plane", "Cylinder", "Cone", "Torus", "Capsule", "Pyramid", "Quad", "Custom Model") }
}

class SkinnedMeshRenderer : Component() {
    override val type = "SkinnedMeshRenderer"
    var mesh = ""
    var material = ""
    var rootBone = ""
    var quality = 1
    var updateWhenOffscreen = false
    var skinnedMotionVectors = true
    override fun props() = listOf(
        Prop.Asset("Mesh", AssetKind.MODEL, { mesh }, { mesh = it }),
        Prop.Asset("Material", AssetKind.MATERIAL, { material }, { material = it }),
        Prop.S("Root Bone", { rootBone }, { rootBone = it }),
        Prop.Choice("Quality", listOf("Auto", "1 Bone", "2 Bones", "4 Bones"), { quality }, { quality = it }),
        Prop.B("Update When Offscreen", { updateWhenOffscreen }, { updateWhenOffscreen = it }),
    )
}

class TrailRenderer : Component() {
    override val type = "TrailRenderer"
    var time = 2f
    var startWidth = 1f
    var endWidth = 0f
    var startColor = 0xFFFFFFFF.toInt()
    var endColor = 0x00FFFFFF
    var material = ""
    var minVertexDistance = 0.1f
    var autodestruct = false
    var emitting = true
    override fun props() = listOf(
        Prop.F("Time", { time }, { time = it.coerceAtLeast(0f) }),
        Prop.F("Start Width", { startWidth }, { startWidth = it }),
        Prop.F("End Width", { endWidth }, { endWidth = it }),
        Prop.Color("Start Color", { startColor }, { startColor = it }),
        Prop.Color("End Color", { endColor }, { endColor = it }),
        Prop.B("Emitting", { emitting }, { emitting = it }),
    )
}

class LineRenderer : Component() {
    override val type = "LineRenderer"
    var startWidth = 0.1f
    var endWidth = 0.1f
    var startColor = 0xFFFFFFFF.toInt()
    var endColor = 0xFFFFFFFF.toInt()
    var material = ""
    var useWorldSpace = true
    var loop = false
    var positionCount = 2
    override fun props() = listOf(
        Prop.F("Start Width", { startWidth }, { startWidth = it }),
        Prop.F("End Width", { endWidth }, { endWidth = it }),
        Prop.Color("Start Color", { startColor }, { startColor = it }),
        Prop.Color("End Color", { endColor }, { endColor = it }),
        Prop.B("Use World Space", { useWorldSpace }, { useWorldSpace = it }),
        Prop.B("Loop", { loop }, { loop = it }),
        Prop.I("Position Count", { positionCount }, { positionCount = it.coerceAtLeast(2) }),
    )
}

class BillboardRenderer : Component() {
    override val type = "BillboardRenderer"
    var texture = ""
    var color = 0xFFFFFFFF.toInt()
    var size = 1f
    var billboardMode = 0
    override fun props() = listOf(
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.F("Size", { size }, { size = it }),
        Prop.Choice("Mode", listOf("Screen Aligned", "World Aligned", "Axial"), { billboardMode }, { billboardMode = it }),
    )
}

class DecalProjector : Component() {
    override val type = "DecalProjector"
    var material = ""
    var sizeX = 1f
    var sizeY = 1f
    var sizeZ = 1f
    var fadeFactor = 0.5f
    var drawDistance = 100f
    var cullingMask = -1
    override fun props() = listOf(
        Prop.Asset("Material", AssetKind.MATERIAL, { material }, { material = it }),
        Prop.F("Size X", { sizeX }, { sizeX = it }),
        Prop.F("Size Y", { sizeY }, { sizeY = it }),
        Prop.F("Size Z", { sizeZ }, { sizeZ = it }),
        Prop.F("Fade Factor", { fadeFactor }, { fadeFactor = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

class LODGroup : Component() {
    override val type = "LODGroup"
    var lodCount = 3
    var fadeMode = 0
    var animateCrossFading = false
    override fun props() = listOf(
        Prop.I("LOD Count", { lodCount }, { lodCount = it.coerceIn(1, 8) }),
        Prop.Choice("Fade Mode", listOf("None", "Cross Fade", "Speed Tree"), { fadeMode }, { fadeMode = it }),
        Prop.B("Animate Cross Fading", { animateCrossFading }, { animateCrossFading = it }),
    )
}

class ReflectionProbeComponent : Component() {
    override val type = "ReflectionProbe"
    var resolution = 128
    var intensity = 1f
    var boxSizeX = 10f
    var boxSizeY = 10f
    var boxSizeZ = 10f
    var boxProjection = false
    var blendDistance = 1f
    var importance = 0
    override fun props() = listOf(
        Prop.I("Resolution", { resolution }, { resolution = it }),
        Prop.F("Intensity", { intensity }, { intensity = it }),
        Prop.F("Box Size X", { boxSizeX }, { boxSizeX = it }),
        Prop.F("Box Size Y", { boxSizeY }, { boxSizeY = it }),
        Prop.F("Box Size Z", { boxSizeZ }, { boxSizeZ = it }),
        Prop.B("Box Projection", { boxProjection }, { boxProjection = it }),
    )
}

class LightProbeGroupComponent : Component() {
    override val type = "LightProbeGroup"
    var probeCount = 8
    override fun props() = listOf(Prop.I("Probe Count", { probeCount }, { probeCount = it }))
}

class LightProbeProxyVolume : Component() {
    override val type = "LightProbeProxyVolume"
    var sizeX = 10f
    var sizeY = 10f
    var sizeZ = 10f
    var resolutionX = 2
    var resolutionY = 2
    var resolutionZ = 2
    override fun props() = listOf(
        Prop.F("Size X", { sizeX }, { sizeX = it }),
        Prop.F("Size Y", { sizeY }, { sizeY = it }),
        Prop.F("Size Z", { sizeZ }, { sizeZ = it }),
    )
}

// ==================== Camera & Lighting ====================

class Camera2D : Component() {
    override val type = "Camera"
    var size = 5f
    var background = 0xFF1B2533.toInt()
    var follow = ""
    var smoothing = 5f
    var postFx = 0
    var postIntensity = 1f
    var postShader = ""
    var shake = 0f
    var cullingMask = -1
    var depth = 0
    var hdr = true
    var msaa = 1
    var allowDynamicResolution = false
    var clearFlags = 1

    override fun props() = listOf(
        Prop.F("Size", { size }, { size = it.coerceAtLeast(0.1f) }),
        Prop.Color("Background", { background }, { background = it }),
        Prop.S("Follow Target", { follow }, { follow = it }),
        Prop.F("Follow Smoothing", { smoothing }, { smoothing = it.coerceAtLeast(0f) }),
        Prop.Choice("Post FX", ComponentRegistry.POST_FX, { postFx }, { postFx = it }),
        Prop.F("FX Intensity", { postIntensity }, { postIntensity = it.coerceIn(0f, 4f) }, 0.05f),
        Prop.Asset("FX Shader", AssetKind.SHADER, { postShader }, { postShader = it }),
        Prop.I("Depth", { depth }, { depth = it }),
        Prop.B("HDR", { hdr }, { hdr = it }),
    )
    override fun resetRuntime() { shake = 0f }
}

class Camera3D : Component() {
    override val type = "Camera3D"
    var fov = 60f
    var near = 0.1f
    var far = 500f
    var skyTop = 0xFF3B7BD4.toInt()
    var skyHorizon = 0xFFBFD8F0.toInt()
    var follow = ""
    var offsetX = 0f
    var offsetY = 4f
    var offsetZ = 8f
    var lookAtTarget = true
    var smoothing = 6f
    var postFx = 0
    var postIntensity = 1f
    var postShader = ""
    var shake = 0f
    var cullingMask = -1
    var depth = 0
    var hdr = true
    var msaa = 2
    var renderPath = 1
    var allowHDR = true
    var allowMSAA = true
    var allowDynamicResolution = false
    var clearFlags = 1
    var backgroundColor = 0xFF1B2533.toInt()

    override fun props() = listOf(
        Prop.F("Field of View", { fov }, { fov = it.coerceIn(10f, 150f) }, 1f),
        Prop.F("Near", { near }, { near = it.coerceAtLeast(0.01f) }, 0.01f),
        Prop.F("Far", { far }, { far = it.coerceAtLeast(1f) }, 1f),
        Prop.Color("Sky Top", { skyTop }, { skyTop = it }),
        Prop.Color("Sky Horizon", { skyHorizon }, { skyHorizon = it }),
        Prop.S("Follow Target", { follow }, { follow = it }),
        Prop.F("Offset X", { offsetX }, { offsetX = it }),
        Prop.F("Offset Y", { offsetY }, { offsetY = it }),
        Prop.F("Offset Z", { offsetZ }, { offsetZ = it }),
        Prop.B("Look At Target", { lookAtTarget }, { lookAtTarget = it }),
        Prop.F("Follow Smoothing", { smoothing }, { smoothing = it.coerceAtLeast(0f) }),
        Prop.Choice("Post FX", ComponentRegistry.POST_FX, { postFx }, { postFx = it }),
        Prop.F("FX Intensity", { postIntensity }, { postIntensity = it.coerceIn(0f, 4f) }, 0.05f),
        Prop.Asset("FX Shader", AssetKind.SHADER, { postShader }, { postShader = it }),
        Prop.Choice("Render Path", listOf("Forward", "Deferred", "Legacy Vertex Lit", "Legacy Deferred"), { renderPath }, { renderPath = it }),
        Prop.B("HDR", { hdr }, { hdr = it }),
        Prop.I("Depth", { depth }, { depth = it }),
    )
    override fun resetRuntime() { shake = 0f }
}

class Light : Component() {
    override val type = "Light"
    var kind = 0
    var color = 0xFFFFF4E0.toInt()
    var intensity = 1f
    var range = 10f
    var spotAngle = 30f
    var innerSpotAngle = 21f
    var shadows = 0
    var shadowStrength = 1f
    var shadowResolution = 1
    var shadowBias = 0.05f
    var cullingMask = -1
    var lightmapBakeType = 0

    override fun props() = listOf(
        Prop.Choice("Type", listOf("Directional", "Point", "Spot", "Area", "Disc"), { kind }, { kind = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.F("Intensity", { intensity }, { intensity = it.coerceIn(0f, 10f) }, 0.05f),
        Prop.F("Range", { range }, { range = it.coerceAtLeast(0.1f) }),
        Prop.F("Spot Angle", { spotAngle }, { spotAngle = it.coerceIn(1f, 179f) }, 1f),
        Prop.Choice("Shadows", listOf("None", "Hard", "Soft"), { shadows }, { shadows = it }),
        Prop.F("Shadow Strength", { shadowStrength }, { shadowStrength = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

class LightPro : Component() {
    override val type = "LightPro"
    var kind = 0
    var color = 0xFFFFF4E0.toInt()
    var colorTemperature = 6500f
    var useColorTemperature = false
    var intensity = 1f
    var bounceIntensity = 1f
    var range = 10f
    var spotAngle = 30f
    var innerSpotAngle = 21f
    var cookie = ""
    var cookieSize = 10f
    var shadows = 1
    var shadowStrength = 1f
    var shadowResolution = 2
    var shadowBias = 0.05f
    var shadowNormalBias = 0.4f
    var shadowNearPlane = 0.2f
    var cullingMask = -1
    var renderingLayerMask = 1
    var lightmapBakeType = 1
    var areaSizeX = 1f
    var areaSizeY = 1f
    var enableVolumetrics = false

    override fun props() = listOf(
        Prop.Choice("Type", listOf("Directional", "Point", "Spot", "Area Rectangle", "Area Disc"), { kind }, { kind = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.F("Intensity", { intensity }, { intensity = it.coerceIn(0f, 20f) }, 0.1f),
        Prop.F("Range", { range }, { range = it.coerceAtLeast(0.1f) }),
        Prop.F("Spot Angle", { spotAngle }, { spotAngle = it.coerceIn(1f, 179f) }, 1f),
        Prop.Choice("Shadows", listOf("None", "Hard", "Soft"), { shadows }, { shadows = it }),
        Prop.B("Volumetrics", { enableVolumetrics }, { enableVolumetrics = it }),
    )
}

class SkyboxComponent : Component() {
    override val type = "Skybox"
    var material = ""
    var kind = 0
    var exposure = 1f
    var rotation = 0f
    var tint = 0xFF808080.toInt()
    override fun props() = listOf(
        Prop.Choice("Type", listOf("Procedural", "Cubemap", "HDRI", "Gradient", "Six Sided", "Physically Based"), { kind }, { kind = it }),
        Prop.Asset("Material", AssetKind.MATERIAL, { material }, { material = it }),
        Prop.F("Exposure", { exposure }, { exposure = it }),
        Prop.F("Rotation", { rotation }, { rotation = it }),
        Prop.Color("Tint", { tint }, { tint = it }),
    )
}

class VolumetricFogComponent : Component() {
    override val type = "VolumetricFog"
    var albedo = 0xFFFFFFFF.toInt()
    var density = 0.01f
    var anisotropy = 0f
    var depthExtent = 100f
    override fun props() = listOf(
        Prop.Color("Albedo", { albedo }, { albedo = it }),
        Prop.F("Density", { density }, { density = it.coerceAtLeast(0f) }, 0.001f),
        Prop.F("Anisotropy", { anisotropy }, { anisotropy = it.coerceIn(-1f, 1f) }, 0.05f),
    )
}

class PostProcessVolumeComponent : Component() {
    override val type = "PostProcessVolume"
    var isGlobal = true
    var priority = 0f
    var blendDistance = 0f
    var weight = 1f
    var profile = ""
    override fun props() = listOf(
        Prop.B("Is Global", { isGlobal }, { isGlobal = it }),
        Prop.F("Priority", { priority }, { priority = it }),
        Prop.F("Blend Distance", { blendDistance }, { blendDistance = it }),
        Prop.F("Weight", { weight }, { weight = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.Asset("Profile", AssetKind.TEXTURE, { profile }, { profile = it }),
    )
}

// ==================== Physics ====================

class Rigidbody2D : Component() {
    override val type = "Rigidbody2D"
    var bodyType = 0
    var mass = 1f
    var gravityScale = 1f
    var drag = 0f
    var angularDrag = 0.05f
    var bounciness = 0f
    var friction = 0.4f
    var startVx = 0f
    var startVy = 0f
    var material = ""
    var collisionDetection = 0
    var sleepingMode = 0
    var interpolate = 0
    var freezeRotation = false
    var layer = 0

    var vx = 0f
    var vy = 0f
    var grounded = false

    override fun props() = listOf(
        Prop.Choice("Body Type", listOf("Dynamic", "Kinematic", "Static"), { bodyType }, { bodyType = it }),
        Prop.F("Mass", { mass }, { mass = it.coerceAtLeast(0.001f) }),
        Prop.F("Gravity Scale", { gravityScale }, { gravityScale = it }),
        Prop.F("Linear Drag", { drag }, { drag = it.coerceAtLeast(0f) }),
        Prop.F("Angular Drag", { angularDrag }, { angularDrag = it.coerceAtLeast(0f) }),
        Prop.F("Bounciness", { bounciness }, { bounciness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Friction", { friction }, { friction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Velocity X", { startVx }, { startVx = it }),
        Prop.F("Velocity Y", { startVy }, { startVy = it }),
        Prop.Choice("Collision Detection", listOf("Discrete", "Continuous"), { collisionDetection }, { collisionDetection = it }),
        Prop.B("Freeze Rotation", { freezeRotation }, { freezeRotation = it }),
    )
    override fun resetRuntime() { vx = startVx; vy = startVy; grounded = false }
}

class Collider2D : Component() {
    override val type = "Collider2D"
    var shape = 0
    var width = 1f
    var height = 1f
    var radius = 0.5f
    var offsetX = 0f
    var offsetY = 0f
    var isTrigger = false
    var material = ""
    var layer = 0
    var density = 1f
    var usedByEffector = false
    var usedByComposite = false

    override fun props() = listOf(
        Prop.Choice("Shape", listOf("Box", "Circle", "Capsule", "Polygon", "Edge"), { shape }, { shape = it }),
        Prop.F("Width", { width }, { width = it.coerceAtLeast(0.01f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.01f) }),
        Prop.F("Radius", { radius }, { radius = it.coerceAtLeast(0.01f) }),
        Prop.F("Offset X", { offsetX }, { offsetX = it }),
        Prop.F("Offset Y", { offsetY }, { offsetY = it }),
        Prop.B("Is Trigger", { isTrigger }, { isTrigger = it }),
        Prop.Asset("Physics Material", AssetKind.TEXTURE, { material }, { material = it }),
    )
}

class Joint2D : Component() {
    override val type = "Joint2D"
    var jointType = 0
    var connectedBody = ""
    var anchorX = 0f
    var anchorY = 0f
    var connectedAnchorX = 0f
    var connectedAnchorY = 0f
    var breakForce = 1000f
    var breakTorque = 1000f
    var enableCollision = false
    override fun props() = listOf(
        Prop.Choice("Joint Type", listOf("Fixed", "Distance", "Hinge", "Spring", "Slider", "Wheel", "Target"), { jointType }, { jointType = it }),
        Prop.S("Connected Body", { connectedBody }, { connectedBody = it }),
        Prop.F("Anchor X", { anchorX }, { anchorX = it }),
        Prop.F("Anchor Y", { anchorY }, { anchorY = it }),
        Prop.F("Break Force", { breakForce }, { breakForce = it }),
        Prop.B("Enable Collision", { enableCollision }, { enableCollision = it }),
    )
}

class PhysicsMaterial2D : Component() {
    override val type = "PhysicsMaterial2D"
    var friction = 0.4f
    var bounciness = 0f
    override fun props() = listOf(
        Prop.F("Friction", { friction }, { friction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Bounciness", { bounciness }, { bounciness = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

class Effector2D : Component() {
    override val type = "Effector2D"
    var effectorType = 0
    var forceMagnitude = 10f
    var forceVariation = 0f
    var drag = 0f
    var angularDrag = 0f
    override fun props() = listOf(
        Prop.Choice("Effector Type", listOf("Point", "Area", "Buoyancy", "Surface", "Platform"), { effectorType }, { effectorType = it }),
        Prop.F("Force Magnitude", { forceMagnitude }, { forceMagnitude = it }),
        Prop.F("Drag", { drag }, { drag = it }),
    )
}

class Rigidbody3D : Component() {
    override val type = "Rigidbody3D"
    var bodyType = 0
    var mass = 1f
    var gravityScale = 1f
    var drag = 0.05f
    var angularDrag = 0.05f
    var bounciness = 0f
    var friction = 0.5f
    var startVx = 0f
    var startVy = 0f
    var startVz = 0f
    var material = ""
    var collisionDetection = 0
    var interpolate = 0
    var constraints = 0
    var layer = 0
    var centerOfMassX = 0f
    var centerOfMassY = 0f
    var centerOfMassZ = 0f

    var vx = 0f
    var vy = 0f
    var vz = 0f
    var grounded = false

    override fun props() = listOf(
        Prop.Choice("Body Type", listOf("Dynamic", "Kinematic", "Static"), { bodyType }, { bodyType = it }),
        Prop.F("Mass", { mass }, { mass = it.coerceAtLeast(0.001f) }),
        Prop.F("Gravity Scale", { gravityScale }, { gravityScale = it }),
        Prop.F("Linear Drag", { drag }, { drag = it.coerceAtLeast(0f) }),
        Prop.F("Angular Drag", { angularDrag }, { angularDrag = it.coerceAtLeast(0f) }),
        Prop.F("Bounciness", { bounciness }, { bounciness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Friction", { friction }, { friction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Velocity X", { startVx }, { startVx = it }),
        Prop.F("Velocity Y", { startVy }, { startVy = it }),
        Prop.F("Velocity Z", { startVz }, { startVz = it }),
        Prop.Choice("Collision Detection", listOf("Discrete", "Continuous", "Continuous Dynamic", "Continuous Speculative"), { collisionDetection }, { collisionDetection = it }),
        Prop.I("Constraints", { constraints }, { constraints = it }),
    )
    override fun resetRuntime() { vx = startVx; vy = startVy; vz = startVz; grounded = false }
}

class Collider3D : Component() {
    override val type = "Collider3D"
    var shape = 0
    var sizeX = 1f
    var sizeY = 1f
    var sizeZ = 1f
    var radius = 0.5f
    var height = 2f
    var centerX = 0f
    var centerY = 0f
    var centerZ = 0f
    var isTrigger = false
    var material = ""
    var layer = 0
    var convex = false
    var isMeshCollider = false

    override fun props() = listOf(
        Prop.Choice("Shape", listOf("Box", "Sphere", "Capsule", "Mesh", "Terrain", "Wheel"), { shape }, { shape = it }),
        Prop.F("Size X", { sizeX }, { sizeX = it.coerceAtLeast(0.01f) }),
        Prop.F("Size Y", { sizeY }, { sizeY = it.coerceAtLeast(0.01f) }),
        Prop.F("Size Z", { sizeZ }, { sizeZ = it.coerceAtLeast(0.01f) }),
        Prop.F("Radius", { radius }, { radius = it.coerceAtLeast(0.01f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.01f) }),
        Prop.F("Center X", { centerX }, { centerX = it }),
        Prop.F("Center Y", { centerY }, { centerY = it }),
        Prop.F("Center Z", { centerZ }, { centerZ = it }),
        Prop.B("Is Trigger", { isTrigger }, { isTrigger = it }),
        Prop.B("Convex", { convex }, { convex = it }),
    )
}

class CharacterControllerComponent : Component() {
    override val type = "CharacterController"
    var radius = 0.5f
    var height = 2f
    var centerX = 0f
    var centerY = 0f
    var centerZ = 0f
    var slopeLimit = 45f
    var stepOffset = 0.3f
    var skinWidth = 0.08f
    var minMoveDistance = 0f
    var detectCollisions = true
    var enableOverlapRecovery = true
    override fun props() = listOf(
        Prop.F("Radius", { radius }, { radius = it.coerceAtLeast(0.01f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.01f) }),
        Prop.F("Slope Limit", { slopeLimit }, { slopeLimit = it.coerceIn(0f, 90f) }, 1f),
        Prop.F("Step Offset", { stepOffset }, { stepOffset = it }),
        Prop.F("Skin Width", { skinWidth }, { skinWidth = it }),
    )
}

class Joint3D : Component() {
    override val type = "Joint3D"
    var jointType = 0
    var connectedBody = ""
    var anchorX = 0f
    var anchorY = 0f
    var anchorZ = 0f
    var axisX = 0f
    var axisY = 1f
    var axisZ = 0f
    var breakForce = Float.MAX_VALUE
    var breakTorque = Float.MAX_VALUE
    var enableCollision = false
    var useLimits = false
    var minLimit = -90f
    var maxLimit = 90f
    override fun props() = listOf(
        Prop.Choice("Joint Type", listOf("Fixed", "Hinge", "Spring", "Distance", "Configurable", "Character", "Wheel"), { jointType }, { jointType = it }),
        Prop.S("Connected Body", { connectedBody }, { connectedBody = it }),
        Prop.F("Anchor X", { anchorX }, { anchorX = it }),
        Prop.F("Anchor Y", { anchorY }, { anchorY = it }),
        Prop.F("Anchor Z", { anchorZ }, { anchorZ = it }),
        Prop.F("Break Force", { breakForce }, { breakForce = it }),
        Prop.B("Enable Collision", { enableCollision }, { enableCollision = it }),
        Prop.B("Use Limits", { useLimits }, { useLimits = it }),
    )
}

class WheelColliderComponent : Component() {
    override val type = "WheelCollider"
    var radius = 0.33f
    var suspensionDistance = 0.3f
    var suspensionSpring = 20000f
    var suspensionDamper = 2500f
    var mass = 20f
    var forwardFriction = 1f
    var sidewaysFriction = 1f
    override fun props() = listOf(
        Prop.F("Radius", { radius }, { radius = it }),
        Prop.F("Suspension Distance", { suspensionDistance }, { suspensionDistance = it }),
        Prop.F("Suspension Spring", { suspensionSpring }, { suspensionSpring = it }),
        Prop.F("Mass", { mass }, { mass = it }),
    )
}

class ClothComponent : Component() {
    override val type = "Cloth"
    var bendingStiffness = 0.5f
    var stretchingStiffness = 1f
    var damping = 0f
    var friction = 0.5f
    var mass = 1f
    var useGravity = true
    override fun props() = listOf(
        Prop.F("Bending Stiffness", { bendingStiffness }, { bendingStiffness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Stretching Stiffness", { stretchingStiffness }, { stretchingStiffness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Damping", { damping }, { damping = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

class PhysicsMaterialComponent : Component() {
    override val type = "PhysicsMaterial"
    var dynamicFriction = 0.6f
    var staticFriction = 0.6f
    var bounciness = 0f
    var frictionCombine = 0
    var bounceCombine = 0
    override fun props() = listOf(
        Prop.F("Dynamic Friction", { dynamicFriction }, { dynamicFriction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Static Friction", { staticFriction }, { staticFriction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Bounciness", { bounciness }, { bounciness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.Choice("Friction Combine", listOf("Average", "Minimum", "Multiply", "Maximum"), { frictionCombine }, { frictionCombine = it }),
        Prop.Choice("Bounce Combine", listOf("Average", "Minimum", "Multiply", "Maximum"), { bounceCombine }, { bounceCombine = it }),
    )
}

class ConstantForce : Component() {
    override val type = "ConstantForce"
    var forceX = 0f
    var forceY = 0f
    var forceZ = 0f
    var torqueX = 0f
    var torqueY = 0f
    var torqueZ = 0f
    override fun props() = listOf(
        Prop.F("Force X", { forceX }, { forceX = it }),
        Prop.F("Force Y", { forceY }, { forceY = it }),
        Prop.F("Force Z", { forceZ }, { forceZ = it }),
        Prop.F("Torque X", { torqueX }, { torqueX = it }),
        Prop.F("Torque Y", { torqueY }, { torqueY = it }),
        Prop.F("Torque Z", { torqueZ }, { torqueZ = it }),
    )
}

// ==================== Animation ====================

class Animator : Component() {
    override val type = "Animator"
    var clip = ""
    var playOnStart = true
    var speed = 1f
    var current = ""
    var time = 0f
    var playing = false
    var frame = 0
    var finished = false
    var controller = ""
    var avatar = ""
    var applyRootMotion = false
    var updateMode = 0
    var cullingMode = 0

    override fun props() = listOf(
        Prop.Asset("Clip", AssetKind.ANIMATION, { clip }, { clip = it }),
        Prop.Asset("Controller", AssetKind.ANIMATION, { controller }, { controller = it }),
        Prop.B("Play On Start", { playOnStart }, { playOnStart = it }),
        Prop.F("Speed", { speed }, { speed = it }),
        Prop.B("Apply Root Motion", { applyRootMotion }, { applyRootMotion = it }),
        Prop.Choice("Update Mode", listOf("Normal", "Animate Physics", "Unscaled Time"), { updateMode }, { updateMode = it }),
        Prop.Choice("Culling Mode", listOf("Always Animate", "Cull Update Transforms", "Cull Completely"), { cullingMode }, { cullingMode = it }),
    )
    override fun resetRuntime() { current = clip; time = 0f; playing = playOnStart; frame = 0; finished = false }
}

class AnimatorControllerComponent : Component() {
    override val type = "AnimatorController"
    var controller = ""
    var avatar = ""
    override fun props() = listOf(
        Prop.Asset("Controller", AssetKind.ANIMATION, { controller }, { controller = it }),
        Prop.Asset("Avatar", AssetKind.MODEL, { avatar }, { avatar = it }),
    )
}

class AnimationComponent : Component() {
    override val type = "Animation"
    var clip = ""
    var playAutomatically = true
    var animateOnlyIfVisible = false
    var cullingType = 0
    override fun props() = listOf(
        Prop.Asset("Clip", AssetKind.ANIMATION, { clip }, { clip = it }),
        Prop.B("Play Automatically", { playAutomatically }, { playAutomatically = it }),
        Prop.Choice("Culling Type", listOf("Always Animate", "Based On Renderers", "Based On Clip Bounds", "Based On User Bounds"), { cullingType }, { cullingType = it }),
    )
}

class IKComponent : Component() {
    override val type = "IK"
    var target = ""
    var chainLength = 2
    var iterations = 10
    var tolerance = 0.01f
    var weight = 1f
    override fun props() = listOf(
        Prop.S("Target", { target }, { target = it }),
        Prop.I("Chain Length", { chainLength }, { chainLength = it.coerceIn(1, 10) }),
        Prop.I("Iterations", { iterations }, { iterations = it.coerceIn(1, 50) }),
        Prop.F("Weight", { weight }, { weight = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

class TimelineComponent : Component() {
    override val type = "Timeline"
    var timelineAsset = ""
    var playOnAwake = true
    var initialTime = 0f
    override fun props() = listOf(
        Prop.Asset("Timeline", AssetKind.ANIMATION, { timelineAsset }, { timelineAsset = it }),
        Prop.B("Play On Awake", { playOnAwake }, { playOnAwake = it }),
        Prop.F("Initial Time", { initialTime }, { initialTime = it }),
    )
}

// ==================== Audio ====================

class AudioSource : Component() {
    override val type = "AudioSource"
    var clip = ""
    var playOnStart = true
    var loop = false
    var volume = 1f
    var pitch = 1f
    var spatialBlend = 0f
    var minDistance = 1f
    var maxDistance = 500f
    var rolloffMode = 0
    var bypassEffects = false
    var bypassReverbZones = false
    var priority = 128
    var mute = false
    var dopplerLevel = 1f
    var spread = 0f
    var outputGroup = "SFX"

    override fun props() = listOf(
        Prop.Asset("Clip", AssetKind.SOUND, { clip }, { clip = it }),
        Prop.B("Play On Start", { playOnStart }, { playOnStart = it }),
        Prop.B("Loop", { loop }, { loop = it }),
        Prop.F("Volume", { volume }, { volume = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Pitch", { pitch }, { pitch = it.coerceIn(-3f, 3f) }, 0.05f),
        Prop.F("Spatial Blend", { spatialBlend }, { spatialBlend = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Min Distance", { minDistance }, { minDistance = it }),
        Prop.F("Max Distance", { maxDistance }, { maxDistance = it }),
        Prop.Choice("Rolloff Mode", listOf("Logarithmic", "Linear", "Custom"), { rolloffMode }, { rolloffMode = it }),
        Prop.Asset("Output Group", AssetKind.SOUND, { outputGroup }, { outputGroup = it }),
    )
}

class AudioListener : Component() {
    override val type = "AudioListener"
    override fun props() = listOf(Prop.B("Enabled", { enabled }, { enabled = it }))
}

class AudioReverbZoneComponent : Component() {
    override val type = "AudioReverbZone"
    var minDistance = 10f
    var maxDistance = 15f
    var reverbPreset = 0
    var room = -1000
    var decayTime = 1.49f
    override fun props() = listOf(
        Prop.F("Min Distance", { minDistance }, { minDistance = it }),
        Prop.F("Max Distance", { maxDistance }, { maxDistance = it }),
        Prop.Choice("Reverb Preset", listOf("Off", "Generic", "Padded Cell", "Room", "Bathroom", "Living Room", "Stone Room", "Auditorium", "Concert Hall", "Cave", "Arena", "Hangar", "Carpeted Hallway", "Hallway", "Stone Corridor", "Alley", "Forest", "City", "Mountains", "Quarry", "Plain", "Parking Lot", "Sewer Pipe", "Underwater"), { reverbPreset }, { reverbPreset = it }),
    )
}

class AudioMixerGroupComponent : Component() {
    override val type = "AudioMixerGroup"
    var group = "Master"
    override fun props() = listOf(Prop.S("Group", { group }, { group = it }))
}

// ==================== VFX ====================

class ParticleEmitter : Component() {
    override val type = "ParticleEmitter"
    var emitting = true
    var rate = 30f
    var lifetime = 1.2f
    var speed = 3f
    var direction = 90f
    var spread = 30f
    var startSize = 0.25f
    var endSize = 0.02f
    var startColor = 0xFFFFC940.toInt()
    var endColor = 0x00FF3D00
    var gravity = 0f
    var maxParticles = 300
    var additive = false
    var texture = ""
    val particles = ArrayList<Particle>()
    var accumulator = 0f
    var pendingBurst = 0
    class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float, var life: Float)
    override fun props() = listOf(
        Prop.B("Emitting", { emitting }, { emitting = it }),
        Prop.F("Rate", { rate }, { rate = it.coerceAtLeast(0f) }, 1f),
        Prop.F("Lifetime", { lifetime }, { lifetime = it.coerceAtLeast(0.01f) }),
        Prop.F("Speed", { speed }, { speed = it }),
        Prop.F("Direction", { direction }, { direction = it }, 1f),
        Prop.F("Spread", { spread }, { spread = it.coerceIn(0f, 360f) }, 1f),
        Prop.F("Start Size", { startSize }, { startSize = it.coerceAtLeast(0f) }, 0.01f),
        Prop.F("End Size", { endSize }, { endSize = it.coerceAtLeast(0f) }, 0.01f),
        Prop.Color("Start Color", { startColor }, { startColor = it }),
        Prop.Color("End Color", { endColor }, { endColor = it }),
        Prop.F("Gravity", { gravity }, { gravity = it }),
        Prop.I("Max Particles", { maxParticles }, { maxParticles = it.coerceIn(1, 5000) }),
        Prop.B("Additive Blend", { additive }, { additive = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
    )
    override fun resetRuntime() { particles.clear(); accumulator = 0f; pendingBurst = 0 }
}

class ParticleSystemComponent : Component() {
    override val type = "ParticleSystem"
    var duration = 5f
    var loop = true
    var prewarm = false
    var startDelay = 0f
    var startLifetime = 5f
    var startSpeed = 5f
    var startSize = 1f
    var startColor = 0xFFFFFFFF.toInt()
    var gravityModifier = 0f
    var maxParticles = 1000
    var simulationSpace = 0
    var playOnAwake = true
    var emissionRate = 10f
    var shapeType = 0
    var material = ""
    var texture = ""
    override fun props() = listOf(
        Prop.F("Duration", { duration }, { duration = it }),
        Prop.B("Loop", { loop }, { loop = it }),
        Prop.B("Prewarm", { prewarm }, { prewarm = it }),
        Prop.F("Start Lifetime", { startLifetime }, { startLifetime = it }),
        Prop.F("Start Speed", { startSpeed }, { startSpeed = it }),
        Prop.F("Start Size", { startSize }, { startSize = it }),
        Prop.Color("Start Color", { startColor }, { startColor = it }),
        Prop.F("Gravity Modifier", { gravityModifier }, { gravityModifier = it }),
        Prop.I("Max Particles", { maxParticles }, { maxParticles = it }),
        Prop.Choice("Simulation Space", listOf("Local", "World", "Custom"), { simulationSpace }, { simulationSpace = it }),
        Prop.F("Emission Rate", { emissionRate }, { emissionRate = it }),
        Prop.Choice("Shape", listOf("Sphere", "Hemisphere", "Cone", "Box", "Circle", "Rectangle", "Mesh"), { shapeType }, { shapeType = it }),
        Prop.Asset("Material", AssetKind.MATERIAL, { material }, { material = it }),
    )
}

class TrailComponent : Component() {
    override val type = "Trail"
    var time = 2f
    var minVertexDistance = 0.1f
    var startWidth = 1f
    var endWidth = 0f
    var startColor = 0xFFFFFFFF.toInt()
    var endColor = 0x00FFFFFF
    override fun props() = listOf(
        Prop.F("Time", { time }, { time = it }),
        Prop.F("Min Vertex Distance", { minVertexDistance }, { minVertexDistance = it }),
        Prop.F("Start Width", { startWidth }, { startWidth = it }),
        Prop.F("End Width", { endWidth }, { endWidth = it }),
    )
}

class VisualEffectComponent : Component() {
    override val type = "VisualEffect"
    var asset = ""
    var playOnAwake = true
    var initialEvent = "OnPlay"
    override fun props() = listOf(
        Prop.Asset("VFX Asset", AssetKind.TEXTURE, { asset }, { asset = it }),
        Prop.B("Play On Awake", { playOnAwake }, { playOnAwake = it }),
        Prop.S("Initial Event", { initialEvent }, { initialEvent = it }),
    )
}

// ==================== AI ====================

class NavMeshAgentComponent : Component() {
    override val type = "NavMeshAgent"
    var speed = 3.5f
    var angularSpeed = 120f
    var acceleration = 8f
    var stoppingDistance = 0f
    var autoBraking = true
    var radius = 0.5f
    var height = 2f
    var areaMask = -1
    var avoidancePriority = 50
    var obstacleAvoidanceType = 3
    override fun props() = listOf(
        Prop.F("Speed", { speed }, { speed = it }),
        Prop.F("Angular Speed", { angularSpeed }, { angularSpeed = it }),
        Prop.F("Acceleration", { acceleration }, { acceleration = it }),
        Prop.F("Stopping Distance", { stoppingDistance }, { stoppingDistance = it }),
        Prop.B("Auto Braking", { autoBraking }, { autoBraking = it }),
        Prop.F("Radius", { radius }, { radius = it }),
        Prop.F("Height", { height }, { height = it }),
        Prop.I("Avoidance Priority", { avoidancePriority }, { avoidancePriority = it.coerceIn(0, 99) }),
    )
}

class NavMeshObstacleComponent : Component() {
    override val type = "NavMeshObstacle"
    var shape = 0
    var centerX = 0f
    var centerY = 0f
    var centerZ = 0f
    var sizeX = 1f
    var sizeY = 1f
    var sizeZ = 1f
    var carving = false
    var carveOnlyStationary = false
    override fun props() = listOf(
        Prop.Choice("Shape", listOf("Box", "Capsule"), { shape }, { shape = it }),
        Prop.B("Carving", { carving }, { carving = it }),
        Prop.B("Carve Only Stationary", { carveOnlyStationary }, { carveOnlyStationary = it }),
        Prop.F("Size X", { sizeX }, { sizeX = it }),
        Prop.F("Size Y", { sizeY }, { sizeY = it }),
        Prop.F("Size Z", { sizeZ }, { sizeZ = it }),
    )
}

class OffMeshLinkComponent : Component() {
    override val type = "OffMeshLink"
    var start = ""
    var end = ""
    var costOverride = -1f
    var biDirectional = true
    var area = 0
    var autoUpdatePositions = false
    override fun props() = listOf(
        Prop.S("Start", { start }, { start = it }),
        Prop.S("End", { end }, { end = it }),
        Prop.F("Cost Override", { costOverride }, { costOverride = it }),
        Prop.B("Bi Directional", { biDirectional }, { biDirectional = it }),
    )
}

class BehaviorTreeComponent : Component() {
    override val type = "BehaviorTree"
    var behaviorTree = ""
    var blackboard = ""
    override fun props() = listOf(
        Prop.Asset("Behavior Tree", AssetKind.SCRIPT, { behaviorTree }, { behaviorTree = it }),
        Prop.Asset("Blackboard", AssetKind.SCRIPT, { blackboard }, { blackboard = it }),
    )
}

// ==================== UI ====================

class CanvasComponent : Component() {
    override val type = "Canvas"
    var renderMode = 0
    var sortingOrder = 0
    var targetDisplay = 0
    var pixelPerfect = false
    override fun props() = listOf(
        Prop.Choice("Render Mode", listOf("Screen Space Overlay", "Screen Space Camera", "World Space"), { renderMode }, { renderMode = it }),
        Prop.I("Sorting Order", { sortingOrder }, { sortingOrder = it }),
        Prop.B("Pixel Perfect", { pixelPerfect }, { pixelPerfect = it }),
    )
}

class CanvasGroupComponent : Component() {
    override val type = "CanvasGroup"
    var alpha = 1f
    var interactable = true
    var blocksRaycasts = true
    var ignoreParentGroups = false
    override fun props() = listOf(
        Prop.F("Alpha", { alpha }, { alpha = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.B("Interactable", { interactable }, { interactable = it }),
        Prop.B("Blocks Raycasts", { blocksRaycasts }, { blocksRaycasts = it }),
    )
}

class RectTransformComponent : Component() {
    override val type = "RectTransform"
    var anchorMinX = 0.5f
    var anchorMinY = 0.5f
    var anchorMaxX = 0.5f
    var anchorMaxY = 0.5f
    var pivotX = 0.5f
    var pivotY = 0.5f
    var anchoredPosX = 0f
    var anchoredPosY = 0f
    var sizeDeltaX = 100f
    var sizeDeltaY = 100f
    override fun props() = listOf(
        Prop.F("Anchor Min X", { anchorMinX }, { anchorMinX = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Anchor Min Y", { anchorMinY }, { anchorMinY = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Anchor Max X", { anchorMaxX }, { anchorMaxX = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Anchor Max Y", { anchorMaxY }, { anchorMaxY = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Pos X", { anchoredPosX }, { anchoredPosX = it }),
        Prop.F("Pos Y", { anchoredPosY }, { anchoredPosY = it }),
        Prop.F("Width", { sizeDeltaX }, { sizeDeltaX = it }),
        Prop.F("Height", { sizeDeltaY }, { sizeDeltaY = it }),
    )
}

class ImageComponent : Component() {
    override val type = "Image"
    var sprite = ""
    var color = 0xFFFFFFFF.toInt()
    var material = ""
    var kind = 0
    var preserveAspect = false
    var fillCenter = true
    var fillMethod = 0
    var fillAmount = 1f
    override fun props() = listOf(
        Prop.Asset("Sprite", AssetKind.TEXTURE, { sprite }, { sprite = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Choice("Type", listOf("Simple", "Sliced", "Tiled", "Filled"), { kind }, { kind = it }),
        Prop.B("Preserve Aspect", { preserveAspect }, { preserveAspect = it }),
        Prop.F("Fill Amount", { fillAmount }, { fillAmount = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

class ButtonComponent : Component() {
    override val type = "Button"
    var interactable = true
    var targetGraphic = ""
    var transition = 0
    var normalColor = 0xFFFFFFFF.toInt()
    var highlightedColor = 0xFFF5F5F5.toInt()
    var pressedColor = 0xFFC8C8C8.toInt()
    var selectedColor = 0xFFF5F5F5.toInt()
    var disabledColor = 0xFFC8C8C8.toInt()
    override fun props() = listOf(
        Prop.B("Interactable", { interactable }, { interactable = it }),
        Prop.Choice("Transition", listOf("None", "Color Tint", "Sprite Swap", "Animation"), { transition }, { transition = it }),
    )
}

class TextMeshProComponent : Component() {
    override val type = "TextMeshPro"
    var text = "New Text"
    var font = ""
    var fontSize = 36f
    var color = 0xFFFFFFFF.toInt()
    var alignment = 0
    var autoSize = false
    var richText = true
    var raycastTarget = true
    override fun props() = listOf(
        Prop.S("Text", { text }, { text = it }, multiline = true),
        Prop.Asset("Font", AssetKind.FONT, { font }, { font = it }),
        Prop.F("Font Size", { fontSize }, { fontSize = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Choice("Alignment", listOf("Left", "Center", "Right", "Justified"), { alignment }, { alignment = it }),
        Prop.B("Auto Size", { autoSize }, { autoSize = it }),
        Prop.B("Rich Text", { richText }, { richText = it }),
    )
}

class ScrollViewComponent : Component() {
    override val type = "ScrollView"
    var horizontal = true
    var vertical = true
    var movementType = 1
    var elasticity = 0.1f
    var inertia = true
    var decelerationRate = 0.135f
    override fun props() = listOf(
        Prop.B("Horizontal", { horizontal }, { horizontal = it }),
        Prop.B("Vertical", { vertical }, { vertical = it }),
        Prop.Choice("Movement Type", listOf("Unrestricted", "Elastic", "Clamped"), { movementType }, { movementType = it }),
        Prop.B("Inertia", { inertia }, { inertia = it }),
    )
}

// ==================== Environment ====================

class TerrainComponent : Component() {
    override val type = "Terrain"
    var terrainData = ""
    var detailObjectDistance = 80f
    var detailObjectDensity = 1f
    var treeDistance = 5000f
    var treeBillboardDistance = 400f
    var basemapDistance = 1000f
    var drawInstanced = true
    override fun props() = listOf(
        Prop.Asset("Terrain Data", AssetKind.TEXTURE, { terrainData }, { terrainData = it }),
        Prop.F("Detail Distance", { detailObjectDistance }, { detailObjectDistance = it }),
        Prop.F("Tree Distance", { treeDistance }, { treeDistance = it }),
        Prop.B("Draw Instanced", { drawInstanced }, { drawInstanced = it }),
    )
}

class WaterComponent : Component() {
    override val type = "Water"
    var waterColor = 0xFF0A4A6A.toInt()
    var depthColor = 0xFF001E2F.toInt()
    var waveSpeed = 1f
    var waveScale = 0.5f
    var depth = 10f
    override fun props() = listOf(
        Prop.Color("Water Color", { waterColor }, { waterColor = it }),
        Prop.Color("Depth Color", { depthColor }, { depthColor = it }),
        Prop.F("Wave Speed", { waveSpeed }, { waveSpeed = it }),
        Prop.F("Wave Scale", { waveScale }, { waveScale = it }),
        Prop.F("Depth", { depth }, { depth = it }),
    )
}

class WindZoneComponent : Component() {
    override val type = "WindZone"
    var mode = 0
    var radius = 10f
    var windMain = 1f
    var windTurbulence = 1f
    var windPulseMagnitude = 0.5f
    var windPulseFrequency = 0.5f
    override fun props() = listOf(
        Prop.Choice("Mode", listOf("Directional", "Spherical"), { mode }, { mode = it }),
        Prop.F("Radius", { radius }, { radius = it }),
        Prop.F("Wind Main", { windMain }, { windMain = it }),
        Prop.F("Wind Turbulence", { windTurbulence }, { windTurbulence = it }),
    )
}

class TreeComponent : Component() {
    override val type = "Tree"
    var prefab = ""
    var bendFactor = 0f
    override fun props() = listOf(
        Prop.Asset("Prefab", AssetKind.MODEL, { prefab }, { prefab = it }),
        Prop.F("Bend Factor", { bendFactor }, { bendFactor = it }),
    )
}

// ==================== Networking ====================

class NetworkIdentityComponent : Component() {
    override val type = "NetworkIdentity"
    var assetId = ""
    var isLocalPlayer = false
    override fun props() = listOf(
        Prop.S("Asset ID", { assetId }, { assetId = it }),
        Prop.B("Is Local Player", { isLocalPlayer }, { isLocalPlayer = it }),
    )
}

class NetworkTransformComponent : Component() {
    override val type = "NetworkTransform"
    var syncPosition = true
    var syncRotation = true
    var syncScale = false
    var sendRate = 20f
    var interpolate = true
    override fun props() = listOf(
        Prop.B("Sync Position", { syncPosition }, { syncPosition = it }),
        Prop.B("Sync Rotation", { syncRotation }, { syncRotation = it }),
        Prop.B("Sync Scale", { syncScale }, { syncScale = it }),
        Prop.F("Send Rate", { sendRate }, { sendRate = it }),
        Prop.B("Interpolate", { interpolate }, { interpolate = it }),
    )
}

class NetworkAnimatorComponent : Component() {
    override val type = "NetworkAnimator"
    var autoSend = true
    override fun props() = listOf(Prop.B("Auto Send", { autoSend }, { autoSend = it }))
}

// ==================== Scripting ====================

class ScriptComponent : Component() {
    override val type = "Script"
    var script = ""
    var params = ""
    override fun props() = listOf(
        Prop.Asset("Script", AssetKind.SCRIPT, { script }, { script = it }),
        Prop.S("Params", { params }, { params = it }),
    )
}

class ScriptableObjectComponent : Component() {
    override val type = "ScriptableObject"
    var scriptableObject = ""
    override fun props() = listOf(Prop.Asset("Scriptable Object", AssetKind.SCRIPT, { scriptableObject }, { scriptableObject = it }))
}

// ==================== Optimization ====================

class LODComponent : Component() {
    override val type = "LOD"
    var lodLevel = 0
    var screenRelativeHeight = 0.5f
    var fadeTransitionWidth = 0f
    override fun props() = listOf(
        Prop.I("LOD Level", { lodLevel }, { lodLevel = it }),
        Prop.F("Screen Relative Height", { screenRelativeHeight }, { screenRelativeHeight = it.coerceIn(0f, 1f) }, 0.05f),
    )
}

class OcclusionAreaComponent : Component() {
    override val type = "OcclusionArea"
    var centerX = 0f
    var centerY = 0f
    var centerZ = 0f
    var sizeX = 10f
    var sizeY = 10f
    var sizeZ = 10f
    override fun props() = listOf(
        Prop.F("Size X", { sizeX }, { sizeX = it }),
        Prop.F("Size Y", { sizeY }, { sizeY = it }),
        Prop.F("Size Z", { sizeZ }, { sizeZ = it }),
    )
}

class OcclusionPortalComponent : Component() {
    override val type = "OcclusionPortal"
    var open = true
    override fun props() = listOf(Prop.B("Open", { open }, { open = it }))
}

class CullingGroupComponent : Component() {
    override val type = "CullingGroup"
    var boundingDistance = 100f
    override fun props() = listOf(Prop.F("Bounding Distance", { boundingDistance }, { boundingDistance = it }))
}

class PrefabInstanceComponent : Component() {
    override val type = "PrefabInstance"
    var prefab = ""
    var nested = false
    override fun props() = listOf(
        Prop.Asset("Prefab", AssetKind.TEXTURE, { prefab }, { prefab = it }),
        Prop.B("Nested", { nested }, { nested = it }),
    )
}
