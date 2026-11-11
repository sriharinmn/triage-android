package io.github.sriharinmn.triage.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sriharinmn.triage.data.alerts.ALERTS_WORK_TAG
import io.github.sriharinmn.triage.data.alerts.AlertCheckWorker

/**
 * Debug builds only: `adb shell am broadcast -n io.github.sriharinmn.triage/.debug.CheckNowReceiver` runs one alert check
 * now, for testing on a phone. WorkManager won't run the periodic check early (it skips a job forced before its interval
 * is up), so this queues a one-time run of the same worker; the runner makes it wait for any check already running.
 */
class CheckNowReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val request =
      OneTimeWorkRequestBuilder<AlertCheckWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        // Tagged like all alert work, so forgetting the account cancels it too.
        .addTag(ALERTS_WORK_TAG)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(CHECK_NOW_WORK, ExistingWorkPolicy.REPLACE, request)
  }

  private companion object {
    const val CHECK_NOW_WORK = "alert-check-now"
  }
}
