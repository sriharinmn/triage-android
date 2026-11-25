package io.github.sriharinmn.triage.ui.about

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import io.github.sriharinmn.triage.theme.TriageTheme
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val opened = mutableListOf<String>()

  private fun show(buildType: String = "debug") {
    val state = AboutViewModel(versionName = "1.0", versionCode = 1, buildType = buildType).state
    // The license text is read on the main thread. Read on an IO thread, this test's dispatcher sets the dialog's
    // state from that thread. We suspect, without proof, that this is why the text twice never showed in a full run.
    composeTestRule.setContent {
      TriageTheme { AboutContent(state, onBack = {}, licenseReadContext = EmptyCoroutineContext, onOpenLink = { opened += it }) }
    }
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

    // A semantics click rather than a tap: this test is about the license text, not gestures, and the tap version timed
    // out once in a full phone run without a known cause.
    composeTestRule.onNodeWithTag(jetBrainsMono).performSemanticsAction(SemanticsActions.OnClick)

    // In two steps, so a failure says whether the dialog never opened or its text never loaded.
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

  @Test
  fun sourceAndContact_openTheirLinks_andTriagesOwnLicenseIsListed() {
    show()

    composeTestRule.onNodeWithTag(AboutTags.LIST).performScrollToNode(hasTestTag(AboutTags.SOURCE))
    composeTestRule.onNodeWithTag(AboutTags.SOURCE).performClick()
    composeTestRule.onNodeWithTag(AboutTags.LIST).performScrollToNode(hasTestTag(AboutTags.CONTACT))
    composeTestRule.onNodeWithTag(AboutTags.CONTACT).performClick()

    assertEquals(listOf("https://github.com/sriharinmn/triage-android", "mailto:triagedev2026@gmail.com"), opened)
    composeTestRule.onNodeWithTag(AboutTags.LIST).performScrollToNode(hasTestTag(AboutTags.library("Triage")))
    composeTestRule.onNodeWithText("Srihari Kanakaraj · Apache License 2.0").assertIsDisplayed()
  }
}
