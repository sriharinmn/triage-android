package io.github.sriharinmn.triage.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.ui.common.EventBars
import io.github.sriharinmn.triage.ui.common.StatusLabel
import io.github.sriharinmn.triage.ui.common.labelRes
import io.github.sriharinmn.triage.ui.common.levelColor
import io.github.sriharinmn.triage.ui.format.compactCount
import io.github.sriharinmn.triage.ui.format.elapsedSince
import io.github.sriharinmn.triage.ui.format.label
import io.github.sriharinmn.triage.ui.format.toQuantity
import io.github.sriharinmn.triage.ui.inbox.labelRes
import java.time.Instant

/** Title, culprit, labels, counts, the 24h or 30d trend, and when the issue was first and last seen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummarySection(summary: IssueSummary, now: Instant, modifier: Modifier = Modifier) {
  var daily by rememberSaveable { mutableStateOf(false) }
  // The 30-day series arrives with the fresh issue; until then the chart stays on 24 hours.
  val showDaily = daily && summary.dailyEvents.isNotEmpty()
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(summary.title, style = MaterialTheme.typography.titleMedium)
    summary.culprit?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      LevelLabel(summary.level)
      summary.badge?.let { StatusLabel(stringResource(it.labelRes())) }
      if (summary.isUnhandled) StatusLabel(stringResource(R.string.issue_badge_unhandled))
      summary.status.labelRes()?.let { StatusLabel(stringResource(it)) }
    }
    Text(
      listOf(
          pluralStringResource(R.plurals.issue_events, summary.eventCount.toQuantity(), compactCount(summary.eventCount)),
          pluralStringResource(R.plurals.issue_users, summary.userCount.toQuantity(), compactCount(summary.userCount)),
        )
        .joinToString(" · "),
      style = MaterialTheme.typography.bodyMedium,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val counts = if (showDaily) summary.dailyEvents else summary.hourlyEvents
      val total = counts.sum()
      EventBars(
        counts = counts,
        color = levelColor(summary.level),
        description =
          if (showDaily) pluralStringResource(R.plurals.detail_events_last_30d, total.toQuantity(), total)
          else pluralStringResource(R.plurals.issue_events_last_24h, total.toQuantity(), total),
        modifier = Modifier.weight(1f).height(40.dp),
      )
      FilterChip(
        selected = !showDaily,
        onClick = { daily = false },
        label = { Text(stringResource(R.string.detail_trend_24h)) },
        modifier = Modifier.testTag(DetailTags.TREND_24H),
      )
      FilterChip(
        selected = showDaily,
        onClick = { daily = true },
        enabled = summary.dailyEvents.isNotEmpty(),
        label = { Text(stringResource(R.string.detail_trend_30d)) },
        modifier = Modifier.testTag(DetailTags.TREND_30D),
      )
    }
    SeenLine(R.string.detail_first_seen, summary.firstSeen, summary.firstRelease, now)
    SeenLine(R.string.detail_last_seen, summary.lastSeen, summary.lastRelease, now)
  }
}

/** The level as a coloured dot plus a word, so it is never told by colour alone. */
@Composable
private fun LevelLabel(level: IssueLevel) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Box(Modifier.size(8.dp).background(levelColor(level), CircleShape))
    Text(stringResource(level.labelRes()), style = MaterialTheme.typography.labelMedium)
  }
}

/** "First seen 3d ago · 1.4.0"; nothing when Sentry didn't say when. */
@Composable
private fun SeenLine(@StringRes labelRes: Int, at: Instant?, release: String?, now: Instant) {
  if (at == null) return
  Text(
    listOfNotNull(stringResource(labelRes, elapsedSince(at, now).label()), release).joinToString(" · "),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
