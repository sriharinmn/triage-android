package io.github.sriharinmn.triage.data.appearance

import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.data.demo.DemoAccount
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeSeenTest {
  private val store = DataStoreAppearanceStore(InMemoryPreferencesDataStore())

  @Test
  fun signedOut_leavesTheWelcomeAlone_untilAnAccountConnects() = runTest {
    val accounts = MutableStateFlow<AccountState>(AccountState.Loading)
    backgroundScope.launch { markWelcomeSeenOnceConnected(accounts, store) }

    accounts.value = AccountState.Ready(null)
    runCurrent()
    assertFalse(store.settings.first().welcomeSeen)

    accounts.value = AccountState.Ready(TEST_ACCOUNT)
    runCurrent()
    assertTrue(store.settings.first().welcomeSeen)
  }

  @Test
  fun theDemo_doesntMarkTheWelcomeSeen() = runTest {
    val accounts = MutableStateFlow<AccountState>(AccountState.Ready(DemoAccount))
    val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { markWelcomeSeenOnceConnected(accounts, store) }

    assertFalse(store.settings.first().welcomeSeen)

    accounts.value = AccountState.Ready(TEST_ACCOUNT)
    job.join()
    assertTrue(store.settings.first().welcomeSeen)
  }

  @Test
  fun anAccountAlreadyConnected_marksItAtOnce() = runTest {
    markWelcomeSeenOnceConnected(flowOf(AccountState.Ready(TEST_ACCOUNT)), store)

    assertTrue(store.settings.first().welcomeSeen)
  }

  @Test
  fun aFileThatCannotBeWritten_isNotACrash() = runTest {
    // UnreadableDataStore throws CorruptionException, an IOException; the next launch tries again.
    markWelcomeSeenOnceConnected(flowOf(AccountState.Ready(TEST_ACCOUNT)), DataStoreAppearanceStore(UnreadableDataStore()))
  }
}
