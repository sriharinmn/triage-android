package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.account.StoredAccount
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.IssueActionRepository
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.issues.IssueRepository

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

  /** The saved connection couldn't be read, so nothing was sent: says so, and opens Triage to check it. */
  fun showAccountUnreadable(request: NotificationActionRequest)
}

/**
 * Sends a notification's Resolve or Archive once its Undo window is over, with the same request as the in-app action and
 * through the app-wide rate-limit gate. It reads the account itself: a missing account or another org drops the action
 * quietly, and an unreadable one says so.
 */
class NotificationActionSender(
  /** One read of the saved connection, at send time. */
  private val readAccount: suspend () -> StoredAccount,
  private val actions: IssueActionRepository,
  private val issues: IssueRepository,
  private val notifier: ActionNotifier,
) {
  suspend fun send(request: NotificationActionRequest) {
    val account =
      when (val stored = readAccount()) {
        is StoredAccount.Connected -> stored.account
        // Disconnected since the tap: the action was for an account that's gone.
        StoredAccount.Missing -> return notifier.cancelIssue(request.issueId)
        // Still connected as far as anyone knows, but unusable now: dropping it silently would look like it worked.
        StoredAccount.Unreadable -> return notifier.showAccountUnreadable(request)
      }
    if (account.orgSlug != request.orgSlug) {
      // Connected to another org since the tap.
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
