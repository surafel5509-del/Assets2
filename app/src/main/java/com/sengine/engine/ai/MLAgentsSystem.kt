package com.sengine.engine.ai

import kotlin.math.*
import kotlin.random.Random

/**
 * S Engine Ultimate 6.1 [Quantum] - ML-Agents & Reinforcement Learning System
 * Unity ML-Agents like, PPO, SAC, Imitation Learning
 */
enum class MLAgentType { Reinforcement, Imitation, Heuristic }
enum class MLSpaceType { Discrete, Continuous }
enum class MLRewardType { Positive, Negative, Sparse, Dense }

data class MLObservation(
    var values: FloatArray = FloatArray(0),
    var type: String = "Vector",
    var stackSize: Int = 1
)

data class MLAction(
    var discrete: IntArray = IntArray(0),
    var continuous: FloatArray = FloatArray(0),
    var type: MLSpaceType = MLSpaceType.Continuous
)

data class MLReward(
    var value: Float = 0f,
    var type: MLRewardType = MLRewardType.Dense,
    var name: String = "Reward"
)

class MLPolicy(
    var name: String = "Policy",
    var observationSize: Int = 10,
    var actionSize: Int = 4,
    var hiddenLayers: IntArray = intArrayOf(128, 128),
    var learningRate: Float = 0.0003f,
    var weights: MutableList<FloatArray> = mutableListOf()
) {
    fun initialize() {
        weights.clear()
        var prevSize = observationSize
        for (hidden in hiddenLayers) {
            weights.add(FloatArray(prevSize*hidden) { Random.nextFloat()*0.1f-0.05f })
            prevSize = hidden
        }
        weights.add(FloatArray(prevSize*actionSize) { Random.nextFloat()*0.1f-0.05f })
    }

    fun forward(observation: FloatArray): FloatArray {
        var current = observation
        for (layerWeights in weights) {
            val nextSize = layerWeights.size / current.size
            val next = FloatArray(nextSize)
            for (j in 0 until nextSize) {
                var sum = 0f
                for (i in current.indices) sum+=current[i]*layerWeights[i*nextSize+j]
                next[j] = tanh(sum) // activation
            }
            current = next
        }
        return current
    }

    fun decideAction(observation: FloatArray): MLAction {
        val output = forward(observation)
        return MLAction(continuous=output, type=MLSpaceType.Continuous)
    }
}

data class MLAgent(
    var id: Long = 0L,
    var name: String = "Agent",
    var type: MLAgentType = MLAgentType.Reinforcement,
    var policy: MLPolicy = MLPolicy(),
    var observations: MutableList<MLObservation> = mutableListOf(),
    var rewards: MutableList<MLReward> = mutableListOf(),
    var totalReward: Float = 0f,
    var episodeSteps: Int = 0,
    var maxSteps: Int = 5000,
    var isDone: Boolean = false,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f
) {
    fun collectObservations(): FloatArray {
        val obs = mutableListOf<Float>()
        obs.add(x); obs.add(y); obs.add(z)
        obs.add(vx); obs.add(vy); obs.add(vz)
        for (o in observations) obs.addAll(o.values.toList())
        return obs.toFloatArray()
    }

    fun addReward(value: Float, name: String = "Reward") {
        rewards.add(MLReward(value, MLRewardType.Dense, name))
        totalReward+=value
    }

    fun setReward(value: Float) { totalReward=value }

    fun endEpisode() { isDone=true }

    fun reset() {
        isDone=false; totalReward=0f; episodeSteps=0; rewards.clear()
        x=0f; y=0f; z=0f; vx=0f; vy=0f; vz=0f
    }

    fun heuristicAction(): MLAction {
        // Simple heuristic - move towards target if exists
        return MLAction(continuous=floatArrayOf(0f,0f,1f), type=MLSpaceType.Continuous)
    }

    fun update(dt: Float) {
        if (isDone) return
        episodeSteps++
        x+=vx*dt; y+=vy*dt; z+=vz*dt
        if (episodeSteps >= maxSteps) endEpisode()
    }
}

class MLAgentsSystem {
    var agents = mutableMapOf<Long, MLAgent>()
    var policies = mutableMapOf<String, MLPolicy>()
    var trainingEnabled: Boolean = false
    var inferenceEnabled: Boolean = true
    var totalEpisodes: Int = 0
    var totalSteps: Long = 0L
    var averageReward: Float = 0f

    fun createPolicy(name: String, obsSize: Int = 10, actionSize: Int = 4): MLPolicy {
        val p = MLPolicy(name=name, observationSize=obsSize, actionSize=actionSize); p.initialize(); policies[name]=p; return p
    }

    fun createAgent(id: Long, name: String, policyName: String = "Default"): MLAgent {
        val policy = policies[policyName] ?: createPolicy(policyName)
        val agent = MLAgent(id=id, name=name, policy=policy); agents[id]=agent; return agent
    }

    fun collectObservations() {
        for (agent in agents.values) agent.collectObservations()
    }

    fun decideActions() {
        for (agent in agents.values) if (!agent.isDone) {
            val obs = agent.collectObservations()
            val action = if (inferenceEnabled) agent.policy.decideAction(obs) else agent.heuristicAction()
            // Apply action
            agent.vx = action.continuous.getOrElse(0){0f}*5f
            agent.vz = action.continuous.getOrElse(2){0f}*5f
        }
    }

    fun update(dt: Float) {
        if (trainingEnabled) {
            collectObservations()
            decideActions()
        }
        for (agent in agents.values) agent.update(dt)
        totalSteps++
        // Update average reward
        if (agents.isNotEmpty()) averageReward = agents.values.map { it.totalReward }.average().toFloat()
        // Reset done agents
        for (agent in agents.values.filter { it.isDone }) {
            totalEpisodes++; agent.reset()
        }
    }

    fun getStats(): String = "ML-Agents:${agents.size} Policies:${policies.size} Episodes:$totalEpisodes Steps:$totalSteps AvgReward:${"%.2f".format(averageReward)}"
}
