package io.github.sriharinmn.triage

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.account.AccountState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/** The Connect ↔ inbox switch, driven by a fake account state (never the real account store). */
class AccountGateTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val account = Account("https://de.sentry.io", "https://example-org.sentry.io", "sntryu_test", "example-org", "Example Org")

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
}
