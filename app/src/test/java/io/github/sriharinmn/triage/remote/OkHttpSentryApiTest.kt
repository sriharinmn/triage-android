package io.github.sriharinmn.triage.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpSentryApiTest {
  private lateinit var server: MockWebServer
  private val api = OkHttpSentryApi(OkHttpClient(), userAgent = "Triage-Test")

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun baseUrl() = server.url("/").toString().trimEnd('/')

  @Test
  fun listOrganizations_sendsAuthHeadersAndParsesOrgs() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody(ORGS_JSON))

    val result = api.listOrganizations(baseUrl(), "sntryu_abc")

    val request = server.takeRequest()
    assertEquals("/api/0/organizations/", request.path)
    assertEquals("Bearer sntryu_abc", request.getHeader("Authorization"))
    assertEquals("Triage-Test", request.getHeader("User-Agent"))
    val orgs = (result as ApiResult.Success).value
    assertEquals(listOf("acme", "beta-gmbh"), orgs.map { it.slug })
    assertEquals("https://de.sentry.io", orgs[1].links?.regionUrl)
  }

  @Test
  fun unauthorized_mapsToUnauthorized() = runTest {
    server.enqueue(MockResponse().setResponseCode(401))
    assertEquals(ApiResult.Failure(ApiError.Unauthorized), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun forbidden_mapsToForbidden() = runTest {
    server.enqueue(MockResponse().setResponseCode(403))
    assertEquals(ApiResult.Failure(ApiError.Forbidden), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun rateLimited_readsRetryAfter() = runTest {
    server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
    assertEquals(ApiResult.Failure(ApiError.RateLimited(30)), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun serverError_mapsToHttp() = runTest {
    server.enqueue(MockResponse().setResponseCode(500))
    assertEquals(ApiResult.Failure(ApiError.Http(500)), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun malformedJson_mapsToParse() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("{not json"))
    assertEquals(ApiResult.Failure(ApiError.Parse), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun unreachableServer_mapsToNetwork() = runTest {
    val dead = MockWebServer().apply { start() }
    val url = dead.url("/").toString().trimEnd('/')
    dead.shutdown()

    val result = api.listOrganizations(url, "t")

    assertTrue(result is ApiResult.Failure && result.error is ApiError.Network)
  }

  private companion object {
    val ORGS_JSON =
      """
      [
        {"id": "1", "slug": "acme", "name": "Acme Inc", "status": {"id": "active"},
         "links": {"organizationUrl": "https://acme.sentry.io", "regionUrl": "https://us.sentry.io"}},
        {"id": "2", "slug": "beta-gmbh", "name": "Beta GmbH",
         "links": {"organizationUrl": "https://beta-gmbh.sentry.io", "regionUrl": "https://de.sentry.io"}}
      ]
      """
        .trimIndent()
  }
}
