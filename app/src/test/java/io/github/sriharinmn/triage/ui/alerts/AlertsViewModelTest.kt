package io.github.sriharinmn.triage.ui.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.AlertSettings
import io.github.sriharinmn.triage.data.alerts.AlertSettingsStore
import io.github.sriharinmn.triage.data.alerts.AlertState
import io.github.sriharinmn.triage.data.alerts.CheckInterval
import io.github.sriharinmn.triage.data.alerts.DataStoreAlertSettingsStore
import io.github.sriharinmn.triage.data.alerts.DataStoreAlertStateStore
import io.github.sriharinmn.triage.data.alerts.QuietHours
import io.github.sriharinmn.triage.data.issues.Project
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlertsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val settings = DataStoreAlertSettingsStore(InMemoryPreferencesDataStore())
  private val alertState = DataStoreAlertStateStore(InMemoryPreferencesDataStore())
  private val projects =
    MutableStateFlow(
      listOf(
        Project("101", "triage-demo", "triage-demo", "node", emptyList()),
        Project("102", "triage-demo-android", "triage-demo-android", "android", emptyList()),
      )
    )

  private fun viewModel() = AlertsViewModel(settings, alertState, projects)

  @Test
  fun theScreen_showsTheSettingsTheProjectsAndTheLastCheck() = runTest {
    val checkedAt = Instant.parse("2026-11-11T10:42:00Z")
    alertState.save(AlertState(lastSuccessAt = checkedAt, lastAttemptFailed = true))

    val state = viewModel().state.value
    assertTrue(state.isLoaded)

    assertEquals(AlertSettings(), state.settings)
    assertEquals(listOf("101", "102"), state.projects.map { it.id })
    assertEquals(checkedAt, state.lastSuccessAt)
    assertTrue(state.lastAttemptFailed)
  }

  @Test
  fun everyControl_savesItsChoice() = runTest {
    val vm = viewModel()

    vm.onEnabledChanged(true)
    vm.onIntervalSelected(CheckInterval.Hour1)
    vm.onKindChanged(AlertKind.Regressed, false)
    vm.onProjectChanged("102", false)
    vm.onQuietHoursChanged(true)
    vm.onQuietStartSelected(23 * 60)
    vm.onQuietEndSelected(6 * 60)

    val saved = settings.settings.first()
    assertTrue(saved.enabled)
    assertEquals(CheckInterval.Hour1, saved.interval)
    assertEquals(setOf(AlertKind.New, AlertKind.Escalating), saved.kinds)
    assertEquals(setOf("102"), saved.projectsOff)
    assertEquals(QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 6 * 60), saved.quietHours)
    assertEquals(saved, vm.state.value.settings)
  }

  @Test
  fun switchingAProjectBackOn_asksForABaseline() = runTest {
    val vm = viewModel()
    vm.onProjectChanged("102", false)
    val before = settings.settings.first().baselineRequest

    vm.onProjectChanged("102", true)

    assertEquals(before + 1, settings.settings.first().baselineRequest)
  }

  @Test
  fun aProjectAddedLater_showsAsOn() = runTest {
    val vm = viewModel()
    vm.onProjectChanged("102", false)

    projects.value = projects.value + Project("103", "checkout-web", "checkout-web", "javascript", emptyList())

    assertEquals(listOf("101", "102", "103"), vm.state.value.projects.map { it.id })
    assertEquals(setOf("102"), vm.state.value.settings.projectsOff)
  }

  @Test
  fun beforeTheStoresAnswer_theScreenIsNotLoaded() {
    val stored = settings
    val silent = object : AlertSettingsStore by stored {
      override val settings: Flow<AlertSettings> = emptyFlow()
    }

    val state = AlertsViewModel(silent, alertState, projects).state.value

    // The screen shows nothing rather than the defaults ("off", "15 min") for a frame.
    assertFalse(state.isLoaded)
  }

  @Test
  fun twoQuickQuietHourChanges_bothLand() = runTest {
    val slow = SlowWrites()
    val store = DataStoreAlertSettingsStore(slow)
    val vm = AlertsViewModel(store, alertState, projects)

    vm.onQuietHoursChanged(true)
    // Read-then-write would read the window here, before the first change is written, and so undo it.
    vm.onQuietStartSelected(23 * 60)
    slow.open()

    assertEquals(QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60), store.settings.first().quietHours)
  }
}

/** A preferences file whose writes wait until [open], as on a slow phone, so two edits can be in flight at once. */
private class SlowWrites : DataStore<Preferences> {
  private val state = MutableStateFlow(emptyPreferences())
  private val gate = CompletableDeferred<Unit>()
  private val writes = Mutex()

  override val data: Flow<Preferences> = state

  override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
    gate.await()
    return writes.withLock { transform(state.value).also { state.value = it } }
  }

  fun open() {
    gate.complete(Unit)
  }
}
