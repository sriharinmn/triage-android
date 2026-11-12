package io.github.sriharinmn.triage.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.theme.mono
import io.github.sriharinmn.triage.theme.tabular
import io.github.sriharinmn.triage.ui.common.LevelGlyph
import io.github.sriharinmn.triage.ui.common.StatusLabel
import io.github.sriharinmn.triage.ui.common.StatusWord
import io.github.sriharinmn.triage.ui.common.labelRes
import io.github.sriharinmn.triage.ui.format.compactCount
import io.github.sriharinmn.triage.ui.format.elapsedSince
import io.github.sriharinmn.triage.ui.format.label
import java.time.Instant

/** From this font scale on, the stat strip goes to two rows of two so no number is squeezed. */
private const val LARGE_FONT_SCALE = 1.5f

/** Shown in a stat cell when Sentry didn't say. */
private const val NO_VALUE = "–"

/**
 * The detail's top: the eyebrow (level, status, release), the title, the culprit, the assignee, the stat strip and the
 * events chart. It sits at the top of the scroll, above the tabs.
 */
@Composable
internal fun DetailTop(
  summary: IssueSummary,
  assignee: ShownAssignee,
  now: Instant,
  showDaily: Boolean,
  onShowDailyChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Eyebrow(summary)
    Text(summary.title, style = MaterialTheme.typography.titleLarge)
    summary.culprit?.let { Text(it, style = MaterialTheme.typography.bodySmall.mono(), color = muted) }
    when (assignee) {
      is ShownAssignee.Named ->
        Text(stringResource(R.string.detail_assigned_to, assignee.name), style = MaterialTheme.typography.bodyMedium)
      ShownAssignee.Unassigned ->
        Text(stringResource(R.string.detail_unassigned), style = MaterialTheme.typography.bodyMedium, color = muted)
      // Only the cached row is loaded, which doesn't say; show nothing rather than guess.
      ShownAssignee.Unknown -> Unit
    }
    StatStrip(summary, now, Modifier.padding(top = 4.dp))
    TrendChart(summary, now, showDaily, onShowDailyChange, Modifier.padding(top = 8.dp))
  }
}

/** "■ Error  ↺ Regressed  Unhandled          1.4.2" in mono: the level, the status and tags, the last release on the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Eyebrow(summary: IssueSummary) {
  val style = MaterialTheme.typography.labelMedium.mono()
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    FlowRow(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      itemVerticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // The word next to it names the level, so the glyph isn't read out as well.
        LevelGlyph(summary.level, Modifier.clearAndSetSemantics {})
        Text(stringResource(summary.level.labelRes()), style = style, color = muted)
      }
      summary.badge?.let { StatusWord(it, style = style) }
      summary.status.labelRes()?.let { StatusLabel(stringResource(it)) }
      if (summary.isUnhandled) StatusLabel(stringResource(R.string.issue_badge_unhandled))
    }
    summary.lastRelease?.let {
      Text(it, style = style, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
    }
  }
}

private class StatCell(val value: String, val label: String, val tag: String)

/** Events, users, first and last seen in one rounded tile: four across, or two rows of two at large font sizes. */
@Composable
private fun StatStrip(summary: IssueSummary, now: Instant, modifier: Modifier = Modifier) {
  val cells =
    listOf(
      StatCell(compactCount(summary.eventCount), stringResource(R.string.detail_stat_events), DetailTags.STAT_EVENTS),
      StatCell(compactCount(summary.userCount), stringResource(R.string.detail_stat_users), DetailTags.STAT_USERS),
      StatCell(
        summary.firstSeen?.let { elapsedSince(it, now).label() } ?: NO_VALUE,
        stringResource(R.string.detail_stat_first_seen),
        DetailTags.STAT_FIRST_SEEN,
      ),
      StatCell(
        summary.lastSeen?.let { elapsedSince(it, now).label() } ?: NO_VALUE,
        stringResource(R.string.detail_stat_last_seen),
        DetailTags.STAT_LAST_SEEN,
      ),
    )
  val perRow = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else 4
  val hairline = MaterialTheme.colorScheme.outlineVariant
  Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) {
    Column {
      cells.chunked(perRow).forEachIndexed { rowIndex, row ->
        if (rowIndex > 0) HorizontalDivider(color = hairline)
        Row(Modifier.height(IntrinsicSize.Min)) {
          row.forEachIndexed { index, cell ->
            if (index > 0) VerticalDivider(color = hairline)
            Column(
              Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp).testTag(cell.tag).semantics(mergeDescendants = true) {}
            ) {
              Text(cell.value, style = MaterialTheme.typography.titleMedium.tabular(), maxLines = 1)
              SmallCapsText(cell.label)
            }
          }
        }
      }
    }
  }
}

/** A small uppercase label, as used in the stat strip and over the chart. */
@Composable
internal fun SmallCapsText(text: String, modifier: Modifier = Modifier) {
  val locale = LocalConfiguration.current.locales[0]
  Text(
    text.uppercase(locale),
    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier,
  )
}
