package io.github.sriharinmn.triage.ui.alerts

import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.AlertSettings
import io.github.sriharinmn.triage.data.alerts.AlertState
import io.github.sriharinmn.triage.data.alerts.CheckInterval
import io.github.sriharinmn.triage.data.alerts.DataStoreAlertSettingsStore
import io.github.sriharinmn.triage.data.alerts.DataStoreAlertStateStore
import io.github.sriharinmn.triage.data.alerts.QuietHours
import io.github.sriharinmn.triage.data.issues.Project
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
