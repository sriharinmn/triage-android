package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.data.alerts.AlertSettings
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoAlertKickTest {
  private val accounts = MutableStateFlow<AccountState>(AccountState.Loading)
  private val settings = MutableStateFlow(AlertSettings())
  private var kicks = 0

  private fun kotlinx.coroutines.test.TestScope.watch() =
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { kickDemoWhenAlertsTurnOn(accounts, settings) { kicks++ } }

  @Test
  fun switchingAlertsOn_inTheDemo_kicksOnce() = runTest {
    accounts.value = AccountState.Ready(DemoAccount)
    watch()

    settings.value = AlertSettings(enabled = true, baselineRequest = 1)
    settings.value = AlertSettings(enabled = true, baselineRequest = 2)

    assertEquals(1, kicks)
  }

  @Test
  fun offAndOnAgain_kicksAgain() = runTest {
    accounts.value = AccountState.Ready(DemoAccount)
    watch()

    settings.value = AlertSettings(enabled = true)
    settings.value = AlertSettings(enabled = false)
    settings.value = AlertSettings(enabled = true)

    assertEquals(2, kicks)
  }

  @Test
  fun alertsAlreadyOnWhenTheAppStarts_isNoKick() = runTest {
    accounts.value = AccountState.Ready(DemoAccount)
    settings.value = AlertSettings(enabled = true)
    watch()

    assertEquals(0, kicks)
  }

  @Test
  fun aRealAccount_neverKicks() = runTest {
    accounts.value = AccountState.Ready(TEST_ACCOUNT)
    watch()

    settings.value = AlertSettings(enabled = true)

    assertEquals(0, kicks)
  }
}
