package io.github.sriharinmn.triage.ui.detail

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.data.members.Member
import io.github.sriharinmn.triage.ui.common.ActionNoticeEffect
import io.github.sriharinmn.triage.ui.common.DisconnectDialog
import io.github.sriharinmn.triage.ui.common.ReconnectBanner
import io.github.sriharinmn.triage.ui.format.elapsedSince
import io.github.sriharinmn.triage.ui.format.label
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the action bar, assignee sheet and action notices call. Each defaults to doing nothing, which tests rely on. */
internal data class DetailActionHandlers(
  val onResolve: () -> Unit = {},
  val onArchive: () -> Unit = {},
  val onAssign: () -> Unit = {},
  val onMarkReviewed: () -> Unit = {},
  val onMemberChosen: (Member) -> Unit = {},
  val onUnassign: () -> Unit = {},
  val onNotMe: () -> Unit = {},
  val onRetryMembers: () -> Unit = {},
  val onAssignSheetDismissed: () -> Unit = {},
  val onCloseHandled: () -> Unit = {},
  val onUndo: (ActionNotice.Pending) -> Unit = {},
  val onRetryAction: (ActionNotice.Failed) -> Unit = {},
  val onNoticeDismissed: (ActionNotice) -> Unit = {},
)

/** The issue detail, wired to [viewModel]; [onBack] returns to the inbox. */
@Composable
fun IssueDetailScreen(viewModel: IssueDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val uriHandler = LocalUriHandler.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val openFailed = stringResource(R.string.inbox_open_failed)
  // "2m ago" labels tick every minute.
  val now by
    produceState(initialValue = Instant.now()) {
      while (true) {
        value = Instant.now()
        delay(60_000)
      }
    }

  IssueDetailContent(
    state = state,
    now = now,
    onBack = onBack,
    onOpenInBrowser = {
      try {
        uriHandler.openUri(viewModel.browserUrl())
      } catch (e: IllegalArgumentException) {
        // AndroidUriHandler wraps ActivityNotFoundException (no browser installed) in this.
        scope.launch { snackbarHostState.showSnackbar(openFailed) }
      }
    },
    onEventKindSelected = viewModel::onEventKindSelected,
    onRetry = viewModel::onRetry,
    onFrameToggled = viewModel::onFrameToggled,
    onShowLibraryFrames = viewModel::onShowLibraryFrames,
    onShowAllBreadcrumbs = viewModel::onShowAllBreadcrumbs,
    onReconnectConfirmed = viewModel::onReconnectConfirmed,
    onDisconnectFailureShown = viewModel::onDisconnectFailureShown,
    snackbarHostState = snackbarHostState,
    actions =
      DetailActionHandlers(
        onResolve = viewModel::onResolve,
        onArchive = viewModel::onArchive,
        onAssign = viewModel::onAssignClicked,
        onMarkReviewed = viewModel::onMarkReviewed,
        onMemberChosen = viewModel::onMemberChosen,
        onUnassign = viewModel::onUnassign,
        onNotMe = viewModel::onNotMe,
        onRetryMembers = viewModel::onRetryMembers,
        onAssignSheetDismissed = viewModel::onAssignSheetDismissed,
        onCloseHandled = viewModel::onCloseHandled,
        onUndo = viewModel::onUndo,
        onRetryAction = viewModel::onRetryAction,
        onNoticeDismissed = viewModel::onNoticeDismissed,
      ),
    modifier = modifier,
  )
}

@Composable
internal fun IssueDetailContent(
  state: IssueDetailUiState,
  now: Instant,
  onBack: () -> Unit,
  onOpenInBrowser: () -> Unit,
  onEventKindSelected: (EventKind) -> Unit,
  onRetry: () -> Unit,
  onFrameToggled: (FrameRef) -> Unit,
  onShowLibraryFrames: (Int) -> Unit,
  onShowAllBreadcrumbs: () -> Unit,
  onReconnectConfirmed: () -> Unit,
  onDisconnectFailureShown: () -> Unit,
  actions: DetailActionHandlers = DetailActionHandlers(),
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  var confirmingReconnect by rememberSaveable { mutableStateOf(false) }
  val disconnectFailedText = stringResource(R.string.inbox_disconnect_failed)
  LaunchedEffect(state.disconnectFailed) {
    if (state.disconnectFailed) {
      snackbarHostState.showSnackbar(disconnectFailedText)
      onDisconnectFailureShown()
    }
  }
  // Action notices get their own host, so Undo never waits behind another snackbar until its window has ended.
  val noticeHostState = remember { SnackbarHostState() }
  ActionNoticeEffect(
    state.notice,
    noticeHostState,
    onUndo = actions.onUndo,
    onRetry = actions.onRetryAction,
    onDismissed = actions.onNoticeDismissed,
  )
  // After Resolve or Archive, go back: the inbox shows the same Undo for the action.
  LaunchedEffect(state.closeRequested) {
    if (state.closeRequested) {
      actions.onCloseHandled()
      onBack()
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = { DetailTopBar(title = state.summary?.shortId.orEmpty(), onBack = onBack, onOpenInBrowser = onOpenInBrowser) },
    // A notice covers any other snackbar, which shows once the notice goes.
    snackbarHost = {
      if (noticeHostState.currentSnackbarData != null) SnackbarHost(noticeHostState) else SnackbarHost(snackbarHostState)
    },
    bottomBar = {
      if (state.canAct) {
        DetailActionBar(
          canMarkReviewed = state.canMarkReviewed,
          onResolve = actions.onResolve,
          onArchive = actions.onArchive,
          onAssign = actions.onAssign,
          onMarkReviewed = actions.onMarkReviewed,
        )
      }
    },
  ) { padding ->
    val summary = state.summary
    Box(Modifier.fillMaxSize().padding(padding)) {
      when {
        state.isGone ->
          MessagePanel(
            message = stringResource(R.string.detail_gone),
            actionLabel = stringResource(R.string.detail_back_to_inbox),
            onAction = onBack,
          )
        summary != null ->
          DetailList(
            state = state,
            summary = summary,
            now = now,
            onEventKindSelected = onEventKindSelected,
            onRetry = onRetry,
            onOpenInBrowser = onOpenInBrowser,
            onFrameToggled = onFrameToggled,
            onShowLibraryFrames = onShowLibraryFrames,
            onShowAllBreadcrumbs = onShowAllBreadcrumbs,
            onReconnect = { confirmingReconnect = true },
          )
        state.isLoadingSummary -> LoadingPanel(stringResource(R.string.detail_loading))
        else -> {
          val error = state.summaryError ?: DetailError.Server
          val reconnect = error.needsReconnect()
          when {
            // Retrying can't make the issue smaller, but Sentry's own page can still show it.
            error == DetailError.TooLarge ->
              MessagePanel(
                message = stringResource(R.string.detail_issue_too_large),
                actionLabel = stringResource(R.string.detail_open_in_browser),
                onAction = onOpenInBrowser,
              )
            else ->
              MessagePanel(
                message = detailErrorMessage(error),
                actionLabel = stringResource(if (reconnect) R.string.inbox_reconnect else R.string.inbox_retry),
                onAction = { if (reconnect) confirmingReconnect = true else onRetry() },
              )
          }
        }
      }
    }
  }

  if (confirmingReconnect) {
    DisconnectDialog(
      onConfirm = {
        confirmingReconnect = false
        onReconnectConfirmed()
      },
      onDismiss = { confirmingReconnect = false },
    )
  }

  state.assignSheet?.let { sheet ->
    AssigneeSheet(
      state = sheet,
      onMemberChosen = actions.onMemberChosen,
      onUnassign = actions.onUnassign,
      onNotMe = actions.onNotMe,
      onRetry = actions.onRetryMembers,
      onDismiss = actions.onAssignSheetDismissed,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit, onOpenInBrowser: () -> Unit) {
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = {
      IconButton(onClick = onBack, modifier = Modifier.testTag(DetailTags.BACK)) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.detail_back))
      }
    },
    actions = {
      IconButton(onClick = onOpenInBrowser, modifier = Modifier.testTag(DetailTags.OPEN_IN_BROWSER)) {
        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = stringResource(R.string.detail_open_in_browser))
      }
    },
  )
}

@Composable
private fun DetailList(
  state: IssueDetailUiState,
  summary: IssueSummary,
  now: Instant,
  onEventKindSelected: (EventKind) -> Unit,
  onRetry: () -> Unit,
  onOpenInBrowser: () -> Unit,
  onFrameToggled: (FrameRef) -> Unit,
  onShowLibraryFrames: (Int) -> Unit,
  onShowAllBreadcrumbs: () -> Unit,
  onReconnect: () -> Unit,
) {
  val reconnectError = state.reconnectError
  val event = state.event
  val eventError = state.eventError
  LazyColumn(Modifier.fillMaxSize().testTag(DetailTags.LIST)) {
    if (reconnectError != null) {
      item(key = "reconnect") {
        ReconnectBanner(message = detailErrorMessage(reconnectError), onReconnect = onReconnect, buttonTag = DetailTags.RECONNECT)
      }
    }
    item(key = "summary") { SummarySection(summary, state.shownAssignee, now) }
    item(key = "event_header") { EventHeader(state.selectedKind, event, now, onEventKindSelected) }
    when {
      event != null -> {
        stackTraceSection(event, state.expandedFrames, state.revealedStacks, onFrameToggled, onShowLibraryFrames)
        breadcrumbsSection(event.breadcrumbs, state.showAllBreadcrumbs, onShowAllBreadcrumbs)
        tagsSection(state.tags, event.tags)
        detailsSection(event.context)
      }
      state.isLoadingEvent -> item(key = "event_loading") { EventLoading() }
      // The banner above already explains a rejected token.
      eventError != null && !eventError.needsReconnect() ->
        item(key = "event_error") { EventErrorPanel(eventError, onRetry, onOpenInBrowser) }
    }
    item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
  }
}

/** The Recommended / Latest / Oldest chips and, once loaded, when the event happened, its release and environment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventHeader(selected: EventKind, event: IssueEvent?, now: Instant, onSelected: (EventKind) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    HorizontalDivider()
    // Wraps at large font sizes, so the last chip stays on screen.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
      for (kind in EventKind.entries) {
        FilterChip(
          selected = kind == selected,
          onClick = { onSelected(kind) },
          label = { Text(stringResource(kind.labelRes())) },
          modifier = Modifier.testTag(DetailTags.eventChip(kind)),
        )
      }
    }
    event?.let { EventMetaLine(it, now) }
  }
}

/** "2m ago · release 1.4.2 · production"; TalkBack hears the exact date and time instead of "2m ago". */
@Composable
private fun EventMetaLine(event: IssueEvent, now: Instant) {
  val context = LocalContext.current
  val release = event.release?.let { stringResource(R.string.detail_release, it) }
  val ago = event.dateCreated?.let { stringResource(R.string.issue_last_seen, elapsedSince(it, now).label()) }
  val shown = listOfNotNull(ago, release, event.environment)
  if (shown.isEmpty()) return
  val exact =
    event.dateCreated?.let {
      DateUtils.formatDateTime(
        context,
        it.toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
      )
    }
  val spoken = listOfNotNull(exact, release, event.environment).joinToString(", ")
  Text(
    shown.joinToString(" · "),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.semantics { contentDescription = spoken },
  )
}

@Composable
private fun EventLoading() {
  val description = stringResource(R.string.detail_event_loading)
  Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = description })
  }
}

/** Why the event couldn't load, with the one action that can help. */
@Composable
private fun EventErrorPanel(error: DetailError, onRetry: () -> Unit, onOpenInBrowser: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(detailErrorMessage(error), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    when (error) {
      DetailError.TooLarge -> OutlinedButton(onClick = onOpenInBrowser) { Text(stringResource(R.string.detail_open_in_browser)) }
      // Sentry has no event of this kind; another chip may still have one.
      DetailError.NotFound -> Unit
      else ->
        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(DetailTags.RETRY)) {
          Text(stringResource(R.string.inbox_retry))
        }
    }
  }
}

@Composable
private fun MessagePanel(message: String, actionLabel: String, onAction: () -> Unit) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
      OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
  }
}

@Composable
private fun LoadingPanel(description: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.semantics { contentDescription = description })
  }
}
