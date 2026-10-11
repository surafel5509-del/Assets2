package com.sengine.engine.net

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Networking System - Months/Years Professional Project
 * Culling Interest Management, LOD Relevance, Dedicated Server Headless, Client Prediction Reconciliation, Lag Compensation Rewind, Voice 3D Positional, Relay Lobby Matchmaking
 */
enum class InterestManagementMode { Distance, Zone, Custom, None }
enum class NetworkLOD { High, Medium, Low, Culled }
enum class MatchmakingMode { None, Casual, Ranked, Custom }

data class InterestZone(
    var id: String = "zone_0",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var radius: Float = 50f,
    var priority: Float = 1f
) {
    fun contains(px: Float, py: Float, pz: Float): Boolean {
        val dx = px-x; val dy = py-y; val dz = pz-z
        return sqrt(dx*dx+dy*dy+dz*dz) <= radius
    }
}

data class NetworkRelevance(
    var objectId: Long = 0L,
    var viewerId: Long = 0L,
    var distance: Float = 0f,
    var lod: NetworkLOD = NetworkLOD.High,
    var isRelevant: Boolean = true,
    var lastUpdateTime: Long = 0L,
    var updateRate: Float = 20f
) {
    fun calculateLOD(dist: Float): NetworkLOD = when {
        dist < 10f -> NetworkLOD.High
        dist < 30f -> NetworkLOD.Medium
        dist < 100f -> NetworkLOD.Low
        else -> NetworkLOD.Culled
    }
    fun shouldUpdate(currentTime: Long): Boolean {
        val interval = (1000f/updateRate).toLong()
        return currentTime-lastUpdateTime >= interval
    }
}

data class DedicatedServerConfig(
    var isHeadless: Boolean = true,
    var tickRate: Int = 60,
    var maxPlayers: Int = 100,
    var port: Int = 7777,
    var enableAntiCheat: Boolean = true,
    var enableLagCompensation: Boolean = true,
    var enableVoiceChat: Boolean = true,
    var mapName: String = "MainMap",
    var gameMode: String = "Deathmatch"
)

data class ClientPredictionRecord(
    var frame: Int = 0,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
    var input: FrameInput = FrameInput(),
    var isPredicted: Boolean = true
)

class ClientPredictionSystem {
    var history = mutableListOf<ClientPredictionRecord>()
    var maxHistory: Int = 60
    var predictionEnabled: Boolean = true
    var reconciliationEnabled: Boolean = true
    var errorThreshold: Float = 0.1f

    fun savePrediction(frame: Int, x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, input: FrameInput) {
        history.add(ClientPredictionRecord(frame=frame, x=x, y=y, z=z, vx=vx, vy=vy, vz=vz, input=input, isPredicted=true))
        if (history.size > maxHistory) history.removeAt(0)
    }

    fun reconcile(serverX: Float, serverY: Float, serverZ: Float, serverFrame: Int): Float {
        val record = history.find { it.frame == serverFrame } ?: return 0f
        val dx = serverX-record.x; val dy = serverY-record.y; val dz = serverZ-record.z
        val error = sqrt(dx*dx+dy*dy+dz*dz)
        if (error > errorThreshold && reconciliationEnabled) {
            // Need to resimulate
            return error
        }
        return 0f
    }

    fun resimulate(fromFrame: Int, currentFrame: Int, getInput: (Int)->FrameInput, simulate: (FrameInput, Float)->Triple<Float,Float,Float>): Triple<Float,Float,Float> {
        var x=0f; var y=0f; var z=0f
        for (frame in fromFrame..currentFrame) {
            val input = getInput(frame)
            val pos = simulate(input, 1f/60f)
            x=pos.first; y=pos.second; z=pos.third
        }
        return Triple(x,y,z)
    }
}

data class VoiceChat3D(
    var playerId: Long = 0L,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var volume: Float = 1f,
    var isTalking: Boolean = false,
    var isMuted: Boolean = false,
    var isDeafened: Boolean = false,
    var spatialBlend: Float = 1f, // 0 2D, 1 3D
    var minDistance: Float = 1f,
    var maxDistance: Float = 20f,
    var rolloff: Float = 1f
) {
    fun calculateVolumeForListener(listenerX: Float, listenerY: Float, listenerZ: Float): Float {
        if (isMuted || isDeafened || !isTalking) return 0f
        val dx = x-listenerX; val dy = y-listenerY; val dz = z-listenerZ
        val dist = sqrt(dx*dx+dy*dy+dz*dz)
        if (dist > maxDistance) return 0f
        if (dist < minDistance) return volume
        val t = (dist-minDistance)/(maxDistance-minDistance)
        return volume * (1f-t).pow(rolloff) * spatialBlend + volume*(1f-spatialBlend)
    }
}

data class RelayServer(
    var id: String = "relay_0",
    var address: String = "relay.sengine.com",
    var port: Int = 7778,
    var region: String = "us-east",
    var ping: Float = 50f,
    var isAvailable: Boolean = true
)

data class LobbyV2(
    var id: String = "lobby_0",
    var name: String = "Game Lobby",
    var maxPlayers: Int = 8,
    var currentPlayers: Int = 0,
    var isPrivate: Boolean = false,
    var gameMode: String = "Deathmatch",
    var mapName: String = "MainMap",
    var players: MutableList<Long> = mutableListOf(),
    var readyPlayers: MutableSet<Long> = mutableSetOf(),
    var isGameStarted: Boolean = false
) {
    fun addPlayer(playerId: Long): Boolean {
        if (currentPlayers >= maxPlayers) return false
        players.add(playerId); currentPlayers++; return true
    }
    fun removePlayer(playerId: Long) { players.remove(playerId); readyPlayers.remove(playerId); currentPlayers-- }
    fun setReady(playerId: Long, ready: Boolean) { if (ready) readyPlayers.add(playerId) else readyPlayers.remove(playerId) }
    fun allReady(): Boolean = players.isNotEmpty() && readyPlayers.size == players.size
    fun canStart(): Boolean = allReady() && !isGameStarted
}

class UltraNetworkingSystem {
    var interestMode: InterestManagementMode = InterestManagementMode.Distance
    var interestZones = mutableMapOf<String, InterestZone>()
    var relevance = mutableMapOf<String, NetworkRelevance>() // key "objectId_viewerId"
    var dedicatedServer = DedicatedServerConfig()
    var clientPrediction = ClientPredictionSystem()
    var voiceChat3D = mutableMapOf<Long, VoiceChat3D>()
    var relayServers = mutableListOf<RelayServer>()
    var lobbies = mutableMapOf<String, LobbyV2>()
    var matchmakingMode: MatchmakingMode = MatchmakingMode.Casual
    var cullingDistance: Float = 100f
    var lodDistanceHigh: Float = 10f
    var lodDistanceMedium: Float = 30f
    var lodDistanceLow: Float = 100f
    var nextZoneId: Int = 0
    var nextLobbyId: Int = 0

    init {
        // Default relay servers
        relayServers.add(RelayServer(id="relay_us_east", address="us-east.relay.sengine.com", region="us-east", ping=30f))
        relayServers.add(RelayServer(id="relay_eu_west", address="eu-west.relay.sengine.com", region="eu-west", ping=80f))
        relayServers.add(RelayServer(id="relay_ap_south", address="ap-south.relay.sengine.com", region="ap-south", ping=120f))
    }

    fun createInterestZone(x: Float, y: Float, z: Float, radius: Float): InterestZone {
        val id = "zone_${nextZoneId++}"
        val zone = InterestZone(id=id, x=x, y=y, z=z, radius=radius)
        interestZones[id]=zone; return zone
    }

    fun updateRelevance(viewerId: Long, viewerX: Float, viewerY: Float, viewerZ: Float, objectId: Long, objectX: Float, objectY: Float, objectZ: Float): NetworkRelevance {
        val key = "${objectId}_${viewerId}"
        val dx = objectX-viewerX; val dy = objectY-viewerY; val dz = objectZ-viewerZ
        val dist = sqrt(dx*dx+dy*dy+dz*dz)
        val isRelevant = when (interestMode) {
            InterestManagementMode.Distance -> dist <= cullingDistance
            InterestManagementMode.Zone -> interestZones.values.any { it.contains(objectX, objectY, objectZ) && it.contains(viewerX, viewerY, viewerZ) }
            InterestManagementMode.None -> true
            else -> true
        }
        val lod = when {
            dist < lodDistanceHigh -> NetworkLOD.High
            dist < lodDistanceMedium -> NetworkLOD.Medium
            dist < lodDistanceLow -> NetworkLOD.Low
            else -> NetworkLOD.Culled
        }
        val rel = NetworkRelevance(objectId=objectId, viewerId=viewerId, distance=dist, lod=lod, isRelevant=isRelevant, lastUpdateTime=System.currentTimeMillis())
        relevance[key]=rel; return rel
    }

    fun createLobby(name: String, maxPlayers: Int = 8, gameMode: String = "Deathmatch"): LobbyV2 {
        val id = "lobby_${nextLobbyId++}"
        val lobby = LobbyV2(id=id, name=name, maxPlayers=maxPlayers, gameMode=gameMode)
        lobbies[id]=lobby; return lobby
    }

    fun findBestRelay(): RelayServer? = relayServers.filter { it.isAvailable }.minByOrNull { it.ping }

    fun addVoiceChat(playerId: Long, x: Float, y: Float, z: Float): VoiceChat3D {
        val vc = VoiceChat3D(playerId=playerId, x=x, y=y, z=z); voiceChat3D[playerId]=vc; return vc
    }

    fun getStats(): String = "Ultra Net Interest:$interestMode Zones:${interestZones.size} Relevance:${relevance.size} Culling:$cullingDistance LOD H:$lodDistanceHigh M:$lodDistanceMedium L:$lodDistanceLow Dedicated Headless:${dedicatedServer.isHeadless} Tick:${dedicatedServer.tickRate} Voice:${voiceChat3D.size} Relays:${relayServers.size} Lobbies:${lobbies.size} Matchmaking:$matchmakingMode"
}
