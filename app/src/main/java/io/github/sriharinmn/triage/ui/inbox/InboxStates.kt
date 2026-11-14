package io.github.sriharinmn.triage.ui.inbox

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.InboxError
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.ui.common.EmptyState
import java.time.Instant
import java.util.Date

/** A rejected or under-scoped token, which only reconnecting (with a new token) can fix. */
internal fun InboxError?.needsReconnect(): Boolean = this == InboxError.Unauthorized || this == InboxError.Forbidden

/** What went wrong, in a sentence: the snackbar over a cached list, and the help line of the full-screen panel. */
@Composable
internal fun errorMessage(error: InboxError): String =
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

/** Nothing cached and the load failed: what happened, and the one action that helps. */
@Composable
internal fun InboxErrorPanel(error: InboxError, onRetry: () -> Unit, onReconnect: () -> Unit) {
  val reconnect = error.needsReconnect()
  val icon = if (error == InboxError.Network) R.drawable.ic_cloud_off else R.drawable.ic_warning
  val headline =
    when (error) {
      InboxError.Network -> stringResource(R.string.state_cant_reach)
      InboxError.Unauthorized -> stringResource(R.string.state_reconnect)
      InboxError.Forbidden -> stringResource(R.string.state_missing_permissions)
      is InboxError.RateLimited -> stringResource(R.string.state_rate_limited)
      InboxError.Server -> stringResource(R.string.state_server)
      InboxError.Storage -> stringResource(R.string.state_storage)
    }
  val help = if (error == InboxError.Network) stringResource(R.string.state_cant_reach_help) else errorMessage(error)
  CenteredScrollable {
    EmptyState(
      icon = icon,
      headline = headline,
      help = help,
      actionLabel = stringResource(if (reconnect) R.string.inbox_reconnect else R.string.inbox_retry),
      onAction = if (reconnect) onReconnect else onRetry,
      actionTag = InboxTags.ERROR_ACTION,
    )
  }
}

/** The tab is empty because a project or environment is chosen. */
@Composable
internal fun NoMatchesPanel(onClearFilters: () -> Unit) {
  CenteredScrollable {
    EmptyState(
      icon = R.drawable.ic_filter_list_off,
      headline = stringResource(R.string.inbox_no_matches),
      help = stringResource(R.string.inbox_no_matches_help),
      actionLabel = stringResource(R.string.inbox_clear_filters),
      onAction = onClearFilters,
      actionTag = InboxTags.CLEAR_FILTERS,
    )
  }
}

/** The tab is empty for real ("All clear"). */
@Composable
internal fun EmptyTabPanel(tab: InboxTab) {
  CenteredScrollable {
    EmptyState(
      icon = R.drawable.ic_check_circle,
      headline = stringResource(tab.emptyHeadlineRes()),
      help = stringResource(tab.emptyHelpRes()),
    )
  }
}

/** Offline over a cached list: a slim strip saying how old the list is, with Retry, instead of an error. */
@Composable
internal fun OfflineBanner(refreshedAt: Instant?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val text =
    refreshedAt?.let {
      stringResource(R.string.inbox_offline_since, DateFormat.getTimeFormat(context).format(Date(it.toEpochMilli())))
    } ?: stringResource(R.string.inbox_offline)
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.fillMaxWidth().testTag(InboxTags.OFFLINE_BANNER),
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, modifier = Modifier.size(18.dp))
      Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
      )
      TextButton(onClick = onRetry) { Text(stringResource(R.string.inbox_retry)) }
    }
  }
}

@Composable
internal fun LoadingPanel() {
  val description = stringResource(R.string.inbox_loading)
  CenteredScrollable { CircularProgressIndicator(Modifier.semantics { contentDescription = description }) }
}

/** Fills the space and centres its content, but stays scrollable so pull-to-refresh still works on it. */
@Composable
internal fun CenteredScrollable(content: @Composable () -> Unit) {
  LazyColumn(Modifier.fillMaxSize()) {
    item { Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() } }
  }
}
