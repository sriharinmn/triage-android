package io.github.sriharinmn.triage.ui.inbox

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatTierTest {
  private val now = Instant.parse("2026-11-12T12:00:00Z")

  /** 24 hourly counts: [earlier] events in the first hour, nothing until the last three hours, which hold [lastThree]. */
  private fun day(vararg lastThree: Long, earlier: Long = 0): List<Long> =
    List(24 - RECENT_BUCKETS) { if (it == 0) earlier else 0L } + lastThree.toList()

  @Test
  fun eventsInTheLastThreeHours_againstAQuietDay_areHot() {
    assertEquals(HeatTier.Hot, heatTier(day(0, 1, 1), lastSeen = null, now = now))
  }

  @Test
  fun hotNeedsTheLastThreeHoursToBeMoreThanAnEighthOfTheDay() {
    // recent 2, total 15: 16 > 15.
    assertEquals(HeatTier.Hot, heatTier(day(1, 0, 1, earlier = 13), lastSeen = null, now = now))
    // recent 2, total 16: 16 is not more than 16.
    assertEquals(HeatTier.Warm, heatTier(day(1, 0, 1, earlier = 14), lastSeen = null, now = now))
  }

  @Test
  fun oneRecentEvent_isNotASpike() {
    assertEquals(HeatTier.Warm, heatTier(day(0, 0, 1), lastSeen = null, now = now))
  }

  @Test
  fun aSteadyIssue_isWarm_notHot() {
    // Three hours of a steady day are exactly an eighth of it.
    assertEquals(HeatTier.Warm, heatTier(List(24) { 1L }, lastSeen = null, now = now))
  }

  @Test
  fun noEventsAllDay_isQuiet() {
    assertEquals(HeatTier.Quiet, heatTier(List(24) { 0L }, lastSeen = now, now = now))
  }

  @Test
  fun withoutStats_aLastSeenWithin24Hours_isWarm() {
    assertEquals(HeatTier.Warm, heatTier(emptyList(), lastSeen = now - Duration.ofHours(23), now = now))
    assertEquals(HeatTier.Warm, heatTier(emptyList(), lastSeen = now - Duration.ofHours(24), now = now))
    // Clock skew can put lastSeen slightly ahead of the phone's clock.
    assertEquals(HeatTier.Warm, heatTier(emptyList(), lastSeen = now.plusSeconds(30), now = now))
  }

  @Test
  fun withoutStats_anOlderOrUnknownLastSeen_isQuiet() {
    assertEquals(HeatTier.Quiet, heatTier(emptyList(), lastSeen = now - Duration.ofHours(25), now = now))
    assertEquals(HeatTier.Quiet, heatTier(emptyList(), lastSeen = null, now = now))
  }
}
