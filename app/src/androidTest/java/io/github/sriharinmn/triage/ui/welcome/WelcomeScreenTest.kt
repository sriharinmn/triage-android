package io.github.sriharinmn.triage.ui.welcome

import androidx.compose.material3.Text
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import io.github.sriharinmn.triage.theme.TriageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var started = 0

  private fun show(fontScale: Float = 1f) {
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
        TriageTheme { WelcomeScreen(onGetStarted = { started++ }) }
      }
    }
  }

  @Test
  fun saysWhatTriageIs_andGetStartedMovesOn() {
    show()

    composeTestRule.onNodeWithText("triage.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Your Sentry issues, on your phone.", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Alerts for what matters").assertExists()
    composeTestRule.onNodeWithText("Triage in a swipe").assertExists()
    composeTestRule.onNodeWithText("Private by design").assertExists()
    composeTestRule.onNodeWithText("not affiliated", substring = true).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(WelcomeTags.GET_STARTED).performScrollTo().performClick()

    assertEquals(1, started)
  }

  @Test
  fun atTwiceTheFontSize_getStartedIsStillReachable() {
    show(fontScale = 2f)

    composeTestRule.onNodeWithTag(WelcomeTags.GET_STARTED).performScrollTo().assertIsDisplayed().performClick()

    assertEquals(1, started)
  }

  @Test
  fun aFirstLaunch_goesFromTheWelcomeToConnect_andSavesThatItDid() {
    var saved = 0
    composeTestRule.setContent {
      TriageTheme { SignedOutScreens(AppearanceSettings(), onGetStarted = { saved++ }) { Text("connect screen") } }
    }

    composeTestRule.onNodeWithText("connect screen").assertDoesNotExist()
    composeTestRule.onNodeWithTag(WelcomeTags.GET_STARTED).performScrollTo().performClick()

    composeTestRule.onNodeWithText("connect screen").assertIsDisplayed()
    assertEquals(1, saved)
  }

  @Test
  fun afterTheWelcome_aMissingAccountGoesStraightToConnect() {
    composeTestRule.setContent {
      TriageTheme { SignedOutScreens(AppearanceSettings(welcomeSeen = true), onGetStarted = {}) { Text("connect screen") } }
    }

    composeTestRule.onNodeWithText("connect screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag(WelcomeTags.GET_STARTED).assertDoesNotExist()
  }

  private fun showWithSampleData(status: SampleDataStatus, onClick: () -> Unit = {}) {
    composeTestRule.setContent {
      TriageTheme { WelcomeScreen(onGetStarted = { started++ }, sampleData = SampleDataButton(status, onClick)) }
    }
  }

  @Test
  fun trySampleData_isOfferedUnderGetStarted() {
    var tapped = 0
    showWithSampleData(SampleDataStatus.Idle) { tapped++ }

    composeTestRule.onNodeWithText("Try with sample data").performScrollTo().assertIsDisplayed().performClick()

    assertEquals(1, tapped)
    assertEquals(0, started)
  }

  @Test
  fun whileTheSampleDataOpens_theButtonShowsProgress_andCantBeTappedAgain() {
    showWithSampleData(SampleDataStatus.Starting)

    composeTestRule.onNodeWithTag(SampleDataTags.BUTTON).performScrollTo().assertIsNotEnabled()
    composeTestRule.onNodeWithContentDescription("Opening the sample data").assertExists()
  }

  @Test
  fun aFailedStart_saysSo_andTheButtonWorksAgain() {
    showWithSampleData(SampleDataStatus.Failed)

    composeTestRule.onNodeWithTag(SampleDataTags.ERROR).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Couldn't open the sample data. Try again.").assertExists()
    composeTestRule.onNodeWithTag(SampleDataTags.BUTTON).assertIsEnabled()
  }

  @Test
  fun theSignedOutWelcome_offersTheSampleData() {
    composeTestRule.setContent {
      TriageTheme {
        SignedOutScreens(AppearanceSettings(), onGetStarted = {}, sampleData = SampleDataButton(SampleDataStatus.Idle) {}) {
          Text("connect screen")
        }
      }
    }

    composeTestRule.onNodeWithTag(SampleDataTags.BUTTON).performScrollTo().assertIsDisplayed()
  }
}
