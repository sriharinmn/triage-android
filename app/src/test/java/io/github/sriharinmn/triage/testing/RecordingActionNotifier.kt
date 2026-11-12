package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.alerts.ActionNotifier
import io.github.sriharinmn.triage.data.alerts.NotificationActionRequest
import io.github.sriharinmn.triage.data.detail.DetailError

/** Records how a notification action's notification would change. */
class RecordingActionNotifier : ActionNotifier {
  val calls = mutableListOf<String>()

  override fun showSending(request: NotificationActionRequest) {
    calls += "sending:${request.issueId}"
  }

  override fun cancelIssue(issueId: String) {
    calls += "cancel:$issueId"
  }

  override fun showFailed(request: NotificationActionRequest, error: DetailError) {
    calls += "failed:${request.issueId}:$error"
  }
}
