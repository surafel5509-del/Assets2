package com.sengine.engine.audio

import com.sengine.engine.core.GameObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Ultimate Edition Audio System - Unity Audio Mixer + Unreal MetaSounds + Wwise-like
 * 3D spatial audio, occlusion, reverb zones, mixer groups, effects
 */

enum class AudioRolloffMode { Logarithmic, Linear, Custom }

data class AudioMixerGroup(var name: String = "Master") {
    var volume: Float = 1f
    var pitch: Float = 1f
    var parent: AudioMixerGroup? = null
    val children = mutableListOf<AudioMixerGroup>()
    val effects = mutableListOf<AudioEffect>()

    fun getEffectiveVolume(): Float {
        var v = volume
        var p = parent
        while (p != null) { v *= p.volume; p = p.parent }
        return v
    }

    fun addChild(group: AudioMixerGroup) { group.parent = this; children.add(group) }
}

abstract class AudioEffect(var name: String) {
    var enabled: Boolean = true
    var mix: Float = 1f
    abstract fun process(samples: FloatArray, channels: Int)
}

class LowPassEffect : AudioEffect("LowPass") {
    var cutoff: Float = 5000f
    var resonance: Float = 1f
    override fun process(samples: FloatArray, channels: Int) {
        // Simple one-pole lowpass placeholder
        var prev = 0f
        val rc = 1f / (1f + 2f * 3.14159f * cutoff / 44100f)
        for (i in samples.indices) {
            prev = prev + rc * (samples[i] - prev)
            samples[i] = prev * mix + samples[i] * (1 - mix)
        }
    }
}

class ReverbEffect : AudioEffect("Reverb") {
    var decay: Float = 1f
    var roomSize: Float = 0.5f
    var damping: Float = 0.5f
    override fun process(samples: FloatArray, channels: Int) {
        // Placeholder - would use comb/allpass filters
    }
}

class EchoEffect : AudioEffect("Echo") {
    var delay: Float = 0.5f
    var decayRatio: Float = 0.5f
    override fun process(samples: FloatArray, channels: Int) {
        val delaySamples = (delay * 44100).toInt()
        if (delaySamples <= 0 || delaySamples >= samples.size) return
        for (i in delaySamples until samples.size) {
            samples[i] += samples[i - delaySamples] * decayRatio * mix
        }
    }
}

class AudioMixer {
    val masterGroup = AudioMixerGroup("Master")
    val groups = mutableMapOf<String, AudioMixerGroup>()

    init {
        groups["Master"] = masterGroup
        val sfx = AudioMixerGroup("SFX")
        val music = AudioMixerGroup("Music")
        val ui = AudioMixerGroup("UI")
        val ambient = AudioMixerGroup("Ambient")
        masterGroup.addChild(sfx)
        masterGroup.addChild(music)
        masterGroup.addChild(ui)
        masterGroup.addChild(ambient)
        groups["SFX"] = sfx
        groups["Music"] = music
        groups["UI"] = ui
        groups["Ambient"] = ambient
    }

    fun findGroup(name: String): AudioMixerGroup? = groups[name]
    fun createGroup(name: String, parent: String = "Master"): AudioMixerGroup {
        val group = AudioMixerGroup(name)
        val p = groups[parent] ?: masterGroup
        p.addChild(group)
        groups[name] = group
        return group
    }
}

data class AudioClip(
    var name: String = "",
    var length: Float = 1f,
    var channels: Int = 1,
    var frequency: Int = 44100,
    var is3D: Boolean = false,
    var loadType: Int = 0 // 0 decompress on load, 1 compressed in memory, 2 streaming
)

class AudioSourcePro {
    var clip: AudioClip? = null
    var volume: Float = 1f
    var pitch: Float = 1f
    var loop: Boolean = false
    var is3D: Boolean = true
    var minDistance: Float = 1f
    var maxDistance: Float = 50f
    var rolloffMode: AudioRolloffMode = AudioRolloffMode.Logarithmic
    var spatialBlend: Float = 1f // 0 = 2D, 1 = 3D
    var dopplerLevel: Float = 1f
    var spread: Float = 0f
    var bypassEffects: Boolean = false
    var bypassReverbZones: Boolean = false
    var priority: Int = 128
    var mixerGroup: String = "SFX"
    var mute: Boolean = false

    // 3D
    var pan2D: Float = 0f

    fun calculateVolumeAndPan(listenerPos: FloatArray, sourcePos: FloatArray): Pair<Float, Float> {
        if (!is3D || spatialBlend < 0.01f) return volume to pan2D

        val dx = sourcePos[0] - listenerPos[0]
        val dy = sourcePos[1] - listenerPos[1]
        val dz = sourcePos[2] - listenerPos[2]
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        val atten = when (rolloffMode) {
            AudioRolloffMode.Logarithmic -> {
                if (distance <= minDistance) 1f else minDistance / max(minDistance, distance)
            }
            AudioRolloffMode.Linear -> {
                if (distance <= minDistance) 1f else 1f - ((distance - minDistance) / (maxDistance - minDistance)).coerceIn(0f, 1f)
            }
            AudioRolloffMode.Custom -> {
                // custom curve placeholder
                1f / (1f + distance * 0.1f)
            }
        }

        // Simple stereo pan based on X
        val pan = (dx / maxDistance).coerceIn(-1f, 1f)

        return (volume * atten * spatialBlend + volume * (1 - spatialBlend)) to pan
    }
}

class AudioReverbZone {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var minDistance: Float = 10f
    var maxDistance: Float = 15f
    var reverbPreset: Int = 0
    var room: Int = -1000
    var roomHF: Int = -100
    var decayTime: Float = 1.49f
    var decayHFRatio: Float = 0.83f
    var reflections: Int = -2602
    var reflectDelay: Float = 0.007f
    var reverb: Int = 200
    var reverbDelay: Float = 0.011f
    var diffusion: Float = 100f
    var density: Float = 100f
    var hfReference: Float = 5000f
    var roomLF: Int = 0
    var lfReference: Float = 250f

    fun getReverbAmount(pos: FloatArray): Float {
        val dx = pos[0] - x; val dy = pos[1] - y; val dz = pos[2] - z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        return when {
            dist <= minDistance -> 1f
            dist >= maxDistance -> 0f
            else -> 1f - (dist - minDistance) / (maxDistance - minDistance)
        }
    }
}

class AudioOcclusionSystem {
    data class OcclusionResult(var directOcclusion: Float = 0f, var reverbOcclusion: Float = 0f)

    fun calculateOcclusion(listenerPos: FloatArray, sourcePos: FloatArray, scene: com.sengine.engine.core.Scene): OcclusionResult {
        // Raycast for occlusion - simplified
        var occlusion = 0f
        // Count colliders between listener and source
        val dirX = sourcePos[0] - listenerPos[0]
        val dirY = sourcePos[1] - listenerPos[1]
        val dirZ = sourcePos[2] - listenerPos[2]
        val dist = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        if (dist > 0.01f) {
            // Placeholder: check if any collider is in between
            // In real engine would raycast against physics world
            occlusion = 0f
        }
        return OcclusionResult(occlusion, occlusion * 0.5f)
    }
}

class AudioSystemPro {
    val mixer = AudioMixer()
    val reverbZones = mutableListOf<AudioReverbZone>()
    val occlusionSystem = AudioOcclusionSystem()
    var listenerPos: FloatArray = floatArrayOf(0f, 0f, 0f)
    var listenerForward: FloatArray = floatArrayOf(0f, 0f, 1f)
    var listenerUp: FloatArray = floatArrayOf(0f, 1f, 0f)
    var dopplerFactor: Float = 1f
    var speedOfSound: Float = 343f

    fun updateListener(go: GameObject) {
        listenerPos = floatArrayOf(go.world3[12], go.world3[13], go.world3[14])
        val forward = com.sengine.engine.math.Mat4.dir(go.world3, 0f, 0f, 1f)
        listenerForward = forward
    }

    fun addReverbZone(zone: AudioReverbZone) { reverbZones.add(zone) }

    fun getReverbForPosition(pos: FloatArray): Float {
        var maxReverb = 0f
        for (zone in reverbZones) maxReverb = max(maxReverb, zone.getReverbAmount(pos))
        return maxReverb
    }
}
