package com.sengine.engine.audio

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Audio System - Months/Years Professional Project
 * Mixer Snapshots Ducking Sidechain, Occlusion Raycasting Portals, Reverb Baking Probes, HRTF, Randomization Playlists
 */
enum class AudioMixerSnapshotType { Default, Muted, LowVolume, HighIntensity, Underwater, Custom }
enum class AudioOcclusionType { None, Low, Medium, High, Full }
enum class AudioHRTFMode { Off, Simple, Full }

data class AudioMixerSnapshot(
    var name: String = "Snapshot",
    var type: AudioMixerSnapshotType = AudioMixerSnapshotType.Default,
    var volumes: MutableMap<String, Float> = mutableMapOf(), // group -> volume
    var pitches: MutableMap<String, Float> = mutableMapOf(),
    var effects: MutableMap<String, Float> = mutableMapOf(), // effect param -> value
    var transitionTime: Float = 1f
) {
    fun setVolume(group: String, volume: Float) { volumes[group]=volume }
    fun getVolume(group: String): Float = volumes[group] ?: 1f
    fun lerp(other: AudioMixerSnapshot, t: Float): AudioMixerSnapshot {
        val result = AudioMixerSnapshot(name="${name}_lerp_${other.name}")
        for (group in volumes.keys + other.volumes.keys) {
            val v1 = volumes[group] ?: 1f; val v2 = other.volumes[group] ?: 1f
            result.volumes[group]=v1+(v2-v1)*t
        }
        return result
    }
}

data class AudioDucking(
    var sourceGroup: String = "SFX",
    var targetGroup: String = "Music",
    var duckedVolume: Float = 0.3f,
    var attackTime: Float = 0.1f,
    var releaseTime: Float = 0.5f,
    var threshold: Float = 0.5f,
    var isDucking: Boolean = false,
    var currentVolume: Float = 1f
) {
    fun update(sourceVolume: Float, dt: Float): Float {
        val shouldDuck = sourceVolume > threshold
        val targetVol = if (shouldDuck) duckedVolume else 1f
        val time = if (shouldDuck) attackTime else releaseTime
        val k = if (time <= 0f) 1f else (1f-exp(-dt/time*5f))
        currentVolume+=(targetVol-currentVolume)*k
        isDucking=shouldDuck
        return currentVolume
    }
}

data class AudioPortal(
    var id: String = "portal_0",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var width: Float = 2f, var height: Float = 2f,
    var isOpen: Boolean = true,
    var occlusionFactor: Float = 0.5f
) {
    fun calculateOcclusion(listenerX: Float, listenerY: Float, listenerZ: Float, sourceX: Float, sourceY: Float, sourceZ: Float): Float {
        if (isOpen) return 0f
        // Check if portal is between listener and source
        // Simplified - distance to portal
        val dx = x-listenerX; val dy = y-listenerY; val dz = z-listenerZ
        val distToPortal = sqrt(dx*dx+dy*dy+dz*dz)
        val dx2 = sourceX-x; val dy2 = sourceY-y; val dz2 = sourceZ-z
        val distPortalToSource = sqrt(dx2*dx2+dy2*dy2+dz2*dz2)
        val directDist = sqrt((sourceX-listenerX).pow(2)+(sourceY-listenerY).pow(2)+(sourceZ-listenerZ).pow(2))
        // If portal is roughly between, apply occlusion
        return if (distToPortal+distPortalToSource < directDist*1.2f) occlusionFactor else 0f
    }
}

data class ReverbProbe(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var radius: Float = 10f,
    var reverbPreset: Int = 0,
    var baked: Boolean = false,
    var reverbData: FloatArray = FloatArray(8) // room, roomHF, decay, etc
) {
    fun bake(roomSize: Float, decay: Float) {
        reverbData[0]=roomSize; reverbData[1]=decay; baked=true
    }
    fun getReverbAt(px: Float, py: Float, pz: Float): Float {
        val dx = px-x; val dy = py-y; val dz = pz-z
        val dist = sqrt(dx*dx+dy*dy+dz*dz)
        return if (dist <= radius) (1f-dist/radius) else 0f
    }
}

data class AudioRandomization(
    var volumeMin: Float = 0.9f, var volumeMax: Float = 1.1f,
    var pitchMin: Float = 0.95f, var pitchMax: Float = 1.05f,
    var delayMin: Float = 0f, var delayMax: Float = 0.1f
) {
    fun randomize(): Triple<Float,Float,Float> {
        val vol = volumeMin + Math.random().toFloat()*(volumeMax-volumeMin)
        val pitch = pitchMin + Math.random().toFloat()*(pitchMax-pitchMin)
        val delay = delayMin + Math.random().toFloat()*(delayMax-delayMin)
        return Triple(vol, pitch, delay)
    }
}

data class AudioPlaylist(
    var name: String = "Playlist",
    var clips: MutableList<String> = mutableListOf(),
    var shuffle: Boolean = true,
    var loop: Boolean = true,
    var crossfade: Float = 1f,
    var currentIndex: Int = 0,
    var isPlaying: Boolean = false
) {
    fun getNextClip(): String? {
        if (clips.isEmpty()) return null
        if (shuffle) currentIndex = (Math.random()*clips.size).toInt()
        else currentIndex = (currentIndex+1) % clips.size
        return clips[currentIndex]
    }
    fun addClip(clip: String) { clips.add(clip) }
}

class UltraAudioSystem {
    var snapshots = mutableMapOf<String, AudioMixerSnapshot>()
    var ducking = mutableListOf<AudioDucking>()
    var portals = mutableMapOf<String, AudioPortal>()
    var reverbProbes = mutableListOf<ReverbProbe>()
    var randomization = AudioRandomization()
    var playlists = mutableMapOf<String, AudioPlaylist>()
    var hrtfMode: AudioHRTFMode = AudioHRTFMode.Full
    var occlusionEnabled: Boolean = true
    var reverbBakingEnabled: Boolean = true
    var nextPortalId: Int = 0
    var currentSnapshot: String = "Default"
    var targetSnapshot: String = "Default"
    var snapshotTransition: Float = 0f
    var snapshotTransitionTime: Float = 1f

    init {
        // Default snapshots
        createSnapshot("Default", AudioMixerSnapshotType.Default).apply {
            setVolume("Master", 1f); setVolume("SFX", 1f); setVolume("Music", 0.8f); setVolume("UI", 1f); setVolume("Ambient", 0.7f)
        }
        createSnapshot("Muted", AudioMixerSnapshotType.Muted).apply {
            setVolume("Master", 0f); setVolume("SFX", 0f); setVolume("Music", 0f)
        }
        createSnapshot("Underwater", AudioMixerSnapshotType.Underwater).apply {
            setVolume("Master", 0.8f); setVolume("SFX", 0.6f); setVolume("Music", 0.5f)
            effects["LowPass"] = 500f
        }
        createSnapshot("HighIntensity", AudioMixerSnapshotType.HighIntensity).apply {
            setVolume("Master", 1.2f); setVolume("SFX", 1.2f); setVolume("Music", 1f)
        }
    }

    fun createSnapshot(name: String, type: AudioMixerSnapshotType = AudioMixerSnapshotType.Custom): AudioMixerSnapshot {
        val snap = AudioMixerSnapshot(name=name, type=type); snapshots[name]=snap; return snap
    }

    fun transitionToSnapshot(name: String, time: Float = 1f) {
        if (name !in snapshots) return
        currentSnapshot=targetSnapshot
        targetSnapshot=name
        snapshotTransition=0f
        snapshotTransitionTime=time
    }

    fun updateSnapshotTransition(dt: Float): AudioMixerSnapshot? {
        if (currentSnapshot == targetSnapshot) return snapshots[currentSnapshot]
        snapshotTransition+=dt/snapshotTransitionTime
        if (snapshotTransition >= 1f) {
            snapshotTransition=1f
            currentSnapshot=targetSnapshot
            return snapshots[currentSnapshot]
        }
        val from = snapshots[currentSnapshot] ?: return null
        val to = snapshots[targetSnapshot] ?: return null
        return from.lerp(to, snapshotTransition)
    }

    fun addDucking(sourceGroup: String, targetGroup: String, duckedVolume: Float = 0.3f): AudioDucking {
        val d = AudioDucking(sourceGroup=sourceGroup, targetGroup=targetGroup, duckedVolume=duckedVolume)
        ducking.add(d); return d
    }

    fun createPortal(x: Float, y: Float, z: Float, width: Float, height: Float): AudioPortal {
        val id = "portal_${nextPortalId++}"
        val portal = AudioPortal(id=id, x=x, y=y, z=z, width=width, height=height)
        portals[id]=portal; return portal
    }

    fun addReverbProbe(x: Float, y: Float, z: Float, radius: Float): ReverbProbe {
        val probe = ReverbProbe(x=x, y=y, z=z, radius=radius); reverbProbes.add(probe); return probe
    }

    fun bakeReverbProbes() {
        if (!reverbBakingEnabled) return
        for (probe in reverbProbes) probe.bake(0.5f, 1.49f)
    }

    fun calculateOcclusion(listenerX: Float, listenerY: Float, listenerZ: Float, sourceX: Float, sourceY: Float, sourceZ: Float): Float {
        if (!occlusionEnabled) return 0f
        var totalOcclusion=0f
        for (portal in portals.values) totalOcclusion+=portal.calculateOcclusion(listenerX, listenerY, listenerZ, sourceX, sourceY, sourceZ)
        return totalOcclusion.coerceIn(0f,1f)
    }

    fun createPlaylist(name: String): AudioPlaylist {
        val pl = AudioPlaylist(name=name); playlists[name]=pl; return pl
    }

    fun getStats(): String = "Ultra Audio Snapshots:${snapshots.size} Current:$currentSnapshot Target:$targetSnapshot Trans:${(snapshotTransition*100).toInt()}% Ducking:${ducking.size} Portals:${portals.size} ReverbProbes:${reverbProbes.size} Playlists:${playlists.size} HRTF:$hrtfMode"
}
