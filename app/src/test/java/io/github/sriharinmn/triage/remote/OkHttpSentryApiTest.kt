package io.github.sriharinmn.triage.remote

import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpSentryApiTest {
  private lateinit var server: MockWebServer
  // MockWebServer serves plain http.
  private val client = OkHttpClient()
  private val api = OkHttpSentryApi(client, userAgent = "Triage-Test", requireHttps = false)

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
  fun rateLimited_readsRetryAfterSeconds() = runTest {
    server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
    assertEquals(ApiResult.Failure(ApiError.RateLimited(30)), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun rateLimited_readsRetryAfterHttpDate() = runTest {
    val inTwoMinutes = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(120))
    server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", inTwoMinutes))

    val error = (api.listOrganizations(baseUrl(), "t") as ApiResult.Failure).error as ApiError.RateLimited

    val seconds = error.retryAfterSeconds
    assertTrue("retryAfterSeconds=$seconds", seconds != null && seconds in 100..121)
  }

  @Test
  fun rateLimitedWithoutRetryAfter_hasNoDelay() = runTest {
    server.enqueue(MockResponse().setResponseCode(429))
    assertEquals(ApiResult.Failure(ApiError.RateLimited(null)), api.listOrganizations(baseUrl(), "t"))
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

  @Test
  fun aFailedSecureConnection_isReportedAsATlsProblem() = runTest {
    val untrusted =
      OkHttpClient.Builder()
        .addInterceptor(
          Interceptor {
            throw SSLHandshakeException("certificate not trusted").apply {
              initCause(CertificateException("Trust anchor for certification path not found."))
            }
          }
        )
        .build()

    val result = OkHttpSentryApi(untrusted, userAgent = "Triage-Test", requireHttps = false).listOrganizations(baseUrl(), "t")

    assertEquals(NetworkCause.Tls, ((result as ApiResult.Failure).error as ApiError.Network).cause)
  }

  @Test
  fun aSecureConnectionThatDropped_isNotReportedAsATlsProblem() = runTest {
    val reset = SSLException("Read error: ssl=0x0: I/O error during system call, Connection reset by peer")
    val dropping = OkHttpClient.Builder().addInterceptor(Interceptor { throw reset }).build()

    val result = OkHttpSentryApi(dropping, userAgent = "Triage-Test", requireHttps = false).listOrganizations(baseUrl(), "t")

    assertEquals(NetworkCause.Other, ((result as ApiResult.Failure).error as ApiError.Network).cause)
  }

  @Test
  fun aServerNameThatDoesNotResolve_isReportedAsAnUnknownHost() = runTest {
    val noDns = OkHttpClient.Builder().addInterceptor(Interceptor { throw UnknownHostException("sentry.example.invalid") }).build()

    val result = OkHttpSentryApi(noDns, userAgent = "Triage-Test", requireHttps = false).listOrganizations(baseUrl(), "t")

    assertEquals(NetworkCause.UnknownHost, ((result as ApiResult.Failure).error as ApiError.Network).cause)
  }

  @Test
  fun malformedToken_mapsToUnauthorizedWithoutRequest() = runTest {
    val result = api.listOrganizations(baseUrl(), "sntryu_abc\nInjected: x")

    assertEquals(ApiResult.Failure(ApiError.Unauthorized), result)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun insecureUrl_isRefusedWhenHttpsRequired() = runTest {
    // Queued so that a missing guard shows up as a successful call rather than a slow timeout.
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
    val strictApi = OkHttpSentryApi(OkHttpClient(), userAgent = "Triage-Test")

    val result = strictApi.listOrganizations(baseUrl(), "sntryu_abc")

    assertTrue("expected a network failure: $result", result is ApiResult.Failure && result.error is ApiError.Network)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun cancellation_cancelsInFlightCall() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]").setHeadersDelay(10, TimeUnit.SECONDS))

    val job = launch(Dispatchers.Default) { api.listOrganizations(baseUrl(), "t") }
    assertNotNull("request never reached the server", server.takeRequest(5, TimeUnit.SECONDS))

    val elapsedMs = measureTimeMillis { job.cancelAndJoin() }

    assertTrue("cancelAndJoin took ${elapsedMs}ms", elapsedMs < 2_000)
    // The OkHttp call itself must be cancelled, not just abandoned while it keeps the connection busy.
    val deadline = System.currentTimeMillis() + 2_000
    while (client.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
    assertEquals(0, client.dispatcher.runningCallsCount())
  }

  @Test
  fun request_asksForJson() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
    api.listOrganizations(baseUrl(), "t")
    assertEquals("application/json", server.takeRequest().getHeader("Accept"))
  }

  @Test
  fun baseUrlWithPath_keepsThePath() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
    api.listOrganizations("${baseUrl()}/sentry/", "t")
    assertEquals("/sentry/api/0/organizations/", server.takeRequest().path)
  }

  @Test
  fun emptyBody_mapsToParse() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody(""))
    assertEquals(ApiResult.Failure(ApiError.Parse), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun noContent_mapsToParse() = runTest {
    server.enqueue(MockResponse().setResponseCode(204))
    assertEquals(ApiResult.Failure(ApiError.Parse), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun wrongShapeJson_mapsToParse() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"detail": "not a list"}"""))
    assertEquals(ApiResult.Failure(ApiError.Parse), api.listOrganizations(baseUrl(), "t"))
  }

  @Test
  fun listOrganizations_followsNextCursorAcrossPages() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(ACME_PAGE).setHeader("Link", nextLink(results = true, "0:100:0"))
    )
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(BETA_PAGE).setHeader("Link", nextLink(results = false, "0:200:0"))
    )

    val result = api.listOrganizations(baseUrl(), "t")

    assertEquals(listOf("acme", "beta-gmbh"), (result as ApiResult.Success).value.map { it.slug })
    assertEquals("/api/0/organizations/", server.takeRequest().path)
    val second = server.takeRequest().requestUrl!!
    assertEquals("/api/0/organizations/", second.encodedPath)
    assertEquals("0:100:0", second.queryParameter("cursor"))
  }

  @Test
  fun listOrganizations_stopsAfterTenPages() = runTest {
    repeat(11) {
      server.enqueue(
        MockResponse().setResponseCode(200).setBody(ACME_PAGE).setHeader("Link", nextLink(results = true, "c$it"))
      )
    }

    val result = api.listOrganizations(baseUrl(), "t")

    assertEquals(10, (result as ApiResult.Success).value.size)
    assertEquals(10, server.requestCount)
  }

  @Test
  fun listOrganizations_failureOnALaterPage_isReturned() = runTest {
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(ACME_PAGE).setHeader("Link", nextLink(results = true, "0:100:0"))
    )
    server.enqueue(MockResponse().setResponseCode(500))

    assertEquals(ApiResult.Failure(ApiError.Http(500)), api.listOrganizations(baseUrl(), "t"))
  }

  private fun nextLink(results: Boolean, cursor: String) =
    """<https://sentry.io/api/0/organizations/?cursor=0:0:1>; rel="previous"; results="false"; cursor="0:0:1", """ +
      """<https://sentry.io/api/0/organizations/?cursor=$cursor>; rel="next"; results="$results"; cursor="$cursor""""

  private companion object {
    const val ACME_PAGE = """[{"id": "1", "slug": "acme", "name": "Acme Inc"}]"""
    const val BETA_PAGE = """[{"id": "2", "slug": "beta-gmbh", "name": "Beta GmbH"}]"""

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
