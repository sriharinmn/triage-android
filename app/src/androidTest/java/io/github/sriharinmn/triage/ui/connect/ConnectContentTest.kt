package io.github.sriharinmn.triage.ui.connect

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.sriharinmn.triage.data.account.ConnectError
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.theme.TriageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun show(state: ConnectUiState, onOrganizationChosen: (OrganizationDto) -> Unit = {}) {
    composeTestRule.setContent {
      TriageTheme {
        ConnectContent(
          state = state,
          onHostChoiceChange = {},
          onSelfHostedUrlChange = {},
          onTokenChange = {},
          onOpenTokenPage = {},
          onPasteToken = {},
          onSubmit = {},
          onOrganizationChosen = onOrganizationChosen,
          onBackFromOrganizations = {},
        )
      }
    }
  }

  @Test
  fun connectButton_disabledWithoutToken() {
    show(ConnectUiState())
    composeTestRule.onNodeWithTag(ConnectTags.CONNECT).assertIsNotEnabled()
  }

  @Test
  fun connectButton_enabledWithToken() {
    show(ConnectUiState(token = "sntryu_abc"))
    composeTestRule.onNodeWithTag(ConnectTags.CONNECT).assertIsEnabled()
  }

  @Test
  fun serverUrlField_shownForSelfHosted() {
    show(ConnectUiState(hostChoice = HostChoice.SelfHosted))
    composeTestRule.onNodeWithTag(ConnectTags.SERVER_URL).assertIsDisplayed()
  }

  @Test
  fun serverUrlField_hiddenForCloud() {
    show(ConnectUiState())
    composeTestRule.onNodeWithTag(ConnectTags.SERVER_URL).assertDoesNotExist()
  }

  @Test
  fun errorMessage_isShown() {
    show(ConnectUiState(token = "x", error = ConnectError.InvalidToken))
    composeTestRule.onNodeWithText("Sentry rejected this token", substring = true).assertIsDisplayed()
  }

  @Test
  fun organizationStep_listsOrgsAndReportsChoice() {
    var chosen: OrganizationDto? = null
    val orgs = listOf(OrganizationDto("1", "acme", "Acme Inc"), OrganizationDto("2", "beta", "Beta GmbH"))
    show(ConnectUiState(step = Step.ChooseOrganization, organizations = orgs), onOrganizationChosen = { chosen = it })

    composeTestRule.onNodeWithText("Beta GmbH").performClick()

    assertEquals("beta", chosen?.slug)
  }

  @Test
  fun tokenField_disabledWhileValidating() {
    show(ConnectUiState(token = "sntryu_abc", step = Step.Validating))
    composeTestRule.onNodeWithTag(ConnectTags.TOKEN).assertIsNotEnabled()
  }
}
