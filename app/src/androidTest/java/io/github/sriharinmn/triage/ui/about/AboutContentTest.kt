package io.github.sriharinmn.triage.ui.about

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.sriharinmn.triage.theme.TriageTheme
import org.junit.Rule
import org.junit.Test

class AboutContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun show(buildType: String = "debug") {
    val state = AboutViewModel(versionName = "1.0", versionCode = 1, buildType = buildType).state
    composeTestRule.setContent { TriageTheme { AboutContent(state, onBack = {}) } }
  }

  @Test
  fun theVersionTheDisclaimerAndThePrivacySummary_show() {
    show()

    composeTestRule.onNodeWithText("Version 1.0 (1) · debug").assertIsDisplayed()
    composeTestRule.onNodeWithText("not affiliated", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AboutTags.LIST).performScrollToNode(hasText("Android Keystore", substring = true))
    composeTestRule.onNodeWithText("No analytics and no tracking.").assertIsDisplayed()
  }

  @Test
  fun aReleaseBuild_showsJustItsVersion() {
    show(buildType = "release")

    composeTestRule.onNodeWithText("Version 1.0 (1)").assertIsDisplayed()
  }

  @Test
  fun aLibrary_opensItsLicensesText() {
    show()
    composeTestRule.onNodeWithTag(AboutTags.LIST).performScrollToNode(hasTestTag(AboutTags.library("JetBrains Mono")))

    composeTestRule.onNodeWithTag(AboutTags.library("JetBrains Mono")).performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
        .onAllNodes(hasText("licensed under the SIL Open Font License", substring = true))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
  }
}
