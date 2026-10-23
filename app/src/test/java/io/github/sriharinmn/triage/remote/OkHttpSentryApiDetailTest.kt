package io.github.sriharinmn.triage.remote

import io.github.sriharinmn.triage.testing.readFixture
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpSentryApiDetailTest {
  private lateinit var server: MockWebServer
  // MockWebServer serves plain http.
  private val api = OkHttpSentryApi(OkHttpClient(), userAgent = "Triage-Test", requireHttps = false)

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

  private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

  @Test
  fun getIssue_requestsTheOrgScopedIssueAndReadsIt() = runTest {
    server.enqueue(ok(readFixture("issue_detail.json")))

    val result = api.getIssue(baseUrl(), "sntryu_abc", "demo-org", "4201")

    val request = server.takeRequest()
    assertEquals("/api/0/organizations/demo-org/issues/4201/", request.requestUrl!!.encodedPath)
    assertEquals("Bearer sntryu_abc", request.getHeader("Authorization"))
    val issue = (result as ApiResult.Success).value
    assertEquals("TRIAGE-DEMO-1A", issue.shortId)
    assertEquals(1204L, issue.count)
    assertEquals(340L, issue.userCount)
    assertNull(issue.isUnhandled)
    assertEquals("1.4.0", issue.firstRelease?.shortVersion)
    assertEquals(24, issue.stats["24h"]?.size)
    assertEquals(30, issue.stats["30d"]?.size)
    assertEquals(9, issue.tags.size)
    assertEquals("os.name", issue.tags[4].key)
  }

  @Test
  fun getIssueEvent_requestsTheEventByKindAndKeepsTheEntriesRaw() = runTest {
    server.enqueue(ok(readFixture("event_js.json")))

    val result = api.getIssueEvent(baseUrl(), "sntryu_abc", "demo-org", "4201", "recommended")

    assertEquals("/api/0/organizations/demo-org/issues/4201/events/recommended/", server.takeRequest().requestUrl!!.encodedPath)
    val event = (result as ApiResult.Success).value
    assertEquals("a1b2c3d4e5f60718293a4b5c6d7e8f90", event.id)
    assertEquals(3, event.entries.size)
    assertTrue(event.release is JsonObject)
  }

  @Test
  fun getIssueTag_encodesTheKeyAsOnePathSegment() = runTest {
    server.enqueue(ok(readFixture("tag_browser.json")))
    server.enqueue(ok(readFixture("tag_browser.json")))

    val result = api.getIssueTag(baseUrl(), "sntryu_abc", "demo-org", "4201", "os.name")
    api.getIssueTag(baseUrl(), "sntryu_abc", "demo-org", "4201", "a/b")

    assertEquals("/api/0/organizations/demo-org/issues/4201/tags/os.name/", server.takeRequest().requestUrl!!.encodedPath)
    assertEquals("/api/0/organizations/demo-org/issues/4201/tags/a%2Fb/", server.takeRequest().requestUrl!!.encodedPath)
    val tag = (result as ApiResult.Success).value
    assertEquals(1100L, tag.totalValues)
    assertEquals(listOf(858L, 190L, 52L), tag.topValues.map { it.count })
  }

  @Test
  fun dotSegments_areRefusedWithoutARequest() = runTest {
    // OkHttp resolves "." and ".." in paths, so a tag key from a response could otherwise aim the token elsewhere.
    val parent = api.getIssueTag(baseUrl(), "sntryu_abc", "demo-org", "4201", "..")
    val current = api.getIssue(baseUrl(), "sntryu_abc", "demo-org", ".")
    val empty = api.getIssueEvent(baseUrl(), "sntryu_abc", "demo-org", "", "latest")

    assertTrue(parent is ApiResult.Failure && parent.error is ApiError.Network)
    assertTrue(current is ApiResult.Failure && current.error is ApiError.Network)
    assertTrue(empty is ApiResult.Failure && empty.error is ApiError.Network)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun aBodyOverTheCap_isTooLarge() = runTest {
    server.enqueue(ok("x".repeat(MAX_RESPONSE_BYTES.toInt() + 1)))

    assertEquals(
      ApiResult.Failure(ApiError.TooLarge),
      api.getIssueEvent(baseUrl(), "sntryu_abc", "demo-org", "4201", "latest"),
    )
  }

  @Test
  fun aChunkedBodyOverTheCap_isTooLarge() = runTest {
    // No Content-Length: the cap must hold while streaming, too.
    server.enqueue(MockResponse().setResponseCode(200).setChunkedBody("x".repeat(MAX_RESPONSE_BYTES.toInt() + 1), 65_536))

    assertEquals(
      ApiResult.Failure(ApiError.TooLarge),
      api.getIssueEvent(baseUrl(), "sntryu_abc", "demo-org", "4201", "latest"),
    )
  }

  @Test
  fun aChunkedBodyUnderTheCap_isRead() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setChunkedBody(readFixture("tag_browser.json"), 64))

    val result = api.getIssueTag(baseUrl(), "sntryu_abc", "demo-org", "4201", "browser")

    assertEquals("browser", (result as ApiResult.Success).value.key)
  }

  @Test
  fun notFound_isHttp404() = runTest {
    server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Not found"}"""))

    assertEquals(ApiResult.Failure(ApiError.Http(404)), api.getIssue(baseUrl(), "sntryu_abc", "demo-org", "4201"))
  }

  @Test
  fun rateLimited_keepsRetryAfter() = runTest {
    server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))

    assertEquals(
      ApiResult.Failure(ApiError.RateLimited(30)),
      api.getIssueEvent(baseUrl(), "sntryu_abc", "demo-org", "4201", "latest"),
    )
  }
}
