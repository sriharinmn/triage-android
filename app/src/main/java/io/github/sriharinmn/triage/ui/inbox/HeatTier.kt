package io.github.sriharinmn.triage.ui.inbox

import java.time.Duration
import java.time.Instant

/** How busy an issue is right now, shown as its heat tile's brightness. */
enum class HeatTier {
  Hot,
  Warm,
  Quiet,
}

/** The last this many hourly buckets count as "now" (the detail chart highlights the same window). */
internal const val RECENT_BUCKETS = 3

/**
 * From the issue's 24 hourly counts, oldest first:
 * - Hot: the last [RECENT_BUCKETS] hours hold at least 2 events and more than an eighth of the day's, so a spike stands
 *   out against the issue's own usual rate (a steady issue has exactly an eighth there);
 * - Warm: any event in the last 24 hours;
 * - Quiet: none.
 *
 * Without stats, an issue last seen within 24 hours is Warm, anything else Quiet.
 */
fun heatTier(hourlyEvents: List<Long>, lastSeen: Instant?, now: Instant): HeatTier {
  if (hourlyEvents.isEmpty()) {
    val seenToday = lastSeen != null && !lastSeen.isBefore(now.minus(DAY))
    return if (seenToday) HeatTier.Warm else HeatTier.Quiet
  }
  val total = hourlyEvents.sum()
  val recent = hourlyEvents.takeLast(RECENT_BUCKETS).sum()
  return when {
    recent >= 2 && recent * 8 > total -> HeatTier.Hot
    total > 0 -> HeatTier.Warm
    else -> HeatTier.Quiet
  }
}

private val DAY: Duration = Duration.ofHours(24)
