package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.ui.common.EventBars
import io.github.sriharinmn.triage.ui.common.StatusLabel
import io.github.sriharinmn.triage.ui.common.levelColor
import io.github.sriharinmn.triage.ui.format.compactCount
import io.github.sriharinmn.triage.ui.format.elapsedSince
import io.github.sriharinmn.triage.ui.format.label
import io.github.sriharinmn.triage.ui.format.toQuantity
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
      val total = issue.hourlyEvents.sum()
      EventBars(
        counts = issue.hourlyEvents,
        color = levelColor,
        description = pluralStringResource(R.plurals.issue_events_last_24h, total.toQuantity(), total),
        modifier = Modifier.size(width = 56.dp, height = 20.dp),
      )
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
