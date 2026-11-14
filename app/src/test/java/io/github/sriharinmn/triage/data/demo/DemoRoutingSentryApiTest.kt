package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import kotlinx.coroutines.test.runTest
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
    val demoAnswer = ApiResult.Success(emptyList<io.github.sriharinmn.triage.remote.ProjectDto>())
    demo.projectsResult = demoAnswer
    assertEquals(demoAnswer, api.listProjects(DEMO_BASE_URL, "t", "o"))
  }
}
