package io.github.sriharinmn.triage.ui.detail

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.IssueStatus

@StringRes
internal fun EventKind.labelRes(): Int =
  when (this) {
    EventKind.Recommended -> R.string.detail_event_recommended
    EventKind.Latest -> R.string.detail_event_latest
    EventKind.Oldest -> R.string.detail_event_oldest
  }

/** The label for a closed issue; null while it is unresolved. */
@StringRes
internal fun IssueStatus.labelRes(): Int? =
  when (this) {
    IssueStatus.Unresolved -> null
    IssueStatus.Resolved -> R.string.detail_status_resolved
    IssueStatus.Archived -> R.string.detail_status_archived
  }

/**
 * What went wrong, in a sentence. A 404 or a too-large error shown with this is about an event; a missing or too-large
 * issue has its own panel.
 */
@Composable
internal fun detailErrorMessage(error: DetailError): String =
  when (error) {
    DetailError.NotFound -> stringResource(R.string.detail_error_event_not_found)
    DetailError.Unauthorized -> stringResource(R.string.inbox_error_unauthorized)
    DetailError.Forbidden -> stringResource(R.string.detail_error_forbidden)
    is DetailError.RateLimited -> stringResource(R.string.inbox_error_rate_limited, error.retryAfterSeconds.coerceAtLeast(1))
    DetailError.Network -> stringResource(R.string.inbox_error_network)
    DetailError.Server -> stringResource(R.string.inbox_error_server)
    DetailError.TooLarge -> stringResource(R.string.detail_error_too_large)
  }
