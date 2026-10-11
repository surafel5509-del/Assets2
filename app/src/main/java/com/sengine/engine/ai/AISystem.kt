package com.sengine.engine.ai

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

/**
 * Ultimate Edition AI System - Unity NavMesh + Unreal Behavior Trees + Crowd
 */

class NavMeshNode(var x: Float, var y: Float, var z: Float, var walkable: Boolean = true) {
    var gCost: Float = 0f
    var hCost: Float = 0f
    var fCost: Float get() = gCost + hCost
    var parent: NavMeshNode? = null
    val neighbors = mutableListOf<NavMeshNode>()
}

class NavMesh {
    val nodes = mutableListOf<NavMeshNode>()
    var width: Int = 100
    var height: Int = 100
    var cellSize: Float = 1f

    fun bake(groundY: Float = 0f, obstacles: List<GameObject> = emptyList()) {
        nodes.clear()
        for (z in 0 until height) for (x in 0 until width) {
            val wx = (x - width / 2) * cellSize
            val wz = (z - height / 2) * cellSize
            var walkable = true
            for (obs in obstacles) {
                val dx = obs.x - wx; val dz = obs.z - wz
                if (dx * dx + dz * dz < 2f) { walkable = false; break }
            }
            nodes.add(NavMeshNode(wx, groundY, wz, walkable))
        }
        // link neighbors
        for (z in 0 until height) for (x in 0 until width) {
            val idx = z * width + x
            val node = nodes[idx]
            if (!node.walkable) continue
            for (dz in -1..1) for (dx in -1..1) {
                if (dx == 0 && dz == 0) continue
                val nx = x + dx; val nz = z + dz
                if (nx in 0 until width && nz in 0 until height) {
                    val n = nodes[nz * width + nx]
                    if (n.walkable) node.neighbors.add(n)
                }
            }
        }
    }

    fun findPath(start: FloatArray, end: FloatArray): List<FloatArray>? {
        val startNode = nodes.minByOrNull {
            val dx = it.x - start[0]; val dz = it.z - start[2]
            dx * dx + dz * dz
        } ?: return null
        val endNode = nodes.minByOrNull {
            val dx = it.x - end[0]; val dz = it.z - end[2]
            dx * dx + dz * dz
        } ?: return null

        if (!startNode.walkable || !endNode.walkable) return null

        val open = mutableListOf(startNode)
        val closed = mutableSetOf<NavMeshNode>()
        for (n in nodes) { n.gCost = Float.MAX_VALUE; n.hCost = 0f; n.parent = null }
        startNode.gCost = 0f
        startNode.hCost = heuristic(startNode, endNode)

        while (open.isNotEmpty()) {
            open.sortBy { it.fCost }
            val current = open.removeAt(0)
            if (current === endNode) {
                // reconstruct
                val path = mutableListOf<FloatArray>()
                var node: NavMeshNode? = current
                while (node != null) {
                    path.add(floatArrayOf(node.x, node.y, node.z))
                    node = node.parent
                }
                return path.reversed()
            }
            closed.add(current)
            for (neighbor in current.neighbors) {
                if (neighbor in closed) continue
                val tentativeG = current.gCost + distance(current, neighbor)
                if (tentativeG < neighbor.gCost) {
                    neighbor.parent = current
                    neighbor.gCost = tentativeG
                    neighbor.hCost = heuristic(neighbor, endNode)
                    if (neighbor !in open) open.add(neighbor)
                }
            }
        }
        return null
    }

    private fun heuristic(a: NavMeshNode, b: NavMeshNode): Float {
        val dx = a.x - b.x; val dz = a.z - b.z
        return sqrt((dx * dx + dz * dz).toDouble()).toFloat()
    }

    private fun distance(a: NavMeshNode, b: NavMeshNode): Float {
        val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }
}

class NavMeshAgent {
    var speed: Float = 3.5f
    var angularSpeed: Float = 120f
    var acceleration: Float = 8f
    var stoppingDistance: Float = 0.2f
    var autoBraking: Boolean = true
    var autoTraverseOffMeshLink: Boolean = true
    var areaMask: Int = -1
    var obstacleAvoidanceType: Int = 3
    var avoidancePriority: Int = 50

    var destination: FloatArray = floatArrayOf(0f, 0f, 0f)
    var path: List<FloatArray> = emptyList()
    var currentPathIndex: Int = 0
    var isStopped: Boolean = false
    var velocity: FloatArray = floatArrayOf(0f, 0f, 0f)
    var remainingDistance: Float = 0f
    var hasPath: Boolean = false

    fun setDestination(target: FloatArray, navMesh: NavMesh, currentPos: FloatArray): Boolean {
        destination = target
        val newPath = navMesh.findPath(currentPos, target)
        if (newPath != null && newPath.isNotEmpty()) {
            path = newPath
            currentPathIndex = 0
            hasPath = true
            remainingDistance = calculateRemainingDistance(currentPos)
            return true
        }
        hasPath = false
        return false
    }

    private fun calculateRemainingDistance(currentPos: FloatArray): Float {
        if (path.isEmpty()) return 0f
        var dist = 0f
        var prev = currentPos
        for (i in currentPathIndex until path.size) {
            val p = path[i]
            val dx = p[0] - prev[0]; val dy = p[1] - prev[1]; val dz = p[2] - prev[2]
            dist += sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
            prev = p
        }
        return dist
    }

    fun update(go: GameObject, dt: Float): Boolean {
        if (isStopped || !hasPath || path.isEmpty()) return false
        if (currentPathIndex >= path.size) { hasPath = false; return true }

        val target = path[currentPathIndex]
        val dx = target[0] - go.x
        val dy = target[1] - go.y
        val dz = target[2] - go.z
        val dist = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()

        if (dist <= stoppingDistance) {
            currentPathIndex++
            if (currentPathIndex >= path.size) {
                hasPath = false
                velocity = floatArrayOf(0f, 0f, 0f)
                return true
            }
            return false
        }

        val moveDist = speed * dt
        val t = (moveDist / dist).coerceIn(0f, 1f)
        go.x += dx * t
        go.y += dy * t
        go.z += dz * t

        velocity[0] = dx / dist * speed
        velocity[1] = dy / dist * speed
        velocity[2] = dz / dist * speed

        // rotate towards movement
        if (velocity[0] != 0f || velocity[2] != 0f) {
            val targetAngle = Math.toDegrees(kotlin.math.atan2(velocity[0].toDouble(), velocity[2].toDouble())).toFloat()
            var diff = targetAngle - go.rotY
            while (diff > 180f) diff -= 360f
            while (diff < -180f) diff += 360f
            go.rotY += diff.coerceIn(-angularSpeed * dt, angularSpeed * dt)
        }

        remainingDistance = calculateRemainingDistance(floatArrayOf(go.x, go.y, go.z))
        return false
    }
}

/**
 * Behavior Tree - Unreal-like
 */
enum class BTStatus { Success, Failure, Running }

abstract class BTNode {
    var name: String = "Node"
    abstract fun tick(blackboard: Blackboard, dt: Float): BTStatus
    open fun reset() {}
}

class Blackboard {
    private val data = mutableMapOf<String, Any>()
    fun set(key: String, value: Any) { data[key] = value }
    fun <T> get(key: String): T? { @Suppress("UNCHECKED_CAST") return data[key] as? T }
    fun has(key: String): Boolean = key in data
    fun remove(key: String) { data.remove(key) }
}

class BTSequence(vararg val children: BTNode) : BTNode() {
    private var currentIndex = 0
    override fun tick(blackboard: Blackboard, dt: Float): BTStatus {
        while (currentIndex < children.size) {
            val status = children[currentIndex].tick(blackboard, dt)
            when (status) {
                BTStatus.Running -> return BTStatus.Running
                BTStatus.Failure -> { currentIndex = 0; return BTStatus.Failure }
                BTStatus.Success -> currentIndex++
            }
        }
        currentIndex = 0
        return BTStatus.Success
    }
    override fun reset() { currentIndex = 0; for (c in children) c.reset() }
}

class BTSelector(vararg val children: BTNode) : BTNode() {
    private var currentIndex = 0
    override fun tick(blackboard: Blackboard, dt: Float): BTStatus {
        while (currentIndex < children.size) {
            val status = children[currentIndex].tick(blackboard, dt)
            when (status) {
                BTStatus.Running -> return BTStatus.Running
                BTStatus.Success -> { currentIndex = 0; return BTStatus.Success }
                BTStatus.Failure -> currentIndex++
            }
        }
        currentIndex = 0
        return BTStatus.Failure
    }
    override fun reset() { currentIndex = 0; for (c in children) c.reset() }
}

class BTInverter(val child: BTNode) : BTNode() {
    override fun tick(blackboard: Blackboard, dt: Float): BTStatus {
        return when (child.tick(blackboard, dt)) {
            BTStatus.Success -> BTStatus.Failure
            BTStatus.Failure -> BTStatus.Success
            BTStatus.Running -> BTStatus.Running
        }
    }
}

class BTCondition(val condition: (Blackboard) -> Boolean) : BTNode() {
    override fun tick(blackboard: Blackboard, dt: Float): BTStatus {
        return if (condition(blackboard)) BTStatus.Success else BTStatus.Failure
    }
}

class BTAction(val action: (Blackboard, Float) -> BTStatus) : BTNode() {
    override fun tick(blackboard: Blackboard, dt: Float): BTStatus = action(blackboard, dt)
}

class BTWait(val duration: Float) : BTNode() {
    private var elapsed = 0f
    override fun tick(blackboard: Blackboard, dt: Float): BTStatus {
        elapsed += dt
        return if (elapsed >= duration) { elapsed = 0f; BTStatus.Success } else BTStatus.Running
    }
    override fun reset() { elapsed = 0f }
}

class BehaviorTree(var root: BTNode) {
    val blackboard = Blackboard()
    fun tick(dt: Float): BTStatus = root.tick(blackboard, dt)
    fun reset() { root.reset() }
}

/**
 * Crowd Simulation - many agents avoiding each other
 */
class CrowdSimulation(val navMesh: NavMesh) {
    val agents = mutableListOf<Pair<GameObject, NavMeshAgent>>()

    fun addAgent(go: GameObject, agent: NavMeshAgent) { agents.add(go to agent) }

    fun update(dt: Float) {
        for ((go, agent) in agents) {
            // simple avoidance: check nearby agents
            var avoidX = 0f; var avoidZ = 0f
            for ((otherGo, _) in agents) {
                if (otherGo === go) continue
                val dx = go.x - otherGo.x; val dz = go.z - otherGo.z
                val dist2 = dx * dx + dz * dz
                if (dist2 < 4f && dist2 > 0.01f) {
                    val dist = sqrt(dist2.toDouble()).toFloat()
                    avoidX += dx / dist * (2f - dist) * 0.5f
                    avoidZ += dz / dist * (2f - dist) * 0.5f
                }
            }
            go.x += avoidX * dt
            go.z += avoidZ * dt
            agent.update(go, dt)
        }
    }
}
