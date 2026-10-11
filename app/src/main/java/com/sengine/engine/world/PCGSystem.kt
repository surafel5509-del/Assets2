package com.sengine.engine.world

import kotlin.math.*
import kotlin.random.Random

/**
 * S Engine Ultimate 6.1 [Quantum] - Procedural Content Generation (PCG) System
 * UE5 PCG-like: Graph, Nodes, Rules, Generation
 */
enum class PCGNodeType {
    Input, Output, Point, Spline, Density, Filter, Transform, Spawn, Merge, Split,
    Noise, Random, Math, Branch, Loop, Subgraph, Biome, Scatter, Cluster, Path, Surface
}

data class PCGPoint(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var scale: Float = 1f,
    var rotX: Float = 0f, var rotY: Float = 0f, var rotZ: Float = 0f,
    var density: Float = 1f,
    var color: Int = 0xFFFFFFFF.toInt(),
    var seed: Int = 0,
    var attributes: MutableMap<String, Float> = mutableMapOf()
)

data class PCGNode(
    var id: String = "node",
    var type: PCGNodeType = PCGNodeType.Point,
    var x: Float = 0f, var y: Float = 0f, // graph position
    var properties: MutableMap<String, Any> = mutableMapOf(),
    var inputs: MutableList<String> = mutableListOf(), // connected node ids
    var outputs: MutableList<String> = mutableListOf()
)

data class PCGGraph(
    var name: String = "PCG_Graph",
    var nodes: MutableMap<String, PCGNode> = mutableMapOf(),
    var seed: Int = 12345,
    var boundsMin: FloatArray = floatArrayOf(-100f, 0f, -100f),
    var boundsMax: FloatArray = floatArrayOf(100f, 50f, 100f)
) {
    fun addNode(id: String, type: PCGNodeType, props: Map<String, Any> = emptyMap()): PCGNode {
        val node = PCGNode(id=id, type=type, properties=props.toMutableMap()); nodes[id]=node; return node
    }
    fun connect(fromId: String, toId: String) {
        nodes[fromId]?.outputs?.add(toId); nodes[toId]?.inputs?.add(fromId)
    }
    fun getInputNodes(): List<PCGNode> = nodes.values.filter { it.type == PCGNodeType.Input }
    fun getOutputNodes(): List<PCGNode> = nodes.values.filter { it.type == PCGNodeType.Output }
    fun topologicalSort(): List<PCGNode> {
        val visited = mutableSetOf<String>(); val sorted = mutableListOf<PCGNode>()
        fun visit(id: String) {
            if (id in visited) return; visited.add(id)
            nodes[id]?.inputs?.forEach { visit(it) }
            nodes[id]?.let { sorted.add(it) }
        }
        nodes.keys.forEach { visit(it) }
        return sorted
    }
}

class PCGSystem {
    var graphs = mutableMapOf<String, PCGGraph>()
    var generatedPoints = mutableListOf<PCGPoint>()
    var seed: Int = 12345
    var random = Random(seed)

    fun createGraph(name: String): PCGGraph {
        val g = PCGGraph(name=name, seed=seed); graphs[name]=g; return g
    }

    fun createDefaultGraphs() {
        // Forest PCG
        val forest = createGraph("Forest_PCG")
        forest.addNode("input", PCGNodeType.Input, mapOf("bounds" to 200f))
        forest.addNode("density", PCGNodeType.Density, mapOf("density" to 0.3f, "radius" to 5f))
        forest.addNode("noise", PCGNodeType.Noise, mapOf("scale" to 0.05f, "octaves" to 3))
        forest.addNode("filter", PCGNodeType.Filter, mapOf("minSlope" to 0f, "maxSlope" to 0.6f, "minHeight" to 0f))
        forest.addNode("scatter", PCGNodeType.Scatter, mapOf("count" to 500, "prototype" to "Tree"))
        forest.addNode("output", PCGNodeType.Output)
        forest.connect("input","density"); forest.connect("density","noise"); forest.connect("noise","filter"); forest.connect("filter","scatter"); forest.connect("scatter","output")

        // City PCG
        val city = createGraph("City_PCG")
        city.addNode("input", PCGNodeType.Input)
        city.addNode("path", PCGNodeType.Path, mapOf("roadWidth" to 8f, "blockSize" to 50f))
        city.addNode("split", PCGNodeType.Split, mapOf("lots" to 10))
        city.addNode("spawn", PCGNodeType.Spawn, mapOf("buildingTypes" to 5, "heightVariation" to 0.5f))
        city.addNode("output", PCGNodeType.Output)
        city.connect("input","path"); city.connect("path","split"); city.connect("split","spawn"); city.connect("spawn","output")

        // Biome PCG
        val biome = createGraph("Biome_PCG")
        biome.addNode("input", PCGNodeType.Input)
        biome.addNode("biome", PCGNodeType.Biome, mapOf("temperature" to 0.5f, "humidity" to 0.5f, "height" to 0.5f))
        biome.addNode("scatter", PCGNodeType.Scatter, mapOf("grass" to 1000, "rocks" to 100, "trees" to 50))
        biome.addNode("output", PCGNodeType.Output)
        biome.connect("input","biome"); biome.connect("biome","scatter"); biome.connect("scatter","output")
    }

    fun generate(graphName: String, seed: Int = this.seed, boundsMin: FloatArray = floatArrayOf(-100f,0f,-100f), boundsMax: FloatArray = floatArrayOf(100f,50f,100f)): List<PCGPoint> {
        val graph = graphs[graphName] ?: return emptyList()
        random = Random(seed)
        val points = mutableListOf<PCGPoint>()
        val sorted = graph.topologicalSort()

        var currentPoints = mutableListOf<PCGPoint>()
        // Input - generate initial points grid
        currentPoints.addAll(generateGrid(boundsMin, boundsMax, 10f))

        for (node in sorted) {
            currentPoints = when (node.type) {
                PCGNodeType.Input -> currentPoints
                PCGNodeType.Density -> applyDensity(currentPoints, node.properties["density"] as? Float ?: 0.5f)
                PCGNodeType.Noise -> applyNoise(currentPoints, node.properties["scale"] as? Float ?: 0.05f)
                PCGNodeType.Filter -> applyFilter(currentPoints, node.properties)
                PCGNodeType.Scatter -> applyScatter(currentPoints, node.properties)
                PCGNodeType.Transform -> applyTransform(currentPoints, node.properties)
                PCGNodeType.Spawn -> applySpawn(currentPoints, node.properties)
                PCGNodeType.Merge -> currentPoints // merge handled via multiple inputs
                PCGNodeType.Split -> applySplit(currentPoints, node.properties)
                PCGNodeType.Cluster -> applyCluster(currentPoints, node.properties)
                PCGNodeType.Biome -> applyBiome(currentPoints, node.properties)
                PCGNodeType.Path -> applyPath(currentPoints, node.properties)
                else -> currentPoints
            }
        }
        generatedPoints = currentPoints
        return currentPoints
    }

    private fun generateGrid(min: FloatArray, max: FloatArray, step: Float): MutableList<PCGPoint> {
        val points = mutableListOf<PCGPoint>()
        var x = min[0]; while (x <= max[0]) {
            var z = min[2]; while (z <= max[2]) {
                points.add(PCGPoint(x=x, y=0f, z=z, seed=random.nextInt()))
                z+=step
            }
            x+=step
        }
        return points
    }

    private fun applyDensity(points: List<PCGPoint>, density: Float): MutableList<PCGPoint> {
        return points.filter { random.nextFloat() < density }.toMutableList()
    }

    private fun applyNoise(points: List<PCGPoint>, scale: Float): MutableList<PCGPoint> {
        return points.map { p ->
            val n = perlinNoise(p.x*scale, p.z*scale)
            p.copy(y=p.y+n*10f, density=p.density*n)
        }.toMutableList()
    }

    private fun applyFilter(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> {
        val minSlope = props["minSlope"] as? Float ?: 0f; val maxSlope = props["maxSlope"] as? Float ?: 1f
        val minHeight = props["minHeight"] as? Float ?: -100f
        return points.filter { it.y >= minHeight && it.density in minSlope..maxSlope }.toMutableList()
    }

    private fun applyScatter(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> {
        val count = props["count"] as? Int ?: 100
        return points.shuffled(random).take(count).map { it.copy(scale=0.8f+random.nextFloat()*0.4f, rotY=random.nextFloat()*360f) }.toMutableList()
    }

    private fun applyTransform(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> {
        val scale = props["scale"] as? Float ?: 1f
        return points.map { it.copy(scale=it.scale*scale) }.toMutableList()
    }

    private fun applySpawn(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> = points.toMutableList()
    private fun applySplit(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> = points.toMutableList()
    private fun applyCluster(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> = points.toMutableList()
    private fun applyBiome(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> {
        val temp = props["temperature"] as? Float ?: 0.5f
        return points.map { it.copy(attributes=mutableMapOf("temperature" to temp, "humidity" to 0.5f)) }.toMutableList()
    }
    private fun applyPath(points: List<PCGPoint>, props: Map<String, Any>): MutableList<PCGPoint> = points.toMutableList()

    private fun perlinNoise(x: Float, z: Float): Float {
        return (sin(x*12.9898f+z*78.233f)*43758.5453f % 1f + 1f) % 1f
    }

    fun getStats(): String = "PCG Graphs:${graphs.size} Points:${generatedPoints.size}"
}
