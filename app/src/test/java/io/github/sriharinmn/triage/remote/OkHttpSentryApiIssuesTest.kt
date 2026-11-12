package io.github.sriharinmn.triage.remote

import io.github.sriharinmn.triage.testing.readFixture
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpSentryApiIssuesTest {
  private lateinit var server: MockWebServer
  // MockWebServer serves plain http.
  private val api = OkHttpSentryApi(OkHttpClient(), userAgent = "Triage-Test", requireHttps = false)
  private val unresolved = IssueListParams(query = "is:unresolved", projectIds = emptyList(), environment = null, sort = "date")

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

  private fun link(results: Boolean, cursor: String) =
    """<https://sentry.io/x/?cursor=0:0:1>; rel="previous"; results="false"; cursor="0:0:1", """ +
      """<https://sentry.io/x/?cursor=$cursor>; rel="next"; results="$results"; cursor="$cursor""""

  @Test
  fun listIssues_sendsTheFilterAsQueryParameters() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

    api.listIssues(
      baseUrl(),
      "sntryu_abc",
      "demo-org",
      unresolved.copy(projectIds = listOf("102"), environment = "production", sort = "freq"),
      cursor = "0:25:0",
    )

    val request = server.takeRequest()
    assertEquals("Bearer sntryu_abc", request.getHeader("Authorization"))
    val url = request.requestUrl!!
    assertEquals("/api/0/organizations/demo-org/issues/", url.encodedPath)
    assertEquals("is:unresolved", url.queryParameter("query"))
    assertEquals("102", url.queryParameter("project"))
    assertEquals("production", url.queryParameter("environment"))
    assertEquals("freq", url.queryParameter("sort"))
    assertEquals("14d", url.queryParameter("statsPeriod"))
    assertEquals("24h", url.queryParameter("groupStatsPeriod"))
    assertEquals("25", url.queryParameter("limit"))
    assertEquals(listOf("lifetime", "filtered"), url.queryParameterValues("collapse"))
    assertEquals("0:25:0", url.queryParameter("cursor"))
  }

  @Test
  fun listIssues_asksForEachChosenProject_andAWholePage() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

    api.listIssues(baseUrl(), "t", "demo-org", unresolved.copy(projectIds = listOf("101", "103"), limit = 100), cursor = null)

    val url = server.takeRequest().requestUrl!!
    assertEquals(listOf("101", "103"), url.queryParameterValues("project"))
    assertEquals("100", url.queryParameter("limit"))
  }

  @Test
  fun listIssues_firstPageOfAllProjectsAndEnvironments() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

    api.listIssues(baseUrl(), "t", "demo-org", unresolved, cursor = null)

    val url = server.takeRequest().requestUrl!!
    assertEquals("-1", url.queryParameter("project"))
    assertNull(url.queryParameter("environment"))
    assertNull(url.queryParameter("cursor"))
  }

  @Test
  fun listIssues_parsesThePageAndItsNextCursor() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(readFixture("issues_page.json"))
        .setHeader("Link", link(results = true, "1792749600000:0:0"))
    )

    val page = (api.listIssues(baseUrl(), "t", "example-org", unresolved, null) as ApiResult.Success).value

    assertEquals("1792749600000:0:0", page.nextCursor)
    assertEquals(listOf("TRIAGE-DEMO-1", "TRIAGE-DEMO-ANDROID-3", "TRIAGE-DEMO-8"), page.items.map { it.shortId })
    val node = page.items[0]
    assertEquals(48L, node.count) // Sentry sends count as a string
    assertEquals(11L, node.userCount)
    assertEquals("new", node.substatus)
    assertEquals("error", node.level)
    assertFalse(node.isUnhandled)
    assertEquals(24, node.stats["24h"]?.size)
    assertEquals(9.0, node.stats["24h"]?.last()?.get(1))
    val android = page.items[1]
    assertEquals(28L, android.count) // ...and sometimes as a number
    assertTrue(android.isUnhandled)
    assertEquals("102", android.project.id)
    assertEquals("triage-demo-android", android.project.slug)
    assertTrue(page.items[2].stats.isEmpty())
  }

  @Test
  fun listIssues_lastPageHasNoCursor() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]").setHeader("Link", link(results = false, "x")))
    val page = (api.listIssues(baseUrl(), "t", "example-org", unresolved, null) as ApiResult.Success).value
    assertNull(page.nextCursor)
  }

  @Test
  fun listIssues_orgSlugIsPathEncoded() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
    api.listIssues(baseUrl(), "t", "a/b", unresolved, null)
    assertEquals("/api/0/organizations/a%2Fb/issues/", server.takeRequest().requestUrl!!.encodedPath)
  }

  @Test
  fun listIssues_nullCount_readsAsZero() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody(issueJson(count = "null")))
    val page = (api.listIssues(baseUrl(), "t", "example-org", unresolved, null) as ApiResult.Success).value
    assertEquals(0L, page.items.single().count)
  }

  @Test
  fun listIssues_nonNumericCount_mapsToParse() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody(issueJson(count = "\"lots\"")))
    assertEquals(ApiResult.Failure(ApiError.Parse), api.listIssues(baseUrl(), "t", "example-org", unresolved, null))
  }

  @Test
  fun listProjects_parsesEnvironmentsAndAccess() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody(readFixture("projects.json")))

    val projects = (api.listProjects(baseUrl(), "t", "example-org") as ApiResult.Success).value

    assertEquals("/api/0/organizations/example-org/projects/", server.takeRequest().path)
    assertEquals(listOf("triage-demo", "triage-demo-android", "billing"), projects.map { it.slug })
    assertEquals(listOf("staging", "production"), projects[0].environments)
    assertEquals("android", projects[1].platform)
    assertFalse(projects[2].hasAccess)
  }

  @Test
  fun listProjects_followsPages() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""[{"id": "1", "slug": "a", "name": "A"}]""")
        .setHeader("Link", link(results = true, "0:100:0"))
    )
    server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id": "2", "slug": "b", "name": "B"}]"""))

    val projects = (api.listProjects(baseUrl(), "t", "example-org") as ApiResult.Success).value

    assertEquals(listOf("a", "b"), projects.map { it.slug })
    server.takeRequest()
    assertEquals("0:100:0", server.takeRequest().requestUrl!!.queryParameter("cursor"))
  }

  private fun issueJson(count: String) =
    """[{"id": "1", "shortId": "X-1", "title": "Boom", "count": $count, "userCount": 1, "project": {"id": "1", "slug": "x"}}]"""
}
