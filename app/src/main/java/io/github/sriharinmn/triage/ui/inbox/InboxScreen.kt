package io.github.sriharinmn.triage.ui.inbox

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.issues.InboxError
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueSort
import io.github.sriharinmn.triage.ui.common.ActionNoticeEffect
import io.github.sriharinmn.triage.ui.common.DisconnectDialog
import io.github.sriharinmn.triage.ui.common.ReconnectBanner
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay

/** Test tags for the inbox's interactive and scrollable parts. */
object InboxTags {
  const val LIST = "inbox_list"
  const val MENU = "inbox_menu"
  const val PROJECT_FILTER = "inbox_filter_project"
  const val ENVIRONMENT_FILTER = "inbox_filter_environment"
  const val SORT_FILTER = "inbox_filter_sort"
  const val LOAD_MORE_RETRY = "inbox_load_more_retry"
  const val ERROR_ACTION = "inbox_error_action"

  fun row(issueId: String) = "inbox_issue_$issueId"
}

/** Ask for the next page once the last visible row is this close to the end of the list. */
private const val LOAD_MORE_THRESHOLD = 5

/** The inbox: tabs, filters and the issue list, wired to [viewModel]; tapping an issue calls [onOpenIssue]. */
@Composable
fun InboxScreen(viewModel: InboxViewModel, onOpenIssue: (issueId: String) -> Unit, modifier: Modifier = Modifier) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  // "3m ago" labels tick every minute and catch up whenever fresh data arrives.
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

  // Over a cached list a failed refresh is a snackbar; with nothing cached, the full-screen message says it instead.
  // The error stays set during the next refresh, so keying on isRefreshing makes a repeated failure show it again:
  // starting a refresh cancels (dismisses) the old snackbar, and the failure that ends it shows a new one.
  LaunchedEffect(error, hasIssues, state.isRefreshing) {
    if (!state.isRefreshing && errorText != null && hasIssues && !error.needsReconnect()) {
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
  ActionNoticeEffect(state.notice, snackbarHostState, onUndo = onUndo, onRetry = onRetryAction, onDismissed = onNoticeDismissed)

  Scaffold(
    modifier = modifier,
    topBar = { InboxTopBar(orgName = state.orgName, onDisconnect = { confirmingDisconnect = true }) },
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
      val refreshedAt = state.refreshedAt
      if (hasIssues && errorText != null) {
        if (error.needsReconnect()) {
          ReconnectBanner(message = errorText, onReconnect = onReconnect, buttonTag = InboxTags.ERROR_ACTION)
        } else if (refreshedAt != null) {
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
          hasIssues -> key(state.filter) { IssueList(state, now, onIssueClick, onResolve, onArchive, onLoadMore, onRetryLoadMore) }
          // Retry (or a pull) shows progress instead of the unchanged error while the new attempt runs.
          state.isRefreshing -> LoadingPanel()
          errorText != null ->
            MessagePanel(
              message = errorText,
              actionLabel = stringResource(if (error.needsReconnect()) R.string.inbox_reconnect else R.string.inbox_retry),
              onAction = if (error.needsReconnect()) onReconnect else onRefresh,
            )
          refreshedAt == null -> LoadingPanel()
          else -> MessagePanel(message = stringResource(state.filter.tab.emptyRes()))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxTopBar(orgName: String, onDisconnect: () -> Unit) {
  var menuOpen by remember { mutableStateOf(false) }
  TopAppBar(
    title = { Text(orgName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    actions = {
      Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(InboxTags.MENU)) {
          Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.inbox_menu))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.inbox_disconnect)) },
            onClick = {
              menuOpen = false
              onDisconnect()
            },
          )
        }
      }
    },
  )
}

@Composable
private fun IssueList(
  state: InboxUiState,
  now: Instant,
  onIssueClick: (Issue) -> Unit,
  onResolve: (Issue) -> Unit,
  onArchive: (Issue) -> Unit,
  onLoadMore: () -> Unit,
  onRetryLoadMore: () -> Unit,
) {
  val listState = rememberLazyListState()
  val nearEnd by remember {
    derivedStateOf {
      val info = listState.layoutInfo
      val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
      lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
    }
  }
  // The ViewModel ignores calls while a page is loading, after a failure, or on the last page. It also ignores them during
  // a refresh, so the effect re-runs when the refresh ends to pick up a request that was dropped.
  LaunchedEffect(nearEnd, state.hasMore, state.issues.size, state.isRefreshing) {
    if (nearEnd && state.hasMore && !state.isRefreshing) onLoadMore()
  }

  LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(InboxTags.LIST)) {
    items(state.issues, key = { it.id }) { issue ->
      SwipeableIssueRow(
        issue = issue,
        now = now,
        onClick = { onIssueClick(issue) },
        onResolve = { onResolve(issue) },
        onArchive = { onArchive(issue) },
        modifier = Modifier.testTag(InboxTags.row(issue.id)),
      )
      HorizontalDivider()
    }
    if (state.isLoadingMore || state.loadMoreFailed) {
      item(key = "footer") { LoadMoreFooter(failed = state.loadMoreFailed, onRetry = onRetryLoadMore) }
    }
  }
}

@Composable
private fun LoadMoreFooter(failed: Boolean, onRetry: () -> Unit) {
  Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
    if (failed) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.inbox_load_more_failed), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry, modifier = Modifier.testTag(InboxTags.LOAD_MORE_RETRY)) {
          Text(stringResource(R.string.inbox_retry))
        }
      }
    } else {
      CircularProgressIndicator(Modifier.size(24.dp))
    }
  }
}

@Composable
private fun MessagePanel(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
  CenteredScrollable {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
      if (actionLabel != null) {
        OutlinedButton(onClick = onAction, modifier = Modifier.testTag(InboxTags.ERROR_ACTION)) { Text(actionLabel) }
      }
    }
  }
}

@Composable
private fun LoadingPanel() {
  val description = stringResource(R.string.inbox_loading)
  CenteredScrollable { CircularProgressIndicator(Modifier.semantics { contentDescription = description }) }
}

/** Fills the space and centres its content, but stays scrollable so pull-to-refresh still works on it. */
@Composable
private fun CenteredScrollable(content: @Composable () -> Unit) {
  LazyColumn(Modifier.fillMaxSize()) {
    item { Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() } }
  }
}

private fun InboxError?.needsReconnect(): Boolean = this == InboxError.Unauthorized || this == InboxError.Forbidden

@Composable
private fun errorMessage(error: InboxError): String =
  when (error) {
    InboxError.Unauthorized -> stringResource(R.string.inbox_error_unauthorized)
    InboxError.Forbidden -> stringResource(R.string.inbox_error_forbidden)
    is InboxError.RateLimited ->
      error.retryAfterSeconds?.let { stringResource(R.string.inbox_error_rate_limited, it.coerceAtLeast(1)) }
        ?: stringResource(R.string.inbox_error_rate_limited_unknown)
    InboxError.Network -> stringResource(R.string.inbox_error_network)
    InboxError.Server -> stringResource(R.string.inbox_error_server)
    InboxError.Storage -> stringResource(R.string.inbox_error_storage)
  }
