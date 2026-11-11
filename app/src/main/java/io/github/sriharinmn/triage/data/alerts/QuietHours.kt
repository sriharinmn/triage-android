package io.github.sriharinmn.triage.data.alerts

import java.time.LocalTime

/**
 * One nightly window without alerts, as minutes after midnight in the phone's time zone. A [startMinute] later than
 * [endMinute] crosses midnight (the default, 22:00–07:00). Checks keep running inside it; what they find waits in the
 * backlog.
 */
data class QuietHours(
  val enabled: Boolean = false,
  val startMinute: Int = 22 * 60,
  val endMinute: Int = 7 * 60,
) {
  /** Whether [time] is inside the window, from [startMinute] (included) to [endMinute] (excluded). Equal ends mean no window. */
  fun isQuietAt(time: LocalTime): Boolean {
    if (!enabled || startMinute == endMinute) return false
    val minute = time.hour * 60 + time.minute
    return if (startMinute < endMinute) minute in startMinute until endMinute
    else minute >= startMinute || minute < endMinute
  }

  companion object {
    const val MINUTES_PER_DAY = 24 * 60
  }
}
