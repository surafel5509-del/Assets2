package com.sengine.engine.tools

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Professional 2D Tools
 * Sprite Editor, Tile Palette, Tilemap Editor, PSD Importer, Atlas Packer
 */
enum class SpriteEditorMode { Single, Multiple, Polygon, Skinning }
enum class SpriteSlicingType { Automatic, GridByCellSize, GridByCellCount, Isometric }
enum class TilePaletteBrush { Default, Eraser, Fill, Picker, Random }

data class SpriteRect(
    var name: String = "Sprite",
    var x: Int = 0, var y: Int = 0,
    var width: Int = 32, var height: Int = 32,
    var pivotX: Float = 0.5f, var pivotY: Float = 0.5f,
    var borderLeft: Int = 0, var borderRight: Int = 0, var borderTop: Int = 0, var borderBottom: Int = 0
) {
    fun getPivotPixel(): Pair<Int,Int> = Pair((width*pivotX).toInt(), (height*pivotY).toInt())
    fun getBorder(): IntArray = intArrayOf(borderLeft, borderBottom, borderRight, borderTop)
}

data class SpriteEditorData(
    var textureWidth: Int = 1024,
    var textureHeight: Int = 1024,
    var sprites: MutableList<SpriteRect> = mutableListOf(),
    var mode: SpriteEditorMode = SpriteEditorMode.Multiple,
    var pixelsPerUnit: Float = 100f,
    var meshType: Int = 0, // 0 full rect, 1 tight
    var extrude: Int = 1,
    var pivot: Int = 4 // 0 center, 1 top left, etc
) {
    fun sliceAutomatic(minSize: Int = 16): List<SpriteRect> {
        // Simulate automatic slicing - create grid
        sprites.clear()
        val cols = textureWidth/64; val rows = textureHeight/64
        for (r in 0 until rows) for (c in 0 until cols) {
            sprites.add(SpriteRect(name="Sprite_${c}_${r}", x=c*64, y=r*64, width=64, height=64))
        }
        return sprites
    }
    fun sliceGrid(cellWidth: Int, cellHeight: Int, offsetX: Int = 0, offsetY: Int = 0, paddingX: Int = 0, paddingY: Int = 0): List<SpriteRect> {
        sprites.clear()
        var y = offsetY
        var row=0
        while (y+cellHeight <= textureHeight) {
            var x = offsetX; var col=0
            while (x+cellWidth <= textureWidth) {
                sprites.add(SpriteRect(name="Sprite_${col}_${row}", x=x, y=y, width=cellWidth, height=cellHeight))
                x+=cellWidth+paddingX; col++
            }
            y+=cellHeight+paddingY; row++
        }
        return sprites
    }
    fun addSprite(name: String, x: Int, y: Int, w: Int, h: Int): SpriteRect {
        val rect = SpriteRect(name=name, x=x, y=y, width=w, height=h); sprites.add(rect); return rect
    }
}

data class TilePalette(
    var name: String = "Palette",
    var tiles: MutableList<String> = mutableListOf(), // tile ids
    var gridCellSize: Float = 1f,
    var selectedTile: String = "",
    var brush: TilePaletteBrush = TilePaletteBrush.Default,
    var brushSize: Int = 1
) {
    fun addTile(tileId: String) { if (tileId !in tiles) tiles.add(tileId) }
    fun removeTile(tileId: String) { tiles.remove(tileId) }
    fun selectTile(tileId: String) { selectedTile=tileId }
    fun getSelected(): String = selectedTile
}

data class AtlasPackerSettings(
    var maxWidth: Int = 2048,
    var maxHeight: Int = 2048,
    var padding: Int = 4,
    var allowRotation: Boolean = true,
    var powerOfTwo: Boolean = true,
    var forceSquare: Boolean = false
)

class AtlasPacker {
    var settings = AtlasPackerSettings()
    var packedRects = mutableMapOf<String, SpriteRect>()

    fun pack(sprites: List<SpriteRect>): Map<String, SpriteRect> {
        packedRects.clear()
        // Simple shelf packing
        var curX=0; var curY=0; var shelfHeight=0
        for (sprite in sprites.sortedByDescending { it.height }) {
            if (curX+sprite.width > settings.maxWidth) { curX=0; curY+=shelfHeight+settings.padding; shelfHeight=0 }
            if (curY+sprite.height > settings.maxHeight) break // atlas full
            val packed = sprite.copy(x=curX, y=curY)
            packedRects[sprite.name]=packed
            curX+=sprite.width+settings.padding
            shelfHeight=max(shelfHeight, sprite.height)
        }
        return packedRects
    }

    fun getUtilization(): Float {
        var usedArea=0
        for (rect in packedRects.values) usedArea+=rect.width*rect.height
        return usedArea.toFloat()/(settings.maxWidth*settings.maxHeight)
    }
}

class SpriteEditorTools {
    var editorData = SpriteEditorData()
    var palettes = mutableMapOf<String, TilePalette>()
    var atlasPacker = AtlasPacker()
    var currentPalette: String = "Default"
    var isEditing: Boolean = false

    fun createPalette(name: String): TilePalette {
        val palette = TilePalette(name=name); palettes[name]=palette; return palette
    }

    fun sliceTexture(type: SpriteSlicingType, cellWidth: Int = 32, cellHeight: Int = 32): List<SpriteRect> {
        return when (type) {
            SpriteSlicingType.Automatic -> editorData.sliceAutomatic()
            SpriteSlicingType.GridByCellSize -> editorData.sliceGrid(cellWidth, cellHeight)
            SpriteSlicingType.GridByCellCount -> editorData.sliceGrid(editorData.textureWidth/cellWidth, editorData.textureHeight/cellHeight)
            else -> editorData.sliceAutomatic()
        }
    }

    fun packAtlas(sprites: List<SpriteRect>): Map<String, SpriteRect> = atlasPacker.pack(sprites)

    fun getStats(): String = "SpriteEditor Sprites:${editorData.sprites.size} Palettes:${palettes.size} AtlasUtil:${(atlasPacker.getUtilization()*100).toInt()}%"
}

// Tilemap Editor Tools
data class TilemapBrush(
    var name: String = "Brush",
    var size: Int = 1,
    var tiles: MutableList<String> = mutableListOf(),
    var randomize: Boolean = false,
    var flipX: Boolean = false,
    var flipY: Boolean = false,
    var rotation: Float = 0f
)

class TilemapEditorTools {
    var brushes = mutableMapOf<String, TilemapBrush>()
    var currentBrush: String = "Default"
    var tool: TilePaletteBrush = TilePaletteBrush.Default
    var isPainting: Boolean = false
    var floodFillTolerance: Float = 0.1f
    var pickTiles: Boolean = false

    fun createBrush(name: String, size: Int = 1): TilemapBrush {
        val brush = TilemapBrush(name=name, size=size); brushes[name]=brush; return brush
    }

    fun paintTilemap(tilemap: com.sengine.engine.tilemap.TilemapLayer, x: Int, y: Int, tile: com.sengine.engine.tilemap.Tile) {
        val brush = brushes[currentBrush] ?: TilemapBrush()
        for (dy in -brush.size/2..brush.size/2) for (dx in -brush.size/2..brush.size/2) {
            val nx = x+dx; val ny = y+dy
            if (brush.randomize && brush.tiles.isNotEmpty()) {
                val randomTileId = brush.tiles.random()
                // Would need to lookup tile by id
                tilemap.setTile(nx, ny, tile)
            } else {
                tilemap.setTile(nx, ny, tile)
            }
        }
    }

    fun eraseTilemap(tilemap: com.sengine.engine.tilemap.TilemapLayer, x: Int, y: Int) {
        val brush = brushes[currentBrush] ?: TilemapBrush()
        for (dy in -brush.size/2..brush.size/2) for (dx in -brush.size/2..brush.size/2) {
            tilemap.removeTile(x+dx, y+dy)
        }
    }

    fun floodFill(tilemap: com.sengine.engine.tilemap.TilemapLayer, x: Int, y: Int, newTile: com.sengine.engine.tilemap.Tile) {
        tilemap.floodFill(x, y, newTile)
    }

    fun getStats(): String = "TilemapEditor Brushes:${brushes.size} Current:$currentBrush Tool:$tool"
}
