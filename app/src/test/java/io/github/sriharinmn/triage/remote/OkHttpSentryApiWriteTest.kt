package io.github.sriharinmn.triage.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpSentryApiWriteTest {
  private lateinit var server: MockWebServer
  // MockWebServer serves plain http.
  private val api = OkHttpSentryApi(OkHttpClient(), userAgent = "Triage-Test", requireHttps = false)
  private val resolve = buildJsonObject { put("status", "resolved") }

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
  fun updateIssue_putsTheChangesAsJson() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"resolved"}"""))

    val result = api.updateIssue(baseUrl(), "sntryu_abc", "demo-org", "4201", resolve)

    val request = server.takeRequest()
    assertEquals("PUT", request.method)
    assertEquals("/api/0/organizations/demo-org/issues/4201/", request.requestUrl!!.encodedPath)
    assertEquals("Bearer sntryu_abc", request.getHeader("Authorization"))
    assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
    assertEquals("""{"status":"resolved"}""", request.body.readUtf8())
    assertEquals(ApiResult.Success(Unit), result)
  }

  @Test
  fun updateIssue_anEmptyAnswerIsStillSuccess() = runTest {
    server.enqueue(MockResponse().setResponseCode(204))

    assertEquals(ApiResult.Success(Unit), api.updateIssue(baseUrl(), "sntryu_abc", "demo-org", "4201", resolve))
  }

  @Test
  fun updateIssue_errorsBecomeValues() = runTest {
    server.enqueue(MockResponse().setResponseCode(403))
    server.enqueue(MockResponse().setResponseCode(404))
    server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))

    assertEquals(ApiResult.Failure(ApiError.Forbidden), api.updateIssue(baseUrl(), "t", "o", "1", resolve))
    assertEquals(ApiResult.Failure(ApiError.Http(404)), api.updateIssue(baseUrl(), "t", "o", "1", resolve))
    assertEquals(ApiResult.Failure(ApiError.RateLimited(30)), api.updateIssue(baseUrl(), "t", "o", "1", resolve))
  }

  @Test
  fun updateIssue_refusesDotSegmentsWithoutARequest() = runTest {
    val result = api.updateIssue(baseUrl(), "sntryu_abc", "demo-org", "..", resolve)

    assertTrue(result is ApiResult.Failure && result.error is ApiError.Network)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun listMembers_followsEveryPage_andKeepsInvites() = runTest {
    val next = """<https://sentry.io/x/?cursor=0:100:0>; rel="next"; results="true"; cursor="0:100:0""""
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Link", next)
        .setBody("""[{"id":"7","name":"Ana Silva","email":"ana@example.com","user":{"id":"41","name":"Ana Silva","email":"ana@example.com"}}]""")
    )
    server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":"8","email":"new@example.com","user":null}]"""))

    val result = api.listMembers(baseUrl(), "sntryu_abc", "demo-org")

    assertEquals("/api/0/organizations/demo-org/members/", server.takeRequest().requestUrl!!.encodedPath)
    assertEquals("0:100:0", server.takeRequest().requestUrl!!.queryParameter("cursor"))
    val members = (result as ApiResult.Success).value
    assertEquals(listOf("7", "8"), members.map { it.id })
    assertEquals("41", members[0].user?.id)
    assertNull(members[1].user)
  }

  @Test
  fun getIssue_asksForTheReviewState_andReadsTheAssignee() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"id":"4201","shortId":"DEMO-1","title":"Boom","project":{"id":"101","slug":"demo"},
             "assignedTo":{"type":"user","id":"41","name":"Ana Silva","email":"ana@example.com"},
             "inbox":{"reason":0,"date_added":"2026-10-22T10:00:00Z"}}"""
        )
    )

    val result = api.getIssue(baseUrl(), "sntryu_abc", "demo-org", "4201")

    assertEquals("inbox", server.takeRequest().requestUrl!!.queryParameter("expand"))
    val issue = (result as ApiResult.Success).value
    assertEquals(AssigneeDto(type = "user", id = "41", name = "Ana Silva", email = "ana@example.com"), issue.assignedTo)
    assertTrue(issue.inbox is JsonObject)
  }
}
