package io.github.sriharinmn.triage.ui.detail

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.sriharinmn.triage.theme.mono
import io.github.sriharinmn.triage.ui.common.ActionNoticeEffect
import io.github.sriharinmn.triage.ui.common.DisconnectDialog
import io.github.sriharinmn.triage.ui.common.EmptyState
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
  val noWebPage = stringResource(R.string.detail_demo_no_web_page)
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
      val url = viewModel.browserUrl()
      if (url == null) {
        scope.launch { snackbarHostState.showSnackbar(noWebPage) }
      } else {
        try {
          uriHandler.openUri(url)
        } catch (e: IllegalArgumentException) {
          // AndroidUriHandler wraps ActivityNotFoundException (no browser installed) in this.
          scope.launch { snackbarHostState.showSnackbar(openFailed) }
        }
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
  // The chart's 24h / 30d choice, held above the tabs so switching tabs doesn't reset it.
  var showDaily by rememberSaveable { mutableStateOf(false) }
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
          CenteredPanel {
            EmptyState(
              icon = R.drawable.ic_info,
              headline = stringResource(R.string.state_issue_gone),
              help = stringResource(R.string.detail_gone),
              actionLabel = stringResource(R.string.detail_back_to_inbox),
              onAction = onBack,
            )
          }
        summary != null ->
          DetailBody(
            state = state,
            summary = summary,
            now = now,
            showDaily = showDaily,
            onShowDailyChange = { showDaily = it },
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
          CenteredPanel {
            if (error == DetailError.TooLarge) {
              // Retrying can't make the issue smaller, but Sentry's own page can still show it.
              EmptyState(
                icon = R.drawable.ic_info,
                headline = stringResource(R.string.state_too_large),
                help = stringResource(R.string.detail_issue_too_large),
                actionLabel = stringResource(R.string.detail_open_in_browser),
                onAction = onOpenInBrowser,
              )
            } else {
              EmptyState(
                icon = error.iconRes(),
                headline = detailErrorHeadline(error),
                help = detailErrorMessage(error),
                actionLabel = stringResource(if (reconnect) R.string.inbox_reconnect else R.string.inbox_retry),
                onAction = { if (reconnect) confirmingReconnect = true else onRetry() },
              )
            }
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
    title = { Text(title, style = MaterialTheme.typography.titleMedium.mono(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

/**
 * The shared top, then the tabs (sticky, so they stay at hand deep in a long trace), then the selected tab's content.
 * Each tab has its own list and scroll position, so coming back to a tab returns to where the user was in it. A tab not
 * yet scrolled opens with the tab row pinned when the tab being left had it pinned ([tabSwitchScrollTarget]). Each list
 * ends in a filler that makes even a short tab tall enough to keep the tab row pinned ([tabFillerPx]).
 */
@Composable
private fun DetailBody(
  state: IssueDetailUiState,
  summary: IssueSummary,
  now: Instant,
  showDaily: Boolean,
  onShowDailyChange: (Boolean) -> Unit,
  onEventKindSelected: (EventKind) -> Unit,
  onRetry: () -> Unit,
  onOpenInBrowser: () -> Unit,
  onFrameToggled: (FrameRef) -> Unit,
  onShowLibraryFrames: (Int) -> Unit,
  onShowAllBreadcrumbs: () -> Unit,
  onReconnect: () -> Unit,
) {
  var tab by rememberSaveable { mutableStateOf(DetailTab.Trace) }
  val listStates = DetailTab.entries.map { rememberLazyListState() }
  val reconnectError = state.reconnectError
  // The sticky tab row's index in the list: after "top", which itself follows the reconnect banner when that shows.
  val tabsIndex = if (reconnectError != null) 2 else 1
  fun selectTab(next: DetailTab) {
    val target =
      tabSwitchScrollTarget(listStates[tab.ordinal].firstVisibleItemIndex, listStates[next.ordinal].firstVisibleItemIndex, tabsIndex)
    // The new tab's list isn't composed yet (key(tab) rebuilds it). requestScrollToItem doesn't suspend or wait for a layout:
    // it stores the position, which that list applies at its first measure, so its first frame is already pinned.
    if (target != null) listStates[next.ordinal].requestScrollToItem(target)
    tab = next
  }
  val minFillerPx = with(LocalDensity.current) { 24.dp.roundToPx() }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val viewport = constraints.maxHeight
    key(tab) {
      // This list's own tab as a plain value. Reading the `tab` state inside the list's content would let a layout pass
      // that runs between a tab tap and the recomposition (which removes this list) measure this tab's scroll state
      // against the new tab's items, which clamps it to the top and loses the user's place.
      val listTab = tab
      val listState = listStates[listTab.ordinal]
      // The layout the state holds from before this list was shown (it outlives the list) or its content last changed.
      // Until the list has been measured since, the filler is a whole viewport, so neither a requested scroll nor
      // content that shrinks under a pinned tab row gets clamped; it settles to its real height on the next frame.
      // Only what can change the content's height counts: a notice or the assign sheet doesn't. The minute tick counts
      // only when it changes the event line's "Xm ago", which can wrap differently ("59m ago" to "1h ago").
      val contentKey =
        listOf(
          listTab,
          state.event,
          state.event?.dateCreated?.let { elapsedSince(it, now).label() },
          state.isLoadingEvent,
          state.eventError,
          state.expandedFrames,
          state.revealedStacks,
          state.showAllBreadcrumbs,
          state.tagsTabContent(),
          reconnectError,
        )
      val layoutBefore = remember(contentKey) { Snapshot.withoutReadObservation { listState.layoutInfo } }
      val fillerPx by
        remember(listState, layoutBefore, viewport, tabsIndex, minFillerPx) {
          derivedStateOf { listState.layoutInfo.tabFillerPx(layoutBefore, viewport, tabsIndex, minFillerPx) }
        }
      LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(DetailTags.LIST)) {
        if (reconnectError != null) {
          item(key = "reconnect") {
            ReconnectBanner(message = detailErrorMessage(reconnectError), onReconnect = onReconnect, buttonTag = DetailTags.RECONNECT)
          }
        }
        item(key = "top") { DetailTop(summary, state.shownAssignee, now, showDaily, onShowDailyChange) }
        stickyHeader(key = "tabs") { _ -> DetailTabRow(selected = listTab, onSelected = ::selectTab) }
        when (listTab) {
          DetailTab.Trace ->
            eventTab(state, now, onEventKindSelected, onRetry, onOpenInBrowser) { event ->
              stackTraceSection(event, state.expandedFrames, state.revealedStacks, onFrameToggled, onShowLibraryFrames)
            }
          DetailTab.Breadcrumbs ->
            eventTab(state, now, onEventKindSelected, onRetry, onOpenInBrowser) { event ->
              breadcrumbsSection(event.breadcrumbs, state.showAllBreadcrumbs, onShowAllBreadcrumbs)
            }
          // The issue's tags across all its events, so no event selector here.
          DetailTab.Tags -> tagsSection(state.tagsTabContent(), onRetry, onOpenInBrowser)
          DetailTab.Details ->
            eventTab(state, now, onEventKindSelected, onRetry, onOpenInBrowser) { event -> detailsSection(event.context) }
        }
        // Must stay the last item: tabFillerPx takes everything between the tab row and it as the tab's content.
        item(key = "bottom_space") { Spacer(Modifier.height(with(LocalDensity.current) { fillerPx.toDp() })) }
      }
    }
  }
}

/** A tab about the shown event: the event selector and the event's line first, then [content] once the event has loaded. */
private fun LazyListScope.eventTab(
  state: IssueDetailUiState,
  now: Instant,
  onEventKindSelected: (EventKind) -> Unit,
  onRetry: () -> Unit,
  onOpenInBrowser: () -> Unit,
  content: LazyListScope.(IssueEvent) -> Unit,
) {
  item(key = "event_header") { EventHeader(state.selectedKind, state.event, now, onEventKindSelected) }
  val event = state.event
  val eventError = state.eventError
  when {
    event != null -> content(event)
    state.isLoadingEvent -> item(key = "event_loading") { SectionLoading(stringResource(R.string.detail_event_loading)) }
    // The banner above already explains a rejected token.
    eventError != null && !eventError.needsReconnect() ->
      item(key = "event_error") { TabErrorPanel(eventError, onRetry, onOpenInBrowser) }
  }
}

/** The Recommended / Latest / Oldest chips and, once loaded, when the event happened, its release and environment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventHeader(selected: EventKind, event: IssueEvent?, now: Instant, onSelected: (EventKind) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Wraps at large font sizes, so the last chip stays on screen.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** "2m ago · release 1.4.2 · production" in mono; TalkBack hears the exact date and time instead of "2m ago". */
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
    style = MaterialTheme.typography.bodySmall.mono(),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.semantics { contentDescription = spoken },
  )
}

@Composable
private fun CenteredPanel(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun LoadingPanel(description: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.semantics { contentDescription = description })
  }
}
