package io.github.sriharinmn.triage.ui.connect

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.input.ImeAction
import io.github.sriharinmn.triage.data.account.ConnectError
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.theme.TriageTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val tokenPage = "https://sentry.io/settings/account/api/auth-tokens/"
  private val orgs = listOf(OrganizationDto("1", "acme", "Acme Inc"), OrganizationDto("2", "beta", "Beta GmbH"))

  private fun show(
    state: ConnectUiState,
    onOrganizationChosen: (OrganizationDto) -> Unit = {},
    onSubmit: () -> Unit = {},
    onCancelValidation: () -> Unit = {},
    onCopyTokenPageUrl: (String) -> Unit = {},
    fontScale: Float = 1f,
  ) {
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
        TriageTheme {
          ConnectContent(
            state = state,
            onHostChoiceChange = {},
            onSelfHostedUrlChange = {},
            onTokenChange = {},
            onOpenTokenPage = {},
            onCopyTokenPageUrl = onCopyTokenPageUrl,
            onPasteToken = {},
            onSubmit = onSubmit,
            onCancelValidation = onCancelValidation,
            onOrganizationChosen = onOrganizationChosen,
            onBackFromOrganizations = {},
          )
        }
      }
    }
  }

  private val marked = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)
  private val unmarked = SemanticsMatcher.keyNotDefined(SemanticsProperties.Error)

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
  fun thePermissionsChecklist_namesAllFour() {
    show(ConnectUiState())

    composeTestRule.onNodeWithText("Organization: Read").assertExists()
    composeTestRule.onNodeWithText("Project: Read").assertExists()
    composeTestRule.onNodeWithText("Issue & Event: Read & Write").assertExists()
    composeTestRule.onNodeWithText("Member: Read").assertExists()
  }

  @Test
  fun aRejectedToken_marksTheTokenField() {
    show(ConnectUiState(token = "x", error = ConnectError.InvalidToken))

    composeTestRule.onNodeWithText("Sentry rejected this token", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ConnectTags.TOKEN).assert(marked)
    composeTestRule.onNodeWithTag(ConnectTags.FORM_ERROR).assertDoesNotExist()
  }

  @Test
  fun aNetworkError_showsUnderTheButton_andLeavesTheTokenFieldAlone() {
    show(ConnectUiState(token = "x", error = ConnectError.Network))

    composeTestRule.onNodeWithTag(ConnectTags.FORM_ERROR).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Couldn't reach the server", substring = true).assertExists()
    composeTestRule.onNodeWithTag(ConnectTags.TOKEN).assert(unmarked)
  }

  @Test
  fun aSelfHostedCertificateProblem_marksTheUrlField_inPlainWords() {
    show(ConnectUiState(hostChoice = HostChoice.SelfHosted, selfHostedUrl = "sentry.example.com", token = "x", error = ConnectError.InsecureConnection))

    composeTestRule.onNodeWithTag(ConnectTags.SERVER_URL).assert(marked)
    composeTestRule.onNodeWithText("certificate isn't trusted", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ConnectTags.TOKEN).assert(unmarked)
  }

  @Test
  fun rateLimitWithSeconds_saysHowLongToWait() {
    show(ConnectUiState(token = "x", error = ConnectError.RateLimited(42)))
    composeTestRule.onNodeWithText("Try again in 42 seconds", substring = true).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun pasteWithNothingCopied_saysSo() {
    show(ConnectUiState(pasteEmpty = true))

    composeTestRule.onNodeWithText("Nothing to paste: copy the token first.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ConnectTags.TOKEN).assert(unmarked)
  }

  @Test
  fun aTokenPageThatCannotOpen_showsItsAddressToCopy() {
    var copied: String? = null
    show(ConnectUiState(tokenPageUrlToCopy = tokenPage), onCopyTokenPageUrl = { copied = it })

    composeTestRule.onNodeWithTag(ConnectTags.TOKEN_PAGE_URL).assertTextEquals(tokenPage)
    composeTestRule.onNodeWithTag(ConnectTags.COPY_TOKEN_PAGE_URL).performClick()

    assertEquals(tokenPage, copied)
  }

  @Test
  fun validating_offersCancel() {
    var cancelled = false
    show(ConnectUiState(token = "sntryu_abc", step = Step.Validating, canCancel = true), onCancelValidation = { cancelled = true })

    composeTestRule.onNodeWithTag(ConnectTags.CANCEL).performScrollTo().performClick()

    assertTrue(cancelled)
  }

  @Test
  fun organizationStep_listsOrgsAndReportsChoice() {
    var chosen: OrganizationDto? = null
    show(ConnectUiState(step = Step.ChooseOrganization, organizations = orgs), onOrganizationChosen = { chosen = it })

    composeTestRule.onNodeWithText("Beta GmbH").performClick()

    assertEquals("beta", chosen?.slug)
  }

  @Test
  fun whileTheChosenOrgSaves_theListStaysWithProgress_andIgnoresTaps() {
    var chosen: OrganizationDto? = null
    show(ConnectUiState(step = Step.Saving, organizations = orgs), onOrganizationChosen = { chosen = it })

    composeTestRule.onNodeWithText("Acme Inc").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Connecting…").assertExists()
    composeTestRule.onNodeWithText("Beta GmbH").performClick()

    assertNull(chosen)
  }

  @Test
  fun tokenField_disabledWhileValidating() {
    show(ConnectUiState(token = "sntryu_abc", step = Step.Validating))
    composeTestRule.onNodeWithTag(ConnectTags.TOKEN).assertIsNotEnabled()
  }

  @Test
  fun tokenField_doneKeySubmits() {
    var submitted = false
    show(ConnectUiState(token = "sntryu_abc"), onSubmit = { submitted = true })

    composeTestRule.onNodeWithTag(ConnectTags.TOKEN).performImeAction()

    assertTrue(submitted)
  }

  @Test
  fun tokenField_isPasswordField() {
    show(ConnectUiState(token = "sntryu_abc"))
    // Compose exposes no semantics property for KeyboardType, so Done and the masking flag are what we can observe.
    composeTestRule
      .onNodeWithTag(ConnectTags.TOKEN)
      .assert(hasImeAction(ImeAction.Done))
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
  }

  @Test
  fun atTwiceTheFontSize_everythingIsStillReachable() {
    show(ConnectUiState(token = "sntryu_abc"), fontScale = 2f)

    composeTestRule.onNodeWithTag(ConnectTags.OPEN_TOKEN_PAGE).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(ConnectTags.CONNECT).performScrollTo().assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("not affiliated", substring = true).performScrollTo().assertIsDisplayed()
  }
}
