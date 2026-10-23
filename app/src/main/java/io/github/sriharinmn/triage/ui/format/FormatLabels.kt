package io.github.sriharinmn.triage.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.sriharinmn.triage.R

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
