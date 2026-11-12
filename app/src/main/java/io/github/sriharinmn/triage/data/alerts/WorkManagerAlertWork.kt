package io.github.sriharinmn.triage.data.alerts

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sriharinmn.triage.data.actions.PendingActions
import java.util.concurrent.TimeUnit

/** Unique periodic work for the background check. */
const val ALERT_CHECK_WORK = "alert-check"

/** On every piece of alert work, so forgetting the account cancels them all at once. */
const val ALERTS_WORK_TAG = "triage-alerts"

/** Unique work per issue, so a second tap on the same issue replaces the first. */
fun actionWorkName(issueId: String): String = "notification-action-$issueId"

/**
 * The alert work in WorkManager: the periodic check, and each notification action waiting out its Undo window.
 * [workManager] is asked for only when needed, so building the app's container doesn't start WorkManager.
 */
class WorkManagerAlertWork(private val workManager: () -> WorkManager) : PeriodicCheckWork {
  override fun schedule(interval: CheckInterval) {
    val request =
      PeriodicWorkRequestBuilder<AlertCheckWorker>(interval.minutes, TimeUnit.MINUTES)
        .setConstraints(NETWORK)
        .addTag(ALERTS_WORK_TAG)
        .build()
    // UPDATE changes the interval of the existing work without resetting its timing; with no work yet it enqueues
    // this one, whose first check runs soon.
    workManager().enqueueUniquePeriodicWork(ALERT_CHECK_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
  }

  override fun cancel() {
    workManager().cancelUniqueWork(ALERT_CHECK_WORK)
  }

  /**
   * Sends [request] once the Undo window is over: never before, and a little later if the phone is saving battery.
   * There's no network constraint: offline, the send fails at once and says so instead of waiting for hours.
   */
  fun enqueueAction(request: NotificationActionRequest) {
    val work =
      OneTimeWorkRequestBuilder<NotificationActionWorker>()
        .setInitialDelay(PendingActions.UNDO_WINDOW.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .setInputData(request.toWorkData())
        .addTag(ALERTS_WORK_TAG)
        .build()
    workManager().enqueueUniqueWork(actionWorkName(request.issueId), ExistingWorkPolicy.REPLACE, work)
  }

  /** Undo: the waiting action is dropped and nothing reaches Sentry. */
  fun cancelAction(issueId: String) {
    workManager().cancelUniqueWork(actionWorkName(issueId))
  }

  /** Cancels the periodic check and every waiting action: the account is being forgotten. */
  fun cancelAll() {
    workManager().cancelAllWorkByTag(ALERTS_WORK_TAG)
  }

  private companion object {
    val NETWORK: Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
  }
}
