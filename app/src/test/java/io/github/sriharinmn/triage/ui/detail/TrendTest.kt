package io.github.sriharinmn.triage.ui.detail

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendTest {
  private val now = Instant.parse("2026-10-22T12:34:56Z")

  @Test
  fun noEvents_haveNoBusiestBar() {
    assertEquals(TrendSummary(total = 0, busiestStart = null), trendSummary(List(24) { 0L }, TrendPeriod.Last24Hours, now))
    assertEquals(TrendSummary(total = 0, busiestStart = null), trendSummary(emptyList(), TrendPeriod.Last24Hours, now))
  }

  @Test
  fun theBusiestHour_isCountedBackFromTheCurrentHour() {
    val counts = List(24) { if (it == 21) 9L else 1L }

    // The last bar is the 12:00 hour; bar 21 is two hours before it.
    assertEquals(TrendSummary(total = 32, busiestStart = Instant.parse("2026-10-22T10:00:00Z")), trendSummary(counts, TrendPeriod.Last24Hours, now))
  }

  @Test
  fun aTie_goesToTheMostRecentBar() {
    val counts = List(24) { if (it == 5 || it == 20) 4L else 0L }

    assertEquals(Instant.parse("2026-10-22T09:00:00Z"), trendSummary(counts, TrendPeriod.Last24Hours, now).busiestStart)
  }

  @Test
  fun days_areUtcDays_countedBackFromToday() {
    val counts = List(30) { if (it == 27) 50L else 10L }

    assertEquals(TrendSummary(total = 340, busiestStart = Instant.parse("2026-10-20T00:00:00Z")), trendSummary(counts, TrendPeriod.Last30Days, now))
  }
}
