package io.github.sriharinmn.triage.data.alerts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What the periodic check should be doing. */
sealed interface CheckSchedule {
  data object Off : CheckSchedule

  data class Every(val interval: CheckInterval) : CheckSchedule
}

/** Checks run only while an account is connected and alerts are switched on. */
fun checkSchedule(connected: Boolean, settings: AlertSettings): CheckSchedule =
  if (connected && settings.enabled) CheckSchedule.Every(settings.interval) else CheckSchedule.Off

/** The system job behind the periodic check (WorkManager in the app, a recorder in tests). */
interface PeriodicCheckWork {
  /** Starts the periodic check, or changes its interval without restarting its timing. */
  fun schedule(interval: CheckInterval)

  fun cancel()
}

/** Keeps the periodic check (unique work `alert-check`) in line with the account and the alert settings. */
class AlertScheduler(
  private val connected: Flow<Boolean>,
  private val settings: Flow<AlertSettings>,
  private val work: PeriodicCheckWork,
) {
  /** Applies every change for as long as [scope] lives. Repeats are skipped: quiet hours or the card don't reschedule. */
  fun start(scope: CoroutineScope): Job =
    scope.launch {
      combine(connected, settings) { isConnected, alertSettings -> checkSchedule(isConnected, alertSettings) }
        .distinctUntilChanged()
        .collect { schedule ->
          when (schedule) {
            CheckSchedule.Off -> work.cancel()
            is CheckSchedule.Every -> work.schedule(schedule.interval)
          }
        }
    }
}
