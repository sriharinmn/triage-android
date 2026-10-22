package io.github.sriharinmn.triage.ui.connect

import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.ConnectError
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.testing.EU_ORG
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.MainDispatcherRule
import io.github.sriharinmn.triage.testing.US_ORG
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
  private val viewModel = ConnectViewModel(AccountRepository(api, store))

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
  fun submit_withSingleOrganization_connectsAndResets() {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))
    viewModel.onTokenChange("sntryu_abc")

    viewModel.onSubmit()

    assertEquals("acme", store.current?.orgSlug)
    assertEquals("sntryu_abc", store.current?.token)
    assertEquals(ConnectUiState(), viewModel.state.value)
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
    assertEquals(ConnectUiState(), viewModel.state.value)
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
  fun stateToString_redactsToken() {
    viewModel.onTokenChange("sntryu_secret")
    assertFalse(viewModel.state.value.toString().contains("sntryu_secret"))
  }
}
