package io.github.sriharinmn.triage.data.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException

/** Runs one background check. A failed round never asks WorkManager to retry: the next period simply tries again. */
class AlertCheckWorker(
  context: Context,
  params: WorkerParameters,
  private val check: suspend () -> Unit,
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    try {
      check()
    } catch (_: IOException) {
      // The alert stores or the cache couldn't be read or written (a full phone); the next period tries again.
    }
    return Result.success()
  }
}

/** Sends one notification action after its Undo window. Only ids, the action and the org arrive here, never a token. */
class NotificationActionWorker(
  context: Context,
  params: WorkerParameters,
  private val send: suspend (NotificationActionRequest) -> Unit,
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val request = inputData.toNotificationActionRequest() ?: return Result.failure()
    try {
      send(request)
    } catch (_: IOException) {
      // A store couldn't be read; like an in-app action, nothing is retried by itself.
    }
    return Result.success()
  }
}

/**
 * Builds the alert workers with the app's dependencies (WorkManager's default factory can't: they take a third
 * parameter). Anything else falls back to WorkManager's default.
 */
class TriageWorkerFactory(
  private val check: suspend () -> Unit,
  private val send: suspend (NotificationActionRequest) -> Unit,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? =
    when (workerClassName) {
      AlertCheckWorker::class.java.name -> AlertCheckWorker(appContext, workerParameters, check)
      NotificationActionWorker::class.java.name -> NotificationActionWorker(appContext, workerParameters, send)
      else -> null
    }
}

/** What a queued notification action keeps in WorkManager: ids, the action and the org. */
internal fun NotificationActionRequest.toWorkData(): Data =
  workDataOf(KEY_ISSUE_ID to issueId, KEY_SHORT_ID to shortId, KEY_ORG_SLUG to orgSlug, KEY_ACTION to action.name)

/** Null if anything is missing or the action is unknown. */
internal fun Data.toNotificationActionRequest(): NotificationActionRequest? {
  val issueId = getString(KEY_ISSUE_ID) ?: return null
  val shortId = getString(KEY_SHORT_ID) ?: return null
  val orgSlug = getString(KEY_ORG_SLUG) ?: return null
  val action = AlertAction.entries.firstOrNull { it.name == getString(KEY_ACTION) } ?: return null
  return NotificationActionRequest(issueId, shortId, orgSlug, action)
}

private const val KEY_ISSUE_ID = "issue_id"
private const val KEY_SHORT_ID = "short_id"
private const val KEY_ORG_SLUG = "org_slug"
private const val KEY_ACTION = "action"
