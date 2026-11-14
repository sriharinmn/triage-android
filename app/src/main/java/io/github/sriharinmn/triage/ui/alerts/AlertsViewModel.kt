package io.github.sriharinmn.triage.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.AlertSettings
import io.github.sriharinmn.triage.data.alerts.AlertSettingsStore
import io.github.sriharinmn.triage.data.alerts.AlertStateStore
import io.github.sriharinmn.triage.data.alerts.CheckInterval
import io.github.sriharinmn.triage.data.alerts.QuietHours
import io.github.sriharinmn.triage.data.issues.Project
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Alerts screen shows from Triage's own stores; the phone's settings come from [PhoneAlertStatus]. */
data class AlertsUiState(
  val settings: AlertSettings = AlertSettings(),
  /** From the inbox's project list (M3). */
  val projects: List<Project> = emptyList(),
  val lastSuccessAt: Instant? = null,
  val lastAttemptFailed: Boolean = false,
  /** False only for the placeholder before the stores answer, when the screen shows nothing rather than "off / 15 min". */
  val isLoaded: Boolean = true,
)

/** Drives the Alerts screen: every control saves straight to the settings store, and the screen shows what the store says. */
class AlertsViewModel(
  private val settingsStore: AlertSettingsStore,
  stateStore: AlertStateStore,
  projects: Flow<List<Project>>,
) : ViewModel() {
  val state: StateFlow<AlertsUiState> =
    combine(
        settingsStore.settings,
        stateStore.state,
        // A cache that can't be read just shows no projects.
        projects.catch { if (it is IOException) emit(emptyList()) else throw it },
      ) { settings, alertState, projectList ->
        AlertsUiState(settings, projectList, alertState.lastSuccessAt, alertState.lastAttemptFailed)
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, AlertsUiState(isLoaded = false))

  /** Call with true only once Android allows notifications: the screen asks first. */
  fun onEnabledChanged(enabled: Boolean) = save { setEnabled(enabled) }

  fun onIntervalSelected(interval: CheckInterval) = save { setInterval(interval) }

  fun onKindChanged(kind: AlertKind, on: Boolean) = save { setKind(kind, on) }

  fun onProjectChanged(projectId: String, on: Boolean) = save { setProject(projectId, on) }

  fun onQuietHoursChanged(enabled: Boolean) = saveQuietHours { it.copy(enabled = enabled) }

  fun onQuietStartSelected(minuteOfDay: Int) = saveQuietHours { it.copy(startMinute = minuteOfDay) }

  fun onQuietEndSelected(minuteOfDay: Int) = saveQuietHours { it.copy(endMinute = minuteOfDay) }

  /** One store edit, so two quick changes (on, then a time) never undo each other. */
  private fun saveQuietHours(change: (QuietHours) -> QuietHours) = save { updateQuietHours(change) }

  private fun save(change: suspend AlertSettingsStore.() -> Unit) {
    viewModelScope.launch {
      try {
        settingsStore.change()
      } catch (_: IOException) {
        // The control stays where the store says, so the user sees it didn't change and can try again.
      }
    }
  }
}
