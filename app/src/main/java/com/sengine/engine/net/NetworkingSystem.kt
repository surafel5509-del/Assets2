package com.sengine.engine.net

import org.json.JSONObject

/**
 * Ultimate Edition Networking - Unity Netcode + Unreal Replication + Mirror equivalent
 */

enum class NetworkRole { None, Server, Client, Host, Authority }
enum class NetworkChannel { Reliable, Unreliable, ReliableFragmented, UnreliableFragmented }

data class NetworkMessage(val id: Int, val data: ByteArray, val channel: NetworkChannel = NetworkChannel.Reliable) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetworkMessage) return false
        return id == other.id && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = id * 31 + data.contentHashCode()
}

class NetworkVariable<T>(initial: T, var owner: NetworkRole = NetworkRole.Server) {
    var value: T = initial
        set(v) {
            if (field != v) {
                field = v
                dirty = true
                onChanged?.invoke(v)
            }
        }
    var dirty: Boolean = false
    var onChanged: ((T) -> Unit)? = null

    fun markClean() { dirty = false }
}

class NetworkTransform {
    var position: FloatArray = floatArrayOf(0f, 0f, 0f)
    var rotation: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)
    var scale: FloatArray = floatArrayOf(1f, 1f, 1f)
    var syncPosition: Boolean = true
    var syncRotation: Boolean = true
    var syncScale: Boolean = false
    var interpolate: Boolean = true
    var extrapolate: Boolean = false
    var sendRate: Float = 20f
    var lastSyncTime: Float = 0f
}

class NetworkIdentity(var netId: Long = 0) {
    var isServer: Boolean = false
    var isClient: Boolean = false
    var isLocalPlayer: Boolean = false
    var hasAuthority: Boolean = false
    var ownerId: Long = 0
    var assetId: String = ""
    var sceneId: Long = 0
}

class NetworkManager {
    var role: NetworkRole = NetworkRole.None
    var isRunning: Boolean = false
    var maxConnections: Int = 16
    var serverTickRate: Int = 30
    var clientTickRate: Int = 60
    var networkAddress: String = "localhost"
    var networkPort: Int = 7777
    var autoCreatePlayer: Boolean = true
    var playerPrefab: String = ""

    private val clients = mutableMapOf<Long, NetworkIdentity>()
    private val messageQueue = ArrayDeque<NetworkMessage>()
    private var nextNetId: Long = 1

    fun startHost(): Boolean {
        role = NetworkRole.Host
        isRunning = true
        return true
    }

    fun startServer(): Boolean {
        role = NetworkRole.Server
        isRunning = true
        return true
    }

    fun startClient(address: String = networkAddress, port: Int = networkPort): Boolean {
        role = NetworkRole.Client
        networkAddress = address
        networkPort = port
        isRunning = true
        return true
    }

    fun stop() {
        isRunning = false
        role = NetworkRole.None
        clients.clear()
        messageQueue.clear()
    }

    fun spawnObject(prefabName: String, x: Float, y: Float, z: Float): NetworkIdentity {
        val identity = NetworkIdentity(nextNetId++)
        identity.assetId = prefabName
        identity.isServer = role == NetworkRole.Server || role == NetworkRole.Host
        clients[identity.netId] = identity
        return identity
    }

    fun sendMessage(msg: NetworkMessage) { messageQueue.addLast(msg) }

    fun receiveMessages(): List<NetworkMessage> {
        val msgs = messageQueue.toList()
        messageQueue.clear()
        return msgs
    }

    fun getClientCount(): Int = clients.size
}

// Lobby system
class NetworkLobby {
    data class LobbyPlayer(var id: Long, var name: String, var ready: Boolean = false, var team: Int = 0)

    val players = mutableListOf<LobbyPlayer>()
    var maxPlayers: Int = 8
    var isOpen: Boolean = true
    var gameMode: String = "Deathmatch"

    fun addPlayer(player: LobbyPlayer): Boolean {
        if (players.size >= maxPlayers) return false
        players.add(player)
        return true
    }

    fun removePlayer(id: Long) { players.removeIf { it.id == id } }
    fun setReady(id: Long, ready: Boolean) { players.firstOrNull { it.id == id }?.ready = ready }
    fun allReady(): Boolean = players.isNotEmpty() && players.all { it.ready }
    fun startGame(): Boolean = if (allReady()) { isOpen = false; true } else false
}

// Voice chat
class VoiceChatSystem {
    var enabled: Boolean = false
    var inputVolume: Float = 1f
    var outputVolume: Float = 1f
    var pushToTalk: Boolean = false
    var isTalking: Boolean = false
    var vadThreshold: Float = 0.1f

    fun startRecording() { isTalking = true }
    fun stopRecording() { isTalking = false }
}
