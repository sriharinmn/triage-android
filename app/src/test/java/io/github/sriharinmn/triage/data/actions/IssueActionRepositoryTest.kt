package io.github.sriharinmn.triage.data.actions

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.members.Member
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueActionRepositoryTest {
  private val api = FakeSentryApi()
  private val clock = TestClock()
  private val repository = IssueActionRepository(api, RateLimitGate(clock::now))
  private val ana = Member(userId = "41", name = "Ana Silva", email = "ana@example.com")

  @Test
  fun eachAction_sendsItsDocumentedChanges() = runTest {
    repository.apply(TEST_ACCOUNT, "1", IssueAction.Resolve)
    repository.apply(TEST_ACCOUNT, "2", IssueAction.Archive)
    repository.apply(TEST_ACCOUNT, "3", IssueAction.Assign(ana))
    repository.apply(TEST_ACCOUNT, "4", IssueAction.Assign(null))
    repository.apply(TEST_ACCOUNT, "5", IssueAction.MarkReviewed)

    assertEquals(
      listOf(
        "1" to """{"status":"resolved"}""",
        "2" to """{"status":"ignored","substatus":"archived_until_escalating","statusDetails":{}}""",
        "3" to """{"assignedTo":"user:41"}""",
        "4" to """{"assignedTo":""}""",
        "5" to """{"inbox":false}""",
      ),
      api.updateCalls.map { (id, changes) -> id to changes.toString() },
    )
  }

  @Test
  fun success_isSuccess() = runTest {
    assertEquals(DetailResult.Success(Unit), repository.apply(TEST_ACCOUNT, "1", IssueAction.Resolve))
  }

  @Test
  fun errors_mapForTheScreens() = runTest {
    api.updateHandler = { _, _ -> ApiResult.Failure(ApiError.Forbidden) }
    assertEquals(DetailResult.Failure(DetailError.Forbidden), repository.apply(TEST_ACCOUNT, "1", IssueAction.Resolve))
    api.updateHandler = { _, _ -> ApiResult.Failure(ApiError.Http(404)) }
    assertEquals(DetailResult.Failure(DetailError.NotFound), repository.apply(TEST_ACCOUNT, "1", IssueAction.Resolve))
  }

  @Test
  fun aRateLimit_holdsTheNextActionWithoutSendingIt() = runTest {
    api.updateHandler = { _, _ -> ApiResult.Failure(ApiError.RateLimited(30)) }
    assertEquals(DetailResult.Failure(DetailError.RateLimited(30)), repository.apply(TEST_ACCOUNT, "1", IssueAction.Resolve))

    val held = repository.apply(TEST_ACCOUNT, "2", IssueAction.Archive)

    assertTrue(held is DetailResult.Failure && held.error is DetailError.RateLimited)
    assertEquals(1, api.updateCalls.size)
  }
}
