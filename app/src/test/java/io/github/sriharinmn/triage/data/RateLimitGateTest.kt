package io.github.sriharinmn.triage.data

import io.github.sriharinmn.triage.testing.TestClock
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateLimitGateTest {
  private val clock = TestClock()
  private val gate = RateLimitGate(clock::now)

  @Test
  fun withoutAHold_callsMayGoOut() {
    assertNull(gate.remainingSeconds())
  }

  @Test
  fun aHold_countsDownInWholeSecondsRoundedUp() {
    assertEquals(30L, gate.hold(30))
    assertEquals(30L, gate.remainingSeconds())

    clock.advance(Duration.ofMillis(29_500))
    assertEquals(1L, gate.remainingSeconds())

    clock.advance(Duration.ofMillis(500))
    assertNull(gate.remainingSeconds())
  }

  @Test
  fun holds_defaultToAMinuteAndStayBetweenOneSecondAndAnHour() {
    assertEquals(60L, gate.hold(null))
    gate.clear()
    assertEquals(1L, gate.hold(0))
    gate.clear()
    assertEquals(3600L, gate.hold(86_400))
  }

  @Test
  fun aShorterHold_neverShortensALongerOne() {
    gate.hold(600)
    gate.hold(5)

    assertEquals(600L, gate.remainingSeconds())
  }

  @Test
  fun clear_liftsTheHold() {
    gate.hold(600)

    gate.clear()

    assertNull(gate.remainingSeconds())
  }
}
