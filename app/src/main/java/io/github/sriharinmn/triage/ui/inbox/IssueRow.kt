package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.theme.mono
import io.github.sriharinmn.triage.ui.common.LevelGlyph
import io.github.sriharinmn.triage.ui.common.StatusLabel
import io.github.sriharinmn.triage.ui.common.StatusWord
import io.github.sriharinmn.triage.ui.common.labelRes
import io.github.sriharinmn.triage.ui.format.compactCount
import io.github.sriharinmn.triage.ui.format.elapsedSince
import io.github.sriharinmn.triage.ui.format.label
import io.github.sriharinmn.triage.ui.format.spokenAgo
import io.github.sriharinmn.triage.ui.format.toQuantity
import java.text.NumberFormat
import java.time.Instant

/**
 * One inbox row, flat on the background: the heat tile with the event count, then the title, the culprit in mono, and a
 * meta line (level glyph, short id, status, users and age). No card and no colored stripe. TalkBack hears the row as one
 * sentence; Resolve and Archive come in as custom actions through [modifier] (see SwipeableIssueRow), which is why it
 * goes before the clearing semantics.
 */
@Composable
internal fun IssueRow(issue: Issue, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val tier = heatTier(issue.hourlyEvents, issue.lastSeen, now)
  val description = issueRowDescription(issue, tier, now)
  val openLabel = stringResource(R.string.inbox_open_issue)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClickLabel = openLabel, onClick = onClick)
        .clearAndSetSemantics { contentDescription = description }
        .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    HeatTile(count = issue.eventCount, tier = tier)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(issue.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      issue.culprit?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall.mono(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      MetaLine(issue, now)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(issue: Issue, now: Instant) {
  val style = MaterialTheme.typography.labelSmall
  val users = pluralStringResource(R.plurals.issue_users, issue.userCount.toQuantity(), compactCount(issue.userCount))
  val usersAndAge =
    issue.lastSeen?.let { stringResource(R.string.inbox_row_users_age, users, elapsedSince(it, now).label()) } ?: users
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    LevelGlyph(issue.level)
    Text(issue.shortId, style = style.mono(), color = MaterialTheme.colorScheme.primary)
    issue.badge?.let { StatusWord(it, style = style) }
    if (issue.isUnhandled) StatusLabel(stringResource(R.string.issue_badge_unhandled))
    Text(usersAndAge, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** "Error. SocketTimeoutException: timeout. 60 events, busier than usual, 8 users. Escalating. 21 hours ago." */
@Composable
private fun issueRowDescription(issue: Issue, tier: HeatTier, now: Instant): String {
  val locale = LocalConfiguration.current.locales[0]
  val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
  val events = pluralStringResource(R.plurals.issue_events, issue.eventCount.toQuantity(), number.format(issue.eventCount))
  val users = pluralStringResource(R.plurals.issue_users, issue.userCount.toQuantity(), number.format(issue.userCount))
  val counts =
    if (tier == HeatTier.Hot) stringResource(R.string.inbox_row_counts_hot, events, users)
    else stringResource(R.string.inbox_row_counts, events, users)
  return listOfNotNull(
      stringResource(issue.level.labelRes()),
      issue.title.trimEnd('.'),
      counts,
      issue.badge?.let { stringResource(it.labelRes()) },
      if (issue.isUnhandled) stringResource(R.string.issue_badge_unhandled) else null,
      issue.lastSeen?.let { elapsedSince(it, now).spokenAgo() },
    )
    .joinToString(separator = ". ", postfix = ".")
}
