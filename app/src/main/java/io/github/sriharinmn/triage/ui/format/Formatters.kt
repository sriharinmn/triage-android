package io.github.sriharinmn.triage.ui.format

import java.time.Duration
import java.time.Instant

/** The units [elapsedSince] reports, from under a minute up to years. */
enum class SpanUnit {
  UnderAMinute,
  Minutes,
  Hours,
  Days,
  Weeks,
  Months,
  Years,
}

/** A rough age such as "3 hours", turned into text by the UI (e.g. "3h ago"). */
data class ElapsedSpan(val unit: SpanUnit, val amount: Long)

/** How long ago [then] was, rounded down to the largest whole unit: 90 minutes is 1 hour. */
fun elapsedSince(then: Instant, now: Instant): ElapsedSpan {
  val minutes = Duration.between(then, now).toMinutes().coerceAtLeast(0)
  val hours = minutes / 60
  val days = hours / 24
  return when {
    minutes < 1 -> ElapsedSpan(SpanUnit.UnderAMinute, 0)
    hours < 1 -> ElapsedSpan(SpanUnit.Minutes, minutes)
    days < 1 -> ElapsedSpan(SpanUnit.Hours, hours)
    days < 7 -> ElapsedSpan(SpanUnit.Days, days)
    days < 30 -> ElapsedSpan(SpanUnit.Weeks, days / 7)
    days < 365 -> ElapsedSpan(SpanUnit.Months, days / 30)
    else -> ElapsedSpan(SpanUnit.Years, days / 365)
  }
}

/** 999, 1.2k, 15k, 1.5M: one decimal below ten of a unit, always rounded down so counts never look bigger. */
fun compactCount(count: Long): String {
  val n = count.coerceAtLeast(0)
  return when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> scaled(n, 1_000, "k")
    else -> scaled(n, 1_000_000, "M")
  }
}

private fun scaled(n: Long, unit: Long, suffix: String): String {
  val whole = n / unit
  if (whole >= 10) return "$whole$suffix"
  val tenth = n % unit * 10 / unit
  return if (tenth == 0L) "$whole$suffix" else "$whole.$tenth$suffix"
}
