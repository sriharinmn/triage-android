package io.github.sriharinmn.triage.ui.alerts

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.AlertSettings
import io.github.sriharinmn.triage.data.alerts.CheckInterval
import io.github.sriharinmn.triage.data.alerts.QuietHours
import io.github.sriharinmn.triage.data.issues.Project
import io.github.sriharinmn.triage.theme.TriageTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlertsContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val calls = mutableListOf<String>()
  private val projects =
    listOf(
      Project("101", "triage-demo", "triage-demo", "node", emptyList()),
      Project("102", "triage-demo-android", "triage-demo-android", "android", emptyList()),
    )

  private fun show(state: AlertsUiState, phone: PhoneAlertStatus = PhoneAlertStatus()) {
    composeTestRule.setContent {
      TriageTheme {
        AlertsContent(
          state = state,
          phone = phone,
          batteryTip = BatteryTip.OnePlus,
          onBack = { calls += "back" },
          onEnabledChanged = { calls += "enabled:$it" },
          onIntervalSelected = { calls += "interval:${it.minutes}" },
          onKindChanged = { kind, on -> calls += "kind:${kind.name}:$on" },
          onProjectChanged = { projectId, on -> calls += "project:$projectId:$on" },
          onQuietHoursChanged = { calls += "quiet:$it" },
          onQuietStartSelected = { calls += "from:$it" },
          onQuietEndSelected = { calls += "to:$it" },
          onOpenBatterySettings = { calls += "batterySettings" },
          onOpenNotificationSettings = { calls += "notificationSettings" },
        )
      }
    }
  }

  @Test
  fun theSwitches_showTheSettings_andReportTaps() {
    show(
      AlertsUiState(
        settings = AlertSettings(enabled = true, kinds = setOf(AlertKind.New, AlertKind.Escalating), projectsOff = setOf("102")),
        projects = projects,
      )
    )

    composeTestRule.onNodeWithTag(AlertsTags.MASTER).assertIsOn()
    composeTestRule.onNodeWithTag(AlertsTags.kind(AlertKind.New)).performScrollTo().assertIsOn()
    composeTestRule.onNodeWithTag(AlertsTags.kind(AlertKind.Regressed)).performScrollTo().assertIsOff()
    composeTestRule.onNodeWithTag(AlertsTags.project("101")).performScrollTo().assertIsOn()
    composeTestRule.onNodeWithTag(AlertsTags.project("102")).performScrollTo().assertIsOff()

    composeTestRule.onNodeWithTag(AlertsTags.kind(AlertKind.Regressed)).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(AlertsTags.project("102")).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(AlertsTags.MASTER).performClick()

    assertEquals(listOf("kind:Regressed:true", "project:102:true", "enabled:false"), calls)
  }

  @Test
  fun theInterval_isOneTapAway() {
    show(AlertsUiState())

    composeTestRule.onNodeWithTag(AlertsTags.interval(CheckInterval.Minutes15)).performScrollTo().assertIsSelected()
    composeTestRule.onNodeWithTag(AlertsTags.interval(CheckInterval.Hour1)).performScrollTo().performClick()

    assertEquals(listOf("interval:60"), calls)
  }

  @Test
  fun lastChecked_saysWhen_andWhetherTheLastAttemptFailed() {
    show(AlertsUiState(lastSuccessAt = Instant.now(), lastAttemptFailed = true))

    composeTestRule
      .onNode(hasText("Last checked", substring = true) and hasText("last attempt failed", substring = true))
      .assertIsDisplayed()
  }

  @Test
  fun neverChecked_saysSo() {
    show(AlertsUiState())

    composeTestRule.onNodeWithText("Not checked yet").assertIsDisplayed()
  }

  @Test
  fun beforeTheStoresAnswer_nothingShows() {
    show(AlertsUiState(isLoaded = false))

    composeTestRule.onNodeWithTag(AlertsTags.MASTER).assertDoesNotExist()
    composeTestRule.onNodeWithText("Not checked yet").assertDoesNotExist()
  }

  @Test
  fun theBatteryRow_showsWhenRestricted_andItsHelpOpensAndroidsList() {
    show(AlertsUiState(), PhoneAlertStatus(batteryRestricted = true))

    composeTestRule.onNodeWithTag(AlertsTags.BATTERY_ROW).performClick()
    composeTestRule.onNodeWithText("Allow background activity", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertsTags.BATTERY_SETTINGS).performClick()

    assertEquals(listOf("batterySettings"), calls)
  }

  @Test
  fun withNothingRestricted_neitherWarningShows() {
    show(AlertsUiState())

    composeTestRule.onNodeWithTag(AlertsTags.BATTERY_ROW).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AlertsTags.NOTIFICATIONS_ROW).assertDoesNotExist()
  }

  @Test
  fun blockedNotifications_showARowToAndroidsSettings() {
    show(AlertsUiState(), PhoneAlertStatus(notificationsBlocked = true))

    composeTestRule.onNodeWithTag(AlertsTags.NOTIFICATIONS_ROW).performClick()

    assertEquals(listOf("notificationSettings"), calls)
  }

  @Test
  fun quietHoursOff_hidesTheTimes() {
    show(AlertsUiState())

    composeTestRule.onNodeWithTag(AlertsTags.QUIET_FROM).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AlertsTags.QUIET_TO).assertDoesNotExist()
  }

  @Test
  fun quietHoursOn_showsBothTimes_andCanBeSwitchedOff() {
    show(AlertsUiState(settings = AlertSettings(quietHours = QuietHours(enabled = true))))

    composeTestRule.onNodeWithTag(AlertsTags.QUIET_FROM).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertsTags.QUIET_TO).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertsTags.QUIET).performScrollTo().performClick()

    assertEquals(listOf("quiet:false"), calls)
  }

  @Test
  fun aTimeRow_opensAPicker_thatReportsTheChosenTime() {
    show(AlertsUiState(settings = AlertSettings(quietHours = QuietHours(enabled = true))))

    composeTestRule.onNodeWithTag(AlertsTags.QUIET_FROM).performScrollTo().performClick()
    composeTestRule.onNodeWithText("OK").performClick()

    assertEquals(listOf("from:${22 * 60}"), calls)
  }

  @Test
  fun withoutProjectsYet_itSaysWhereTheyComeFrom() {
    show(AlertsUiState())

    composeTestRule.onNodeWithText("Your projects show here", substring = true).performScrollTo().assertIsDisplayed()
  }
}
