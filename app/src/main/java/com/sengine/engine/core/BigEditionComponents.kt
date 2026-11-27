package com.sengine.engine.core

import com.sengine.engine.math.Affine
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A grid of textured or palette-coloured tiles attached to a GameObject's 2D transform. */
class Tilemap : Component() {
    override val type = "Tilemap"
    var tileset = ""
    var columns = 2
    var rows = 1
    var tileSize = 1f
    var map = "0,1,0,1;1,1,1,0;0,1,0,0"
    var palette = "#FF6D9E4B,#FF8D6E63,#FF4FC3F7,#FFFFC107"
    var color = 0xFFFFFFFF.toInt()
    var collide = false
    var layer = 0
    var mask = 0xFFFF

    data class Tile(
        val col: Int, val row: Int, val id: Int,
        val cx: Float, val cy: Float, val hw: Float, val hh: Float, val color: Int,
    )

    override fun props() = listOf(
        Prop.Asset("Tileset", AssetKind.TEXTURE, { tileset }, { tileset = it }),
        Prop.I("Sheet Columns", { columns }, { columns = it.coerceIn(1, 256) }),
        Prop.I("Sheet Rows", { rows }, { rows = it.coerceIn(1, 256) }),
        Prop.F("Tile Size", { tileSize }, { tileSize = it.coerceAtLeast(0.01f) }, 0.05f),
        Prop.S("Map", { map }, { map = it }, multiline = true),
        Prop.S("Palette", { palette }, { palette = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.B("Solid Collision", { collide }, { collide = it }),
        Prop.I("Layer", { layer }, { layer = it.coerceIn(0, 15) }),
        Prop.I("Mask", { mask }, { mask = it and 0xFFFF }),
    )

    fun getTile(col: Int, row: Int): Int {
        val r = TilemapData.rows(map)
        if (row !in r.indices || col !in r[row].indices) return -1
        return r[row][col]
    }

    fun setTile(col: Int, row: Int, id: Int): Boolean {
        val mutable = TilemapData.rows(map).map { it.toMutableList() }.toMutableList()
        if (row !in mutable.indices || col !in mutable[row].indices) return false
        mutable[row][col] = id
        map = TilemapData.format(mutable)
        return true
    }

    /** Tile id at a world-space point, or -1 when the point is outside the map. */
    fun tileAtWorld(wx: Float, wy: Float): Int {
        val inv = gameObject.computeWorld().inverted() ?: return -1
        val lx = inv.mapX(wx, wy)
        val ly = inv.mapY(wx, wy)
        val col = floor(lx / tileSize).toInt()
        val row = floor(ly / tileSize).toInt()
        return getTile(col, row)
    }

    fun colorFor(id: Int): Int = TilemapData.colorFor(palette, id, color)

    /** World-space tile AABBs. Row 0 is the bottom row of the map. */
    fun tiles(go: GameObject): List<Tile> {
        val out = ArrayList<Tile>()
        val w = go.computeWorld()
        val sx = abs(w.scaleX).coerceAtLeast(1e-6f)
        val sy = abs(w.scaleY).coerceAtLeast(1e-6f)
        val hw = tileSize * sx * 0.5f
        val hh = tileSize * sy * 0.5f
        for ((row, cells) in TilemapData.rows(map).withIndex()) {
            for ((col, id) in cells.withIndex()) {
                if (!TilemapData.isSolid(id)) continue
                val lx = (col + 0.5f) * tileSize
                val ly = (row + 0.5f) * tileSize
                out.add(Tile(col, row, id, w.mapX(lx, ly), w.mapY(lx, ly), hw, hh, colorFor(id)))
            }
        }
        return out
    }

    fun toAssetJson(): JSONObject = JSONObject()
        .put("tileset", tileset).put("columns", columns).put("rows", rows)
        .put("tileSize", tileSize.toDouble()).put("map", map)
        .put("palette", palette).put("color", String.format("#%08X", color))

    companion object {
        fun fromAsset(text: String): Tilemap = Tilemap().also { t ->
            val o = JSONObject(text)
            t.tileset = o.optString("tileset", "")
            t.columns = o.optInt("columns", 2).coerceIn(1, 256)
            t.rows = o.optInt("rows", 1).coerceIn(1, 256)
            t.tileSize = o.optDouble("tileSize", 1.0).toFloat().coerceAtLeast(0.01f)
            t.map = o.optString("map", t.map)
            t.palette = o.optString("palette", t.palette)
            t.color = try { Component.parseColor(o.optString("color", "#FFFFFFFF")) } catch (_: Exception) { t.color }
        }
    }
}

/** Normalised mapping from a UI reference canvas to the current viewport. */
data class UIFrame(val offsetX: Float, val offsetY: Float, val scaleX: Float, val scaleY: Float) {
    fun mapX(x: Float) = offsetX + x * scaleX
    fun mapY(y: Float) = offsetY + y * scaleY
}

/** Screen-space UI root. Children with UIRect and UI draw components use its scaling. */
class UICanvas : Component() {
    override val type = "UICanvas"
    var scaleMode = 0 // 0 Stretch, 1 Aspect Fit, 2 Pixel Perfect
    var referenceWidth = 1920f
    var referenceHeight = 1080f
    var opacity = 1f
    var interactive = true
    var sortOrder = 0

    override fun props() = listOf(
        Prop.Choice("Scale Mode", listOf("Stretch", "Aspect Fit", "Pixel Perfect"), { scaleMode }, { scaleMode = it }),
        Prop.F("Reference Width", { referenceWidth }, { referenceWidth = it.coerceAtLeast(1f) }, 10f),
        Prop.F("Reference Height", { referenceHeight }, { referenceHeight = it.coerceAtLeast(1f) }, 10f),
        Prop.F("Opacity", { opacity }, { opacity = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.B("Interactive", { interactive }, { interactive = it }),
        Prop.I("Sort Order", { sortOrder }, { sortOrder = it }),
    )

    fun frame(viewWidthPx: Int, viewHeightPx: Int): UIFrame {
        val w = viewWidthPx.coerceAtLeast(1).toFloat()
        val h = viewHeightPx.coerceAtLeast(1).toFloat()
        val rw = referenceWidth.coerceAtLeast(1f)
        val rh = referenceHeight.coerceAtLeast(1f)
        return when (scaleMode) {
            1 -> {
                val scale = min(w / rw, h / rh)
                val sx = (rw * scale / w).coerceIn(0.0001f, 1f)
                val sy = (rh * scale / h).coerceIn(0.0001f, 1f)
                UIFrame((1f - sx) * 0.5f, (1f - sy) * 0.5f, sx, sy)
            }
            2 -> UIFrame(0f, 0f, (rw / w).coerceAtLeast(0.0001f), (rh / h).coerceAtLeast(0.0001f))
            else -> UIFrame(0f, 0f, 1f, 1f)
        }
    }

    companion object {
        val SCALE_MODES = listOf("Stretch", "Aspect Fit", "Pixel Perfect")
    }
}

/** Normalised rectangle: anchor and size are fractions of the canvas; pivot is 0..1. */
class UIRect : Component() {
    override val type = "UIRect"
    var anchorX = 0.5f
    var anchorY = 0.5f
    var width = 0.25f
    var height = 0.09f
    var pivotX = 0.5f
    var pivotY = 0.5f

    data class Layout(val left: Float, val bottom: Float, val width: Float, val height: Float) {
        fun contains(x: Float, y: Float, pad: Float = 0f): Boolean =
            x >= left - pad && x <= left + width + pad && y >= bottom - pad && y <= bottom + height + pad
    }

    override fun props() = listOf(
        Prop.F("Anchor X", { anchorX }, { anchorX = it.coerceIn(-1f, 2f) }, 0.01f),
        Prop.F("Anchor Y", { anchorY }, { anchorY = it.coerceIn(-1f, 2f) }, 0.01f),
        Prop.F("Width", { width }, { width = it.coerceIn(0f, 4f) }, 0.01f),
        Prop.F("Height", { height }, { height = it.coerceIn(0f, 4f) }, 0.01f),
        Prop.F("Pivot X", { pivotX }, { pivotX = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Pivot Y", { pivotY }, { pivotY = it.coerceIn(0f, 1f) }, 0.05f),
    )

    fun layout(frame: UIFrame): Layout {
        val w = width * frame.scaleX
        val h = height * frame.scaleY
        return Layout(frame.mapX(anchorX) - w * pivotX, frame.mapY(anchorY) - h * pivotY, w, h)
    }
}

class UIImage : Component() {
    override val type = "UIImage"
    var color = 0xFFFFFFFF.toInt()
    var texture = ""
    var shape = 0
    var flipX = false
    var flipY = false
    var shader = ""
    var shaderParam = 1f
    var atlas = ""
    var frame = ""

    override fun props() = listOf(
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.Choice("Shape", listOf("Square", "Circle", "Triangle", "Ring"), { shape }, { shape = it }),
        Prop.B("Flip X", { flipX }, { flipX = it }),
        Prop.B("Flip Y", { flipY }, { flipY = it }),
        Prop.Asset("Shader", AssetKind.SHADER, { shader }, { shader = it }),
        Prop.F("Shader Param", { shaderParam }, { shaderParam = it }),
        Prop.Asset("Atlas", AssetKind.ATLAS, { atlas }, { atlas = it }),
        Prop.S("Atlas Frame", { frame }, { frame = it }),
    )
}

class UIText : Component() {
    override val type = "UIText"
    var text = "Big Edition"
    var size = 0.045f
    var color = 0xFFFFFFFF.toInt()
    var align = 1
    var bold = true
    var font = ""

    override fun props() = listOf(
        Prop.S("Text", { text }, { text = it }, multiline = true),
        Prop.F("Size", { size }, { size = it.coerceIn(0.005f, 0.5f) }, 0.005f),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Choice("Align", listOf("Left", "Center", "Right"), { align }, { align = it }),
        Prop.B("Bold", { bold }, { bold = it }),
        Prop.Asset("Font", AssetKind.FONT, { font }, { font = it }),
    )
}

class UIButton : Component() {
    override val type = "UIButton"
    var label = "Press"
    var color = 0xFF37474F.toInt()
    var pressedColor = 0xFF4FC3F7.toInt()
    var textColor = 0xFFFFFFFF.toInt()
    var texture = ""
    var action = "onClick"

    // runtime
    @Volatile var clicked = false
    @Volatile var pressed = false
    var clickCount = 0
    var pressedTime = 0f

    override fun props() = listOf(
        Prop.S("Label", { label }, { label = it }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Color("Pressed Color", { pressedColor }, { pressedColor = it }),
        Prop.Color("Text Color", { textColor }, { textColor = it }),
        Prop.Asset("Texture", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.S("Action", { action }, { action = it }),
    )

    override fun resetRuntime() {
        clicked = false; pressed = false; clickCount = 0; pressedTime = 0f
    }
}

class UIProgressBar : Component() {
    override val type = "UIProgressBar"
    var value = 0.5f
    var min = 0f
    var max = 1f
    var fillColor = 0xFF4FC3F7.toInt()
    var backgroundColor = 0xFF263238.toInt()
    var direction = 0 // 0 right, 1 left, 2 up, 3 down
    var showBackground = true

    override fun props() = listOf(
        Prop.F("Value", { value }, { value = it }),
        Prop.F("Min", { min }, { min = it }),
        Prop.F("Max", { max }, { max = it }),
        Prop.Color("Fill Color", { fillColor }, { fillColor = it }),
        Prop.Color("Background Color", { backgroundColor }, { backgroundColor = it }),
        Prop.Choice("Direction", listOf("Right", "Left", "Up", "Down"), { direction }, { direction = it }),
        Prop.B("Show Background", { showBackground }, { showBackground = it }),
    )

    val normalized: Float get() {
        val span = max - min
        return if (abs(span) < 1e-6f) 0f else ((value - min) / span).coerceIn(0f, 1f)
    }

    companion object {
        val DIRECTIONS = listOf("Right", "Left", "Up", "Down")
    }
}

/** Surface overrides consumed by PhysicsWorld when resolving this object's contacts. */
class PhysicsMaterial2D : Component() {
    override val type = "PhysicsMaterial2D"
    var friction = 0.6f
    var bounciness = 0.2f
    var density = 1f

    override fun props() = listOf(
        Prop.F("Friction", { friction }, { friction = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Bounciness", { bounciness }, { bounciness = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Density", { density }, { density = it.coerceIn(0.01f, 100f) }, 0.1f),
    )
}

/** Distance or spring constraint between two 2D rigidbodies, with anchor offsets. */
class Joint2D : Component() {
    override val type = "Joint2D"
    var mode = 0 // 0 Distance, 1 Spring
    var target = ""
    var length = 2f
    var stiffness = 20f
    var damping = 2f
    var maxStretch = 0f // 0 = unlimited
    var anchorX = 0f
    var anchorY = 0f
    var targetAnchorX = 0f
    var targetAnchorY = 0f

    // runtime
    @Volatile var stressed = false
    @Volatile var broken = false

    override fun props() = listOf(
        Prop.Choice("Constraint", listOf("Distance", "Spring"), { mode }, { mode = it }),
        Prop.S("Target", { target }, { target = it }),
        Prop.F("Length", { length }, { length = it.coerceAtLeast(0.01f) }, 0.1f),
        Prop.F("Stiffness", { stiffness }, { stiffness = it.coerceAtLeast(0f) }, 1f),
        Prop.F("Damping", { damping }, { damping = it.coerceAtLeast(0f) }, 0.5f),
        Prop.F("Max Stretch", { maxStretch }, { maxStretch = it.coerceAtLeast(0f) }, 0.1f),
        Prop.F("Anchor X", { anchorX }, { anchorX = it }),
        Prop.F("Anchor Y", { anchorY }, { anchorY = it }),
        Prop.F("Target Anchor X", { targetAnchorX }, { targetAnchorX = it }),
        Prop.F("Target Anchor Y", { targetAnchorY }, { targetAnchorY = it }),
    )

    override fun resetRuntime() { stressed = false; broken = false }

    companion object {
        val MODES = listOf("Distance", "Spring")
    }
}
