package io.github.sriharinmn.triage.data.alerts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The workers built through Triage's factory, run directly with WorkManager's test builder (no WorkManager instance). */
@RunWith(AndroidJUnit4::class)
class AlertWorkersTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val request = NotificationActionRequest("42", "DEMO-42", "example-org", AlertAction.Archive)
  private var checks = 0
  private var checkError: Exception? = null
  private val sent = mutableListOf<NotificationActionRequest>()
  private val factory =
    TriageWorkerFactory(
      check = {
        checks++
        checkError?.let { throw it }
      },
      send = { sent += it },
    )

  @Test
  fun theCheckWorker_runsOneCheck_andSucceeds() = runBlocking {
    val worker = TestListenableWorkerBuilder<AlertCheckWorker>(context).setWorkerFactory(factory).build()

    assertEquals(ListenableWorker.Result.success(), worker.doWork())
    assertEquals(1, checks)
  }

  @Test
  fun aStorageFailure_stillSucceeds_soWorkManagerNeverRetries() = runBlocking {
    checkError = IOException("disk full")
    val worker = TestListenableWorkerBuilder<AlertCheckWorker>(context).setWorkerFactory(factory).build()

    assertEquals(ListenableWorker.Result.success(), worker.doWork())
  }

  @Test
  fun theActionWorker_sendsTheRequestInItsData() = runBlocking {
    val worker =
      TestListenableWorkerBuilder<NotificationActionWorker>(context, inputData = request.toWorkData())
        .setWorkerFactory(factory)
        .build()

    assertEquals(ListenableWorker.Result.success(), worker.doWork())
    assertEquals(listOf(request), sent)
  }

  @Test
  fun unreadableData_failsWithoutSending() = runBlocking {
    val worker =
      TestListenableWorkerBuilder<NotificationActionWorker>(context, inputData = workDataOf("issue_id" to "42"))
        .setWorkerFactory(factory)
        .build()

    assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    assertTrue(sent.isEmpty())
  }

  @Test
  fun workData_carriesOnlyIdsTheActionAndTheOrg() {
    val data = request.toWorkData()

    assertEquals(setOf("issue_id", "short_id", "org_slug", "action"), data.keyValueMap.keys)
    assertEquals(request, data.toNotificationActionRequest())
  }
}
