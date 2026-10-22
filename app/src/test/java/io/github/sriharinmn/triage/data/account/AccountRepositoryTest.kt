package io.github.sriharinmn.triage.data.account

import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.testing.EU_ORG
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.US_ORG
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {
  private val api = FakeSentryApi()
  private val store = InMemoryAccountStore()
  private val repository = AccountRepository(api, store)

  @Test
  fun blankToken_isRejectedWithoutCallingApi() = runTest {
    assertEquals(ConnectResult.Error(ConnectError.EmptyToken), repository.fetchOrganizations(SentryHost.Cloud, "   "))
    assertTrue(api.calls.isEmpty())
  }

  @Test
  fun orgAuthToken_isRejectedWithoutCallingApi() = runTest {
    assertEquals(
      ConnectResult.Error(ConnectError.OrgTokenNotSupported),
      repository.fetchOrganizations(SentryHost.Cloud, "sntrys_abc"),
    )
    assertTrue(api.calls.isEmpty())
  }

  @Test
  fun token_isTrimmedAndSentToHostBaseUrl() = runTest {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG))
    repository.fetchOrganizations(SentryHost.Cloud, "  sntryu_abc \n")
    assertEquals(listOf("https://sentry.io" to "sntryu_abc"), api.calls)
  }

  @Test
  fun organizations_areReturned() = runTest {
    api.organizationsResult = ApiResult.Success(listOf(US_ORG, EU_ORG))
    assertEquals(
      ConnectResult.Organizations(listOf(US_ORG, EU_ORG)),
      repository.fetchOrganizations(SentryHost.Cloud, "sntryu_abc"),
    )
  }

  @Test
  fun emptyOrganizationList_isAnError() = runTest {
    api.organizationsResult = ApiResult.Success(emptyList())
    assertEquals(
      ConnectResult.Error(ConnectError.NoOrganizations),
      repository.fetchOrganizations(SentryHost.Cloud, "sntryu_abc"),
    )
  }

  @Test
  fun apiErrors_mapToConnectErrors() = runTest {
    val cases =
      mapOf(
        ApiError.Unauthorized to ConnectError.InvalidToken,
        ApiError.Forbidden to ConnectError.MissingScopes,
        ApiError.RateLimited(10) to ConnectError.RateLimited,
        ApiError.Network("timeout") to ConnectError.Network,
        ApiError.Http(500) to ConnectError.Unknown,
        ApiError.Parse to ConnectError.Unknown,
      )
    for ((apiError, expected) in cases) {
      api.organizationsResult = ApiResult.Failure(apiError)
      assertEquals(ConnectResult.Error(expected), repository.fetchOrganizations(SentryHost.Cloud, "sntryu_abc"))
    }
  }

  @Test
  fun connect_onCloud_usesOrgRegionAndOrgUrl() = runTest {
    repository.connect(SentryHost.Cloud, " sntryu_abc ", EU_ORG)
    assertEquals(
      Account(
        apiBaseUrl = "https://de.sentry.io",
        webBaseUrl = "https://beta-gmbh.sentry.io",
        token = "sntryu_abc",
        orgSlug = "beta-gmbh",
        orgName = "Beta GmbH",
      ),
      store.current,
    )
  }

  @Test
  fun connect_onCloudWithoutLinks_fallsBackToSentryIo() = runTest {
    repository.connect(SentryHost.Cloud, "sntryu_abc", US_ORG.copy(links = null))
    assertEquals("https://sentry.io", store.current?.apiBaseUrl)
    assertEquals("https://sentry.io", store.current?.webBaseUrl)
  }

  @Test
  fun connect_onSelfHosted_ignoresLinks() = runTest {
    repository.connect(SentryHost.SelfHosted("https://sentry.example.com"), "sntryu_abc", US_ORG)
    assertEquals("https://sentry.example.com", store.current?.apiBaseUrl)
    assertEquals("https://sentry.example.com", store.current?.webBaseUrl)
  }

  @Test
  fun disconnect_clearsAccount() = runTest {
    repository.connect(SentryHost.Cloud, "sntryu_abc", US_ORG)
    repository.disconnect()
    assertNull(store.current)
  }

  @Test
  fun account_toStringRedactsToken() {
    val account = Account("https://us.sentry.io", "https://acme.sentry.io", "sntryu_secret", "acme", "Acme Inc")
    assertFalse(account.toString().contains("sntryu_secret"))
  }
}
