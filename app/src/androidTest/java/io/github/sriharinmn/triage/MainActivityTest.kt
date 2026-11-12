package io.github.sriharinmn.triage

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Launches the real app. Non-destructive: passes on the welcome, the Connect screen or the inbox. */
class MainActivityTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launch_showsWelcomeConnectOrInbox() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
        .onAllNodes(hasText("Get started") or hasText("Connect to Sentry") or hasText("Unresolved"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
  }
}
