package io.github.sriharinmn.triage.ui.inbox

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.issues.InboxError
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueSort
import io.github.sriharinmn.triage.ui.alerts.rememberNotificationPermissionRequest
import io.github.sriharinmn.triage.ui.common.ActionNoticeEffect
import io.github.sriharinmn.triage.ui.common.DisconnectDialog
import io.github.sriharinmn.triage.ui.common.OrgInitialTile
import io.github.sriharinmn.triage.ui.common.ReconnectBanner
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay

/** Test tags for the inbox's interactive and scrollable parts. */
object InboxTags {
  const val LIST = "inbox_list"
  const val SETTINGS = "inbox_settings"
  const val PROJECT_FILTER = "inbox_filter_project"
  const val ENVIRONMENT_FILTER = "inbox_filter_environment"
  const val SORT_FILTER = "inbox_filter_sort"
  const val LOAD_MORE_RETRY = "inbox_load_more_retry"
  const val ERROR_ACTION = "inbox_error_action"
  const val CLEAR_FILTERS = "inbox_clear_filters"
  const val OFFLINE_BANNER = "inbox_offline_banner"
  const val ALERTS_CARD = "inbox_alerts_card"
  const val ALERTS_CARD_TURN_ON = "inbox_alerts_card_turn_on"
  const val ALERTS_CARD_NOT_NOW = "inbox_alerts_card_not_now"

  fun row(issueId: String) = "inbox_issue_$issueId"
}

/**
 * The inbox: tabs, filters and the issue list, wired to [viewModel]; tapping an issue calls [onOpenIssue], and the gear
 * calls [onOpenSettings].
 */
@Composable
fun InboxScreen(
  viewModel: InboxViewModel,
  onOpenIssue: (issueId: String) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  // The card's Turn on shows Android's notification popup first (Android 13+); alerts go on only if it's allowed.
  val requestNotifications = rememberNotificationPermissionRequest(viewModel::onAlertsCardTurnOn)
  val snackbarHostState = remember { SnackbarHostState() }
  // "3m" labels tick every minute and catch up whenever fresh data arrives.
  val now by
    produceState(initialValue = Instant.now(), state.refreshedAt) {
      while (true) {
        value = Instant.now()
        delay(60_000)
      }
    }

  InboxContent(
    state = state,
    now = now,
    onTabSelected = viewModel::onTabSelected,
    onProjectSelected = viewModel::onProjectSelected,
    onEnvironmentSelected = viewModel::onEnvironmentSelected,
    onSortSelected = viewModel::onSortSelected,
    onClearFilters = viewModel::onClearFilters,
    onRefresh = viewModel::onRefresh,
    onLoadMore = viewModel::onLoadMore,
    onRetryLoadMore = viewModel::onRetryLoadMore,
    onIssueClick = { issue -> onOpenIssue(issue.id) },
    onResolve = viewModel::onResolve,
    onArchive = viewModel::onArchive,
    onUndo = viewModel::onUndo,
    onRetryAction = viewModel::onRetryAction,
    onNoticeDismissed = viewModel::onNoticeDismissed,
    onDisconnectConfirmed = viewModel::onDisconnectConfirmed,
    onDisconnectFailureShown = viewModel::onDisconnectFailureShown,
    onAlertsCardTurnOn = requestNotifications,
    onAlertsCardNotNow = viewModel::onAlertsCardNotNow,
    onOpenSettings = onOpenSettings,
    snackbarHostState = snackbarHostState,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxContent(
  state: InboxUiState,
  now: Instant,
  onTabSelected: (InboxTab) -> Unit,
  onProjectSelected: (String?) -> Unit,
  onEnvironmentSelected: (String?) -> Unit,
  onSortSelected: (IssueSort) -> Unit,
  onClearFilters: () -> Unit,
  onRefresh: () -> Unit,
  onLoadMore: () -> Unit,
  onRetryLoadMore: () -> Unit,
  onIssueClick: (Issue) -> Unit,
  onResolve: (Issue) -> Unit,
  onArchive: (Issue) -> Unit,
  onUndo: (ActionNotice.Pending) -> Unit,
  onRetryAction: (ActionNotice.Failed) -> Unit,
  onNoticeDismissed: (ActionNotice) -> Unit,
  onDisconnectConfirmed: () -> Unit,
  onDisconnectFailureShown: () -> Unit,
  onAlertsCardTurnOn: () -> Unit,
  onAlertsCardNotNow: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  var confirmingDisconnect by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val error = state.error
  val errorText = error?.let { errorMessage(it) }
  val retryLabel = stringResource(R.string.inbox_retry)
  val disconnectFailedText = stringResource(R.string.inbox_disconnect_failed)
  val hasIssues = state.issues.isNotEmpty()
  // A rejected or under-scoped token can only be replaced, and Sentry shows a token only once (a 403 may also be fixable
  // by changing the token's permissions on Sentry), so Reconnect asks first, with the same dialog as Disconnect.
  val onReconnect = { confirmingDisconnect = true }

  // Over a cached list a failed refresh is a snackbar (offline, the banner says it instead); with nothing cached, the
  // full-screen panel does. The error stays set during the next refresh, so keying on isRefreshing makes a repeated
  // failure show it again: starting a refresh cancels (dismisses) the old snackbar, and the failure that ends it shows a
  // new one.
  LaunchedEffect(error, hasIssues, state.isRefreshing) {
    val snackbar = !state.isRefreshing && errorText != null && hasIssues && !error.needsReconnect() && error != InboxError.Network
    if (snackbar) {
      val result = snackbarHostState.showSnackbar(errorText, actionLabel = retryLabel, duration = SnackbarDuration.Long)
      if (result == SnackbarResult.ActionPerformed) onRefresh()
    }
  }
  LaunchedEffect(state.disconnectFailed) {
    if (state.disconnectFailed) {
      snackbarHostState.showSnackbar(disconnectFailedText)
      onDisconnectFailureShown()
    }
  }
  // Action notices get their own host: queued behind a refresh error (shown for 10 s), Undo would never show before its
  // window ended.
  val noticeHostState = remember { SnackbarHostState() }
  ActionNoticeEffect(
    state.notice,
    noticeHostState,
    onUndo = onUndo,
    onRetry = onRetryAction,
    onDismissed = onNoticeDismissed,
  )

  Scaffold(
    modifier = modifier,
    topBar = { InboxTopBar(orgName = state.orgName, onOpenSettings = onOpenSettings) },
    // A notice covers any other snackbar, which shows once the notice goes.
    snackbarHost = {
      if (noticeHostState.currentSnackbarData != null) SnackbarHost(noticeHostState) else SnackbarHost(snackbarHostState)
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      PrimaryScrollableTabRow(selectedTabIndex = state.filter.tab.ordinal, edgePadding = 8.dp) {
        InboxTab.entries.forEach { tab ->
          Tab(
            selected = tab == state.filter.tab,
            onClick = { onTabSelected(tab) },
            text = { Text(stringResource(tab.labelRes())) },
          )
        }
      }
      InboxFilterRow(
        state = state,
        onProjectSelected = onProjectSelected,
        onEnvironmentSelected = onEnvironmentSelected,
        onSortSelected = onSortSelected,
      )
      if (state.showAlertsCard) AlertsPromptCard(onTurnOn = onAlertsCardTurnOn, onNotNow = onAlertsCardNotNow)
      val refreshedAt = state.refreshedAt
      if (hasIssues && errorText != null) {
        when {
          error.needsReconnect() -> ReconnectBanner(message = errorText, onReconnect = onReconnect, buttonTag = InboxTags.ERROR_ACTION)
          error == InboxError.Network -> OfflineBanner(refreshedAt = refreshedAt, onRetry = onRefresh)
          refreshedAt != null ->
            Text(
              stringResource(R.string.inbox_updated_stale, DateFormat.getTimeFormat(context).format(Date(refreshedAt.toEpochMilli()))),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
      }
      PullToRefreshBox(
        isRefreshing = state.isRefreshing && hasIssues,
        onRefresh = onRefresh,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) {
        when {
          // A new key per filter resets the scroll position when the list changes.
          hasIssues ->
            key(state.filter) {
              IssueList(state, now, onIssueClick, onResolve, onArchive, onLoadMore, onRetryLoadMore)
            }
          // Retry (or a pull) shows progress instead of the unchanged error while the new attempt runs.
          state.isRefreshing -> LoadingPanel()
          error != null -> InboxErrorPanel(error, onRetry = onRefresh, onReconnect = onReconnect)
          refreshedAt == null -> LoadingPanel()
          state.hasFilters -> NoMatchesPanel(onClearFilters)
          else -> EmptyTabPanel(state.filter.tab)
        }
      }
    }
  }

  if (confirmingDisconnect) {
    DisconnectDialog(
      onConfirm = {
        confirmingDisconnect = false
        onDisconnectConfirmed()
      },
      onDismiss = { confirmingDisconnect = false },
    )
  }
}

/** The org's initial and name, and the gear to Settings (Disconnect, Alerts, Appearance and About live there). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxTopBar(orgName: String, onOpenSettings: () -> Unit) {
  TopAppBar(
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OrgInitialTile(orgName)
        Text(orgName, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    },
    actions = {
      IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(InboxTags.SETTINGS)) {
        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.inbox_settings))
      }
    },
  )
}
