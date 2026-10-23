package io.github.sriharinmn.triage.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.detail.Breadcrumb
import io.github.sriharinmn.triage.data.detail.EventContext
import io.github.sriharinmn.triage.data.detail.EventStack
import io.github.sriharinmn.triage.data.detail.EventTag
import io.github.sriharinmn.triage.data.detail.EventUser
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.StackFrame
import io.github.sriharinmn.triage.data.detail.TagSummary
import io.github.sriharinmn.triage.ui.common.labelRes
import io.github.sriharinmn.triage.ui.common.levelColor
import io.github.sriharinmn.triage.ui.format.rememberTimeWithSecondsFormatter

/** How many breadcrumbs show before "Show all". */
internal const val BREADCRUMBS_PREVIEW = 5

/**
 * The event's stacks: each exception's (or thread's) heading, then its visible frames, which can be tapped to show
 * their code. An event without a stack shows its message instead, if it has one.
 */
internal fun LazyListScope.stackTraceSection(
  event: IssueEvent,
  expandedFrames: Set<FrameRef>,
  revealedStacks: Set<Int>,
  onFrameToggled: (FrameRef) -> Unit,
  onShowLibraryFrames: (Int) -> Unit,
) {
  if (event.stacks.isEmpty()) {
    val message = event.message
    item(key = "stack_title") {
      SectionTitle(stringResource(if (message != null) R.string.detail_message else R.string.detail_stack_trace))
    }
    item(key = "stack_none") { SectionText(message ?: stringResource(R.string.detail_no_stack_trace)) }
    return
  }
  item(key = "stack_title") { SectionTitle(stringResource(R.string.detail_stack_trace)) }
  event.stacks.forEachIndexed { stackIndex, stack ->
    item(key = "stack_$stackIndex") { StackHeading(stack, isCause = stackIndex > 0) }
    val revealed = stackIndex in revealedStacks
    for ((frameIndex, frame) in stack.visibleFrames(revealed)) {
      val ref = FrameRef(stackIndex, frameIndex)
      item(key = "frame_${stackIndex}_$frameIndex") {
        FrameRow(
          frame = frame,
          expanded = ref in expandedFrames,
          onToggle = { onFrameToggled(ref) },
          modifier = Modifier.testTag(DetailTags.frame(ref)),
        )
      }
    }
    val hidden = stack.hiddenFrameCount(revealed)
    if (hidden > 0) {
      item(key = "stack_more_$stackIndex") {
        TextButton(
          onClick = { onShowLibraryFrames(stackIndex) },
          modifier = Modifier.padding(horizontal = 8.dp).testTag(DetailTags.showLibraryFrames(stackIndex)),
        ) {
          Text(pluralStringResource(R.plurals.detail_show_library_frames, hidden, hidden))
        }
      }
    }
  }
}

/** What happened before the event, newest first: the first [BREADCRUMBS_PREVIEW], or all of them once asked. */
internal fun LazyListScope.breadcrumbsSection(breadcrumbs: List<Breadcrumb>, showAll: Boolean, onShowAll: () -> Unit) {
  if (breadcrumbs.isEmpty()) return
  item(key = "crumbs_title") { SectionTitle(stringResource(R.string.detail_breadcrumbs)) }
  val shown = if (showAll) breadcrumbs else breadcrumbs.take(BREADCRUMBS_PREVIEW)
  shown.forEachIndexed { index, crumb -> item(key = "crumb_$index") { BreadcrumbRow(crumb) } }
  if (shown.size < breadcrumbs.size) {
    item(key = "crumbs_more") {
      TextButton(onClick = onShowAll, modifier = Modifier.padding(horizontal = 8.dp).testTag(DetailTags.SHOW_ALL_BREADCRUMBS)) {
        Text(stringResource(R.string.detail_show_all_breadcrumbs, breadcrumbs.size))
      }
    }
  }
}

/**
 * The issue's main tags with their most common value. While those are missing (loading, or Sentry couldn't give
 * them), the event's own tags show instead.
 */
internal fun LazyListScope.tagsSection(tags: List<TagSummary>?, eventTags: List<EventTag>) {
  val distribution = tags.orEmpty()
  if (distribution.isEmpty() && eventTags.isEmpty()) return
  item(key = "tags_title") { SectionTitle(stringResource(R.string.detail_tags)) }
  if (distribution.isNotEmpty()) {
    for (tag in distribution) item(key = "tag_${tag.key}") { TagDistributionRow(tag) }
  } else {
    eventTags.forEachIndexed { index, tag -> item(key = "event_tag_$index") { KeyValueRow(tag.key, tag.value) } }
  }
}

/** Device, OS, runtime, browser, app, user and request: only the lines the event has. */
internal fun LazyListScope.detailsSection(context: EventContext) {
  if (context == EventContext()) return
  item(key = "details_title") { SectionTitle(stringResource(R.string.detail_details)) }
  item(key = "details") {
    Column {
      context.device?.let { KeyValueRow(stringResource(R.string.detail_device), it) }
      context.os?.let { KeyValueRow(stringResource(R.string.detail_os), it) }
      context.runtime?.let { KeyValueRow(stringResource(R.string.detail_runtime), it) }
      context.browser?.let { KeyValueRow(stringResource(R.string.detail_browser), it) }
      context.app?.let { KeyValueRow(stringResource(R.string.detail_app), it) }
      context.user?.let { KeyValueRow(stringResource(R.string.detail_user), it.describe()) }
      context.request?.let {
        KeyValueRow(stringResource(R.string.detail_request), listOfNotNull(it.method, it.url).joinToString(" "))
      }
    }
  }
}

@Composable
private fun StackHeading(stack: EventStack, isCause: Boolean) {
  val heading =
    when {
      stack.type != null -> listOfNotNull(stack.type, stack.value).joinToString(": ")
      stack.threadName != null -> stringResource(R.string.detail_thread, stack.threadName)
      else -> stack.value ?: stringResource(R.string.detail_unknown_error)
    }
  Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
    if (isCause) {
      Text(
        stringResource(R.string.detail_caused_by),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(heading, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
  }
}

/** One frame: its name and place, the app's own frames in bold. Tapping a frame with code shows or hides that code. */
@Composable
private fun FrameRow(frame: StackFrame, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val hasCode = frame.context.isNotEmpty()
  val toggleLabel = stringResource(if (expanded) R.string.detail_hide_code else R.string.detail_show_code)
  Column(
    modifier
      .fillMaxWidth()
      .then(if (hasCode) Modifier.clickable(onClickLabel = toggleLabel, onClick = onToggle) else Modifier)
      .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (hasCode) {
        Icon(
          painterResource(R.drawable.ic_arrow_drop_down),
          contentDescription = null,
          modifier = Modifier.size(24.dp).rotate(if (expanded) 0f else -90f),
        )
      } else {
        Spacer(Modifier.size(24.dp))
      }
      Text(
        frame.title(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (frame.inApp) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      frame.location()?.let {
        Text(
          it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          // The end ("cart.js:42") matters more than the start of a long path.
          overflow = TextOverflow.StartEllipsis,
          modifier = Modifier.padding(start = 8.dp).widthIn(max = 160.dp),
        )
      }
    }
    if (expanded && hasCode) CodeBlock(frame, modifier = Modifier.padding(start = 24.dp, top = 4.dp))
  }
}

/** The frame's source lines with its own line highlighted; long lines scroll sideways inside the block, not the page. */
@Composable
private fun CodeBlock(frame: StackFrame, modifier: Modifier = Modifier) {
  val numberWidth = frame.context.maxOf { it.lineNo.toString().length }
  val highlight = MaterialTheme.colorScheme.errorContainer
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(6.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(Modifier.horizontalScroll(rememberScrollState()).width(IntrinsicSize.Max).padding(vertical = 6.dp)) {
      for (line in frame.context) {
        val errorLine =
          if (line.lineNo == frame.lineNo) stringResource(R.string.detail_error_line, line.lineNo, line.text) else null
        Text(
          text = line.lineNo.toString().padStart(numberWidth) + "  " + line.text,
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodySmall,
          softWrap = false,
          modifier =
            Modifier.fillMaxWidth()
              .then(if (errorLine != null) Modifier.background(highlight).semantics { contentDescription = errorLine } else Modifier)
              .padding(horizontal = 8.dp),
        )
      }
    }
  }
}

/** Time, category and what happened, with a level mark that TalkBack reads out. */
@Composable
private fun BreadcrumbRow(crumb: Breadcrumb) {
  val timeFormatter = rememberTimeWithSecondsFormatter()
  val levelName = stringResource(crumb.level.labelRes())
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics(mergeDescendants = true) {},
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      Modifier.padding(top = 6.dp)
        .size(8.dp)
        .background(levelColor(crumb.level), CircleShape)
        .semantics { contentDescription = levelName }
    )
    Column(Modifier.weight(1f)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        crumb.timestamp?.let {
          Text(timeFormatter.format(it), style = MaterialTheme.typography.labelSmall, color = muted, fontFamily = FontFamily.Monospace)
        }
        crumb.category?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
      }
      crumb.text?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis) }
    }
  }
}

@Composable
private fun TagDistributionRow(tag: TagSummary) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics(mergeDescendants = true) {},
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      tag.key,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.width(88.dp),
    )
    Column(Modifier.weight(1f)) {
      Row {
        Text(
          tag.topValue,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.detail_tag_percent, tag.percent), style = MaterialTheme.typography.labelMedium)
      }
      LinearProgressIndicator(progress = { tag.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
  }
}

/** "id 4821 · user@example.com · 203.0.113.7": whatever Sentry has, as it has it. */
@Composable
private fun EventUser.describe(): String {
  val idLabel = id?.let { stringResource(R.string.detail_user_id, it) }
  return listOfNotNull(idLabel, email, username, ipAddress).joinToString(" · ")
}
