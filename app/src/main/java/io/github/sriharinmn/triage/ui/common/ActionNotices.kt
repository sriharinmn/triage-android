package io.github.sriharinmn.triage.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.detail.DetailError

/**
 * Shows the latest issue action in [snackbarHostState]. While the action waits out its Undo window, the snackbar reads
 * "Resolved DEMO-1 · Undo". The notice is cleared the moment sending starts (the end of the window), which hides the
 * snackbar. If the action failed, the snackbar says why and offers Retry, unless retrying can't help (the issue is
 * gone, or the token can't do it). Material 3 snackbars can't be swiped away: a pending notice is only dismissed in code
 * or by the accessibility dismiss action, which sends its action at once ([onDismissed]).
 */
@Composable
internal fun ActionNoticeEffect(
  notice: ActionNotice?,
  snackbarHostState: SnackbarHostState,
  onUndo: (ActionNotice.Pending) -> Unit,
  onRetry: (ActionNotice.Failed) -> Unit,
  onDismissed: (ActionNotice) -> Unit,
) {
  val message = notice?.let { actionNoticeMessage(it) }
  val undo = stringResource(R.string.action_undo)
  val retry = stringResource(R.string.inbox_retry)
  // Keyed on the notice: once the action is sent or undone the notice changes, which cancels (hides) its snackbar
  // without calling back.
  LaunchedEffect(notice) {
    if (notice == null || message == null) return@LaunchedEffect
    val pending = notice is ActionNotice.Pending
    val retryable = notice is ActionNotice.Failed && notice.error !in RETRY_CANT_HELP
    val result =
      snackbarHostState.showSnackbar(
        message,
        actionLabel = if (pending) undo else retry.takeIf { retryable },
        withDismissAction = !pending,
        duration = if (pending) SnackbarDuration.Indefinite else SnackbarDuration.Long,
      )
    when {
      result != SnackbarResult.ActionPerformed -> onDismissed(notice)
      notice is ActionNotice.Pending -> onUndo(notice)
      notice is ActionNotice.Failed -> onRetry(notice)
    }
  }
}

/** Failures a retry would only repeat: the issue is gone, or only a new token can help. */
private val RETRY_CANT_HELP = setOf(DetailError.NotFound, DetailError.Unauthorized, DetailError.Forbidden)

/** "Resolved DEMO-1", "Assigned DEMO-1 to Ana Silva", or "Couldn't resolve DEMO-1. Couldn't reach Sentry. …" */
@Composable
internal fun actionNoticeMessage(notice: ActionNotice): String {
  val shortId = notice.action.shortId
  val action = notice.action.action
  return when (notice) {
    is ActionNotice.Pending ->
      when (action) {
        IssueAction.Resolve -> stringResource(R.string.action_resolved, shortId)
        IssueAction.Archive -> stringResource(R.string.action_archived, shortId)
        IssueAction.MarkReviewed -> stringResource(R.string.action_reviewed, shortId)
        is IssueAction.Assign ->
          action.member?.let { stringResource(R.string.action_assigned, shortId, it.name) }
            ?: stringResource(R.string.action_unassigned, shortId)
      }
    is ActionNotice.Failed -> {
      if (notice.error == DetailError.NotFound) return stringResource(R.string.action_error_not_found, shortId)
      val what =
        when (action) {
          IssueAction.Resolve -> stringResource(R.string.action_resolve_failed, shortId)
          IssueAction.Archive -> stringResource(R.string.action_archive_failed, shortId)
          IssueAction.MarkReviewed -> stringResource(R.string.action_review_failed, shortId)
          is IssueAction.Assign -> stringResource(R.string.action_assign_failed, shortId)
        }
      stringResource(R.string.action_failed_with_reason, what, actionErrorReason(notice.error))
    }
  }
}

@Composable
private fun actionErrorReason(error: DetailError): String =
  when (error) {
    DetailError.Forbidden -> stringResource(R.string.action_error_forbidden)
    DetailError.Unauthorized -> stringResource(R.string.inbox_error_unauthorized)
    is DetailError.RateLimited -> stringResource(R.string.inbox_error_rate_limited, error.retryAfterSeconds.coerceAtLeast(1))
    DetailError.Network -> stringResource(R.string.inbox_error_network)
    DetailError.NotFound,
    DetailError.Server,
    DetailError.TooLarge -> stringResource(R.string.inbox_error_server)
  }
