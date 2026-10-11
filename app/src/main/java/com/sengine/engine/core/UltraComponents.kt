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
    override fun clone() = BurstJobComponent().also { it.jobName=jobName; it.optimization=optimization; it.isCompiled=isCompiled }
}

class AssemblyDefinitionComponent : Component() {
    override val type = "AssemblyDefinition"
    var assemblyName: String = "MainAssembly"
    var assemblyType: String = "Runtime"
    var allowUnsafeCode: Boolean = false
    var autoReferenced: Boolean = true
    override fun clone() = AssemblyDefinitionComponent().also { it.assemblyName=assemblyName; it.assemblyType=assemblyType; it.allowUnsafeCode=allowUnsafeCode; it.autoReferenced=autoReferenced }
}

class ScriptableObjectComponent : Component() {
    override val type = "ScriptableObject"
    var soName: String = "GameConfig"
    var soType: String = "GameConfig"
    var guid: String = ""
    override fun clone() = ScriptableObjectComponent().also { it.soName=soName; it.soType=soType; it.guid=guid }
}

class ClusteredRenderingComponent : Component() {
    override val type = "ClusteredRendering"
    var clusterCountX: Int = 16
    var clusterCountY: Int = 9
    var clusterCountZ: Int = 24
    var mode: String = "Clustered"
    override fun clone() = ClusteredRenderingComponent().also { it.clusterCountX=clusterCountX; it.clusterCountY=clusterCountY; it.clusterCountZ=clusterCountZ; it.mode=mode }
}

class ShadowCSMComponent : Component() {
    override val type = "ShadowCSM"
    var cascadeCount: Int = 4
    var lambda: Float = 0.5f
    var shadowDistance: Float = 100f
    var pcssEnabled: Boolean = true
    var vsmEnabled: Boolean = false
    var contactShadows: Boolean = false
    override fun clone() = ShadowCSMComponent().also { it.cascadeCount=cascadeCount; it.lambda=lambda; it.shadowDistance=shadowDistance; it.pcssEnabled=pcssEnabled; it.vsmEnabled=vsmEnabled; it.contactShadows=contactShadows }
}

class UpscalerComponent : Component() {
    override val type = "Upscaler"
    var upscalerType: String = "FSR2"
    var qualityMode: String = "Quality"
    var renderScale: Float = 0.67f
    var sharpness: Float = 0.5f
    override fun clone() = UpscalerComponent().also { it.upscalerType=upscalerType; it.qualityMode=qualityMode; it.renderScale=renderScale; it.sharpness=sharpness }
}

class VolumetricCloudsV2Component : Component() {
    override val type = "VolumetricCloudsV2"
    var quality: String = "High"
    var coverage: Float = 0.5f
    var density: Float = 0.5f
    var bottomAltitude: Float = 1500f
    var topAltitude: Float = 4000f
    var raymarchSteps: Int = 64
    override fun clone() = VolumetricCloudsV2Component().also { it.quality=quality; it.coverage=coverage; it.density=density; it.bottomAltitude=bottomAltitude; it.topAltitude=topAltitude; it.raymarchSteps=raymarchSteps }
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
    override fun clone() = WaterV2Component().also { it.width=width; it.height=height; it.waveScale=waveScale; it.waveSpeed=waveSpeed; it.foamThreshold=foamThreshold; it.hasRefraction=hasRefraction; it.hasReflection=hasReflection; it.underwaterFog=underwaterFog }
}

class PhysicsQueryComponent : Component() {
    override val type = "PhysicsQuery"
    var queryType: String = "BoxCast"
    var maxHits: Int = 32
    var queryTriggerInteraction: String = "UseGlobal"
    override fun clone() = PhysicsQueryComponent().also { it.queryType=queryType; it.maxHits=maxHits; it.queryTriggerInteraction=queryTriggerInteraction }
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
    override fun clone() = ConfigurableJointComponent().also { it.xMotion=xMotion; it.yMotion=yMotion; it.zMotion=zMotion; it.angularXMotion=angularXMotion; it.angularYMotion=angularYMotion; it.angularZMotion=angularZMotion; it.projectionMode=projectionMode }
}

class TireFrictionComponent : Component() {
    override val type = "TireFriction"
    var frictionModel: String = "Pacejka"
    var forwardStiffness: Float = 1f
    var sidewaysStiffness: Float = 1f
    var extremumSlip: Float = 0.4f
    var asymptoteSlip: Float = 0.8f
    override fun clone() = TireFrictionComponent().also { it.frictionModel=frictionModel; it.forwardStiffness=forwardStiffness; it.sidewaysStiffness=sidewaysStiffness; it.extremumSlip=extremumSlip; it.asymptoteSlip=asymptoteSlip }
}

class AnimationEventComponent : Component() {
    override val type = "AnimationEvent"
    var functionName: String = "OnFootstep"
    var time: Float = 0f
    var floatParam: Float = 0f
    var stringParam: String = ""
    var intParam: Int = 0
    override fun clone() = AnimationEventComponent().also { it.functionName=functionName; it.time=time; it.floatParam=floatParam; it.stringParam=stringParam; it.intParam=intParam }
}

class BlendShapeComponent : Component() {
    override val type = "BlendShape"
    var blendShapeName: String = "Smile"
    var weight: Float = 0f
    var mode: String = "Normalized"
    override fun clone() = BlendShapeComponent().also { it.blendShapeName=blendShapeName; it.weight=weight; it.mode=mode }
}

class PoseLibraryComponent : Component() {
    override val type = "PoseLibrary"
    var libraryName: String = "HumanoidPoses"
    var currentPose: String = "Idle"
    var blendTime: Float = 0.2f
    override fun clone() = PoseLibraryComponent().also { it.libraryName=libraryName; it.currentPose=currentPose; it.blendTime=blendTime }
}

class MotionWarpingV2Component : Component() {
    override val type = "MotionWarpingV2"
    var warpName: String = "VaultWarp"
    var warpTranslation: Boolean = true
    var warpRotation: Boolean = true
    var targetX: Float = 0f; var targetY: Float = 0f; var targetZ: Float = 0f
    override fun clone() = MotionWarpingV2Component().also { it.warpName=warpName; it.warpTranslation=warpTranslation; it.warpRotation=warpRotation; it.targetX=targetX; it.targetY=targetY; it.targetZ=targetZ }
}

class AudioMixerSnapshotComponent : Component() {
    override val type = "AudioMixerSnapshot"
    var snapshotName: String = "Default"
    var snapshotType: String = "Default"
    var transitionTime: Float = 0.5f
    override fun clone() = AudioMixerSnapshotComponent().also { it.snapshotName=snapshotName; it.snapshotType=snapshotType; it.transitionTime=transitionTime }
}

class AudioPortalComponent : Component() {
    override val type = "AudioPortal"
    var portalId: String = "portal_0"
    var width: Float = 2f
    var height: Float = 2f
    var isOpen: Boolean = true
    var occlusionFactor: Float = 0.5f
    override fun clone() = AudioPortalComponent().also { it.portalId=portalId; it.width=width; it.height=height; it.isOpen=isOpen; it.occlusionFactor=occlusionFactor }
}

class ReverbProbeComponent : Component() {
    override val type = "ReverbProbe"
    var radius: Float = 10f
    var reverbPreset: String = "Room"
    var baked: Boolean = false
    override fun clone() = ReverbProbeComponent().also { it.radius=radius; it.reverbPreset=reverbPreset; it.baked=baked }
}

class NavMeshAreaComponent : Component() {
    override val type = "NavMeshArea"
    var areaType: String = "Walkable"
    var cost: Float = 1f
    override fun clone() = NavMeshAreaComponent().also { it.areaType=areaType; it.cost=cost }
}

class OffMeshLinkComponent : Component() {
    override val type = "OffMeshLink"
    var linkType: String = "JumpAcross"
    var startX: Float = 0f; var startY: Float = 0f; var startZ: Float = 0f
    var endX: Float = 5f; var endY: Float = 0f; var endZ: Float = 0f
    var bidirectional: Boolean = true
    var costOverride: Float = -1f
    override fun clone() = OffMeshLinkComponent().also { it.linkType=linkType; it.startX=startX; it.startY=startY; it.startZ=startZ; it.endX=endX; it.endY=endY; it.endZ=endZ; it.bidirectional=bidirectional; it.costOverride=costOverride }
}

class PerceptionComponent : Component() {
    override val type = "Perception"
    var sightRange: Float = 20f
    var sightAngle: Float = 90f
    var hearingRange: Float = 10f
    var sightHeight: Float = 1.6f
    override fun clone() = PerceptionComponent().also { it.sightRange=sightRange; it.sightAngle=sightAngle; it.hearingRange=hearingRange; it.sightHeight=sightHeight }
}

class CoverPointComponent : Component() {
    override val type = "CoverPoint"
    var coverType: String = "High"
    var dirX: Float = 0f; var dirZ: Float = 1f
    var quality: Float = 1f
    var isOccupied: Boolean = false
    override fun clone() = CoverPointComponent().also { it.coverType=coverType; it.dirX=dirX; it.dirZ=dirZ; it.quality=quality; it.isOccupied=isOccupied }
}

class GOAPComponent : Component() {
    override val type = "GOAP"
    var currentGoal: String = "KillEnemy"
    var isPlanning: Boolean = false
    var planLength: Int = 0
    override fun clone() = GOAPComponent().also { it.currentGoal=currentGoal; it.isPlanning=isPlanning; it.planLength=planLength }
}

class InterestManagementComponent : Component() {
    override val type = "InterestManagement"
    var mode: String = "Distance"
    var cullingDistance: Float = 100f
    var lodHigh: Float = 10f
    var lodMedium: Float = 30f
    var lodLow: Float = 100f
    override fun clone() = InterestManagementComponent().also { it.mode=mode; it.cullingDistance=cullingDistance; it.lodHigh=lodHigh; it.lodMedium=lodMedium; it.lodLow=lodLow }
}

class ClientPredictionComponent : Component() {
    override val type = "ClientPrediction"
    var predictionEnabled: Boolean = true
    var reconciliationEnabled: Boolean = true
    var errorThreshold: Float = 0.1f
    var maxHistory: Int = 60
    override fun clone() = ClientPredictionComponent().also { it.predictionEnabled=predictionEnabled; it.reconciliationEnabled=reconciliationEnabled; it.errorThreshold=errorThreshold; it.maxHistory=maxHistory }
}

class VoiceChat3DComponent : Component() {
    override val type = "VoiceChat3D"
    var volume: Float = 1f
    var isTalking: Boolean = false
    var spatialBlend: Float = 1f
    var minDistance: Float = 1f
    var maxDistance: Float = 20f
    override fun clone() = VoiceChat3DComponent().also { it.volume=volume; it.isTalking=isTalking; it.spatialBlend=spatialBlend; it.minDistance=minDistance; it.maxDistance=maxDistance }
}

class LobbyComponent : Component() {
    override val type = "Lobby"
    var lobbyName: String = "Game Lobby"
    var maxPlayers: Int = 8
    var gameMode: String = "Deathmatch"
    var isPrivate: Boolean = false
    override fun clone() = LobbyComponent().also { it.lobbyName=lobbyName; it.maxPlayers=maxPlayers; it.gameMode=gameMode; it.isPrivate=isPrivate }
}

class InputRebindingComponent : Component() {
    override val type = "InputRebinding"
    var actionName: String = "Jump"
    var controlScheme: String = "Keyboard&Mouse"
    var currentPath: String = "<Keyboard>/space"
    override fun clone() = InputRebindingComponent().also { it.actionName=actionName; it.controlScheme=controlScheme; it.currentPath=currentPath }
}

class SaveSystemComponent : Component() {
    override val type = "SaveSystem"
    var encryption: String = "AES256"
    var compression: String = "GZip"
    var cloudEnabled: Boolean = true
    var crossSaveEnabled: Boolean = true
    var maxSlots: Int = 10
    override fun clone() = SaveSystemComponent().also { it.encryption=encryption; it.compression=compression; it.cloudEnabled=cloudEnabled; it.crossSaveEnabled=crossSaveEnabled; it.maxSlots=maxSlots }
}

class EditorWindowComponent : Component() {
    override val type = "EditorWindow"
    var windowType: String = "Hierarchy"
    var isDocked: Boolean = true
    var isVisible: Boolean = true
    override fun clone() = EditorWindowComponent().also { it.windowType=windowType; it.isDocked=isDocked; it.isVisible=isVisible }
}

class XRComponent : Component() {
    override val type = "XR"
    var trackingType: String = "Hand"
    var isTracked: Boolean = false
    var confidence: Float = 0f
    override fun clone() = XRComponent().also { it.trackingType=trackingType; it.isTracked=isTracked; it.confidence=confidence }
}

class ARPlaneComponent : Component() {
    override val type = "ARPlane"
    var width: Float = 1f
    var height: Float = 1f
    var isValid: Boolean = true
    override fun clone() = ARPlaneComponent().also { it.width=width; it.height=height; it.isValid=isValid }
}
