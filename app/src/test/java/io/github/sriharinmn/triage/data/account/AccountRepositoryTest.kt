package io.github.sriharinmn.triage.data.account

import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.NetworkCause
import io.github.sriharinmn.triage.remote.OrganizationLinksDto
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

  private fun orgWithLinks(regionUrl: String?, organizationUrl: String?) =
    US_ORG.copy(links = OrganizationLinksDto(organizationUrl = organizationUrl, regionUrl = regionUrl))

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
        ApiError.RateLimited(10) to ConnectError.RateLimited(10),
        ApiError.RateLimited(null) to ConnectError.RateLimited(null),
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
  fun onASelfHostedServer_addressProblemsSayWhatIsWrong() = runTest {
    val selfHosted = SentryHost.SelfHosted("https://sentry.example.com")
    val cases =
      mapOf(
        ApiError.Network("handshake", NetworkCause.Tls) to ConnectError.InsecureConnection,
        ApiError.Network("no such host", NetworkCause.UnknownHost) to ConnectError.ServerNotFound,
        ApiError.Network("timeout") to ConnectError.Network,
        ApiError.Http(404) to ConnectError.NotSentryServer,
        ApiError.Parse to ConnectError.NotSentryServer,
        ApiError.Http(502) to ConnectError.Unknown,
      )
    for ((apiError, expected) in cases) {
      api.organizationsResult = ApiResult.Failure(apiError)
      assertEquals(ConnectResult.Error(expected), repository.fetchOrganizations(selfHosted, "sntryu_abc"))
    }
  }

  @Test
  fun onSentryIo_anUnknownHostOnlyMeansOffline() = runTest {
    val cases =
      mapOf(
        ApiError.Network("no such host", NetworkCause.UnknownHost) to ConnectError.Network,
        ApiError.Network("handshake", NetworkCause.Tls) to ConnectError.InsecureConnection,
        ApiError.Http(404) to ConnectError.Unknown,
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
  fun connect_onCloud_rejectsNonHttpsLinks() = runTest {
    val org = orgWithLinks(regionUrl = "http://de.sentry.io", organizationUrl = "http://beta.sentry.io")
    repository.connect(SentryHost.Cloud, "sntryu_abc", org)
    assertEquals("https://sentry.io", store.current?.apiBaseUrl)
    assertEquals("https://sentry.io", store.current?.webBaseUrl)
  }

  @Test
  fun connect_onCloud_rejectsForeignHostLinks() = runTest {
    val org =
      orgWithLinks(regionUrl = "https://evil.example.com", organizationUrl = "https://sentry.io.evil.example.com")
    repository.connect(SentryHost.Cloud, "sntryu_abc", org)
    assertEquals("https://sentry.io", store.current?.apiBaseUrl)
    assertEquals("https://sentry.io", store.current?.webBaseUrl)
  }

  @Test
  fun connect_onCloud_rejectsLookalikeSuffixHosts() = runTest {
    val org = orgWithLinks(regionUrl = "https://evilsentry.io", organizationUrl = "https://notsentry.io")
    repository.connect(SentryHost.Cloud, "sntryu_abc", org)
    assertEquals("https://sentry.io", store.current?.apiBaseUrl)
    assertEquals("https://sentry.io", store.current?.webBaseUrl)
  }

  @Test
  fun connect_onCloud_keepsOnlyTheOriginOfLinks() = runTest {
    val org =
      orgWithLinks(regionUrl = "https://DE.Sentry.io:443/api/0/", organizationUrl = " https://beta-gmbh.sentry.io/issues/ ")
    repository.connect(SentryHost.Cloud, "sntryu_abc", org)
    assertEquals("https://de.sentry.io", store.current?.apiBaseUrl)
    assertEquals("https://beta-gmbh.sentry.io", store.current?.webBaseUrl)
  }

  @Test
  fun connect_onCloud_blankOrMissingLinkFallsBackPerField() = runTest {
    repository.connect(
      SentryHost.Cloud,
      "sntryu_abc",
      orgWithLinks(regionUrl = "  ", organizationUrl = "https://acme.sentry.io"),
    )
    assertEquals("https://sentry.io", store.current?.apiBaseUrl)
    assertEquals("https://acme.sentry.io", store.current?.webBaseUrl)

    repository.connect(
      SentryHost.Cloud,
      "sntryu_abc",
      orgWithLinks(regionUrl = "https://us.sentry.io", organizationUrl = null),
    )
    assertEquals("https://us.sentry.io", store.current?.apiBaseUrl)
    assertEquals("https://sentry.io", store.current?.webBaseUrl)
  }

  @Test
  fun connect_onCloud_acceptsBareApexLinks() = runTest {
    val org = orgWithLinks(regionUrl = "https://sentry.io", organizationUrl = "https://sentry.io/")
    repository.connect(SentryHost.Cloud, "sntryu_abc", org)
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
