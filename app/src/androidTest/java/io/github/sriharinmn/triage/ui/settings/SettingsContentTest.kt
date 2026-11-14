package io.github.sriharinmn.triage.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import io.github.sriharinmn.triage.data.appearance.ThemeMode
import io.github.sriharinmn.triage.theme.TriageTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val calls = mutableListOf<String>()
  private val connected = SettingsUiState(orgName = "Example Org", serverHost = "de.sentry.io")

  private fun show(state: SettingsUiState = connected, wallpaperColorsAvailable: Boolean = true, fontScale: Float = 1f) {
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
        TriageTheme {
          SettingsContent(
            state = state,
            wallpaperColorsAvailable = wallpaperColorsAvailable,
            onBack = { calls += "back" },
            onOpenAlerts = { calls += "alerts" },
            onOpenAbout = { calls += "about" },
            onThemeModeSelected = { calls += "theme:${it.name}" },
            onWallpaperColorsChanged = { calls += "wallpaper:$it" },
            onDisconnectConfirmed = { calls += "disconnect" },
            onDisconnectFailureShown = {},
          )
        }
      }
    }
  }

  @Test
  fun theAccount_showsTheOrgAndItsServer() {
    show()

    composeTestRule.onNodeWithText("Example Org").assertIsDisplayed()
    composeTestRule.onNodeWithText("de.sentry.io").assertIsDisplayed()
  }

  @Test
  fun disconnect_asksForConfirmationFirst() {
    show()

    composeTestRule.onNodeWithTag(SettingsTags.DISCONNECT).performClick()
    assertTrue(calls.isEmpty())
    composeTestRule.onNodeWithText("Disconnect from Sentry?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Disconnect").performClick()

    assertEquals(listOf("disconnect"), calls)
  }

  @Test
  fun alertsAndAbout_openTheirScreens() {
    show()

    composeTestRule.onNodeWithTag(SettingsTags.ALERTS).performClick()
    composeTestRule.onNodeWithTag(SettingsTags.ABOUT).performScrollTo().performClick()

    assertEquals(listOf("alerts", "about"), calls)
  }

  @Test
  fun theTheme_isOneTapAway() {
    show(connected.copy(appearance = AppearanceSettings(themeMode = ThemeMode.Dark)))

    composeTestRule.onNodeWithTag(SettingsTags.theme(ThemeMode.Dark)).performScrollTo().assertIsSelected()
    composeTestRule.onNodeWithTag(SettingsTags.theme(ThemeMode.Light)).performClick()

    assertEquals(listOf("theme:Light"), calls)
  }

  @Test
  fun wallpaperColors_isASwitch_offByDefault() {
    show()

    composeTestRule.onNodeWithTag(SettingsTags.WALLPAPER_COLORS).performScrollTo().assertIsOff().performClick()

    assertEquals(listOf("wallpaper:true"), calls)
  }

  @Test
  fun wallpaperColors_isHidden_beforeAndroid12() {
    show(wallpaperColorsAvailable = false)

    composeTestRule.onNodeWithTag(SettingsTags.WALLPAPER_COLORS).assertDoesNotExist()
  }

  @Test
  fun theBackButton_isLabelled() {
    show()

    composeTestRule.onNodeWithContentDescription("Back").performClick()

    assertEquals(listOf("back"), calls)
  }

  @Test
  fun theDemo_showsSampleData_andLeavesWithoutAsking() {
    show(connected.copy(isDemo = true))

    composeTestRule.onNodeWithText("Sample data").assertIsDisplayed()
    composeTestRule.onNodeWithText("de.sentry.io").assertDoesNotExist()
    composeTestRule.onNodeWithTag(SettingsTags.DISCONNECT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SettingsTags.LEAVE_DEMO).assertIsDisplayed().performClick()

    assertEquals(listOf("disconnect"), calls)
  }

  @Test
  fun atTwiceTheFontSize_everyRowIsReachable() {
    show(fontScale = 2f)

    composeTestRule.onNodeWithTag(SettingsTags.theme(ThemeMode.System)).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsTags.ABOUT).performScrollTo().assertIsDisplayed()
  }
}
