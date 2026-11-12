package io.github.sriharinmn.triage.ui.detail

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.theme.CHART_BAR_ALPHA
import io.github.sriharinmn.triage.ui.inbox.RECENT_BUCKETS
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * The events chart: real bars from the issue's 24-hour or 30-day series, the latest [RECENT_BUCKETS] in full primary and
 * the rest at [CHART_BAR_ALPHA], with a 24h / 30d switch. TalkBack hears a summary such as "Events, last 24 hours: 12,
 * busiest at 14:00".
 */
@Composable
internal fun TrendChart(
  summary: IssueSummary,
  now: Instant,
  showDaily: Boolean,
  onShowDailyChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  // The 30-day series arrives with the fresh issue; until then the chart stays on 24 hours.
  val daily = showDaily && summary.dailyEvents.isNotEmpty()
  val period = if (daily) TrendPeriod.Last30Days else TrendPeriod.Last24Hours
  val counts = if (daily) summary.dailyEvents else summary.hourlyEvents
  val description = trendDescription(trendSummary(counts, period, now), period)
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      SmallCapsText(stringResource(R.string.detail_chart_label), Modifier.weight(1f))
      SingleChoiceSegmentedButtonRow {
        SegmentedButton(
          selected = !daily,
          onClick = { onShowDailyChange(false) },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          icon = {},
          modifier = Modifier.testTag(DetailTags.TREND_24H),
        ) {
          Text(stringResource(R.string.detail_trend_24h))
        }
        SegmentedButton(
          selected = daily,
          onClick = { onShowDailyChange(true) },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
          enabled = summary.dailyEvents.isNotEmpty(),
          icon = {},
          modifier = Modifier.testTag(DetailTags.TREND_30D),
        ) {
          Text(stringResource(R.string.detail_trend_30d))
        }
      }
    }
    Bars(counts, Modifier.fillMaxWidth().height(56.dp).testTag(DetailTags.CHART).semantics { contentDescription = description })
  }
}

/** Bars scaled to the busiest one; empty buckets keep a 2 dp stub so the shape still reads as a timeline. */
@Composable
private fun Bars(counts: List<Long>, modifier: Modifier) {
  val latest = MaterialTheme.colorScheme.primary
  val older = latest.copy(alpha = CHART_BAR_ALPHA)
  Canvas(modifier) {
    if (counts.isEmpty()) return@Canvas
    val peak = counts.max().coerceAtLeast(1)
    val slot = size.width / counts.size
    val gap = (slot * 0.2f).coerceAtMost(2.dp.toPx())
    val barWidth = (slot - gap).coerceAtLeast(1f)
    val minBar = 2.dp.toPx()
    val corner = CornerRadius(minOf(2.dp.toPx(), barWidth / 2))
    counts.forEachIndexed { index, count ->
      val height = (count.toFloat() / peak * size.height).coerceAtLeast(minBar)
      drawRoundRect(
        color = if (index >= counts.size - RECENT_BUCKETS) latest else older,
        topLeft = Offset(index * slot + gap / 2, size.height - height),
        size = Size(barWidth, height),
        cornerRadius = corner,
      )
    }
  }
}

/** "Events, last 24 hours: 12, busiest at 14:00" (the phone's time) or "…, busiest on Oct 21" (a UTC day, as Sentry's). */
@Composable
private fun trendDescription(summary: TrendSummary, period: TrendPeriod): String {
  val context = LocalContext.current
  val locale = LocalConfiguration.current.locales[0]
  val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
  val busiest =
    summary.busiestStart
      ?: return stringResource(if (period == TrendPeriod.Last24Hours) R.string.detail_chart_none_24h else R.string.detail_chart_none_30d)
  val total = number.format(summary.total)
  return when (period) {
    TrendPeriod.Last24Hours ->
      stringResource(R.string.detail_chart_summary_24h, total, DateFormat.getTimeFormat(context).format(Date(busiest.toEpochMilli())))
    TrendPeriod.Last30Days -> {
      val day = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).withZone(ZoneOffset.UTC)
      stringResource(R.string.detail_chart_summary_30d, total, day.format(busiest))
    }
  }
}
