package io.github.sriharinmn.triage.data

import io.github.sriharinmn.triage.remote.rateLimitHoldSeconds
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds every Sentry call after a 429 until its `Retry-After` passes. Sentry limits the token, not one screen, so the
 * inbox and the issue detail share one gate. Updated atomically because loads run concurrently.
 */
class RateLimitGate(private val clock: () -> Instant = Instant::now) {
  private val heldUntil = AtomicReference<Instant?>(null)

  /** Whole seconds (rounded up) until calls may go out again, or null if they may go out now. */
  fun remainingSeconds(): Long? {
    val until = heldUntil.get() ?: return null
    val remainingMillis = Duration.between(clock(), until).toMillis()
    if (remainingMillis <= 0) {
      // Clear only the hold we read, so a newer 429 that landed meanwhile isn't erased.
      heldUntil.compareAndSet(until, null)
      return null
    }
    return (remainingMillis + 999) / 1000
  }

  /**
   * Starts holding after a 429 that said [retryAfterSeconds], bounded by [rateLimitHoldSeconds], and returns the seconds
   * held. Concurrent 429s never shorten an existing hold: the later instant wins.
   */
  fun hold(retryAfterSeconds: Long?): Long {
    val seconds = rateLimitHoldSeconds(retryAfterSeconds)
    val candidate = clock().plusSeconds(seconds)
    heldUntil.updateAndGet { current -> if (current == null || candidate > current) candidate else current }
    return seconds
  }

  /** Lifts any hold; used when the account's data is forgotten. */
  fun clear() {
    heldUntil.set(null)
  }
}
