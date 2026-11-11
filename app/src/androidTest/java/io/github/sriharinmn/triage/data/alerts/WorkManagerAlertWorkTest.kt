package io.github.sriharinmn.triage.data.alerts

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.sriharinmn.triage.data.actions.PendingActions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WorkManagerAlertWork against WorkManager's test instance: an in-memory WorkManager database, so the owner's real alert
 * work is never touched, and a factory whose workers only record what they were asked to do.
 */
@RunWith(AndroidJUnit4::class)
class WorkManagerAlertWorkTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val checks = AtomicInteger()
  private val sent = CopyOnWriteArrayList<NotificationActionRequest>()
  private val request = NotificationActionRequest("42", "DEMO-42", "example-org", AlertAction.Resolve)
  private lateinit var workManager: WorkManager
  private lateinit var driver: TestDriver
  private lateinit var work: WorkManagerAlertWork

  @Before
  fun setUp() {
    val configuration =
      Configuration.Builder()
        .setExecutor(SynchronousExecutor())
        .setWorkerFactory(TriageWorkerFactory(check = { checks.incrementAndGet() }, send = { sent += it }))
        .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    workManager = WorkManager.getInstance(context)
    driver = WorkManagerTestInitHelper.getTestDriver(context)!!
    work = WorkManagerAlertWork { workManager }
  }

  @After
  fun tearDown() {
    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  private fun actionInfos(issueId: String = "42"): List<WorkInfo> =
    workManager.getWorkInfosForUniqueWork(actionWorkName(issueId)).get()

  private fun checkInfos(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(ALERT_CHECK_WORK).get()

  private fun awaitUntil(what: String, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + 5_000
    while (!condition()) {
      if (SystemClock.uptimeMillis() > deadline) fail("timed out waiting for $what")
      Thread.sleep(20)
    }
  }

  @Test
  fun anAction_waitsOutTheUndoWindow_thenSendsItsRequest() {
    work.enqueueAction(request)
    awaitUntil("the action to be queued") { actionInfos().any { it.state == WorkInfo.State.ENQUEUED } }

    val info = actionInfos().single { it.state == WorkInfo.State.ENQUEUED }
    assertEquals(PendingActions.UNDO_WINDOW.inWholeMilliseconds, info.initialDelayMillis)
    assertTrue(ALERTS_WORK_TAG in info.tags)
    assertTrue(sent.isEmpty())

    driver.setInitialDelayMet(info.id)

    awaitUntil("the action to be sent") { sent.isNotEmpty() }
    assertEquals(listOf(request), sent)
  }

  @Test
  fun aSecondTapOnTheSameIssue_replacesTheFirst() {
    work.enqueueAction(request)
    work.enqueueAction(request.copy(action = AlertAction.Archive))
    awaitUntil("one waiting action") { actionInfos().count { it.state == WorkInfo.State.ENQUEUED } == 1 }

    driver.setInitialDelayMet(actionInfos().single { it.state == WorkInfo.State.ENQUEUED }.id)

    awaitUntil("the action to be sent") { sent.isNotEmpty() }
    assertEquals(listOf(request.copy(action = AlertAction.Archive)), sent)
  }

  @Test
  fun undo_dropsTheWaitingAction() {
    work.enqueueAction(request)
    work.cancelAction(request.issueId)

    awaitUntil("the action to be cancelled") { actionInfos().all { it.state == WorkInfo.State.CANCELLED } }
    assertTrue(sent.isEmpty())
  }

  @Test
  fun thePeriodicCheck_needsANetwork_andAnIntervalChangeKeepsTheSameWork() {
    work.schedule(CheckInterval.Minutes30)
    awaitUntil("the check to be scheduled") { checkInfos().any { it.state == WorkInfo.State.ENQUEUED } }
    val first = checkInfos().single { it.state == WorkInfo.State.ENQUEUED }
    assertEquals(TimeUnit.MINUTES.toMillis(30), first.periodicityInfo?.repeatIntervalMillis)
    assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
    assertTrue(ALERTS_WORK_TAG in first.tags)

    work.schedule(CheckInterval.Hour1)

    awaitUntil("the new interval") {
      checkInfos().single { it.state == WorkInfo.State.ENQUEUED }.periodicityInfo?.repeatIntervalMillis ==
        TimeUnit.MINUTES.toMillis(60)
    }
    assertEquals(first.id, checkInfos().single { it.state == WorkInfo.State.ENQUEUED }.id)
  }

  @Test
  fun thePeriodicCheck_runsTheCheckOnceThereIsANetwork() {
    work.schedule(CheckInterval.Minutes15)
    awaitUntil("the check to be scheduled") { checkInfos().isNotEmpty() }

    driver.setAllConstraintsMet(checkInfos().single().id)

    awaitUntil("a check") { checks.get() == 1 }
  }

  @Test
  fun cancel_stopsThePeriodicCheck() {
    work.schedule(CheckInterval.Minutes15)
    work.cancel()

    awaitUntil("the check to be cancelled") { checkInfos().all { it.state == WorkInfo.State.CANCELLED } }
  }

  @Test
  fun cancelAll_dropsTheCheckAndEveryWaitingAction() {
    work.schedule(CheckInterval.Minutes15)
    work.enqueueAction(request)
    work.enqueueAction(request.copy(issueId = "43", shortId = "DEMO-43"))
    awaitUntil("three pieces of work") { workManager.getWorkInfosByTag(ALERTS_WORK_TAG).get().size == 3 }

    work.cancelAll()

    awaitUntil("everything cancelled") {
      workManager.getWorkInfosByTag(ALERTS_WORK_TAG).get().all { it.state == WorkInfo.State.CANCELLED }
    }
    assertTrue(sent.isEmpty())
  }
}
