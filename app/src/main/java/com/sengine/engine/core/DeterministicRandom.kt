package com.sengine.engine.core

/** xorshift32 PRNG. Same seed + same inputs = same simulation, on every device. */
class DeterministicRandom(seed: Int = 1337) {
    var seed: Int = seed
        private set
    private var state: Int = normalize(seed)

    fun reset(s: Int = seed) {
        seed = s
        state = normalize(s)
    }

    fun nextInt(): Int {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return x
    }

    /** Uniform float in [0,1). */
    fun nextFloat(): Float = (nextInt() ushr 8) / 16777216f

    fun nextDouble(): Double = (nextInt() ushr 8).toDouble() / 16777216.0

    /** Inclusive range. */
    fun nextInt(from: Int, to: Int): Int {
        if (to <= from) return from
        return from + ((nextInt().toLong() and 0xFFFFFFFFL) % (to - from + 1L)).toInt()
    }

    fun fork(salt: Int): DeterministicRandom = DeterministicRandom(seed xor (salt * 0x9E3779B9.toInt()))

    private fun normalize(s: Int): Int = if (s == 0) 0x6D2B79F5 else s
}
