package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.IssueListParams
import io.github.sriharinmn.triage.remote.ProjectDto
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoRoutingSentryApiTest {
  private val real = FakeSentryApi()
  private val demo = FakeSentryApi()
  private val api = DemoRoutingSentryApi(real, demo)

  @Test
  fun theDemosServer_goesToTheDemo_andEveryOtherToTheRealApi() = runTest {
    api.listOrganizations(DEMO_BASE_URL, "sample-data")
    api.listOrganizations(TEST_ACCOUNT.apiBaseUrl, "token")
    api.listProjects(DEMO_BASE_URL, "sample-data", "example-shop")

    assertEquals(listOf(DEMO_BASE_URL to "sample-data"), demo.calls)
    assertEquals(listOf(TEST_ACCOUNT.apiBaseUrl to "token"), real.calls)
    assertEquals(listOf("example-shop"), demo.projectCalls)
    assertEquals(emptyList<String>(), real.projectCalls)
  }

  @Test
  fun everyCall_isRouted() = runTest {
    val demoAnswer = ApiResult.Success(emptyList<ProjectDto>())
    demo.projectsResult = demoAnswer
    assertEquals(demoAnswer, api.listProjects(DEMO_BASE_URL, "t", "o"))
  }

  @Test
  fun everyMethod_goesToTheApiThatOwnsTheServer() = runTest {
    val params = IssueListParams(query = "is:unresolved", projectIds = emptyList(), environment = null, sort = "date", limit = 25)
    val changes = buildJsonObject { put("status", JsonPrimitive("resolved")) }

    suspend fun callAll(baseUrl: String, tag: String) {
      api.listOrganizations(baseUrl, "tok")
      api.listProjects(baseUrl, "tok", "org-$tag")
      api.listIssues(baseUrl, "tok", "org-$tag", params, cursor = tag)
      api.getIssue(baseUrl, "tok", "org-$tag", "issue-$tag")
      api.getIssueEvent(baseUrl, "tok", "org-$tag", "issue-$tag", "latest")
      api.getIssueTag(baseUrl, "tok", "org-$tag", "issue-$tag", "browser")
      api.updateIssue(baseUrl, "tok", "org-$tag", "issue-$tag", changes)
      api.listMembers(baseUrl, "tok", "org-$tag")
    }

    callAll(DEMO_BASE_URL, "demo")
    callAll(TEST_ACCOUNT.apiBaseUrl, "real")

    assertEquals(listOf(DEMO_BASE_URL), demo.calls.map { it.first })
    assertEquals(listOf(TEST_ACCOUNT.apiBaseUrl), real.calls.map { it.first })
    assertEquals(listOf("org-demo"), demo.projectCalls)
    assertEquals(listOf("org-real"), real.projectCalls)
    assertEquals(listOf("org-demo" to "demo"), demo.issueCalls.map { it.orgSlug to it.cursor })
    assertEquals(listOf("org-real" to "real"), real.issueCalls.map { it.orgSlug to it.cursor })
    val details = { tag: String -> listOf("issue" to "issue-$tag", "event:latest" to "issue-$tag", "tag:browser" to "issue-$tag") }
    assertEquals(details("demo"), demo.detailCalls.map { it.what to it.issueId })
    assertEquals(details("real"), real.detailCalls.map { it.what to it.issueId })
    assertEquals(listOf("issue-demo"), demo.updateCalls.map { it.first })
    assertEquals(listOf("issue-real"), real.updateCalls.map { it.first })
    assertEquals(listOf("org-demo"), demo.memberCalls)
    assertEquals(listOf("org-real"), real.memberCalls)
  }
}
