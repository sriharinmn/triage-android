package io.github.sriharinmn.triage.data.demo

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin

/** A stretch with no events (the issue was resolved): from [from] up to [to], or for good while [to] is null. */
internal data class QuietSpan(val from: Instant, val to: Instant?) {
  operator fun contains(time: Instant): Boolean = time >= from && (to == null || time < to)

  fun overlaps(start: Instant, end: Instant): Boolean = from < end && (to == null || to > start)
}

/** How many events an issue has had by some moment, and when the latest was. */
internal data class TrafficSummary(val count: Long, val lastSeen: Instant)

/**
 * One demo issue's events, worked out from a few facts instead of stored: every answer is a pure function of these facts
 * and the moment asked about.
 *
 * An hour gets `floor(rate × shape + u)` events, where `shape` follows a UTC day (busiest at 14:00, quietest at 02:00)
 * and `u` comes from [seed] and the hour. A burst adds [BURST_EVENTS] in its first three hours. Events are spread evenly
 * through their hour; none happen before [firstSeen] or inside a quiet span. [firstSeen] and [extraEvents] (a
 * regression) are single events at exact moments.
 */
internal class IssueTraffic(
  private val seed: Long,
  private val ratePerHour: Double,
  private val firstSeen: Instant,
  extraEvents: List<Instant>,
  private val quiet: List<QuietSpan>,
  private val bursts: List<Instant>,
) {
  private val singles: List<Instant> = (extraEvents + firstSeen).distinct().sorted()
  private val firstHour: Instant = hourOf(firstSeen)

  /** This issue's events in the hour that starts at [hourStart], oldest first, whether or not they have happened yet. */
  fun eventTimesInHour(hourStart: Instant): List<Instant> {
    val hourEnd = hourStart + HOUR
    val generated = generatedInHour(hourStart)
    val spread = List(generated) { i -> hourStart.plusMillis((i + 1) * HOUR_MS / (generated + 1)) }
    return (spread + singles.filter { it >= hourStart && it < hourEnd })
      .filter { it >= firstSeen && quiet.none { span -> it in span } }
      .sorted()
  }

  /** The events up to [now]: at least one (first seen), and the latest of them. */
  fun summary(now: Instant): TrafficSummary {
    var count = 0L
    var hour = firstHour
    val nowHour = hourOf(now)
    while (hour <= nowHour) {
      count += countInHour(hour, now)
      hour += HOUR
    }
    return TrafficSummary(count = max(count, 1L), lastSeen = lastSeen(now))
  }

  /** Sentry's "24h" stats: 24 hourly `[unixSeconds, count]` buckets, oldest first, the last one being the current hour. */
  fun hourlyBuckets(now: Instant): List<List<Double>> {
    val nowHour = hourOf(now)
    return (HOURLY_BUCKETS - 1 downTo 0).map { back ->
      val hour = nowHour - HOUR.multipliedBy(back.toLong())
      listOf(hour.epochSecond.toDouble(), countInHour(hour, now).toDouble())
    }
  }

  /** Sentry's "30d" stats: 30 daily (UTC) `[unixSeconds, count]` buckets, oldest first, the last one being today. */
  fun dailyBuckets(now: Instant): List<List<Double>> {
    val today = now.truncatedTo(ChronoUnit.DAYS)
    return (DAILY_BUCKETS - 1 downTo 0).map { back ->
      val day = today - DAY.multipliedBy(back.toLong())
      val events = (0L until 24L).sumOf { h -> countInHour(day + HOUR.multipliedBy(h), now) }
      listOf(day.epochSecond.toDouble(), events.toDouble())
    }
  }

  /** The events of the hour starting at [hour] that happened by [now]. Whole plain hours are counted without listing them. */
  private fun countInHour(hour: Instant, now: Instant): Long {
    if (hour < firstHour) return 0
    val end = hour + HOUR
    val plain = hour > firstHour && end <= now && quiet.none { it.overlaps(hour, end) }
    return if (plain) {
      (generatedInHour(hour) + singles.count { it >= hour && it < end }).toLong()
    } else {
      eventTimesInHour(hour).count { it <= now }.toLong()
    }
  }

  private fun lastSeen(now: Instant): Instant {
    var hour = hourOf(now)
    while (hour >= firstHour) {
      eventTimesInHour(hour).lastOrNull { it <= now }?.let { return it }
      hour -= HOUR
    }
    return firstSeen
  }

  private fun generatedInHour(hour: Instant): Int {
    val index = hour.epochSecond / HOUR_SECONDS
    val hourOfDay = hour.atOffset(ZoneOffset.UTC).hour
    val shape = 1.0 + DAY_SWING * sin(2 * PI * (hourOfDay - 8) / 24.0)
    val base = floor(ratePerHour * shape + unit(seed, index)).toInt()
    val burst = bursts.sumOf { b -> BURST_EVENTS.getOrElse((index - hourOf(b).epochSecond / HOUR_SECONDS).toInt()) { 0 } }
    return base + burst
  }
}

/** The start of [time]'s hour. */
internal fun hourOf(time: Instant): Instant = time.truncatedTo(ChronoUnit.HOURS)

/** An issue's users: a share of its events, never 0. */
internal fun userCount(count: Long, share: Double): Long = max(1L, (count * share).roundToLong())

private val HOUR: Duration = Duration.ofHours(1)
private val DAY: Duration = Duration.ofDays(1)
private const val HOUR_SECONDS = 3_600L
private const val HOUR_MS = 3_600_000L
private const val HOURLY_BUCKETS = 24
private const val DAILY_BUCKETS = 30

/** How far the hourly rate swings around its average over a UTC day: 0.4× at 02:00, 1.6× at 14:00. */
private const val DAY_SWING = 0.6

/** Extra events in the first, second and third hour of a burst (an escalation). */
private val BURST_EVENTS = listOf(40, 25, 15)
