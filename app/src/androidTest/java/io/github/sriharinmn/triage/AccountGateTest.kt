package io.github.sriharinmn.triage

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.account.AccountState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Connect ↔ inbox switch, driven by a fake account state (never the real account store). */
class AccountGateTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val account = Account("https://de.sentry.io", "https://example-org.sentry.io", "sntryu_test", "example-org", "Example Org")
  private val otherAccount = account.copy(orgSlug = "other-org", orgName = "Other Org")

  @Test
  fun switchesBetweenConnectAndInboxAsTheAccountChanges() {
    val state = MutableStateFlow<AccountState>(AccountState.Loading)
    composeTestRule.setContent {
      val current by state.collectAsState()
      AccountGate(current, signedOut = { Text("connect screen") }, signedIn = { Text("inbox for ${it.orgSlug}") })
    }

    composeTestRule.onNodeWithText("connect screen").assertDoesNotExist()

    state.value = AccountState.Ready(null)
    composeTestRule.onNodeWithText("connect screen").assertIsDisplayed()

    state.value = AccountState.Ready(account)
    composeTestRule.onNodeWithText("inbox for example-org").assertIsDisplayed()
    composeTestRule.onNodeWithText("connect screen").assertDoesNotExist()

    state.value = AccountState.Ready(null)
    composeTestRule.onNodeWithText("connect screen").assertIsDisplayed()
  }

  @Test
  fun reconnectingTheSameOrg_getsAFreshViewModel() {
    val state = MutableStateFlow<AccountState>(AccountState.Ready(account))
    val seen = setUpProbe(state)
    composeTestRule.waitForIdle()

    assertEquals(1, seen.size)
    val first = seen[0]
    assertFalse(first.cleared)

    setState(state, AccountState.Ready(null))
    composeTestRule.onNodeWithText("connect screen").assertIsDisplayed()
    assertTrue("disconnecting must clear the signed-in ViewModels", first.cleared)

    setState(state, AccountState.Ready(account))
    composeTestRule.onNodeWithText("vm ${seen.last().id}").assertIsDisplayed()

    assertEquals(2, seen.size)
    val second = seen[1]
    assertNotSame("a reconnect must not get the old ViewModel back", first, second)
    assertFalse(second.cleared)
  }

  @Test
  fun switchingAccounts_clearsTheOldSessionsViewModels() {
    val state = MutableStateFlow<AccountState>(AccountState.Ready(account))
    val seen = setUpProbe(state)
    composeTestRule.waitForIdle()

    assertEquals(1, seen.size)
    val first = seen[0]

    setState(state, AccountState.Ready(otherAccount))
    composeTestRule.onNodeWithText("vm ${seen.last().id}").assertIsDisplayed()

    assertEquals(2, seen.size)
    val second = seen[1]
    assertNotSame(first, second)
    assertTrue("switching accounts must clear the old account's ViewModels", first.cleared)
    assertFalse(second.cleared)
  }

  @Test
  fun navigationEntryViewModels_belongToTheSession() {
    val state = MutableStateFlow<AccountState>(AccountState.Ready(account))
    val seen = mutableListOf<ProbeViewModel>()
    composeTestRule.setContent {
      val current by state.collectAsState()
      AccountGate(
        current,
        signedOut = { Text("connect screen") },
        signedIn = { ProbeNavigation { vm -> if (seen.none { it === vm }) seen += vm } },
      )
    }
    composeTestRule.waitForIdle()

    assertEquals(1, seen.size)
    val first = seen[0]
    assertFalse(first.cleared)

    setState(state, AccountState.Ready(null))
    composeTestRule.onNodeWithText("connect screen").assertIsDisplayed()
    assertTrue("a Navigation entry's ViewModel must be cleared with its session", first.cleared)

    setState(state, AccountState.Ready(account))
    composeTestRule.onNodeWithText("nav vm ${seen.last().id}").assertIsDisplayed()

    assertEquals(2, seen.size)
    val second = seen[1]
    assertNotSame("a reconnect must not get the old entry ViewModel back", first, second)
    assertFalse(second.cleared)
  }

  @Test
  fun signedOutViewModels_areDroppedOnceAnAccountConnects() {
    val state = MutableStateFlow<AccountState>(AccountState.Ready(null))
    val seen = mutableListOf<ProbeViewModel>()
    composeTestRule.setContent {
      val current by state.collectAsState()
      AccountGate(
        current,
        signedOut = {
          val vm: ProbeViewModel = viewModel { ProbeViewModel() }
          if (seen.none { it === vm }) seen += vm
          Text("connect vm ${vm.id}")
        },
        signedIn = { Text("inbox for ${it.orgSlug}") },
      )
    }
    composeTestRule.waitForIdle()
    assertEquals(1, seen.size)
    val first = seen[0]

    setState(state, AccountState.Ready(account))
    composeTestRule.onNodeWithText("inbox for example-org").assertIsDisplayed()
    assertTrue("connecting must drop Connect's ViewModel and the token typed into it", first.cleared)

    setState(state, AccountState.Ready(null))
    composeTestRule.onNodeWithText("connect vm ${seen.last().id}").assertIsDisplayed()
    assertNotSame("a later sign-out must start on a fresh Connect", first, seen.last())
  }

  /** Shows [ProbeViewModel]s for the signed-in account; every distinct instance the screen gets lands in the result. */
  private fun setUpProbe(state: MutableStateFlow<AccountState>): List<ProbeViewModel> {
    val seen = mutableListOf<ProbeViewModel>()
    composeTestRule.setContent {
      val current by state.collectAsState()
      AccountGate(
        current,
        signedOut = { Text("connect screen") },
        signedIn = {
          val vm: ProbeViewModel = viewModel { ProbeViewModel() }
          if (seen.none { it === vm }) seen += vm
          Text("vm ${vm.id}")
        },
      )
    }
    return seen
  }

  private fun setState(state: MutableStateFlow<AccountState>, value: AccountState) {
    composeTestRule.runOnIdle { state.value = value }
    composeTestRule.waitForIdle()
  }
}

/** A key for [ProbeNavigation]'s single entry. */
@Serializable private data object ProbeKey : NavKey

/** A minimal copy of `TriageNavigation`'s setup: one entry whose ViewModel lives in the entry decorator's store. */
@Composable
private fun ProbeNavigation(onViewModel: (ProbeViewModel) -> Unit) {
  val backStack = rememberNavBackStack(ProbeKey)
  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators =
      listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>()),
    entryProvider =
      entryProvider {
        entry<ProbeKey> {
          val vm: ProbeViewModel = viewModel { ProbeViewModel() }
          onViewModel(vm)
          Text("nav vm ${vm.id}")
        }
      },
  )
}

/** A stand-in for a screen's ViewModel that records whether it was cleared. */
class ProbeViewModel : ViewModel() {
  val id = nextId.incrementAndGet()
  @Volatile var cleared = false

  override fun onCleared() {
    cleared = true
  }

  private companion object {
    val nextId = AtomicInteger()
  }
}
