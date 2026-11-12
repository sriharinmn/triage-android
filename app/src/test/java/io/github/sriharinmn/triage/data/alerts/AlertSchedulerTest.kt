package io.github.sriharinmn.triage.data.alerts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertSchedulerTest {
  private class RecordingWork : PeriodicCheckWork {
    val calls = mutableListOf<String>()

    override fun schedule(interval: CheckInterval) {
      calls += "schedule:${interval.minutes}"
    }

    override fun cancel() {
      calls += "cancel"
    }
  }

  @Test
  fun checksRun_onlyWhileConnectedAndSwitchedOn_atTheChosenInterval() {
    assertEquals(CheckSchedule.Off, checkSchedule(connected = false, AlertSettings(enabled = true)))
    assertEquals(CheckSchedule.Off, checkSchedule(connected = true, AlertSettings(enabled = false)))
    assertEquals(
      CheckSchedule.Every(CheckInterval.Minutes30),
      checkSchedule(connected = true, AlertSettings(enabled = true, interval = CheckInterval.Minutes30)),
    )
  }

  @Test
  fun thePeriodicCheck_followsTheSettingsAndTheAccount_skippingRepeats() = runTest {
    val connected = MutableStateFlow(true)
    val settings = MutableStateFlow(AlertSettings())
    val work = RecordingWork()
    AlertScheduler(connected, settings, work).start(backgroundScope)
    runCurrent()

    settings.value = AlertSettings(enabled = true)
    runCurrent()
    settings.value = settings.value.copy(quietHours = QuietHours(enabled = true), cardDismissed = true)
    runCurrent()
    settings.value = settings.value.copy(interval = CheckInterval.Hour1)
    runCurrent()
    connected.value = false
    runCurrent()
    connected.value = true
    runCurrent()
    settings.value = settings.value.copy(enabled = false)
    runCurrent()

    assertEquals(listOf("cancel", "schedule:15", "schedule:60", "cancel", "schedule:60", "cancel"), work.calls)
  }
}
