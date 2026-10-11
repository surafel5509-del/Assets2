package com.sengine.engine.ai

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Environment Query System (EQS) + Advanced AI
 * UE5 EQS, Utility AI, HTN, State Tree
 */
enum class EQSQueryType { Grid, Cone, Circle, Donut, Line, Path, Custom }
enum class EQSTestType { Distance, Dot, Overlap, Trace, Pathfinding, Visibility, GameplayTag }
enum class EQSScoringEquation { Linear, InverseLinear, Square, InverseSquare, Constant }

data class EQSTest(
    var type: EQSTestType = EQSTestType.Distance,
    var scoringEquation: EQSScoringEquation = EQSScoringEquation.Linear,
    var weight: Float = 1f,
    var minScore: Float = 0f,
    var maxScore: Float = 1f,
    var clampMin: Float = 0f,
    var clampMax: Float = 1f,
    var context: String = "Querier",
    var boolParam: Boolean = true,
    var floatParam: Float = 0f
) {
    fun score(value: Float): Float {
        val normalized = when (scoringEquation) {
            EQSScoringEquation.Linear -> value
            EQSScoringEquation.InverseLinear -> 1f-value
            EQSScoringEquation.Square -> value*value
            EQSScoringEquation.InverseSquare -> 1f-value*value
            EQSScoringEquation.Constant -> 1f
        }
        return (normalized*weight).coerceIn(clampMin, clampMax)
    }
}

data class EQSQuery(
    var name: String = "Query",
    var type: EQSQueryType = EQSQueryType.Grid,
    var tests: MutableList<EQSTest> = mutableListOf(),
    var gridSize: Float = 100f,
    var gridHalfSize: Float = 50f,
    var spaceBetween: Float = 10f,
    var coneAngle: Float = 90f,
    var coneDirection: FloatArray = floatArrayOf(0f,0f,1f),
    var circleRadius: Float = 20f,
    var minDistance: Float = 0f,
    var maxDistance: Float = 100f
) {
    fun generatePoints(querierX: Float, querierY: Float, querierZ: Float): List<FloatArray> {
        val points = mutableListOf<FloatArray>()
        when (type) {
            EQSQueryType.Grid -> {
                var x = querierX-gridHalfSize; while (x <= querierX+gridHalfSize) {
                    var z = querierZ-gridHalfSize; while (z <= querierZ+gridHalfSize) {
                        points.add(floatArrayOf(x, querierY, z)); z+=spaceBetween
                    }
                    x+=spaceBetween
                }
            }
            EQSQueryType.Circle -> {
                val count = (360f/spaceBetween).toInt().coerceAtLeast(8)
                for (i in 0 until count) {
                    val angle = i.toFloat()/count*2f*PI.toFloat()
                    points.add(floatArrayOf(querierX+cos(angle)*circleRadius, querierY, querierZ+sin(angle)*circleRadius))
                }
            }
            EQSQueryType.Cone -> {
                val count = 20
                for (i in 0 until count) {
                    val angle = (i.toFloat()/count-0.5f)*coneAngle*PI.toFloat()/180f
                    val dirX = coneDirection[0]*cos(angle)-coneDirection[2]*sin(angle)
                    val dirZ = coneDirection[0]*sin(angle)+coneDirection[2]*cos(angle)
                    val dist = minDistance + (maxDistance-minDistance)* (i.toFloat()/count)
                    points.add(floatArrayOf(querierX+dirX*dist, querierY, querierZ+dirZ*dist))
                }
            }
            EQSQueryType.Donut -> {
                val count = 16
                for (i in 0 until count) {
                    val angle = i.toFloat()/count*2f*PI.toFloat()
                    val r = minDistance + (maxDistance-minDistance)*0.5f
                    points.add(floatArrayOf(querierX+cos(angle)*r, querierY, querierZ+sin(angle)*r))
                }
            }
            else -> points.add(floatArrayOf(querierX, querierY, querierZ))
        }
        return points
    }
}

data class EQSResult(
    var point: FloatArray = floatArrayOf(0f,0f,0f),
    var score: Float = 0f,
    var testScores: MutableMap<String, Float> = mutableMapOf()
)

class EQSSystem {
    var queries = mutableMapOf<String, EQSQuery>()
    var lastResults = mutableListOf<EQSResult>()

    fun createQuery(name: String, type: EQSQueryType = EQSQueryType.Grid): EQSQuery {
        val q = EQSQuery(name=name, type=type); queries[name]=q; return q
    }

    fun runQuery(queryName: String, querierX: Float, querierY: Float, querierZ: Float, contextObjects: List<FloatArray> = emptyList()): List<EQSResult> {
        val query = queries[queryName] ?: return emptyList()
        val points = query.generatePoints(querierX, querierY, querierZ)
        val results = mutableListOf<EQSResult>()
        for (point in points) {
            var totalScore = 0f; val testScores = mutableMapOf<String, Float>()
            for ((idx, test) in query.tests.withIndex()) {
                val rawScore = when (test.type) {
                    EQSTestType.Distance -> {
                        val dx = point[0]-querierX; val dz = point[2]-querierZ
                        val dist = sqrt(dx*dx+dz*dz); 1f - (dist/query.maxDistance).coerceIn(0f,1f)
                    }
                    EQSTestType.Dot -> {
                        val dx = point[0]-querierX; val dz = point[2]-querierZ
                        val len = sqrt(dx*dx+dz*dz).coerceAtLeast(0.0001f)
                        val dot = (dx/len)*query.coneDirection[0] + (dz/len)*query.coneDirection[2]
                        (dot+1f)*0.5f
                    }
                    EQSTestType.Overlap -> {
                        var overlap = 0f
                        for (obj in contextObjects) {
                            val dx = point[0]-obj[0]; val dz = point[2]-obj[2]
                            if (sqrt(dx*dx+dz*dz) < 5f) overlap+=1f
                        }
                        if (test.boolParam) 1f-overlap.coerceAtMost(1f) else overlap.coerceAtMost(1f)
                    }
                    EQSTestType.Visibility -> 1f // simplified
                    else -> 1f
                }
                val scored = test.score(rawScore)
                testScores["Test_$idx"]=scored; totalScore+=scored
            }
            if (query.tests.isNotEmpty()) totalScore/=query.tests.size
            else totalScore=1f
            results.add(EQSResult(point=point, score=totalScore, testScores=testScores))
        }
        lastResults = results.sortedByDescending { it.score }.toMutableList()
        return lastResults
    }

    fun getBestResult(): EQSResult? = lastResults.firstOrNull()
    fun getStats(): String = "EQS Queries:${queries.size} LastResults:${lastResults.size} Best:${lastResults.firstOrNull()?.score}"
}

// Utility AI System
data class UtilityAction(
    var name: String = "Action",
    var considerations: MutableList<(Blackboard)->Float> = mutableListOf(),
    var action: (Blackboard)->Unit = {},
    var weight: Float = 1f
) {
    fun score(blackboard: Blackboard): Float {
        if (considerations.isEmpty()) return weight
        var total = 1f
        for (c in considerations) total*=c(blackboard)
        return total*weight
    }
}

class UtilityAI {
    var actions = mutableListOf<UtilityAction>()
    var blackboard = Blackboard()

    fun addAction(name: String, weight: Float = 1f, considerations: List<(Blackboard)->Float> = emptyList(), action: (Blackboard)->Unit = {}): UtilityAction {
        val a = UtilityAction(name=name, considerations=considerations.toMutableList(), action=action, weight=weight)
        actions.add(a); return a
    }

    fun selectBestAction(): UtilityAction? = actions.maxByOrNull { it.score(blackboard) }

    fun update() { selectBestAction()?.action?.invoke(blackboard) }

    fun getStats(): String = "UtilityAI Actions:${actions.size} Best:${selectBestAction()?.name}"
}
