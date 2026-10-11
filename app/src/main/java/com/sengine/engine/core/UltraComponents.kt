package com.sengine.engine.core

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Components - Months/Years Professional Project
 * All Anything Working System - Most Powerful Full Control
 */

class BurstJobComponent : Component() {
    override val type = "BurstJob"
    var jobName: String = "TransformJob"
    var optimization: String = "O3"
    var isCompiled: Boolean = false
    override fun props() = listOf(
        Prop.S("JobName", { jobName }, { jobName = it }),
        Prop.S("Optimization", { optimization }, { optimization = it }),
        Prop.B("IsCompiled", { isCompiled }, { isCompiled = it })
    )
}

class AssemblyDefinitionComponent : Component() {
    override val type = "AssemblyDefinition"
    var assemblyName: String = "MainAssembly"
    var assemblyType: String = "Runtime"
    var allowUnsafeCode: Boolean = false
    var autoReferenced: Boolean = true
    override fun props() = listOf(
        Prop.S("AssemblyName", { assemblyName }, { assemblyName = it }),
        Prop.S("AssemblyType", { assemblyType }, { assemblyType = it }),
        Prop.B("AllowUnsafeCode", { allowUnsafeCode }, { allowUnsafeCode = it })
    )
}

class ClusteredRenderingComponent : Component() {
    override val type = "ClusteredRendering"
    var clusterCountX: Int = 16
    var clusterCountY: Int = 9
    var clusterCountZ: Int = 24
    var mode: String = "Clustered"
    override fun props() = listOf(
        Prop.I("ClusterCountX", { clusterCountX }, { clusterCountX = it }),
        Prop.I("ClusterCountY", { clusterCountY }, { clusterCountY = it }),
        Prop.I("ClusterCountZ", { clusterCountZ }, { clusterCountZ = it }),
        Prop.S("Mode", { mode }, { mode = it })
    )
}

class ShadowCSMComponent : Component() {
    override val type = "ShadowCSM"
    var cascadeCount: Int = 4
    var lambda: Float = 0.5f
    var shadowDistance: Float = 100f
    var pcssEnabled: Boolean = true
    var vsmEnabled: Boolean = false
    var contactShadows: Boolean = false
    override fun props() = listOf(
        Prop.I("CascadeCount", { cascadeCount }, { cascadeCount = it }),
        Prop.F("Lambda", { lambda }, { lambda = it }),
        Prop.F("ShadowDistance", { shadowDistance }, { shadowDistance = it }),
        Prop.B("PCSSEnabled", { pcssEnabled }, { pcssEnabled = it })
    )
}

class UpscalerComponent : Component() {
    override val type = "Upscaler"
    var upscalerType: String = "FSR2"
    var qualityMode: String = "Quality"
    var renderScale: Float = 0.67f
    var sharpness: Float = 0.5f
    override fun props() = listOf(
        Prop.S("UpscalerType", { upscalerType }, { upscalerType = it }),
        Prop.S("QualityMode", { qualityMode }, { qualityMode = it }),
        Prop.F("RenderScale", { renderScale }, { renderScale = it })
    )
}

class VolumetricCloudsV2Component : Component() {
    override val type = "VolumetricCloudsV2"
    var quality: String = "High"
    var coverage: Float = 0.5f
    var density: Float = 0.5f
    var bottomAltitude: Float = 1500f
    var topAltitude: Float = 4000f
    var raymarchSteps: Int = 64
    override fun props() = listOf(
        Prop.S("Quality", { quality }, { quality = it }),
        Prop.F("Coverage", { coverage }, { coverage = it }),
        Prop.F("Density", { density }, { density = it }),
        Prop.I("RaymarchSteps", { raymarchSteps }, { raymarchSteps = it })
    )
}

class WaterV2Component : Component() {
    override val type = "WaterV2"
    var width: Float = 100f
    var height: Float = 100f
    var waveScale: Float = 0.5f
    var waveSpeed: Float = 1f
    var foamThreshold: Float = 0.5f
    var hasRefraction: Boolean = true
    var hasReflection: Boolean = true
    var underwaterFog: Boolean = true
    override fun props() = listOf(
        Prop.F("Width", { width }, { width = it }),
        Prop.F("Height", { height }, { height = it }),
        Prop.F("WaveScale", { waveScale }, { waveScale = it }),
        Prop.F("WaveSpeed", { waveSpeed }, { waveSpeed = it })
    )
}

class PhysicsQueryComponent : Component() {
    override val type = "PhysicsQuery"
    var queryType: String = "BoxCast"
    var maxHits: Int = 32
    var queryTriggerInteraction: String = "UseGlobal"
    override fun props() = listOf(
        Prop.S("QueryType", { queryType }, { queryType = it }),
        Prop.I("MaxHits", { maxHits }, { maxHits = it })
    )
}

class ConfigurableJointComponent : Component() {
    override val type = "ConfigurableJoint"
    var xMotion: String = "Locked"
    var yMotion: String = "Locked"
    var zMotion: String = "Locked"
    var angularXMotion: String = "Locked"
    var angularYMotion: String = "Locked"
    var angularZMotion: String = "Locked"
    var projectionMode: String = "None"
    override fun props() = listOf(
        Prop.S("XMotion", { xMotion }, { xMotion = it }),
        Prop.S("YMotion", { yMotion }, { yMotion = it }),
        Prop.S("ZMotion", { zMotion }, { zMotion = it }),
        Prop.S("ProjectionMode", { projectionMode }, { projectionMode = it })
    )
}

class TireFrictionComponent : Component() {
    override val type = "TireFriction"
    var frictionModel: String = "Pacejka"
    var forwardStiffness: Float = 1f
    var sidewaysStiffness: Float = 1f
    var extremumSlip: Float = 0.4f
    var asymptoteSlip: Float = 0.8f
    override fun props() = listOf(
        Prop.S("FrictionModel", { frictionModel }, { frictionModel = it }),
        Prop.F("ForwardStiffness", { forwardStiffness }, { forwardStiffness = it }),
        Prop.F("SidewaysStiffness", { sidewaysStiffness }, { sidewaysStiffness = it })
    )
}

class AnimationEventComponent : Component() {
    override val type = "AnimationEvent"
    var functionName: String = "OnFootstep"
    var time: Float = 0f
    var floatParam: Float = 0f
    var stringParam: String = ""
    var intParam: Int = 0
    override fun props() = listOf(
        Prop.S("FunctionName", { functionName }, { functionName = it }),
        Prop.F("Time", { time }, { time = it }),
        Prop.F("FloatParam", { floatParam }, { floatParam = it })
    )
}

class BlendShapeComponent : Component() {
    override val type = "BlendShape"
    var blendShapeName: String = "Smile"
    var weight: Float = 0f
    var mode: String = "Normalized"
    override fun props() = listOf(
        Prop.S("BlendShapeName", { blendShapeName }, { blendShapeName = it }),
        Prop.F("Weight", { weight }, { weight = it }),
        Prop.S("Mode", { mode }, { mode = it })
    )
}

class PoseLibraryComponent : Component() {
    override val type = "PoseLibrary"
    var libraryName: String = "HumanoidPoses"
    var currentPose: String = "Idle"
    var blendTime: Float = 0.2f
    override fun props() = listOf(
        Prop.S("LibraryName", { libraryName }, { libraryName = it }),
        Prop.S("CurrentPose", { currentPose }, { currentPose = it }),
        Prop.F("BlendTime", { blendTime }, { blendTime = it })
    )
}

class MotionWarpingV2Component : Component() {
    override val type = "MotionWarpingV2"
    var warpName: String = "VaultWarp"
    var warpTranslation: Boolean = true
    var warpRotation: Boolean = true
    var targetX: Float = 0f; var targetY: Float = 0f; var targetZ: Float = 0f
    override fun props() = listOf(
        Prop.S("WarpName", { warpName }, { warpName = it }),
        Prop.B("WarpTranslation", { warpTranslation }, { warpTranslation = it }),
        Prop.B("WarpRotation", { warpRotation }, { warpRotation = it }),
        Prop.F("TargetX", { targetX }, { targetX = it })
    )
}

class AudioMixerSnapshotComponent : Component() {
    override val type = "AudioMixerSnapshot"
    var snapshotName: String = "Default"
    var snapshotType: String = "Default"
    var transitionTime: Float = 0.5f
    override fun props() = listOf(
        Prop.S("SnapshotName", { snapshotName }, { snapshotName = it }),
        Prop.S("SnapshotType", { snapshotType }, { snapshotType = it }),
        Prop.F("TransitionTime", { transitionTime }, { transitionTime = it })
    )
}

class AudioPortalComponent : Component() {
    override val type = "AudioPortal"
    var portalId: String = "portal_0"
    var width: Float = 2f
    var height: Float = 2f
    var isOpen: Boolean = true
    var occlusionFactor: Float = 0.5f
    override fun props() = listOf(
        Prop.S("PortalId", { portalId }, { portalId = it }),
        Prop.F("Width", { width }, { width = it }),
        Prop.F("Height", { height }, { height = it }),
        Prop.B("IsOpen", { isOpen }, { isOpen = it })
    )
}

class ReverbProbeComponent : Component() {
    override val type = "ReverbProbe"
    var radius: Float = 10f
    var reverbPreset: String = "Room"
    var baked: Boolean = false
    override fun props() = listOf(
        Prop.F("Radius", { radius }, { radius = it }),
        Prop.S("ReverbPreset", { reverbPreset }, { reverbPreset = it }),
        Prop.B("Baked", { baked }, { baked = it })
    )
}

class NavMeshAreaComponent : Component() {
    override val type = "NavMeshArea"
    var areaType: String = "Walkable"
    var cost: Float = 1f
    override fun props() = listOf(
        Prop.S("AreaType", { areaType }, { areaType = it }),
        Prop.F("Cost", { cost }, { cost = it })
    )
}

class PerceptionComponent : Component() {
    override val type = "Perception"
    var sightRange: Float = 20f
    var sightAngle: Float = 90f
    var hearingRange: Float = 10f
    var sightHeight: Float = 1.6f
    override fun props() = listOf(
        Prop.F("SightRange", { sightRange }, { sightRange = it }),
        Prop.F("SightAngle", { sightAngle }, { sightAngle = it }),
        Prop.F("HearingRange", { hearingRange }, { hearingRange = it })
    )
}

class CoverPointComponent : Component() {
    override val type = "CoverPoint"
    var coverType: String = "High"
    var dirX: Float = 0f; var dirZ: Float = 1f
    var quality: Float = 1f
    var isOccupied: Boolean = false
    override fun props() = listOf(
        Prop.S("CoverType", { coverType }, { coverType = it }),
        Prop.F("DirX", { dirX }, { dirX = it }),
        Prop.F("DirZ", { dirZ }, { dirZ = it }),
        Prop.B("IsOccupied", { isOccupied }, { isOccupied = it })
    )
}

class GOAPComponent : Component() {
    override val type = "GOAP"
    var currentGoal: String = "KillEnemy"
    var isPlanning: Boolean = false
    var planLength: Int = 0
    override fun props() = listOf(
        Prop.S("CurrentGoal", { currentGoal }, { currentGoal = it }),
        Prop.B("IsPlanning", { isPlanning }, { isPlanning = it }),
        Prop.I("PlanLength", { planLength }, { planLength = it })
    )
}

class InterestManagementComponent : Component() {
    override val type = "InterestManagement"
    var mode: String = "Distance"
    var cullingDistance: Float = 100f
    var lodHigh: Float = 10f
    var lodMedium: Float = 30f
    var lodLow: Float = 100f
    override fun props() = listOf(
        Prop.S("Mode", { mode }, { mode = it }),
        Prop.F("CullingDistance", { cullingDistance }, { cullingDistance = it }),
        Prop.F("LodHigh", { lodHigh }, { lodHigh = it })
    )
}

class ClientPredictionComponent : Component() {
    override val type = "ClientPrediction"
    var predictionEnabled: Boolean = true
    var reconciliationEnabled: Boolean = true
    var errorThreshold: Float = 0.1f
    var maxHistory: Int = 60
    override fun props() = listOf(
        Prop.B("PredictionEnabled", { predictionEnabled }, { predictionEnabled = it }),
        Prop.B("ReconciliationEnabled", { reconciliationEnabled }, { reconciliationEnabled = it }),
        Prop.F("ErrorThreshold", { errorThreshold }, { errorThreshold = it })
    )
}

class VoiceChat3DComponent : Component() {
    override val type = "VoiceChat3D"
    var volume: Float = 1f
    var isTalking: Boolean = false
    var spatialBlend: Float = 1f
    var minDistance: Float = 1f
    var maxDistance: Float = 20f
    override fun props() = listOf(
        Prop.F("Volume", { volume }, { volume = it }),
        Prop.B("IsTalking", { isTalking }, { isTalking = it }),
        Prop.F("SpatialBlend", { spatialBlend }, { spatialBlend = it })
    )
}

class LobbyComponent : Component() {
    override val type = "Lobby"
    var lobbyName: String = "Game Lobby"
    var maxPlayers: Int = 8
    var gameMode: String = "Deathmatch"
    var isPrivate: Boolean = false
    override fun props() = listOf(
        Prop.S("LobbyName", { lobbyName }, { lobbyName = it }),
        Prop.I("MaxPlayers", { maxPlayers }, { maxPlayers = it }),
        Prop.S("GameMode", { gameMode }, { gameMode = it }),
        Prop.B("IsPrivate", { isPrivate }, { isPrivate = it })
    )
}

class InputRebindingComponent : Component() {
    override val type = "InputRebinding"
    var actionName: String = "Jump"
    var controlScheme: String = "Keyboard&Mouse"
    var currentPath: String = "<Keyboard>/space"
    override fun props() = listOf(
        Prop.S("ActionName", { actionName }, { actionName = it }),
        Prop.S("ControlScheme", { controlScheme }, { controlScheme = it }),
        Prop.S("CurrentPath", { currentPath }, { currentPath = it })
    )
}

class SaveSystemComponent : Component() {
    override val type = "SaveSystem"
    var encryption: String = "AES256"
    var compression: String = "GZip"
    var cloudEnabled: Boolean = true
    var crossSaveEnabled: Boolean = true
    var maxSlots: Int = 10
    override fun props() = listOf(
        Prop.S("Encryption", { encryption }, { encryption = it }),
        Prop.S("Compression", { compression }, { compression = it }),
        Prop.B("CloudEnabled", { cloudEnabled }, { cloudEnabled = it }),
        Prop.I("MaxSlots", { maxSlots }, { maxSlots = it })
    )
}

class EditorWindowComponent : Component() {
    override val type = "EditorWindow"
    var windowType: String = "Hierarchy"
    var isDocked: Boolean = true
    var isVisible: Boolean = true
    override fun props() = listOf(
        Prop.S("WindowType", { windowType }, { windowType = it }),
        Prop.B("IsDocked", { isDocked }, { isDocked = it }),
        Prop.B("IsVisible", { isVisible }, { isVisible = it })
    )
}

class XRComponent : Component() {
    override val type = "XR"
    var trackingType: String = "Hand"
    var isTracked: Boolean = false
    var confidence: Float = 0f
    override fun props() = listOf(
        Prop.S("TrackingType", { trackingType }, { trackingType = it }),
        Prop.B("IsTracked", { isTracked }, { isTracked = it }),
        Prop.F("Confidence", { confidence }, { confidence = it })
    )
}

class ARPlaneComponent : Component() {
    override val type = "ARPlane"
    var width: Float = 1f
    var height: Float = 1f
    var isValid: Boolean = true
    override fun props() = listOf(
        Prop.F("Width", { width }, { width = it }),
        Prop.F("Height", { height }, { height = it }),
        Prop.B("IsValid", { isValid }, { isValid = it })
    )
}
