package io.github.sriharinmn.triage.ui.settings

import io.github.sriharinmn.triage.data.AccountDataCleaner
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import io.github.sriharinmn.triage.data.appearance.DataStoreAppearanceStore
import io.github.sriharinmn.triage.data.appearance.ThemeMode
import io.github.sriharinmn.triage.data.demo.DemoAccount
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.InMemoryInboxSettingsStore
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.MainDispatcherRule
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val api = FakeSentryApi()
  private val cache = InMemoryIssueCache()
  private val issues = IssueRepository(api, cache, TestClock()::now)
  private val accountStore = InMemoryAccountStore(TEST_ACCOUNT)
  private val disconnector =
    Disconnector(AccountRepository(api, accountStore), AccountDataCleaner(issues, InMemoryInboxSettingsStore()))
  private val appearance = DataStoreAppearanceStore(InMemoryPreferencesDataStore())

  @Test
  fun theDemo_isMarked_andARealAccountIsNot() {
    assertTrue(SettingsViewModel(DemoAccount, appearance, disconnector).state.value.isDemo)
    assertFalse(viewModel().state.value.isDemo)
  }

  private fun viewModel() = SettingsViewModel(TEST_ACCOUNT, appearance, disconnector)

  @Test
  fun theAccount_isTheOrgAndItsServerHost() {
    val state = viewModel().state.value

    assertEquals("Example Org", state.orgName)
    assertEquals("de.sentry.io", state.serverHost)
  }

  @Test
  fun theServerHost_isTheHostOnly() {
    assertEquals("sentry.example.com", serverHost("https://sentry.example.com/sentry"))
    assertEquals("not a url", serverHost("not a url"))
  }

  @Test
  fun theScreen_showsWhatTheStoreSays() = runTest {
    appearance.setThemeMode(ThemeMode.Dark)

    assertEquals(ThemeMode.Dark, viewModel().state.value.appearance.themeMode)
  }

  @Test
  fun appearanceChoices_areSaved_andShown() = runTest {
    val vm = viewModel()

    vm.onThemeModeSelected(ThemeMode.Light)
    vm.onWallpaperColorsChanged(true)

    val expected = AppearanceSettings(themeMode = ThemeMode.Light, useWallpaperColors = true)
    assertEquals(expected, appearance.settings.first())
    assertEquals(expected, vm.state.value.appearance)
  }

  @Test
  fun disconnect_forgetsTheAccount_butNotTheAppearance() = runTest {
    appearance.setThemeMode(ThemeMode.Dark)
    val vm = viewModel()

    vm.onDisconnectConfirmed()

    assertNull(accountStore.current)
    assertEquals(ThemeMode.Dark, appearance.settings.first().themeMode)
  }

  @Test
  fun aFailedDisconnect_staysConnected_andSaysSoOnce() = runTest {
    cache.clearError = IOException("disk full")
    val vm = viewModel()

    vm.onDisconnectConfirmed()

    assertTrue(vm.state.value.disconnectFailed)
    assertFalse(vm.state.value.isDisconnecting)
    assertNotNull(accountStore.current)
    vm.onDisconnectFailureShown()
    assertFalse(vm.state.value.disconnectFailed)
  }
}
