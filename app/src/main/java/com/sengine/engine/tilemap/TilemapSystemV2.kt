package com.sengine.engine.tilemap

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Unity 2D Tilemap System V2
 * Tile, Tilemap, TilemapRenderer, RuleTile, AnimatedTile, Grid, Palette
 */
enum class TilemapOrientation { XY, XZ, YZ }
enum class TileFlags { None, LockColor, LockTransform, InstantiateGameObjectRuntimeOnly }
enum class TilemapColliderType { None, Sprite, Grid }
enum class GridLayout { Rectangle, Isometric, IsometricZAsY, Hexagon }

data class Tile(
    var id: String = "tile_0",
    var sprite: String = "tile.png",
    var color: Int = 0xFFFFFFFF.toInt(),
    var transform: FloatArray = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f), // 4x4 matrix
    var flags: TileFlags = TileFlags.None,
    var colliderType: TilemapColliderType = TilemapColliderType.Sprite,
    var gameObject: String = "",
    var data: MutableMap<String, Any> = mutableMapOf()
)

data class AnimatedTile(
    var id: String = "animated_0",
    var sprites: MutableList<String> = mutableListOf(),
    var animationSpeed: Float = 1f,
    var animationStartTime: Float = 0f,
    var currentFrame: Int = 0,
    var timeAccumulator: Float = 0f
) {
    fun update(dt: Float): String {
        if (sprites.isEmpty()) return ""
        timeAccumulator+=dt*animationSpeed
        if (timeAccumulator >= 1f) { timeAccumulator=0f; currentFrame=(currentFrame+1)%sprites.size }
        return sprites[currentFrame]
    }
    fun getCurrentSprite(): String = if (sprites.isEmpty()) "" else sprites[currentFrame]
}

data class RuleTile(
    var id: String = "rule_0",
    var defaultSprite: String = "default.png",
    var rules: MutableList<Rule> = mutableListOf()
) {
    data class Rule(
        var pattern: IntArray = IntArray(9) { 0 }, // 3x3 pattern: 0 don't care, 1 this, 2 not this
        var sprite: String = "",
        var rotation: Float = 0f,
        var mirrorX: Boolean = false,
        var mirrorY: Boolean = false,
        var output: Int = 1 // 1 this, 2 not this, 3 random
    )
    fun getSpriteForNeighbors(neighbors: IntArray): String {
        for (rule in rules) {
            var match=true
            for (i in neighbors.indices) {
                if (rule.pattern[i]==0) continue
                if (rule.pattern[i]!=neighbors[i]) { match=false; break }
            }
            if (match) return rule.sprite
        }
        return defaultSprite
    }
}

data class Grid(
    var cellSizeX: Float = 1f,
    var cellSizeY: Float = 1f,
    var cellSizeZ: Float = 1f,
    var cellGapX: Float = 0f,
    var cellGapY: Float = 0f,
    var cellGapZ: Float = 0f,
    var layout: GridLayout = GridLayout.Rectangle,
    var cellSwizzle: Int = 0 // 0 xyz, 1 xzy, etc
) {
    fun worldToCell(wx: Float, wy: Float): Pair<Int,Int> {
        val cx = floor(wx/(cellSizeX+cellGapX)).toInt()
        val cy = floor(wy/(cellSizeY+cellGapY)).toInt()
        return Pair(cx, cy)
    }
    fun cellToWorld(cx: Int, cy: Int): Pair<Float,Float> {
        val wx = cx*(cellSizeX+cellGapX)+cellSizeX*0.5f
        val wy = cy*(cellSizeY+cellGapY)+cellSizeY*0.5f
        return Pair(wx, wy)
    }
    fun getBounds(cellCountX: Int, cellCountY: Int): FloatArray {
        return floatArrayOf(0f,0f, cellCountX*(cellSizeX+cellGapX), cellCountY*(cellSizeY+cellGapY))
    }
}

data class TilemapLayer(
    var name: String = "Layer",
    var width: Int = 100,
    var height: Int = 100,
    var tiles: MutableMap<String, Tile> = mutableMapOf(), // key "x_y" -> Tile
    var animatedTiles: MutableMap<String, AnimatedTile> = mutableMapOf(),
    var color: Int = 0xFFFFFFFF.toInt(),
    var isVisible: Boolean = true,
    var sortOrder: Int = 0,
    var opacity: Float = 1f
) {
    fun key(x: Int, y: Int): String = "${x}_${y}"
    fun setTile(x: Int, y: Int, tile: Tile) { tiles[key(x,y)]=tile }
    fun getTile(x: Int, y: Int): Tile? = tiles[key(x,y)]
    fun removeTile(x: Int, y: Int) { tiles.remove(key(x,y)) }
    fun hasTile(x: Int, y: Int): Boolean = tiles.containsKey(key(x,y))
    fun getAllTiles(): List<Pair<Pair<Int,Int>, Tile>> {
        return tiles.map { entry ->
            val parts = entry.key.split("_"); val cx = parts[0].toInt(); val cy = parts[1].toInt()
            Pair(Pair(cx,cy), entry.value)
        }
    }
    fun clear() { tiles.clear(); animatedTiles.clear() }
    fun floodFill(startX: Int, startY: Int, newTile: Tile) {
        val targetTile = getTile(startX, startY) ?: return
        if (targetTile.id == newTile.id) return
        val queue = ArrayDeque<Pair<Int,Int>>()
        queue.add(Pair(startX, startY))
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val (x,y) = queue.removeFirst()
            val k = key(x,y)
            if (k in visited) continue
            visited.add(k)
            val current = getTile(x,y)
            if (current?.id != targetTile.id) continue
            setTile(x,y,newTile)
            queue.add(Pair(x+1,y)); queue.add(Pair(x-1,y)); queue.add(Pair(x,y+1)); queue.add(Pair(x,y-1))
        }
    }
    fun getNeighbors(x: Int, y: Int): IntArray {
        // 3x3 pattern: 0 1 2 / 3 4 5 / 6 7 8, center 4 is self
        val pattern = IntArray(9)
        var idx=0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x+dx; val ny = y+dy
            pattern[idx] = if (hasTile(nx,ny)) 1 else 2
            idx++
        }
        return pattern
    }
}

class TilemapSystemV2 {
    var grid = Grid()
    var layers = mutableMapOf<String, TilemapLayer>()
    var tiles = mutableMapOf<String, Tile>()
    var ruleTiles = mutableMapOf<String, RuleTile>()
    var animatedTiles = mutableMapOf<String, AnimatedTile>()
    var orientation: TilemapOrientation = TilemapOrientation.XY
    var animationFrameRate: Float = 12f
    var nextTileId: Int = 0

    fun createLayer(name: String, width: Int = 100, height: Int = 100): TilemapLayer {
        val layer = TilemapLayer(name=name, width=width, height=height); layers[name]=layer; return layer
    }

    fun createTile(sprite: String, color: Int = 0xFFFFFFFF.toInt()): Tile {
        val id = "tile_${nextTileId++}"
        val tile = Tile(id=id, sprite=sprite, color=color); tiles[id]=tile; return tile
    }

    fun createRuleTile(defaultSprite: String): RuleTile {
        val id = "rule_${nextTileId++}"
        val ruleTile = RuleTile(id=id, defaultSprite=defaultSprite); ruleTiles[id]=ruleTile; return ruleTile
    }

    fun createAnimatedTile(sprites: List<String>, speed: Float = 1f): AnimatedTile {
        val id = "animated_${nextTileId++}"
        val animTile = AnimatedTile(id=id, sprites=sprites.toMutableList(), animationSpeed=speed); animatedTiles[id]=animTile; return animTile
    }

    fun setTile(layerName: String, x: Int, y: Int, tile: Tile) {
        val layer = layers[layerName] ?: createLayer(layerName)
        layer.setTile(x,y,tile)
        // Apply RuleTile if needed
        updateRuleTilesAround(layerName, x, y)
    }

    fun getTile(layerName: String, x: Int, y: Int): Tile? = layers[layerName]?.getTile(x,y)

    fun removeTile(layerName: String, x: Int, y: Int) {
        layers[layerName]?.removeTile(x,y)
        updateRuleTilesAround(layerName, x, y)
    }

    fun updateRuleTilesAround(layerName: String, cx: Int, cy: Int) {
        val layer = layers[layerName] ?: return
        for (dy in -1..1) for (dx in -1..1) {
            val x = cx+dx; val y = cy+dy
            val tile = layer.getTile(x,y) ?: continue
            // Check if it's a rule tile
            val ruleTile = ruleTiles[tile.id] ?: continue
            val neighbors = layer.getNeighbors(x,y)
            val sprite = ruleTile.getSpriteForNeighbors(neighbors)
            tile.sprite = sprite
        }
    }

    fun update(dt: Float) {
        // Update animated tiles
        for (animTile in animatedTiles.values) animTile.update(dt)
        for (layer in layers.values) for (animTile in layer.animatedTiles.values) animTile.update(dt)
    }

    fun worldToCell(wx: Float, wy: Float): Pair<Int,Int> = grid.worldToCell(wx, wy)
    fun cellToWorld(cx: Int, cy: Int): Pair<Float,Float> = grid.cellToWorld(cx, cy)

    fun getCollisionTiles(layerName: String): List<Pair<Int,Int>> {
        val layer = layers[layerName] ?: return emptyList()
        return layer.tiles.filter { it.value.colliderType != TilemapColliderType.None }.map {
            val parts = it.key.split("_"); Pair(parts[0].toInt(), parts[1].toInt())
        }
    }

    fun clearLayer(name: String) { layers[name]?.clear() }
    fun clearAll() { for (layer in layers.values) layer.clear() }

    fun getStats(): String = "Tilemap Grid:${grid.cellSizeX}x${grid.cellSizeY} Layers:${layers.size} Tiles:${tiles.size} RuleTiles:${ruleTiles.size} Animated:${animatedTiles.size}"
}
