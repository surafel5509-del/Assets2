package com.sengine.engine.tools

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Level Editor Tools
 * Spline Tool, Foliage Tool, Vertex Painting, Terrain Sculpting Advanced, Mesh Painting
 */
enum class EditorTool { Select, Move, Rotate, Scale, Spline, Foliage, TerrainSculpt, TerrainPaint, VertexPaint, MeshPaint, Measure }
enum class TerrainBrushType { Raise, Lower, Smooth, Flatten, Noise, Erode, Terrace }
enum class PaintMode { Paint, Erase, Smooth, Fill }

data class EditorHistory(
    var actions: MutableList<String> = mutableListOf(),
    var index: Int = -1,
    var maxHistory: Int = 100
) {
    fun push(action: String) {
        if (index < actions.size-1) actions = actions.subList(0, index+1).toMutableList()
        actions.add(action); index++
        if (actions.size > maxHistory) { actions.removeAt(0); index-- }
    }
    fun undo(): String? {
        if (index < 0) return null
        val action = actions[index]; index--; return action
    }
    fun redo(): String? {
        if (index >= actions.size-1) return null
        index++; return actions[index]
    }
    fun canUndo(): Boolean = index >= 0
    fun canRedo(): Boolean = index < actions.size-1
}

data class TerrainBrush(
    var type: TerrainBrushType = TerrainBrushType.Raise,
    var size: Float = 5f,
    var strength: Float = 0.5f,
    var falloff: Float = 0.5f,
    var hardness: Float = 0.8f,
    var spacing: Float = 0.1f
) {
    fun getFalloff(distance: Float): Float {
        if (distance > size) return 0f
        val normalized = distance/size
        return (1f-normalized).pow(1f/falloff) * strength
    }
    fun apply(height: Float, distance: Float, targetHeight: Float = 0f): Float {
        val falloff = getFalloff(distance)
        return when (type) {
            TerrainBrushType.Raise -> height+falloff
            TerrainBrushType.Lower -> height-falloff
            TerrainBrushType.Flatten -> height+(targetHeight-height)*falloff
            TerrainBrushType.Smooth -> height // smoothing handled separately
            TerrainBrushType.Noise -> height+(Math.random().toFloat()-0.5f)*falloff
            TerrainBrushType.Terrace -> {
                val terraces = 5
                val stepped = floor(height*terraces)/terraces
                stepped+(height-stepped)* (1f-falloff)
            }
            else -> height
        }
    }
}

data class VertexPaintData(
    var r: Float = 1f, var g: Float = 1f, var b: Float = 1f, var a: Float = 1f,
    var brushSize: Float = 1f,
    var brushStrength: Float = 0.5f,
    var color: Int = 0xFFFFFFFF.toInt()
)

class LevelEditorTools {
    var currentTool: EditorTool = EditorTool.Select
    var history = EditorHistory()
    var terrainBrush = TerrainBrush()
    var vertexPaint = VertexPaintData()
    var paintMode: PaintMode = PaintMode.Paint
    var isPainting: Boolean = false
    var lastPaintX: Float = 0f; var lastPaintY: Float = 0f; var lastPaintZ: Float = 0f
    var snapEnabled: Boolean = true
    var snapSize: Float = 1f
    var gridEnabled: Boolean = true
    var gridSize: Float = 1f

    // Spline Tool
    var splinePoints = mutableListOf<FloatArray>()
    var isCreatingSpline: Boolean = false

    fun startSpline(x: Float, y: Float, z: Float) {
        isCreatingSpline=true; splinePoints.clear(); splinePoints.add(floatArrayOf(x,y,z))
        history.push("Start Spline at $x,$y,$z")
    }
    fun addSplinePoint(x: Float, y: Float, z: Float) {
        if (!isCreatingSpline) return
        splinePoints.add(floatArrayOf(x,y,z))
        history.push("Add Spline Point $x,$y,$z")
    }
    fun finishSpline(): List<FloatArray> {
        isCreatingSpline=false
        val points = splinePoints.toList()
        history.push("Finish Spline ${points.size} points")
        return points
    }

    // Terrain Sculpting
    fun sculptTerrain(terrainHeights: Array<FloatArray>, centerX: Float, centerZ: Float, brush: TerrainBrush = terrainBrush): Array<FloatArray> {
        val result = terrainHeights.map { it.clone() }.toTypedArray()
        val cellSize = 1f
        for (y in terrainHeights.indices) for (x in terrainHeights[y].indices) {
            val wx = x*cellSize; val wz = y*cellSize
            val dx = wx-centerX; val dz = wz-centerZ
            val dist = sqrt(dx*dx+dz*dz)
            if (dist <= brush.size) {
                result[y][x] = brush.apply(terrainHeights[y][x], dist)
            }
        }
        history.push("Sculpt Terrain at $centerX,$centerZ brush ${brush.type}")
        return result
    }

    fun smoothTerrain(terrainHeights: Array<FloatArray>, centerX: Float, centerZ: Float, radius: Float): Array<FloatArray> {
        val result = terrainHeights.map { it.clone() }.toTypedArray()
        val cellSize = 1f
        for (y in terrainHeights.indices) for (x in terrainHeights[y].indices) {
            val wx = x*cellSize; val wz = y*cellSize
            val dx = wx-centerX; val dz = wz-centerZ
            if (sqrt(dx*dx+dz*dz) <= radius) {
                var sum=0f; var count=0
                for (dy in -1..1) for (dx2 in -1..1) {
                    val nx = x+dx2; val ny = y+dy
                    if (ny in terrainHeights.indices && nx in terrainHeights[ny].indices) { sum+=terrainHeights[ny][nx]; count++ }
                }
                if (count > 0) result[y][x] = result[y][x]*(1f-terrainBrush.strength) + (sum/count)*terrainBrush.strength
            }
        }
        history.push("Smooth Terrain at $centerX,$centerZ")
        return result
    }

    // Vertex Painting
    fun paintVertices(vertices: MutableList<FloatArray>, colors: MutableList<Int>, centerX: Float, centerY: Float, centerZ: Float, color: Int): Int {
        var painted = 0
        for (i in vertices.indices) {
            val v = vertices[i]
            val dx = v[0]-centerX; val dy = v[1]-centerY; val dz = v[2]-centerZ
            val dist = sqrt(dx*dx+dy*dy+dz*dz)
            if (dist <= vertexPaint.brushSize) {
                val falloff = 1f - (dist/vertexPaint.brushSize)
                when (paintMode) {
                    PaintMode.Paint -> {
                        // Blend colors
                        val existing = colors.getOrElse(i){0xFFFFFFFF.toInt()}
                        val er = (existing shr 16) and 0xFF; val eg = (existing shr 8) and 0xFF; val eb = existing and 0xFF
                        val nr = (color shr 16) and 0xFF; val ng = (color shr 8) and 0xFF; val nb = color and 0xFF
                        val r = (er*(1f-falloff*vertexPaint.brushStrength) + nr*falloff*vertexPaint.brushStrength).toInt()
                        val g = (eg*(1f-falloff*vertexPaint.brushStrength) + ng*falloff*vertexPaint.brushStrength).toInt()
                        val b = (eb*(1f-falloff*vertexPaint.brushStrength) + nb*falloff*vertexPaint.brushStrength).toInt()
                        colors[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        painted++
                    }
                    PaintMode.Erase -> { colors[i]=0xFFFFFFFF.toInt(); painted++ }
                    else -> {}
                }
            }
        }
        if (painted > 0) history.push("Paint $painted vertices at $centerX,$centerY,$centerZ")
        return painted
    }

    // Snapping
    fun snap(value: Float): Float = if (snapEnabled) floor(value/snapSize+0.5f)*snapSize else value
    fun snapPosition(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(snap(x), snap(y), snap(z))

    fun undo(): String? = history.undo()
    fun redo(): String? = history.redo()

    fun getStats(): String = "Tool:$currentTool History:${history.actions.size} Spline:${splinePoints.size} Snap:$snapEnabled Grid:$gridEnabled"
}
