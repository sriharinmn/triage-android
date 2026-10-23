package io.github.sriharinmn.triage.data.account

import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountStateTest {
  @Test
  fun startsLoading_thenFollowsTheStore() = runTest {
    val stored = MutableSharedFlow<Account?>()
    val state = stored.asAccountState(backgroundScope + UnconfinedTestDispatcher(testScheduler))

    assertEquals(AccountState.Loading, state.value)
    stored.emit(null)
    assertEquals(AccountState.Ready(null), state.value)
    stored.emit(TEST_ACCOUNT)
    assertEquals(AccountState.Ready(TEST_ACCOUNT), state.value)
  }
}
