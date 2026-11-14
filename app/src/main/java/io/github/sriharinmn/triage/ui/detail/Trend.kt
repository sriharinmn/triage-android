package io.github.sriharinmn.triage.ui.detail

import java.time.Instant
import java.time.temporal.ChronoUnit

/** The detail chart's two ranges, and how long one of their bars is. */
enum class TrendPeriod(val bucket: ChronoUnit) {
  Last24Hours(ChronoUnit.HOURS),
  Last30Days(ChronoUnit.DAYS),
}

/** A chart's total, and when its busiest bar starts (null without events), for its spoken summary. */
data class TrendSummary(val total: Long, val busiestStart: Instant?)

/**
 * Sentry's series run oldest first and end with the current hour (or UTC day), and their timestamps aren't kept, so a
 * bar's start is counted back from [now]. A tie goes to the most recent bar.
 */
fun trendSummary(counts: List<Long>, period: TrendPeriod, now: Instant): TrendSummary {
  val total = counts.sum()
  if (total <= 0) return TrendSummary(total = 0, busiestStart = null)
  val peak = counts.max()
  val busiest = counts.indexOfLast { it == peak }
  val lastStart = now.truncatedTo(period.bucket)
  return TrendSummary(total, lastStart.minus((counts.lastIndex - busiest).toLong(), period.bucket))
}
