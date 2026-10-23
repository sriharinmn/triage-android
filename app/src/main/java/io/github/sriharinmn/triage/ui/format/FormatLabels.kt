package io.github.sriharinmn.triage.ui.format

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.sriharinmn.triage.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "3h", "<1m": the short form of an elapsed span, used in "3h ago" and "5d old". */
@Composable
internal fun ElapsedSpan.label(): String =
  when (unit) {
    SpanUnit.UnderAMinute -> stringResource(R.string.span_under_minute)
    SpanUnit.Minutes -> stringResource(R.string.span_minutes, amount)
    SpanUnit.Hours -> stringResource(R.string.span_hours, amount)
    SpanUnit.Days -> stringResource(R.string.span_days, amount)
    SpanUnit.Weeks -> stringResource(R.string.span_weeks, amount)
    SpanUnit.Months -> stringResource(R.string.span_months, amount)
    SpanUnit.Years -> stringResource(R.string.span_years, amount)
  }

/** Plural rules take an Int; counts above Int.MAX_VALUE are "other" anyway. */
internal fun Long.toQuantity(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

/** "14:05:09" or "2:05:09 PM", following the phone's 12/24-hour setting, in the phone's time zone. */
@Composable
internal fun rememberTimeWithSecondsFormatter(): DateTimeFormatter {
  val context = LocalContext.current
  val locale = LocalConfiguration.current.locales[0]
  val is24Hour = DateFormat.is24HourFormat(context)
  return remember(locale, is24Hour) {
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hms" else "hms"), locale)
      .withZone(ZoneId.systemDefault())
  }
}
