package io.github.sriharinmn.triage.data.demo

/**
 * SplitMix64's finalizer: spreads any Long into a well-mixed one. The demo's "randomness" is all derived from it, so the
 * same demo state always looks the same.
 */
internal fun mix(value: Long): Long {
  var z = value + -0x61c8864680b583ebL
  z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
  z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
  return z xor (z ushr 31)
}

/** A number in [0, 1) fixed by [seed] and [n]. */
internal fun unit(seed: Long, n: Long): Double = (mix(seed * 1_000_003L + n) ushr 11).toDouble() / (1L shl 53).toDouble()

/** A seed for [text]. `String.hashCode` is specified by Java, so it is the same on every run and every phone. */
internal fun seedOf(text: String): Long = mix(text.hashCode().toLong())
