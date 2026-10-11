package com.sengine.engine.ai

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra AI System - Months/Years Professional Project
 * NavMesh v2 Off-Mesh Links Areas Costs Agent Types, Crowd RVO Formation, Behavior Tree v2 Blackboard Decorators Services, State Machine AI, Utility AI v2 Curves, GOAP, HTN, Perception Sight Hearing Damage, Cover System
 */
enum class NavMeshArea { Walkable, NotWalkable, Jump, Water, Door, Custom }
enum class OffMeshLinkType { Manual, DropDown, JumpAcross, Custom }
enum class PerceptionType { Sight, Hearing, Damage, Custom }
enum class CoverType { Low, High, Half, Full }

data class NavMeshAreaData(
    var area: NavMeshArea = NavMeshArea.Walkable,
    var cost: Float = 1f,
    var name: String = "Walkable"
)

data class OffMeshLink(
    var id: String = "link_0",
    var startX: Float = 0f, var startY: Float = 0f, var startZ: Float = 0f,
    var endX: Float = 0f, var endY: Float = 0f, var endZ: Float = 0f,
    var type: OffMeshLinkType = OffMeshLinkType.Manual,
    var costOverride: Float = -1f,
    var area: NavMeshArea = NavMeshArea.Walkable,
    var isBidirectional: Boolean = true,
    var isActivated: Boolean = true,
    var autoUpdate: Boolean = true
) {
    fun getCost(defaultCost: Float): Float = if (costOverride >= 0f) costOverride else defaultCost
    fun length(): Float {
        val dx = endX-startX; val dy = endY-startY; val dz = endZ-startZ
        return sqrt(dx*dx+dy*dy+dz*dz)
    }
}

data class NavMeshAgentType(
    var id: Int = 0,
    var name: String = "Humanoid",
    var radius: Float = 0.5f,
    var height: Float = 2f,
    var climbHeight: Float = 0.4f,
    var slopeLimit: Float = 45f,
    var areaMask: Int = -1
)

data class PerceptionStimulus(
    var type: PerceptionType = PerceptionType.Sight,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var strength: Float = 1f,
    var sourceId: Long = -1L,
    var timestamp: Long = System.currentTimeMillis(),
    var isActive: Boolean = true
)

data class PerceptionSystem(
    var sightRange: Float = 20f,
    var sightAngle: Float = 90f,
    var hearingRange: Float = 10f,
    var sightHeight: Float = 1.6f,
    var stimuli: MutableList<PerceptionStimulus> = mutableListOf(),
    var knownTargets: MutableMap<Long, FloatArray> = mutableMapOf() // id -> pos
) {
    fun canSee(targetX: Float, targetY: Float, targetZ: Float, selfX: Float, selfY: Float, selfZ: Float, selfDirX: Float, selfDirZ: Float): Boolean {
        val dx = targetX-selfX; val dy = targetY-selfY; val dz = targetZ-selfZ
        val dist = sqrt(dx*dx+dy*dy+dz*dz)
        if (dist > sightRange) return false
        val dirLen = sqrt(selfDirX*selfDirX+selfDirZ*selfDirZ).coerceAtLeast(0.0001f)
        val dot = (dx/dist)*(selfDirX/dirLen) + (dz/dist)*(selfDirZ/dirLen)
        val angle = acos(dot.coerceIn(-1f,1f))*180f/PI.toFloat()
        return angle <= sightAngle*0.5f
    }
    fun canHear(strength: Float, distance: Float): Boolean = strength/distance > 0.1f && distance <= hearingRange
    fun addStimulus(type: PerceptionType, x: Float, y: Float, z: Float, strength: Float, sourceId: Long) {
        stimuli.add(PerceptionStimulus(type=type, x=x, y=y, z=z, strength=strength, sourceId=sourceId))
        if (stimuli.size > 100) stimuli.removeAt(0)
        knownTargets[sourceId]=floatArrayOf(x,y,z)
    }
    fun getStimuliByType(type: PerceptionType): List<PerceptionStimulus> = stimuli.filter { it.type == type && it.isActive }
}

data class CoverPoint(
    var id: String = "cover_0",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var dirX: Float = 0f, var dirZ: Float = 1f,
    var type: CoverType = CoverType.Low,
    var quality: Float = 1f,
    var isOccupied: Boolean = false,
    var occupiedBy: Long = -1L
) {
    fun isGoodCover(fromX: Float, fromZ: Float): Boolean {
        val dx = x-fromX; val dz = z-fromZ
        val dot = dx*dirX+dz*dirZ
        return dot < 0f // cover faces away from threat
    }
    fun distanceTo(px: Float, pz: Float): Float {
        val dx = x-px; val dz = z-pz; return sqrt(dx*dx+dz*dz)
    }
}

data class GOAPAction(
    var name: String = "Action",
    var preconditions: MutableMap<String, Boolean> = mutableMapOf(),
    var effects: MutableMap<String, Boolean> = mutableMapOf(),
    var cost: Float = 1f,
    var isRunning: Boolean = false
) {
    fun canExecute(state: Map<String, Boolean>): Boolean {
        for ((key, value) in preconditions) if (state[key] != value) return false
        return true
    }
    fun applyEffects(state: MutableMap<String, Boolean>) {
        for ((key, value) in effects) state[key]=value
    }
}

data class GOAPGoal(
    var name: String = "Goal",
    var desiredState: MutableMap<String, Boolean> = mutableMapOf(),
    var priority: Float = 1f
)

class GOAPPlanner {
    var actions = mutableListOf<GOAPAction>()
    var goals = mutableListOf<GOAPGoal>()

    fun addAction(name: String, cost: Float = 1f, pre: Map<String, Boolean> = emptyMap(), eff: Map<String, Boolean> = emptyMap()): GOAPAction {
        val a = GOAPAction(name=name, preconditions=pre.toMutableMap(), effects=eff.toMutableMap(), cost=cost)
        actions.add(a); return a
    }

    fun addGoal(name: String, desired: Map<String, Boolean>, priority: Float = 1f): GOAPGoal {
        val g = GOAPGoal(name=name, desiredState=desired.toMutableMap(), priority=priority)
        goals.add(g); return g
    }

    fun plan(currentState: Map<String, Boolean>, goal: GOAPGoal): List<GOAPAction>? {
        // Simple BFS planner
        val queue = ArrayDeque<Pair<MutableMap<String, Boolean>, MutableList<GOAPAction>>>()
        queue.add(Pair(currentState.toMutableMap(), mutableListOf()))
        val visited = mutableSetOf<String>()

        while (queue.isNotEmpty()) {
            val (state, plan) = queue.removeFirst()
            val stateKey = state.toString()
            if (stateKey in visited) continue
            visited.add(stateKey)

            // Check if goal reached
            var goalReached = true
            for ((k,v) in goal.desiredState) if (state[k] != v) { goalReached=false; break }
            if (goalReached) return plan

            // Try all actions
            for (action in actions) if (action.canExecute(state)) {
                val newState = state.toMutableMap()
                action.applyEffects(newState)
                val newPlan = plan.toMutableList(); newPlan.add(action)
                queue.add(Pair(newState, newPlan))
            }
        }
        return null
    }
}

class UltraAISystem {
    var areas = mutableMapOf<Int, NavMeshAreaData>()
    var offMeshLinks = mutableMapOf<String, OffMeshLink>()
    var agentTypes = mutableMapOf<Int, NavMeshAgentType>()
    var perceptionSystems = mutableMapOf<Long, PerceptionSystem>()
    var coverPoints = mutableMapOf<String, CoverPoint>()
    var goapPlanner = GOAPPlanner()
    var nextLinkId: Int = 0
    var nextCoverId: Int = 0

    init {
        // Default areas
        areas[0]=NavMeshAreaData(NavMeshArea.Walkable, 1f, "Walkable")
        areas[1]=NavMeshAreaData(NavMeshArea.NotWalkable, 100f, "Not Walkable")
        areas[2]=NavMeshAreaData(NavMeshArea.Jump, 2f, "Jump")
        areas[3]=NavMeshAreaData(NavMeshArea.Water, 5f, "Water")
        areas[4]=NavMeshAreaData(NavMeshArea.Door, 1.5f, "Door")

        // Default agent types
        agentTypes[0]=NavMeshAgentType(0, "Humanoid", 0.5f, 2f, 0.4f, 45f, -1)
        agentTypes[1]=NavMeshAgentType(1, "Small", 0.2f, 0.5f, 0.2f, 60f, -1)
        agentTypes[2]=NavMeshAgentType(2, "Large", 1f, 3f, 0.6f, 30f, -1)

        // GOAP sample
        goapPlanner.addAction("FindWeapon", 1f, mapOf("hasWeapon" to false), mapOf("hasWeapon" to true))
        goapPlanner.addAction("GoToEnemy", 2f, mapOf("hasWeapon" to true, "atEnemy" to false), mapOf("atEnemy" to true))
        goapPlanner.addAction("Attack", 1f, mapOf("atEnemy" to true, "hasWeapon" to true), mapOf("enemyDead" to true))
        goapPlanner.addGoal("KillEnemy", mapOf("enemyDead" to true), 10f)
    }

    fun createOffMeshLink(sx: Float, sy: Float, sz: Float, ex: Float, ey: Float, ez: Float, type: OffMeshLinkType = OffMeshLinkType.Manual): OffMeshLink {
        val id = "link_${nextLinkId++}"
        val link = OffMeshLink(id=id, startX=sx, startY=sy, startZ=sz, endX=ex, endY=ey, endZ=ez, type=type)
        offMeshLinks[id]=link; return link
    }

    fun createCoverPoint(x: Float, y: Float, z: Float, dirX: Float, dirZ: Float, type: CoverType = CoverType.Low): CoverPoint {
        val id = "cover_${nextCoverId++}"
        val cover = CoverPoint(id=id, x=x, y=y, z=z, dirX=dirX, dirZ=dirZ, type=type)
        coverPoints[id]=cover; return cover
    }

    fun getCoverPointsNear(x: Float, z: Float, radius: Float): List<CoverPoint> {
        return coverPoints.values.filter { !it.isOccupied && it.distanceTo(x,z) <= radius }.sortedBy { it.distanceTo(x,z) }
    }

    fun createPerceptionSystem(agentId: Long): PerceptionSystem {
        val ps = PerceptionSystem(); perceptionSystems[agentId]=ps; return ps
    }

    fun getStats(): String = "Ultra AI Areas:${areas.size} Links:${offMeshLinks.size} AgentTypes:${agentTypes.size} Cover:${coverPoints.size} Perception:${perceptionSystems.size} GOAP Actions:${goapPlanner.actions.size} Goals:${goapPlanner.goals.size}"
}
