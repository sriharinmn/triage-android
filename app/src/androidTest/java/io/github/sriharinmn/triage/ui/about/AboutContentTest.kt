package io.github.sriharinmn.triage.ui.about

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    val jetBrainsMono = AboutTags.library("JetBrains Mono")
    composeTestRule.onNodeWithTag(AboutTags.LIST).performScrollToNode(hasTestTag(jetBrainsMono))

    // performScrollToNode can stop with the row only partly on screen, where a tap at its centre misses it.
    composeTestRule.onNodeWithTag(jetBrainsMono).performScrollTo().performClick()

    // In two steps, so a failure says whether the tap missed or the license text didn't load.
    composeTestRule.waitUntil("the license dialog opens", timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitUntil("the OFL text is read and shown", timeoutMillis = 5_000) {
      composeTestRule
        .onAllNodes(hasText("licensed under the SIL Open Font License", substring = true))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
  }
}
