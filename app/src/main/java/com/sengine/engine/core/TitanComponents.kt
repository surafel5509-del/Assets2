package com.sengine.engine.core

import org.json.JSONObject

/**
 * S Engine Ultimate 6.1.1 [Titan] - Most Powerful Edition - Titan 2D Components
 * Unity 2D Components: Light2D, ShadowCaster2D, Tilemap, Sorting, Bones, Cinemachine2D, etc
 */

// Light2D Component
class Light2DComponent : Component() {
    override val name = "Light2D"
    var kind: Int = 0 // 0 global, 1 point, 2 spot, 3 freeform, 4 sprite
    var color: Int = 0xFFFFFFFF.toInt()
    var intensity: Float = 1f
    var radius: Float = 5f
    var innerRadius: Float = 0f
    var outerAngle: Float = 360f
    var innerAngle: Float = 0f
    var falloff: Float = 1f
    var shadows: Boolean = false
    var shadowIntensity: Float = 0.5f
    var blendStyle: Int = 0 // 0 additive, 1 multiply
    var normalMapQuality: Int = 1
    var lightOrder: Int = 0
    var cookie: String = ""

    override fun props(): List<Prop> = listOf(
        Prop("kind", PropType.INT, kind),
        Prop("color", PropType.COLOR, color),
        Prop("intensity", PropType.FLOAT, intensity),
        Prop("radius", PropType.FLOAT, radius, min=0f, max=100f),
        Prop("range", PropType.FLOAT, radius, min=0f, max=100f),
        Prop("innerRadius", PropType.FLOAT, innerRadius),
        Prop("outerAngle", PropType.FLOAT, outerAngle),
        Prop("shadows", PropType.BOOL, shadows),
        Prop("blendStyle", PropType.INT, blendStyle),
        Prop("normalMapQuality", PropType.INT, normalMapQuality),
        Prop("lightOrder", PropType.INT, lightOrder)
    )
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "kind"->kind=(v as Number).toInt()
            "color"->color=(v as Number).toInt()
            "intensity"->intensity=(v as Number).toFloat()
            "radius"->radius=(v as Number).toFloat()
            "range"->radius=(v as Number).toFloat()
            "innerRadius"->innerRadius=(v as Number).toFloat()
            "outerAngle"->outerAngle=(v as Number).toFloat()
            "shadows"->shadows=v as Boolean
            "blendStyle"->blendStyle=(v as Number).toInt()
            "normalMapQuality"->normalMapQuality=(v as Number).toInt()
            "lightOrder"->lightOrder=(v as Number).toInt()
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("kind", kind).put("color", color).put("intensity", intensity.toDouble()).put("radius", radius.toDouble()).put("shadows", shadows)
    override fun fromJson(o: JSONObject) { kind=o.optInt("kind",0); color=o.optInt("color",-1); intensity=o.optDouble("intensity",1.0).toFloat(); radius=o.optFloat("radius",5f); shadows=o.optBoolean("shadows",false) }
    override fun duplicate(): Component = Light2DComponent().also { it.kind=kind; it.color=color; it.intensity=intensity; it.radius=radius; it.shadows=shadows }
}

// ShadowCaster2D
class ShadowCaster2DComponent : Component() {
    override val name = "ShadowCaster2D"
    var selfShadows: Boolean = true
    var castShadows: Boolean = true
    var shadowIntensity: Float = 1f
    var useRendererSilhouette: Boolean = true

    override fun props(): List<Prop> = listOf(
        Prop("selfShadows", PropType.BOOL, selfShadows),
        Prop("castShadows", PropType.BOOL, castShadows),
        Prop("shadowIntensity", PropType.FLOAT, shadowIntensity)
    )
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "selfShadows"->selfShadows=v as Boolean
            "castShadows"->castShadows=v as Boolean
            "shadowIntensity"->shadowIntensity=(v as Number).toFloat()
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("selfShadows", selfShadows).put("castShadows", castShadows)
    override fun fromJson(o: JSONObject) { selfShadows=o.optBoolean("selfShadows",true); castShadows=o.optBoolean("castShadows",true) }
    override fun duplicate(): Component = ShadowCaster2DComponent().also { it.selfShadows=selfShadows; it.castShadows=castShadows }
}

// TilemapCollider2D
class TilemapCollider2DComponent : Component() {
    override val name = "TilemapCollider2D"
    var usedByComposite: Boolean = false
    var usedByEffector: Boolean = false
    var maxTileChange: Int = 1000

    override fun props(): List<Prop> = listOf(Prop("usedByComposite", PropType.BOOL, usedByComposite))
    override fun setProp(n: String, v: Any?) { if(n=="usedByComposite") usedByComposite=v as Boolean }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("usedByComposite", usedByComposite)
    override fun fromJson(o: JSONObject) { usedByComposite=o.optBoolean("usedByComposite",false) }
    override fun duplicate(): Component = TilemapCollider2DComponent().also { it.usedByComposite=usedByComposite }
}

// Tile Component
class TileComponent : Component() {
    override val name = "Tile"
    var tileId: String = "tile"
    var gridX: Int = 0
    var gridY: Int = 0
    var sprite: String = ""

    override fun props(): List<Prop> = listOf(Prop("tileId", PropType.STRING, tileId), Prop("gridX", PropType.INT, gridX), Prop("gridY", PropType.INT, gridY))
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "tileId"->tileId=v as String
            "gridX"->gridX=(v as Number).toInt()
            "gridY"->gridY=(v as Number).toInt()
            "sprite"->sprite=v as String
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("tileId", tileId).put("gridX", gridX).put("gridY", gridY)
    override fun fromJson(o: JSONObject) { tileId=o.optString("tileId","tile"); gridX=o.optInt("gridX",0); gridY=o.optInt("gridY",0) }
    override fun duplicate(): Component = TileComponent().also { it.tileId=tileId; it.gridX=gridX; it.gridY=gridY }
}

// SortingGroup
class SortingGroupComponent : Component() {
    override val name = "SortingGroup"
    var sortingLayer: Int = 0
    var sortingOrder: Int = 0
    var sortAtRoot: Boolean = true

    override fun props(): List<Prop> = listOf(Prop("sortingLayer", PropType.INT, sortingLayer), Prop("sortingOrder", PropType.INT, sortingOrder), Prop("sortAtRoot", PropType.BOOL, sortAtRoot))
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "sortingLayer"->sortingLayer=(v as Number).toInt()
            "sortingOrder"->sortingOrder=(v as Number).toInt()
            "sortAtRoot"->sortAtRoot=v as Boolean
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("sortingLayer", sortingLayer).put("sortingOrder", sortingOrder)
    override fun fromJson(o: JSONObject) { sortingLayer=o.optInt("sortingLayer",0); sortingOrder=o.optInt("sortingOrder",0) }
    override fun duplicate(): Component = SortingGroupComponent().also { it.sortingLayer=sortingLayer; it.sortingOrder=sortingOrder }
}

// Bone2D
class Bone2DComponent : Component() {
    override val name = "Bone2D"
    var boneName: String = "Bone"
    var parentBone: String = ""
    var length: Float = 1f

    override fun props(): List<Prop> = listOf(Prop("boneName", PropType.STRING, boneName), Prop("parentBone", PropType.STRING, parentBone), Prop("length", PropType.FLOAT, length))
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "boneName"->boneName=v as String
            "parentBone"->parentBone=v as String
            "length"->length=(v as Number).toFloat()
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("boneName", boneName).put("parentBone", parentBone)
    override fun fromJson(o: JSONObject) { boneName=o.optString("boneName","Bone"); parentBone=o.optString("parentBone","") }
    override fun duplicate(): Component = Bone2DComponent().also { it.boneName=boneName; it.parentBone=parentBone }
}

// IK2D
class IK2DComponent : Component() {
    override val name = "IK2D"
    var target: String = ""
    var chain: String = "" // comma separated bone names
    var tolerance: Float = 0.01f
    var weight: Float = 1f

    override fun props(): List<Prop> = listOf(Prop("target", PropType.STRING, target), Prop("chain", PropType.STRING, chain), Prop("weight", PropType.FLOAT, weight))
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "target"->target=v as String
            "chain"->chain=v as String
            "weight"->weight=(v as Number).toFloat()
            "tolerance"->tolerance=(v as Number).toFloat()
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("target", target).put("chain", chain)
    override fun fromJson(o: JSONObject) { target=o.optString("target",""); chain=o.optString("chain","") }
    override fun duplicate(): Component = IK2DComponent().also { it.target=target; it.chain=chain }
}

// Sprite Library
class SpriteLibraryComponent : Component() {
    override val name = "SpriteLibrary"
    var library: String = ""

    override fun props(): List<Prop> = listOf(Prop("library", PropType.STRING, library))
    override fun setProp(n: String, v: Any?) { if(n=="library") library=v as String }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("library", library)
    override fun fromJson(o: JSONObject) { library=o.optString("library","") }
    override fun duplicate(): Component = SpriteLibraryComponent().also { it.library=library }
}

class SpriteResolverComponent : Component() {
    override val name = "SpriteResolver"
    var category: String = ""
    var label: String = ""

    override fun props(): List<Prop> = listOf(Prop("category", PropType.STRING, category), Prop("label", PropType.STRING, label))
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "category"->category=v as String
            "label"->label=v as String
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("category", category).put("label", label)
    override fun fromJson(o: JSONObject) { category=o.optString("category",""); label=o.optString("label","") }
    override fun duplicate(): Component = SpriteResolverComponent().also { it.category=category; it.label=label }
}

// Cinemachine 2D
class CinemachineVirtualCamera2DComponent : Component() {
    override val name = "CinemachineVirtualCamera2D"
    var follow: String = ""
    var lookAt: String = ""
    var priority: Int = 10
    var mode: Int = 0 // 0 framing transposer, 1 confiner, 2 pixel perfect
    var deadZoneWidth: Float = 0.1f
    var deadZoneHeight: Float = 0.1f
    var softZoneWidth: Float = 0.8f
    var softZoneHeight: Float = 0.8f
    var xDamping: Float = 1f
    var yDamping: Float = 1f
    var screenX: Float = 0.5f
    var screenY: Float = 0.5f

    override fun props(): List<Prop> = listOf(
        Prop("follow", PropType.STRING, follow),
        Prop("priority", PropType.INT, priority),
        Prop("mode", PropType.INT, mode),
        Prop("deadZoneWidth", PropType.FLOAT, deadZoneWidth),
        Prop("deadZoneHeight", PropType.FLOAT, deadZoneHeight),
        Prop("xDamping", PropType.FLOAT, xDamping),
        Prop("yDamping", PropType.FLOAT, yDamping)
    )
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "follow"->follow=v as String
            "priority"->priority=(v as Number).toInt()
            "mode"->mode=(v as Number).toInt()
            "deadZoneWidth"->deadZoneWidth=(v as Number).toFloat()
            "deadZoneHeight"->deadZoneHeight=(v as Number).toFloat()
            "xDamping"->xDamping=(v as Number).toFloat()
            "yDamping"->yDamping=(v as Number).toFloat()
            "screenX"->screenX=(v as Number).toFloat()
            "screenY"->screenY=(v as Number).toFloat()
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("follow", follow).put("priority", priority).put("mode", mode)
    override fun fromJson(o: JSONObject) { follow=o.optString("follow",""); priority=o.optInt("priority",10); mode=o.optInt("mode",0) }
    override fun duplicate(): Component = CinemachineVirtualCamera2DComponent().also { it.follow=follow; it.priority=priority; it.mode=mode }
}

// ParticleSystem2D Component
class ParticleSystem2DComponent : Component() {
    override val name = "ParticleSystem2D"
    var duration: Float = 5f
    var loop: Boolean = true
    var startLifetime: Float = 1f
    var startSpeed: Float = 5f
    var startSize: Float = 1f
    var maxParticles: Int = 1000
    var emissionRate: Float = 10f

    override fun props(): List<Prop> = listOf(
        Prop("duration", PropType.FLOAT, duration),
        Prop("loop", PropType.BOOL, loop),
        Prop("startLifetime", PropType.FLOAT, startLifetime),
        Prop("startSpeed", PropType.FLOAT, startSpeed),
        Prop("maxParticles", PropType.INT, maxParticles),
        Prop("emissionRate", PropType.FLOAT, emissionRate)
    )
    override fun setProp(n: String, v: Any?) {
        when(n) {
            "duration"->duration=(v as Number).toFloat()
            "loop"->loop=v as Boolean
            "startLifetime"->startLifetime=(v as Number).toFloat()
            "startSpeed"->startSpeed=(v as Number).toFloat()
            "maxParticles"->maxParticles=(v as Number).toInt()
            "emissionRate"->emissionRate=(v as Number).toFloat()
        }
    }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("duration", duration.toDouble()).put("loop", loop)
    override fun fromJson(o: JSONObject) { duration=o.optDouble("duration",5.0).toFloat(); loop=o.optBoolean("loop",true) }
    override fun duplicate(): Component = ParticleSystem2DComponent().also { it.duration=duration; it.loop=loop; it.startLifetime=startLifetime }
}

// Full Control Components
class FullControlComponent : Component() {
    override val name = "FullControl"
    var controlLevel: Int = 10 // 0 basic, 10 full titan control

    override fun props(): List<Prop> = listOf(Prop("controlLevel", PropType.INT, controlLevel))
    override fun setProp(n: String, v: Any?) { if(n=="controlLevel") controlLevel=(v as Number).toInt() }
    override fun toJson(): JSONObject = JSONObject().put("type", name).put("controlLevel", controlLevel)
    override fun fromJson(o: JSONObject) { controlLevel=o.optInt("controlLevel",10) }
    override fun duplicate(): Component = FullControlComponent().also { it.controlLevel=controlLevel }
}

private fun JSONObject.optFloat(key: String, default: Float): Float = if(has(key)) getDouble(key).toFloat() else default
