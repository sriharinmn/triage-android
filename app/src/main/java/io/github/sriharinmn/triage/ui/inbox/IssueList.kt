package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.Issue
import java.time.Instant

/** Ask for the next page once the last visible row is this close to the end of the list. */
private const val LOAD_MORE_THRESHOLD = 5

/** The issue rows, flat on the background with hairlines between them, and the next page as the end comes near. */
@Composable
internal fun IssueList(
  state: InboxUiState,
  now: Instant,
  onIssueClick: (Issue) -> Unit,
  onResolve: (Issue) -> Unit,
  onArchive: (Issue) -> Unit,
  onLoadMore: () -> Unit,
  onRetryLoadMore: () -> Unit,
) {
  // One scroll state per filter, so a new tab or filter opens at its top. It is saveable so it survives rotation and a trip
  // to an issue's detail screen.
  val listState = rememberSaveable(state.filter, saver = LazyListState.Saver) { LazyListState() }
  // LazyColumn keeps the first visible issue in place when issues are added above it, which would hide a newer issue just
  // off the top. When the list is at the very top, ask it to stay there. The position is read without observation so
  // scrolling doesn't recompose this list.
  SideEffect {
    val atTop = Snapshot.withoutReadObservation { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    if (atTop) listState.requestScrollToItem(0)
  }
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

  val hairline = MaterialTheme.colorScheme.outlineVariant
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
      HorizontalDivider(color = hairline)
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
