package io.github.sriharinmn.triage.data.demo

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTrafficTest {
  /** Midnight UTC, so hour and day boundaries are easy to reason about. */
  private val start = Instant.parse("2026-11-10T00:00:00Z")

  private fun hours(n: Long) = Duration.ofHours(n)

  private fun traffic(
    rate: Double = 3.0,
    firstSeen: Instant = start,
    extra: List<Instant> = emptyList(),
    quiet: List<QuietSpan> = emptyList(),
    bursts: List<Instant> = emptyList(),
  ) = IssueTraffic(seed = 42, ratePerHour = rate, firstSeen = firstSeen, extraEvents = extra, quiet = quiet, bursts = bursts)

  @Test
  fun theSameFacts_giveTheSameEvents() {
    val now = start + Duration.ofDays(3)
    assertEquals(traffic().summary(now), traffic().summary(now))
    assertEquals(traffic().hourlyBuckets(now), traffic().hourlyBuckets(now))
  }

  @Test
  fun theFirstEventIsAtFirstSeen_andNothingComesBeforeIt() {
    val firstSeen = start + Duration.ofMinutes(25)
    val quietIssue = traffic(rate = 0.0, firstSeen = firstSeen)

    assertEquals(TrafficSummary(count = 1, lastSeen = firstSeen), quietIssue.summary(start + hours(5)))
    assertTrue(traffic(rate = 20.0, firstSeen = firstSeen).eventTimesInHour(start).all { it >= firstSeen })
  }

  @Test
  fun countsOnlyGrow_andLastSeenNeverPassesNow() {
    val issue = traffic(rate = 4.0)
    var previous = 0L
    var now = start
    repeat(120) {
      now += Duration.ofMinutes(37)
      val summary = issue.summary(now)
      assertTrue(summary.count >= previous)
      assertTrue(summary.lastSeen <= now)
      previous = summary.count
    }
  }

  @Test
  fun theRateSetsRoughlyHowManyEventsComeADay() {
    // The day's shape averages 1, and floor(x + u) averages x, so a day has about 24 × rate events, plus first seen.
    val count = traffic(rate = 10.0).summary(start + Duration.ofDays(1) - Duration.ofMillis(1)).count
    assertTrue("$count", count in 200..280)
  }

  @Test
  fun middayUtc_isBusierThanNight() {
    val issue = traffic(rate = 10.0)
    var day = 0L
    var night = 0L
    for (d in 0L until 7L) {
      for (h in 12L..16L) day += issue.eventTimesInHour(start + Duration.ofDays(d) + hours(h)).size
      for (h in 0L..4L) night += issue.eventTimesInHour(start + Duration.ofDays(d) + hours(h)).size
    }
    assertTrue("day $day, night $night", day > night * 2)
  }

  @Test
  fun aQuietSpanHasNoEvents_andARegressionBringsThemBack() {
    val quiet = QuietSpan(start + Duration.ofDays(1), start + Duration.ofDays(2))
    val issue = traffic(rate = 5.0, quiet = listOf(quiet), extra = listOf(start + Duration.ofDays(2)))

    for (h in 24L until 48L) assertTrue(issue.eventTimesInHour(start + hours(h)).isEmpty())
    assertTrue(issue.eventTimesInHour(start + hours(48)).contains(start + Duration.ofDays(2)))
  }

  @Test
  fun anOpenQuietSpan_stopsEventsForGood() {
    val resolvedAt = start + Duration.ofDays(1)
    val issue = traffic(rate = 5.0, quiet = listOf(QuietSpan(resolvedAt, null)))

    val summary = issue.summary(start + Duration.ofDays(10))
    assertTrue(summary.lastSeen < resolvedAt)
    assertEquals(issue.summary(resolvedAt).count, summary.count)
  }

  @Test
  fun aBurst_adds40_25And15EventsInItsFirstThreeHours() {
    val now = start + Duration.ofDays(1)
    val calm = traffic(rate = 1.0).summary(now).count
    val burst = traffic(rate = 1.0, bursts = listOf(start + hours(5) + Duration.ofMinutes(10))).summary(now).count

    assertEquals(80, burst - calm)
  }

  @Test
  fun hourlyBuckets_are24_endingWithTheCurrentHour_andAddUpToThoseHours() {
    val issue = traffic()
    val now = start + hours(30) + Duration.ofMinutes(20)

    val buckets = issue.hourlyBuckets(now)

    assertEquals(24, buckets.size)
    assertEquals((start + hours(30)).epochSecond.toDouble(), buckets.last()[0], 0.0)
    assertEquals((start + hours(7)).epochSecond.toDouble(), buckets.first()[0], 0.0)
    val sinceFirstBucket = issue.summary(now).count - issue.summary(start + hours(7) - Duration.ofMillis(1)).count
    assertEquals(sinceFirstBucket.toDouble(), buckets.sumOf { it[1] }, 0.0)
  }

  @Test
  fun dailyBuckets_are30UtcDays_endingToday() {
    val issue = traffic(firstSeen = start - Duration.ofDays(40))
    val now = start + Duration.ofDays(5) + hours(9)

    val buckets = issue.dailyBuckets(now)

    assertEquals(30, buckets.size)
    assertEquals((start + Duration.ofDays(5)).epochSecond.toDouble(), buckets.last()[0], 0.0)
    assertEquals((start - Duration.ofDays(24)).epochSecond.toDouble(), buckets.first()[0], 0.0)
    val sinceFirstDay = issue.summary(now).count - issue.summary(start - Duration.ofDays(24) - Duration.ofMillis(1)).count
    assertEquals(sinceFirstDay.toDouble(), buckets.sumOf { it[1] }, 0.0)
  }

  @Test
  fun users_areAShareOfEvents_neverZero() {
    assertEquals(1, userCount(count = 0, share = 0.5))
    assertEquals(50, userCount(count = 100, share = 0.5))
    assertEquals(1, userCount(count = 3, share = 0.01))
  }
}
