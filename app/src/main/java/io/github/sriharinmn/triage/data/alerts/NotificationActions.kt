package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.IssueActionRepository
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.issues.IssueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The two buttons a single-issue notification has besides Open. */
enum class AlertAction {
  Resolve,
  Archive;

  /** The same change as the in-app action, so Sentry gets M5's exact request. */
  fun toIssueAction(): IssueAction =
    when (this) {
      Resolve -> IssueAction.Resolve
      Archive -> IssueAction.Archive
    }
}

/**
 * A Resolve or Archive tapped on a notification. This is all that travels in intents and WorkManager data: ids, the
 * action and the org, never the token or the account.
 */
data class NotificationActionRequest(
  val issueId: String,
  val shortId: String,
  val orgSlug: String,
  val action: AlertAction,
)

/** How an issue's notification changes while its action goes out. */
interface ActionNotifier {
  /** The Undo window is over and the action is going out, so Undo is no longer offered. */
  fun showSending(request: NotificationActionRequest)

  /** The issue's notification goes: its action went through, or the account it was for is gone. */
  fun cancelIssue(issueId: String)

  /** Sentry refused the action or couldn't be reached: says why, with Open. Never retried by itself. */
  fun showFailed(request: NotificationActionRequest, error: DetailError)
}

/**
 * Sends a notification's Resolve or Archive once its Undo window is over, with the same request as the in-app action and
 * through the app-wide rate-limit gate. It loads the account itself, and drops the action if that account is gone or is
 * for another org.
 */
class NotificationActionSender(
  private val accounts: Flow<Account?>,
  private val actions: IssueActionRepository,
  private val issues: IssueRepository,
  private val notifier: ActionNotifier,
) {
  suspend fun send(request: NotificationActionRequest) {
    val account = accounts.first()
    if (account == null || account.orgSlug != request.orgSlug) {
      // Disconnected, or connected to another org, since the tap: the action was for an account that's gone.
      notifier.cancelIssue(request.issueId)
      return
    }
    notifier.showSending(request)
    when (val result = actions.apply(account, request.issueId, request.action.toIssueAction())) {
      is DetailResult.Success -> {
        issues.forget(request.issueId)
        notifier.cancelIssue(request.issueId)
      }
      is DetailResult.Failure -> {
        // Merged or deleted in Sentry: it won't come back on a refresh, so the inbox stops showing it now.
        if (result.error == DetailError.NotFound) issues.forget(request.issueId)
        notifier.showFailed(request, result.error)
      }
    }
  }
}
