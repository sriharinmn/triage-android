package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.ui.format.ElapsedSpan
import io.github.sriharinmn.triage.ui.format.SpanUnit
import io.github.sriharinmn.triage.ui.format.compactCount
import io.github.sriharinmn.triage.ui.format.elapsedSince
import java.time.Instant

private val LEVEL_BAR_WIDTH = 4.dp

/** One inbox row: level bar, title, culprit, meta line, and a 24-hour sparkline over the counts. */
@Composable
internal fun IssueRow(issue: Issue, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val levelColor = levelColor(issue.level)
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        // Drawn rather than laid out, so the bar spans the row's full height without intrinsic measurement.
        .drawBehind { drawRect(levelColor, size = Size(LEVEL_BAR_WIDTH.toPx(), size.height)) }
        .padding(start = LEVEL_BAR_WIDTH)
        // One TalkBack stop per issue that reads every text in the row.
        .semantics(mergeDescendants = true) {},
  ) {
    Column(
      modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(issue.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      issue.culprit?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      MetaLine(issue, now)
    }
    Column(
      modifier = Modifier.padding(top = 10.dp, end = 12.dp, bottom = 10.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Sparkline(issue.hourlyEvents, color = levelColor, modifier = Modifier.size(width = 56.dp, height = 20.dp))
      Text(
        pluralStringResource(R.plurals.issue_events, issue.eventCount.toQuantity(), compactCount(issue.eventCount)),
        style = MaterialTheme.typography.labelSmall,
      )
      Text(
        pluralStringResource(R.plurals.issue_users, issue.userCount.toQuantity(), compactCount(issue.userCount)),
        style = MaterialTheme.typography.labelSmall,
        color = muted,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(issue: Issue, now: Instant) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(issue.shortId, style = MaterialTheme.typography.labelSmall, color = muted)
    issue.badge?.let { StatusLabel(stringResource(it.labelRes())) }
    if (issue.isUnhandled) StatusLabel(stringResource(R.string.issue_badge_unhandled))
    issue.lastSeen?.let {
      Text(stringResource(R.string.issue_last_seen, elapsedSince(it, now).label()), style = MaterialTheme.typography.labelSmall, color = muted)
    }
    issue.firstSeen?.let {
      Text(stringResource(R.string.issue_age, elapsedSince(it, now).label()), style = MaterialTheme.typography.labelSmall, color = muted)
    }
  }
}

/** A small tag such as "New". Plain text on a background, so taps fall through to the row. */
@Composable
private fun StatusLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier =
      Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
  )
}

/**
 * 24 hourly bars, scaled to the busiest hour; empty hours get a hairline so the shape still reads as a timeline.
 * With no stats at all it draws nothing and says nothing, rather than announcing "0 events".
 */
@Composable
private fun Sparkline(hourlyEvents: List<Long>, color: Color, modifier: Modifier = Modifier) {
  if (hourlyEvents.isEmpty()) {
    Spacer(modifier)
    return
  }
  val total = hourlyEvents.sum()
  val description = pluralStringResource(R.plurals.issue_events_last_24h, total.toQuantity(), total)
  Canvas(modifier.semantics { contentDescription = description }) {
    val peak = hourlyEvents.max().coerceAtLeast(1)
    val slot = size.width / hourlyEvents.size
    val barWidth = (slot * 0.7f).coerceAtLeast(1f)
    val minBarHeight = 1.dp.toPx()
    hourlyEvents.forEachIndexed { index, count ->
      val barHeight = (count.toFloat() / peak * size.height).coerceAtLeast(minBarHeight)
      drawRect(
        color = color,
        topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - barHeight),
        size = Size(barWidth, barHeight),
      )
    }
  }
}

/** Red-to-blue by severity, with lighter tones in dark mode; deliberately not Sentry's purple. */
@Composable
private fun levelColor(level: IssueLevel): Color {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  return when (level) {
    IssueLevel.Fatal -> if (dark) Color(0xFFFF8A80) else Color(0xFF8E1B1B)
    IssueLevel.Error -> if (dark) Color(0xFFEF5350) else Color(0xFFD32F2F)
    IssueLevel.Warning -> if (dark) Color(0xFFFFB74D) else Color(0xFFB26A00)
    IssueLevel.Info -> if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)
    IssueLevel.Debug,
    IssueLevel.Unknown -> if (dark) Color(0xFFBDBDBD) else Color(0xFF757575)
  }
}

@Composable
private fun ElapsedSpan.label(): String =
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
private fun Long.toQuantity(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
