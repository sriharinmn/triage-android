package io.github.sriharinmn.triage.ui.format

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
  private val now = Instant.parse("2026-10-22T12:00:00Z")

  private fun ago(duration: Duration) = elapsedSince(now - duration, now)

  @Test
  fun elapsed_roundsDownToTheLargestWholeUnit() {
    assertEquals(ElapsedSpan(SpanUnit.UnderAMinute, 0), ago(Duration.ofSeconds(59)))
    assertEquals(ElapsedSpan(SpanUnit.Minutes, 1), ago(Duration.ofSeconds(60)))
    assertEquals(ElapsedSpan(SpanUnit.Minutes, 59), ago(Duration.ofMinutes(59)))
    assertEquals(ElapsedSpan(SpanUnit.Hours, 1), ago(Duration.ofMinutes(90)))
    assertEquals(ElapsedSpan(SpanUnit.Hours, 23), ago(Duration.ofHours(23)))
    assertEquals(ElapsedSpan(SpanUnit.Days, 1), ago(Duration.ofHours(24)))
    assertEquals(ElapsedSpan(SpanUnit.Days, 6), ago(Duration.ofDays(6)))
    assertEquals(ElapsedSpan(SpanUnit.Weeks, 1), ago(Duration.ofDays(7)))
    assertEquals(ElapsedSpan(SpanUnit.Weeks, 4), ago(Duration.ofDays(29)))
    assertEquals(ElapsedSpan(SpanUnit.Months, 1), ago(Duration.ofDays(30)))
    assertEquals(ElapsedSpan(SpanUnit.Months, 12), ago(Duration.ofDays(364)))
    assertEquals(ElapsedSpan(SpanUnit.Years, 2), ago(Duration.ofDays(800)))
  }

  @Test
  fun elapsed_inTheFuture_isUnderAMinute() {
    // Clock skew between phone and server can put lastSeen slightly ahead of "now".
    assertEquals(ElapsedSpan(SpanUnit.UnderAMinute, 0), elapsedSince(now.plusSeconds(30), now))
  }

  @Test
  fun compactCount_staysShortAndNeverOverstates() {
    assertEquals("0", compactCount(0))
    assertEquals("999", compactCount(999))
    assertEquals("1k", compactCount(1_000))
    assertEquals("1.2k", compactCount(1_250))
    assertEquals("1.9k", compactCount(1_999))
    assertEquals("12k", compactCount(12_345))
    assertEquals("999k", compactCount(999_999))
    assertEquals("1M", compactCount(1_000_000))
    assertEquals("1.5M", compactCount(1_520_000))
    assertEquals("15M", compactCount(15_000_000))
    assertEquals("0", compactCount(-5))
  }
}
