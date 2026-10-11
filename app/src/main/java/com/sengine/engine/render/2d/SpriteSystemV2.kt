package com.sengine.engine.render.`2d`

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Unity 2D Sprite System V2
 * Sprite, Sprite Atlas, Sprite Sheet, Sprite Mask, Sprite Shape, 9-Slice, Sorting
 */
enum class SpriteMeshType { FullRect, Tight }
enum class SpriteDrawMode { Simple, Sliced, Tiled }
enum class SpriteMaskInteraction { None, VisibleInsideMask, VisibleOutsideMask }
enum class SpriteSortPoint { Center, Pivot }

data class SpriteV2(
    var name: String = "Sprite",
    var texture: String = "sprite.png",
    var x: Float = 0f, var y: Float = 0f, var width: Float = 1f, var height: Float = 1f, // UV rect
    var pivotX: Float = 0.5f, var pivotY: Float = 0.5f,
    var pixelsPerUnit: Float = 100f,
    var meshType: SpriteMeshType = SpriteMeshType.Tight,
    var drawMode: SpriteDrawMode = SpriteDrawMode.Simple,
    var borderLeft: Float = 0f, var borderRight: Float = 0f, var borderTop: Float = 0f, var borderBottom: Float = 0f, // 9-slice
    var physicsShape: MutableList<FloatArray> = mutableListOf(),
    var bones: MutableList<String> = mutableListOf(),
    var isPacked: Boolean = false,
    var atlasName: String = ""
) {
    fun getRect(): FloatArray = floatArrayOf(x, y, width, height)
    fun getPivot(): FloatArray = floatArrayOf(pivotX, pivotY)
    fun getBorder(): FloatArray = floatArrayOf(borderLeft, borderBottom, borderRight, borderTop)
    fun getPhysicsShapeCount(): Int = physicsShape.size
    fun addPhysicsPoint(px: Float, py: Float) { physicsShape.add(floatArrayOf(px, py)) }
}

data class SpriteAtlas(
    var name: String = "Atlas",
    var width: Int = 2048,
    var height: Int = 2048,
    var padding: Int = 4,
    var sprites: MutableMap<String, SpriteV2> = mutableMapOf(),
    var allowRotation: Boolean = true,
    var tightPacking: Boolean = true,
    var alphaDilation: Boolean = true,
    var isVariant: Boolean = false,
    var masterAtlas: String = "",
    var variantScale: Float = 0.5f
) {
    fun addSprite(sprite: SpriteV2) { sprites[sprite.name]=sprite; sprite.atlasName=name; sprite.isPacked=true }
    fun getSprite(name: String): SpriteV2? = sprites[name]
    fun pack(): Boolean {
        // Simple packing - grid layout
        var curX=0; var curY=0; var rowHeight=0
        for (sprite in sprites.values) {
            if (curX + sprite.width*100 > width) { curX=0; curY+=rowHeight+padding; rowHeight=0 }
            sprite.x = curX.toFloat()/width; sprite.y = curY.toFloat()/height
            curX+=(sprite.width*100+padding).toInt(); rowHeight=max(rowHeight, (sprite.height*100).toInt())
        }
        return curY < height
    }
    fun getUtilization(): Float {
        var used=0f
        for (s in sprites.values) used+=s.width*s.height
        return used/(width*height/10000f)
    }
}

data class SpriteSheet(
    var texture: String = "sheet.png",
    var spriteWidth: Int = 32,
    var spriteHeight: Int = 32,
    var columns: Int = 4,
    var rows: Int = 4,
    var padding: Int = 0,
    var spacing: Int = 0,
    var sprites: MutableList<SpriteV2> = mutableListOf()
) {
    fun slice(): List<SpriteV2> {
        sprites.clear()
        for (row in 0 until rows) for (col in 0 until columns) {
            val x = col*(spriteWidth+spacing)+padding
            val y = row*(spriteHeight+spacing)+padding
            val name = "${texture}_${col}_${row}"
            val sprite = SpriteV2(name=name, texture=texture, x=x.toFloat(), y=y.toFloat(), width=spriteWidth.toFloat(), height=spriteHeight.toFloat())
            sprites.add(sprite)
        }
        return sprites
    }
    fun getSprite(col: Int, row: Int): SpriteV2? {
        val idx = row*columns+col; return if (idx in sprites.indices) sprites[idx] else null
    }
}

data class SpriteMask(
    var id: String = "mask_0",
    var x: Float = 0f, var y: Float = 0f,
    var width: Float = 1f, var height: Float = 1f,
    var sprite: String = "",
    var isCustomRangeActive: Boolean = false,
    var frontSortingLayer: Int = 0, var frontSortingOrder: Int = 0,
    var backSortingLayer: Int = 0, var backSortingOrder: Int = 0,
    var alphaCutoff: Float = 0.5f,
    var isVisible: Boolean = true
) {
    fun containsPoint(px: Float, py: Float): Boolean {
        return px >= x-width*0.5f && px <= x+width*0.5f && py >= y-height*0.5f && py <= y+height*0.5f
    }
}

data class SpriteShape(
    var id: String = "shape_0",
    var points: MutableList<FloatArray> = mutableListOf(), // x,y per point
    var texture: String = "",
    var cornerSprites: MutableList<String> = mutableListOf(),
    var angleRange: Float = 30f,
    var isOpenEnded: Boolean = false,
    var useSpriteBorders: Boolean = true
) {
    fun addPoint(x: Float, y: Float) { points.add(floatArrayOf(x,y)) }
    fun getLength(): Float {
        var len=0f
        for (i in 1 until points.size) {
            val dx = points[i][0]-points[i-1][0]; val dy = points[i][1]-points[i-1][1]
            len+=sqrt(dx*dx+dy*dy)
        }
        return len
    }
    fun getPointAt(t: Float): FloatArray {
        if (points.isEmpty()) return floatArrayOf(0f,0f)
        if (points.size==1) return points[0]
        val totalLen = getLength()
        var targetDist = t*totalLen
        var curDist=0f
        for (i in 1 until points.size) {
            val dx = points[i][0]-points[i-1][0]; val dy = points[i][1]-points[i-1][1]
            val segLen = sqrt(dx*dx+dy*dy)
            if (curDist+segLen >= targetDist) {
                val frac = (targetDist-curDist)/segLen
                return floatArrayOf(points[i-1][0]+dx*frac, points[i-1][1]+dy*frac)
            }
            curDist+=segLen
        }
        return points.last()
    }
}

class SpriteSystemV2 {
    var sprites = mutableMapOf<String, SpriteV2>()
    var atlases = mutableMapOf<String, SpriteAtlas>()
    var sheets = mutableMapOf<String, SpriteSheet>()
    var masks = mutableMapOf<String, SpriteMask>()
    var shapes = mutableMapOf<String, SpriteShape>()
    var pixelsPerUnit: Float = 100f
    var nextMaskId: Int = 0
    var nextShapeId: Int = 0

    fun createSprite(name: String, texture: String, width: Float = 1f, height: Float = 1f): SpriteV2 {
        val sprite = SpriteV2(name=name, texture=texture, width=width, height=height, pixelsPerUnit=pixelsPerUnit)
        sprites[name]=sprite; return sprite
    }

    fun createAtlas(name: String, width: Int = 2048, height: Int = 2048): SpriteAtlas {
        val atlas = SpriteAtlas(name=name, width=width, height=height); atlases[name]=atlas; return atlas
    }

    fun createSheet(texture: String, spriteWidth: Int, spriteHeight: Int, columns: Int, rows: Int): SpriteSheet {
        val sheet = SpriteSheet(texture=texture, spriteWidth=spriteWidth, spriteHeight=spriteHeight, columns=columns, rows=rows)
        sheet.slice(); sheets[texture]=sheet
        for (sprite in sheet.sprites) sprites[sprite.name]=sprite
        return sheet
    }

    fun createMask(x: Float, y: Float, width: Float, height: Float, sprite: String = ""): SpriteMask {
        val id = "mask_${nextMaskId++}"
        val mask = SpriteMask(id=id, x=x, y=y, width=width, height=height, sprite=sprite)
        masks[id]=mask; return mask
    }

    fun createShape(points: List<FloatArray>, texture: String = ""): SpriteShape {
        val id = "shape_${nextShapeId++}"
        val shape = SpriteShape(id=id, points=points.toMutableList(), texture=texture)
        shapes[id]=shape; return shape
    }

    fun getSprite(name: String): SpriteV2? = sprites[name] ?: atlases.values.firstNotNullOfOrNull { it.getSprite(name) }

    fun isVisibleInsideMask(x: Float, y: Float, maskInteraction: SpriteMaskInteraction): Boolean {
        if (maskInteraction == SpriteMaskInteraction.None) return true
        val inside = masks.values.any { it.isVisible && it.containsPoint(x,y) }
        return when (maskInteraction) {
            SpriteMaskInteraction.VisibleInsideMask -> inside
            SpriteMaskInteraction.VisibleOutsideMask -> !inside
            else -> true
        }
    }

    fun packAllAtlases() { for (atlas in atlases.values) atlas.pack() }

    fun getStats(): String = "Sprites:${sprites.size} Atlases:${atlases.size} Sheets:${sheets.size} Masks:${masks.size} Shapes:${shapes.size} PPU:$pixelsPerUnit"
}
