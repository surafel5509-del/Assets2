package com.sengine.engine.core

/**
 * S Engine Ultimate 6.1.1 [Titan] - Most Powerful Edition - Titan 2D Components
 * Unity 2D Components: Light2D, ShadowCaster2D, Tilemap, Sorting, Bones, Cinemachine2D, etc
 * Fixed to use new Component API (type + props)
 */

class Light2DComponent : Component() {
    override val type = "Light2D"
    var kind: Int = 0
    var color: Int = 0xFFFFFFFF.toInt()
    var intensity: Float = 1f
    var radius: Float = 5f
    var innerRadius: Float = 0f
    var outerAngle: Float = 360f
    var innerAngle: Float = 0f
    var falloff: Float = 1f
    var shadows: Boolean = false
    var shadowIntensity: Float = 0.5f
    var blendStyle: Int = 0
    var normalMapQuality: Int = 1
    var lightOrder: Int = 0
    var cookie: String = ""

    override fun props(): List<Prop> = listOf(
        Prop.I("Kind", { kind }, { kind = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.F("Intensity", { intensity }, { intensity = it }),
        Prop.F("Radius", { radius }, { radius = it }),
        Prop.F("InnerRadius", { innerRadius }, { innerRadius = it }),
        Prop.F("OuterAngle", { outerAngle }, { outerAngle = it }),
        Prop.B("Shadows", { shadows }, { shadows = it }),
        Prop.I("BlendStyle", { blendStyle }, { blendStyle = it }),
        Prop.I("LightOrder", { lightOrder }, { lightOrder = it })
    )
}

class ShadowCaster2DComponent : Component() {
    override val type = "ShadowCaster2D"
    var selfShadows: Boolean = true
    var castShadows: Boolean = true
    var shadowIntensity: Float = 1f
    var useRendererSilhouette: Boolean = true

    override fun props(): List<Prop> = listOf(
        Prop.B("SelfShadows", { selfShadows }, { selfShadows = it }),
        Prop.B("CastShadows", { castShadows }, { castShadows = it }),
        Prop.F("ShadowIntensity", { shadowIntensity }, { shadowIntensity = it })
    )
}

class TilemapCollider2DComponent : Component() {
    override val type = "TilemapCollider2D"
    var usedByComposite: Boolean = false
    var usedByEffector: Boolean = false
    var maxTileChange: Int = 1000

    override fun props(): List<Prop> = listOf(
        Prop.B("UsedByComposite", { usedByComposite }, { usedByComposite = it })
    )
}

class TileComponent : Component() {
    override val type = "Tile"
    var tileId: String = "tile"
    var gridX: Int = 0
    var gridY: Int = 0
    var sprite: String = ""

    override fun props(): List<Prop> = listOf(
        Prop.S("TileId", { tileId }, { tileId = it }),
        Prop.I("GridX", { gridX }, { gridX = it }),
        Prop.I("GridY", { gridY }, { gridY = it }),
        Prop.S("Sprite", { sprite }, { sprite = it })
    )
}

class SortingGroupComponent : Component() {
    override val type = "SortingGroup"
    var sortingLayer: Int = 0
    var sortingOrder: Int = 0
    var sortAtRoot: Boolean = true

    override fun props(): List<Prop> = listOf(
        Prop.I("SortingLayer", { sortingLayer }, { sortingLayer = it }),
        Prop.I("SortingOrder", { sortingOrder }, { sortingOrder = it }),
        Prop.B("SortAtRoot", { sortAtRoot }, { sortAtRoot = it })
    )
}

class Bone2DComponent : Component() {
    override val type = "Bone2D"
    var boneName: String = "Bone"
    var parentBone: String = ""
    var length: Float = 1f

    override fun props(): List<Prop> = listOf(
        Prop.S("BoneName", { boneName }, { boneName = it }),
        Prop.S("ParentBone", { parentBone }, { parentBone = it }),
        Prop.F("Length", { length }, { length = it })
    )
}

class IK2DComponent : Component() {
    override val type = "IK2D"
    var target: String = ""
    var chain: String = ""
    var tolerance: Float = 0.01f
    var weight: Float = 1f

    override fun props(): List<Prop> = listOf(
        Prop.S("Target", { target }, { target = it }),
        Prop.S("Chain", { chain }, { chain = it }),
        Prop.F("Weight", { weight }, { weight = it })
    )
}

class SpriteLibraryComponent : Component() {
    override val type = "SpriteLibrary"
    var library: String = ""

    override fun props(): List<Prop> = listOf(
        Prop.S("Library", { library }, { library = it })
    )
}

class SpriteResolverComponent : Component() {
    override val type = "SpriteResolver"
    var category: String = ""
    var label: String = ""

    override fun props(): List<Prop> = listOf(
        Prop.S("Category", { category }, { category = it }),
        Prop.S("Label", { label }, { label = it })
    )
}

class CinemachineVirtualCamera2DComponent : Component() {
    override val type = "CinemachineVirtualCamera2D"
    var follow: String = ""
    var lookAt: String = ""
    var priority: Int = 10
    var mode: Int = 0
    var deadZoneWidth: Float = 0.1f
    var deadZoneHeight: Float = 0.1f
    var softZoneWidth: Float = 0.8f
    var softZoneHeight: Float = 0.8f
    var xDamping: Float = 1f
    var yDamping: Float = 1f
    var screenX: Float = 0.5f
    var screenY: Float = 0.5f

    override fun props(): List<Prop> = listOf(
        Prop.S("Follow", { follow }, { follow = it }),
        Prop.I("Priority", { priority }, { priority = it }),
        Prop.I("Mode", { mode }, { mode = it }),
        Prop.F("DeadZoneWidth", { deadZoneWidth }, { deadZoneWidth = it }),
        Prop.F("DeadZoneHeight", { deadZoneHeight }, { deadZoneHeight = it }),
        Prop.F("XDamping", { xDamping }, { xDamping = it }),
        Prop.F("YDamping", { yDamping }, { yDamping = it })
    )
}

class ParticleSystem2DComponent : Component() {
    override val type = "ParticleSystem2D"
    var duration: Float = 5f
    var loop: Boolean = true
    var startLifetime: Float = 1f
    var startSpeed: Float = 5f
    var startSize: Float = 1f
    var maxParticles: Int = 1000
    var emissionRate: Float = 10f

    override fun props(): List<Prop> = listOf(
        Prop.F("Duration", { duration }, { duration = it }),
        Prop.B("Loop", { loop }, { loop = it }),
        Prop.F("StartLifetime", { startLifetime }, { startLifetime = it }),
        Prop.F("StartSpeed", { startSpeed }, { startSpeed = it }),
        Prop.I("MaxParticles", { maxParticles }, { maxParticles = it }),
        Prop.F("EmissionRate", { emissionRate }, { emissionRate = it })
    )
}

class FullControlComponent : Component() {
    override val type = "FullControl"
    var controlLevel: Int = 10

    override fun props(): List<Prop> = listOf(
        Prop.I("ControlLevel", { controlLevel }, { controlLevel = it })
    )
}
