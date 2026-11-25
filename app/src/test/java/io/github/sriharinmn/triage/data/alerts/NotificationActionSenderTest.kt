package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.StoredAccount
import io.github.sriharinmn.triage.data.actions.IssueActionRepository
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.RecordingActionNotifier
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionSenderTest {
  private val api = FakeSentryApi()
  private val clock = TestClock()
  private val gate = RateLimitGate(clock::now)
  private val issues = IssueRepository(api, InMemoryIssueCache(), clock::now, gate)
  private var stored: StoredAccount = StoredAccount.Connected(TEST_ACCOUNT)
  private val notifier = RecordingActionNotifier()
  private val sender = NotificationActionSender({ stored }, IssueActionRepository(api, gate), issues, notifier)
  private val resolve = NotificationActionRequest("1", "DEMO-1", TEST_ACCOUNT.orgSlug, AlertAction.Resolve)

  private suspend fun inboxIds() = issues.observeInbox(InboxFilter()).first().issues.map { it.id }

  @Test
  fun resolve_sendsM5sRequest_takesTheIssueOutOfTheInbox_andTakesDownTheNotification() = runTest {
    api.issuesHandler = { ApiResult.Success(Page(listOf(issueDto("1"), issueDto("2")), null)) }
    issues.refresh(TEST_ACCOUNT, InboxFilter())

    sender.send(resolve)

    assertEquals(listOf("1" to buildJsonObject { put("status", "resolved") }), api.updateCalls)
    assertEquals(listOf("2"), inboxIds())
    assertEquals(listOf("sending:1", "cancel:1"), notifier.calls)
  }

  @Test
  fun archive_archivesUntilItEscalates() = runTest {
    sender.send(resolve.copy(action = AlertAction.Archive))

    val archive =
      buildJsonObject {
        put("status", "ignored")
        put("substatus", "archived_until_escalating")
        putJsonObject("statusDetails") {}
      }
    assertEquals(listOf("1" to archive), api.updateCalls)
  }

  @Test
  fun aFailure_saysWhy_andIsNotRetried() = runTest {
    api.updateHandler = { _, _ -> ApiResult.Failure(ApiError.Network("offline")) }

    sender.send(resolve)

    assertEquals(1, api.updateCalls.size)
    assertEquals(listOf("sending:1", "failed:1:Network"), notifier.calls)
  }

  @Test
  fun anIssueThatIsGone_isTakenOutOfTheInbox_andReported() = runTest {
    api.issuesHandler = { ApiResult.Success(Page(listOf(issueDto("1"), issueDto("2")), null)) }
    issues.refresh(TEST_ACCOUNT, InboxFilter())
    api.updateHandler = { _, _ -> ApiResult.Failure(ApiError.Http(404)) }

    sender.send(resolve)

    assertEquals(listOf("2"), inboxIds())
    assertEquals(listOf("sending:1", "failed:1:NotFound"), notifier.calls)
  }

  @Test
  fun insideARateLimitHold_itFailsWithoutAskingSentry() = runTest {
    gate.hold(60)

    sender.send(resolve)

    assertTrue(api.updateCalls.isEmpty())
    assertEquals(listOf("sending:1", "failed:1:RateLimited(retryAfterSeconds=60)"), notifier.calls)
  }

  @Test
  fun anAccountThatIsGoneOrAnotherOrg_dropsTheAction() = runTest {
    stored = StoredAccount.Missing
    sender.send(resolve)
    stored = StoredAccount.Connected(TEST_ACCOUNT.copy(orgSlug = "another-org"))
    sender.send(resolve)

    assertTrue(api.updateCalls.isEmpty())
    assertEquals(listOf("cancel:1", "cancel:1"), notifier.calls)
  }

  @Test
  fun anUnreadableAccount_saysSo_andSendsNothing() = runTest {
    stored = StoredAccount.Unreadable

    sender.send(resolve)

    assertTrue(api.updateCalls.isEmpty())
    assertEquals(listOf("account-unreadable:1"), notifier.calls)
  }
}
