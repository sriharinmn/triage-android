package io.github.sriharinmn.triage.ui.connect

import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.ConnectError
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.NetworkCause
import io.github.sriharinmn.triage.testing.EU_ORG
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.MainDispatcherRule
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.US_ORG
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val api = FakeSentryApi()
  private val store = InMemoryAccountStore()
  private val clock = TestClock()
  private val viewModel = ConnectViewModel(AccountRepository(api, store), clock::now)

  @Test
  fun initialState_isCloudWithNothingEntered() {
    assertEquals(ConnectUiState(), viewModel.state.value)
    assertFalse(viewModel.state.value.canSubmit)
  }

  @Test
  fun enteringToken_enablesSubmit() {
    viewModel.onTokenChange("sntryu_abc")
    assertTrue(viewModel.state.value.canSubmit)
  }

  @Test
  fun selfHostedWithoutUrl_cannotSubmit() {
    viewModel.onHostChoiceChange(HostChoice.SelfHosted)
    viewModel.onTokenChange("sntryu_abc")
    assertFalse(viewModel.state.value.canSubmit)
  }

  @Test
  fun submit_withSingleOrganization_connects_andKeepsShowingProgress() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals("acme", store.current?.orgSlug)
    assertEquals("sntryu_abc", store.current?.token)
    // No reset to an empty form first: the inbox replaces the screen, and AccountGate drops this ViewModel.
    assertEquals(Step.Validating, viewModel.state.value.step)
    assertFalse(viewModel.state.value.canCancel)
    assertNull(viewModel.state.value.error)
  }

  @Test
  fun submit_withSeveralOrganizations_asksToChooseThenConnects() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG, EU_ORG))
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals(Step.ChooseOrganization, viewModel.state.value.step)
    assertEquals(listOf(US_ORG, EU_ORG), viewModel.state.value.organizations)
    assertNull(store.current)

    viewModel.onOrganizationChosen(EU_ORG)

    assertEquals("beta-gmbh", store.current?.orgSlug)
    assertEquals("https://de.sentry.io", store.current?.apiBaseUrl)
    // The list stays on screen, with progress, until the inbox replaces it.
    assertEquals(Step.Saving, viewModel.state.value.step)
    assertEquals(listOf(US_ORG, EU_ORG), viewModel.state.value.organizations)
  }

  @Test
  fun backFromOrganizations_returnsToTokenStepKeepingToken() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG, EU_ORG))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()

    viewModel.onBackFromOrganizations()

    assertEquals(Step.EnterToken, viewModel.state.value.step)
    assertTrue(viewModel.state.value.organizations.isEmpty())
    assertEquals("sntryu_abc", viewModel.state.value.token)
  }

  @Test
  fun submit_failure_showsErrorOnTokenStep() {
    api.organizationsResult = ApiResult.Failure(ApiError.Unauthorized)
    viewModel.onTokenChange("sntryu_bad")

    viewModel.onSubmit()

    assertEquals(Step.EnterToken, viewModel.state.value.step)
    assertEquals(ConnectError.InvalidToken, viewModel.state.value.error)
    assertNull(store.current)
  }

  @Test
  fun editingToken_clearsError() {
    api.organizationsResult = ApiResult.Failure(ApiError.Unauthorized)
    viewModel.onTokenChange("sntryu_bad")
    viewModel.onSubmit()

    viewModel.onTokenChange("sntryu_good")

    assertNull(viewModel.state.value.error)
  }

  @Test
  fun selfHosted_invalidUrl_flagsErrorWithoutCallingApi() {
    viewModel.onHostChoiceChange(HostChoice.SelfHosted)
    viewModel.onSelfHostedUrlChange("http://insecure.example.com")
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertTrue(viewModel.state.value.urlError)
    assertTrue(api.calls.isEmpty())
  }

  @Test
  fun selfHosted_validUrl_callsThatServer() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))
    viewModel.onHostChoiceChange(HostChoice.SelfHosted)
    viewModel.onSelfHostedUrlChange("sentry.example.com")
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals("https://sentry.example.com", api.calls.single().first)
    assertEquals("https://sentry.example.com", store.current?.apiBaseUrl)
  }

  @Test
  fun saveFailure_onSingleOrg_returnsToTokenStepWithSaveFailed() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))
    store.saveError = IOException("disk")
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals(Step.EnterToken, viewModel.state.value.step)
    assertEquals(ConnectError.SaveFailed, viewModel.state.value.error)
    assertEquals("sntryu_abc", viewModel.state.value.token)
    assertNull(store.current)
  }

  @Test
  fun saveFailure_afterChoosingOrg_returnsToTokenStep() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG, EU_ORG))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()
    store.saveError = IOException("disk")

    viewModel.onOrganizationChosen(EU_ORG)

    assertEquals(Step.EnterToken, viewModel.state.value.step)
    assertEquals(ConnectError.SaveFailed, viewModel.state.value.error)
    assertTrue(viewModel.state.value.organizations.isEmpty())
  }

  @Test
  fun edits_areIgnoredWhileChoosingOrganization() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG, EU_ORG))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()

    viewModel.onTokenChange("sntryu_other")
    viewModel.onHostChoiceChange(HostChoice.SelfHosted)
    viewModel.onSelfHostedUrlChange("x")

    assertEquals("sntryu_abc", viewModel.state.value.token)
    assertEquals(HostChoice.Cloud, viewModel.state.value.hostChoice)
    assertEquals("", viewModel.state.value.selfHostedUrl)

    viewModel.onOrganizationChosen(EU_ORG)

    assertEquals("sntryu_abc", store.current?.token)
  }

  @Test
  fun chooseOrganization_twiceQuickly_connectsOnlyFirst() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG, EU_ORG))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()
    val gate = CompletableDeferred<Unit>()
    store.saveGate = gate

    viewModel.onOrganizationChosen(EU_ORG)
    viewModel.onOrganizationChosen(US_ORG)

    assertEquals(Step.Saving, viewModel.state.value.step)

    gate.complete(Unit)

    assertEquals("beta-gmbh", store.current?.orgSlug)
    assertEquals(1, store.saveCount)
    assertEquals(Step.Saving, viewModel.state.value.step)
  }

  @Test
  fun back_duringSave_isIgnored() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG, EU_ORG))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()
    val gate = CompletableDeferred<Unit>()
    store.saveGate = gate
    viewModel.onOrganizationChosen(EU_ORG)

    viewModel.onBackFromOrganizations()

    assertEquals(Step.Saving, viewModel.state.value.step)

    gate.complete(Unit)

    assertEquals("beta-gmbh", store.current?.orgSlug)
    assertEquals(Step.Saving, viewModel.state.value.step)
  }

  @Test
  fun cancelWhileValidating_returnsToTheFormKeepingTheToken() {
    api.organizationsGate = CompletableDeferred()
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()
    assertTrue(viewModel.state.value.canCancel)

    viewModel.onCancelValidation()

    assertEquals(Step.EnterToken, viewModel.state.value.step)
    assertEquals("sntryu_abc", viewModel.state.value.token)
    assertFalse(viewModel.state.value.canCancel)
    api.organizationsGate?.complete(Unit)
    assertNull(store.current) // the abandoned check never connects
  }

  @Test
  fun cancel_isNotOfferedWhileSaving() {
    store.saveGate = CompletableDeferred()
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()
    assertFalse(viewModel.state.value.canCancel)

    viewModel.onCancelValidation()

    assertEquals(Step.Validating, viewModel.state.value.step)
    store.saveGate?.complete(Unit)
    assertEquals("acme", store.current?.orgSlug)
  }

  @Test
  fun rateLimited_keepsSentrysRetryAfter() {
    api.organizationsResult = ApiResult.Failure(ApiError.RateLimited(42))
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals(ConnectError.RateLimited(42), viewModel.state.value.error)
  }

  @Test
  fun aTapWhileRateLimited_waitsWithoutAskingSentry() {
    api.organizationsResult = ApiResult.Failure(ApiError.RateLimited(30))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))

    clock.advance(Duration.ofMillis(10_500))
    viewModel.onSubmit()

    assertEquals(1, api.calls.size)
    assertEquals(Step.EnterToken, viewModel.state.value.step)
    assertEquals(ConnectError.RateLimited(20), viewModel.state.value.error)
    assertNull(store.current)
  }

  @Test
  fun aTapAfterTheRateLimit_asksSentryAgain() {
    api.organizationsResult = ApiResult.Failure(ApiError.RateLimited(30))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))

    clock.advance(Duration.ofSeconds(30))
    viewModel.onSubmit()

    assertEquals(2, api.calls.size)
    assertEquals("acme", store.current?.orgSlug)
  }

  @Test
  fun aRateLimitWithoutRetryAfter_holdsForSixtySeconds() {
    api.organizationsResult = ApiResult.Failure(ApiError.RateLimited(null))
    viewModel.onTokenChange("sntryu_abc")
    viewModel.onSubmit()

    clock.advance(Duration.ofSeconds(59))
    viewModel.onSubmit()
    assertEquals(1, api.calls.size)
    assertEquals(ConnectError.RateLimited(1), viewModel.state.value.error)

    clock.advance(Duration.ofSeconds(1))
    viewModel.onSubmit()
    assertEquals(2, api.calls.size)
  }

  @Test
  fun requestTokenPageUrl_forCloud() {
    assertEquals("https://sentry.io/settings/account/api/auth-tokens/", viewModel.requestTokenPageUrl())
  }

  @Test
  fun requestTokenPageUrl_forInvalidSelfHostedUrl_flagsError() {
    viewModel.onHostChoiceChange(HostChoice.SelfHosted)
    viewModel.onSelfHostedUrlChange("not a url")

    assertNull(viewModel.requestTokenPageUrl())
    assertTrue(viewModel.state.value.urlError)
  }

  @Test
  fun aNetworkError_showsUnderTheButton_notOnTheTokenField() {
    api.organizationsResult = ApiResult.Failure(ApiError.Network("timeout"))
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    val state = viewModel.state.value
    assertEquals(ConnectError.Network, state.formError)
    assertNull(state.tokenError)
    assertNull(state.serverUrlError)
  }

  @Test
  fun aRejectedToken_marksTheTokenField() {
    api.organizationsResult = ApiResult.Failure(ApiError.Unauthorized)
    viewModel.onTokenChange("sntryu_bad")

    viewModel.onSubmit()

    assertEquals(ConnectError.InvalidToken, viewModel.state.value.tokenError)
    assertNull(viewModel.state.value.formError)
  }

  @Test
  fun aSelfHostedCertificateProblem_marksTheUrlField_untilTheUrlChanges() {
    api.organizationsResult = ApiResult.Failure(ApiError.Network("handshake", NetworkCause.Tls))
    viewModel.onHostChoiceChange(HostChoice.SelfHosted)
    viewModel.onSelfHostedUrlChange("sentry.example.com")
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals(ConnectError.InsecureConnection, viewModel.state.value.serverUrlError)
    assertNull(viewModel.state.value.tokenError)
    viewModel.onSelfHostedUrlChange("sentry.example.org")
    assertNull(viewModel.state.value.error)
  }

  @Test
  fun onSentryIo_aCertificateProblemShowsUnderTheButton() {
    api.organizationsResult = ApiResult.Failure(ApiError.Network("handshake", NetworkCause.Tls))
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals(ConnectError.InsecureConnection, viewModel.state.value.formError)
  }

  @Test
  fun pasteWithNothingCopied_saysSo_untilTheTokenChanges() {
    viewModel.onPasteToken(null)
    assertTrue(viewModel.state.value.pasteEmpty)

    viewModel.onPasteToken("   ")
    assertTrue(viewModel.state.value.pasteEmpty)

    viewModel.onTokenChange("s")
    assertFalse(viewModel.state.value.pasteEmpty)
  }

  @Test
  fun paste_trimsTheToken() {
    viewModel.onPasteToken("  sntryu_abc\n")

    assertEquals("sntryu_abc", viewModel.state.value.token)
    assertFalse(viewModel.state.value.pasteEmpty)
  }

  @Test
  fun aTokenPageThatCannotOpen_showsItsAddress_untilTheServerChanges() {
    val url = "https://sentry.io/settings/account/api/auth-tokens/"

    viewModel.onTokenPageUnavailable(url)
    assertEquals(url, viewModel.state.value.tokenPageUrlToCopy)

    viewModel.onHostChoiceChange(HostChoice.SelfHosted)
    assertNull(viewModel.state.value.tokenPageUrlToCopy)
  }

  @Test
  fun stateToString_redactsToken() {
    viewModel.onTokenChange("sntryu_secret")
    assertFalse(viewModel.state.value.toString().contains("sntryu_secret"))
  }
}
