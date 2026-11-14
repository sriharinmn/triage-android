package io.github.sriharinmn.triage.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.github.sriharinmn.triage.data.appearance.ThemeMode
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.ui.common.LevelGlyph
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The theme's choices, with the phone's dark mode faked for this composition only; its real setting is never touched. */
class TriageThemeTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var primary = Color.Unspecified
  private var levelError = Color.Unspecified

  private fun show(mode: ThemeMode, phoneDark: Boolean) {
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(phoneDark)) {
        TriageTheme(themeMode = mode) {
          primary = MaterialTheme.colorScheme.primary
          levelError = MaterialTheme.semanticColors.levelError
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun system_followsADarkPhone() {
    show(ThemeMode.System, phoneDark = true)

    assertEquals(Color(0xFFB3C5FF), primary)
    assertEquals(Color(0xFFFF7A70), levelError)
  }

  @Test
  fun system_followsALightPhone() {
    show(ThemeMode.System, phoneDark = false)

    assertEquals(Color(0xFF4A5C92), primary)
    assertEquals(Color(0xFFBA1A1A), levelError)
  }

  @Test
  fun light_staysLightOnADarkPhone() {
    show(ThemeMode.Light, phoneDark = true)

    assertEquals(Color(0xFF4A5C92), primary)
  }

  @Test
  fun dark_staysDarkOnALightPhone() {
    show(ThemeMode.Dark, phoneDark = false)

    assertEquals(Color(0xFFB3C5FF), primary)
    assertEquals(Color(0xFFFF7A70), levelError)
  }

  @Test
  fun aLevelGlyph_isNamedForTalkBack() {
    composeTestRule.setContent { TriageTheme { LevelGlyph(IssueLevel.Warning) } }

    composeTestRule.onNodeWithContentDescription("Warning").assertExists()
  }
}
