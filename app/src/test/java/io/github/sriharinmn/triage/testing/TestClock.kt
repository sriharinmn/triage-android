package io.github.sriharinmn.triage.testing

import java.time.Duration
import java.time.Instant

/** A clock tests move by hand. Pass `clock::now` where code wants `() -> Instant`. */
class TestClock(var current: Instant = Instant.parse("2026-10-22T12:00:00Z")) {
  fun now(): Instant = current

  fun advance(duration: Duration) {
    current += duration
  }
}
