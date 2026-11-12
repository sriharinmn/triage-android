package io.github.sriharinmn.triage.ui.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.sriharinmn.triage.TriageApplication

/**
 * Resolve, Archive and Undo on a notification. Not exported: only Triage's own notifications can reach it.
 * Resolve or Archive swaps the notification for "Resolving DEMO-3… Undo" and queues the send for after the Undo window,
 * even if the app is closed. Undo drops the queued send (nothing reaches Sentry) and puts the notification back.
 */
class NotificationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val request = intent.toActionRequest() ?: return
    val container = (context.applicationContext as TriageApplication).container
    when (intent.action) {
      NotificationIntents.ACTION_RESOLVE,
      NotificationIntents.ACTION_ARCHIVE -> {
        container.alertNotifier.showPending(request)
        container.alertWork.enqueueAction(request)
      }
      NotificationIntents.ACTION_UNDO -> {
        container.alertWork.cancelAction(request.issueId)
        container.alertNotifier.restore(request.issueId)
      }
    }
  }
}
