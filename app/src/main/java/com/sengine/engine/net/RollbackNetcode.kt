package com.sengine.engine.net

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Rollback Netcode & Advanced Multiplayer
 * GGPO-like rollback, Client Prediction, Lag Compensation, Anti-Cheat
 */
enum class RollbackState { Running, RollingBack, Resimulating }
enum class InputType { Button, Axis, Vector }

data class FrameInput(
    var frame: Int = 0,
    var playerId: Long = 0L,
    var buttons: Int = 0, // bitmask
    var axisX: Float = 0f,
    var axisY: Float = 0f,
    var checksum: Int = 0
) {
    fun calculateChecksum(): Int = frame + playerId.toInt() + buttons + (axisX*100).toInt() + (axisY*100).toInt()
    fun isValid(): Boolean = checksum == calculateChecksum()
}

data class GameState(
    var frame: Int = 0,
    var playerStates: MutableMap<Long, FloatArray> = mutableMapOf(), // playerId -> pos/vel
    var objectStates: MutableMap<Long, FloatArray> = mutableMapOf(),
    var checksum: Int = 0
) {
    fun calculateChecksum(): Int {
        var sum = frame
        for ((id, state) in playerStates) sum+=id.toInt()+state.sum().toInt()
        return sum
    }
    fun copy(): GameState = GameState(frame=frame, playerStates=playerStates.mapValues { it.value.clone() }.toMutableMap(), objectStates=objectStates.mapValues { it.value.clone() }.toMutableMap(), checksum=checksum)
}

data class RollbackFrame(
    var frame: Int = 0,
    var inputs: MutableMap<Long, FrameInput> = mutableMapOf(),
    var state: GameState = GameState(),
    var isConfirmed: Boolean = false,
    var isPredicted: Boolean = false
)

class RollbackNetcode {
    var maxRollbackFrames: Int = 8
    var inputDelay: Int = 2
    var frames = mutableListOf<RollbackFrame>()
    var currentFrame: Int = 0
    var confirmedFrame: Int = -1
    var state: RollbackState = RollbackState.Running
    var localPlayerId: Long = 0L
    var players = mutableListOf<Long>()
    var inputHistory = mutableMapOf<Long, MutableList<FrameInput>>()
    var stateHistory = mutableListOf<GameState>()
    var rollbackCount: Int = 0
    var resimulateCount: Int = 0

    fun addPlayer(playerId: Long) {
        if (playerId !in players) {
            players.add(playerId)
            inputHistory[playerId]=mutableListOf()
        }
    }

    fun addInput(playerId: Long, input: FrameInput) {
        inputHistory.getOrPut(playerId){mutableListOf()}.add(input)
        // Keep history limited
        if (inputHistory[playerId]!!.size > 60) inputHistory[playerId]!!.removeAt(0)
    }

    fun saveState(state: GameState) {
        stateHistory.add(state.copy())
        if (stateHistory.size > 60) stateHistory.removeAt(0)
        val frame = RollbackFrame(frame=currentFrame, state=state.copy(), isConfirmed=false)
        frames.add(frame)
        if (frames.size > 60) frames.removeAt(0)
    }

    fun confirmFrame(frame: Int, inputs: Map<Long, FrameInput>) {
        val rollbackFrame = frames.find { it.frame == frame } ?: return
        rollbackFrame.isConfirmed=true
        confirmedFrame = maxOf(confirmedFrame, frame)
        // Check if we need to rollback
        for ((playerId, confirmedInput) in inputs) {
            val predictedInput = rollbackFrame.inputs[playerId]
            if (predictedInput != null && predictedInput.buttons != confirmedInput.buttons) {
                // Misprediction - need rollback
                rollbackTo(frame)
                break
            }
        }
    }

    fun rollbackTo(frame: Int) {
        state = RollbackState.RollingBack
        rollbackCount++
        // Find state at frame
        val stateAtFrame = stateHistory.find { it.frame == frame } ?: return
        // Resimulate from frame to current
        currentFrame = frame
        state = RollbackState.Resimulating
        for (f in frame..currentFrame+maxRollbackFrames) {
            resimulateCount++
            // Apply inputs for this frame
            for (playerId in players) {
                val input = inputHistory[playerId]?.find { it.frame == f }
                // Simulate game logic with input
            }
            currentFrame++
        }
        state = RollbackState.Running
    }

    fun predictInput(playerId: Long, frame: Int): FrameInput {
        // Predict based on last input
        val history = inputHistory[playerId] ?: return FrameInput(frame=frame, playerId=playerId)
        return history.lastOrNull()?.copy(frame=frame) ?: FrameInput(frame=frame, playerId=playerId)
    }

    fun getCurrentInput(playerId: Long): FrameInput {
        return inputHistory[playerId]?.find { it.frame == currentFrame } ?: predictInput(playerId, currentFrame)
    }

    fun update() {
        currentFrame++
        // Save current frame
        for (playerId in players) {
            val input = getCurrentInput(playerId)
            frames.find { it.frame == currentFrame }?.inputs?.set(playerId, input)
        }
    }

    fun getStats(): String = "Rollback Frame:$currentFrame Confirmed:$confirmedFrame Rollbacks:$rollbackCount Resim:$resimulateCount State:$state"
}

// Lag Compensation
data class LagCompensationRecord(
    var playerId: Long = 0L,
    var frame: Int = 0,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var timestamp: Long = 0L
)

class LagCompensationSystem {
    var history = mutableListOf<LagCompensationRecord>()
    var maxHistoryMs: Long = 1000L
    var enabled: Boolean = true

    fun record(playerId: Long, frame: Int, x: Float, y: Float, z: Float) {
        history.add(LagCompensationRecord(playerId=playerId, frame=frame, x=x, y=y, z=z, timestamp=System.currentTimeMillis()))
        // Clean old
        val cutoff = System.currentTimeMillis()-maxHistoryMs
        history.removeAll { it.timestamp < cutoff }
    }

    fun rewind(playerId: Long, latencyMs: Long): LagCompensationRecord? {
        val targetTime = System.currentTimeMillis()-latencyMs
        return history.filter { it.playerId==playerId }.minByOrNull { abs(it.timestamp-targetTime) }
    }

    fun getStats(): String = "LagComp History:${history.size} Max:${maxHistoryMs}ms"
}

// Anti-Cheat
enum class CheatType { SpeedHack, WallHack, AimBot, MemoryEdit, PacketManipulation }

data class CheatDetection(
    var playerId: Long = 0L,
    var type: CheatType = CheatType.SpeedHack,
    var confidence: Float = 0f,
    var timestamp: Long = System.currentTimeMillis(),
    var details: String = ""
)

class AntiCheatSystem {
    var detections = mutableListOf<CheatDetection>()
    var enabled: Boolean = true
    var speedHackThreshold: Float = 15f // m/s
    var teleportThreshold: Float = 50f

    fun checkSpeedHack(playerId: Long, prevX: Float, prevZ: Float, currX: Float, currZ: Float, dt: Float): Boolean {
        if (!enabled) return false
        val dx = currX-prevX; val dz = currZ-prevZ
        val dist = sqrt(dx*dx+dz*dz)
        val speed = if (dt > 0f) dist/dt else 0f
        if (speed > speedHackThreshold) {
            detections.add(CheatDetection(playerId=playerId, type=CheatType.SpeedHack, confidence=minOf(1f, speed/speedHackThreshold-1f), details="Speed $speed > $speedHackThreshold"))
            return true
        }
        return false
    }

    fun checkTeleport(playerId: Long, prevX: Float, prevZ: Float, currX: Float, currZ: Float): Boolean {
        if (!enabled) return false
        val dx = currX-prevX; val dz = currZ-prevZ
        val dist = sqrt(dx*dx+dz*dz)
        if (dist > teleportThreshold) {
            detections.add(CheatDetection(playerId=playerId, type=CheatType.MemoryEdit, confidence=1f, details="Teleport $dist > $teleportThreshold"))
            return true
        }
        return false
    }

    fun getDetectionsForPlayer(playerId: Long): List<CheatDetection> = detections.filter { it.playerId==playerId }
    fun getStats(): String = "AntiCheat Detections:${detections.size} Enabled:$enabled"
}
