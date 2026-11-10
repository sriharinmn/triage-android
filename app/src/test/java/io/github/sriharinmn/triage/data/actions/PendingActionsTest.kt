package io.github.sriharinmn.triage.data.actions

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.members.Member
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingActionsTest {
  private val api = FakeSentryApi()
  private val cache = InMemoryIssueCache()
  private val clock = TestClock()
  private val gate = RateLimitGate(clock::now)
  private val issues = IssueRepository(api, cache, clock::now, gate)
  private val ana = Member(userId = "41", name = "Ana Silva", email = null)
  private val ben = Member(userId = "42", name = "Ben Okafor", email = null)

  private fun TestScope.pending() = PendingActions(IssueActionRepository(api, gate), issues, backgroundScope)

  private fun TestScope.waitOutTheUndoWindow() {
    advanceTimeBy(PendingActions.UNDO_WINDOW.inWholeMilliseconds + 1)
    runCurrent()
  }

  private suspend fun cacheLists(vararg tabs: InboxTab) {
    api.issuesHandler = { ApiResult.Success(Page(listOf(issueDto("1"), issueDto("2")), null)) }
    for (tab in tabs) issues.refresh(TEST_ACCOUNT, InboxFilter(tab = tab))
  }

  private suspend fun ids(tab: InboxTab) = issues.observeInbox(InboxFilter(tab = tab)).first().issues.map { it.id }

  @Test
  fun anAction_hidesTheIssueAtOnce_andIsSentOnlyAfterTheUndoWindow() = runTest {
    val pending = pending()

    val submitted = pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)

    assertTrue(pending.overlays.value.getValue("1").hides(InboxTab.Unresolved))
    assertEquals(ActionNotice.Pending(submitted), pending.notice.value)
    advanceTimeBy(PendingActions.UNDO_WINDOW.inWholeMilliseconds - 1)
    runCurrent()
    assertTrue(api.updateCalls.isEmpty())
    advanceTimeBy(2)
    runCurrent()
    assertEquals(listOf("1"), api.updateCalls.map { it.first })
    assertNull(pending.notice.value)
  }

  @Test
  fun onceTheSendStarts_thePendingNoticeIsGone() = runTest {
    api.updateGate = CompletableDeferred()
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)

    waitOutTheUndoWindow()

    assertEquals(1, api.updateCalls.size)
    assertNull(pending.notice.value)
  }

  @Test
  fun dismissingTheOldNoticeAndFlushing_whileSending_doesNotCancelTheSend() = runTest {
    cacheLists(InboxTab.Unresolved)
    val gateOpen = CompletableDeferred<Unit>()
    api.updateGate = gateOpen
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)
    val old = pending.notice.value!!
    waitOutTheUndoWindow()

    pending.onNoticeDismissed(old)
    pending.flushNow()
    runCurrent()
    gateOpen.complete(Unit)
    runCurrent()

    assertEquals(1, api.updateCalls.size)
    assertTrue(pending.overlays.value.isEmpty())
    assertEquals(listOf("2"), ids(InboxTab.Unresolved))
  }

  @Test
  fun undo_whileSending_changesNothing() = runTest {
    cacheLists(InboxTab.Unresolved)
    val gateOpen = CompletableDeferred<Unit>()
    api.updateGate = gateOpen
    val pending = pending()
    val submitted = pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)
    waitOutTheUndoWindow()

    pending.undo(submitted.id)
    assertTrue(pending.overlays.value.getValue("1").hidden)
    gateOpen.complete(Unit)
    runCurrent()

    assertEquals(1, api.updateCalls.size)
    assertTrue(pending.overlays.value.isEmpty())
    assertEquals(listOf("2"), ids(InboxTab.Unresolved))
  }

  @Test
  fun undo_sendsNothing_andBringsTheIssueBack() = runTest {
    val pending = pending()
    val submitted = pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Archive)

    pending.undo(submitted.id)
    waitOutTheUndoWindow()

    assertTrue(api.updateCalls.isEmpty())
    assertTrue(pending.overlays.value.isEmpty())
    assertNull(pending.notice.value)
  }

  @Test
  fun aResolveThatWentThrough_leavesEveryCachedList() = runTest {
    cacheLists(InboxTab.Unresolved, InboxTab.ForReview)
    val pending = pending()

    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)
    waitOutTheUndoWindow()

    assertEquals(listOf("2"), ids(InboxTab.Unresolved))
    assertEquals(listOf("2"), ids(InboxTab.ForReview))
    // The cache no longer has it, so nothing needs hiding any more.
    assertTrue(pending.overlays.value.isEmpty())
  }

  @Test
  fun markReviewed_leavesOnlyForReview_andStaysMarked() = runTest {
    cacheLists(InboxTab.Unresolved, InboxTab.ForReview)
    val pending = pending()

    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.MarkReviewed)
    assertTrue(pending.overlays.value.getValue("1").hides(InboxTab.ForReview))
    assertFalse(pending.overlays.value.getValue("1").hides(InboxTab.Unresolved))
    waitOutTheUndoWindow()

    assertEquals(listOf("1", "2"), ids(InboxTab.Unresolved))
    assertEquals(listOf("2"), ids(InboxTab.ForReview))
    assertTrue(pending.overlays.value.getValue("1").reviewed)
  }

  @Test
  fun anAssignment_showsAtOnce_staysAfterItWentThrough_andUndoRestoresTheLastOne() = runTest {
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Assign(ana))
    waitOutTheUndoWindow()
    assertEquals(Assignment(ana), pending.overlays.value.getValue("1").assignment)

    val second = pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Assign(ben))
    assertEquals(Assignment(ben), pending.overlays.value.getValue("1").assignment)
    pending.undo(second.id)

    assertEquals(Assignment(ana), pending.overlays.value.getValue("1").assignment)
  }

  @Test
  fun aFailure_bringsTheIssueBack_andSaysWhy() = runTest {
    api.updateHandler = { _, _ -> ApiResult.Failure(ApiError.Network("offline")) }
    val pending = pending()

    val submitted = pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)
    waitOutTheUndoWindow()

    assertTrue(pending.overlays.value.isEmpty())
    assertEquals(ActionNotice.Failed(submitted, DetailError.Network), pending.notice.value)
    pending.onNoticeDismissed(pending.notice.value!!)
    assertNull(pending.notice.value)
  }

  @Test
  fun aMissingIssue_isDroppedFromTheCache() = runTest {
    cacheLists(InboxTab.Unresolved)
    api.updateHandler = { _, _ -> ApiResult.Failure(ApiError.Http(404)) }
    val pending = pending()

    val submitted = pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Assign(ana))
    waitOutTheUndoWindow()

    assertEquals(listOf("2"), ids(InboxTab.Unresolved))
    assertEquals(ActionNotice.Failed(submitted, DetailError.NotFound), pending.notice.value)
  }

  @Test
  fun flushNow_sendsEveryWaitingActionAtOnce() = runTest {
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)
    pending.submit(TEST_ACCOUNT, "2", "DEMO-2", IssueAction.Archive)

    pending.flushNow()
    runCurrent()

    assertEquals(setOf("1", "2"), api.updateCalls.map { it.first }.toSet())
    waitOutTheUndoWindow()
    assertEquals(2, api.updateCalls.size)
  }

  @Test
  fun dismissingThePendingNotice_sendsItNow() = runTest {
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)

    pending.onNoticeDismissed(pending.notice.value!!)
    runCurrent()

    assertEquals(listOf("1"), api.updateCalls.map { it.first })
  }

  @Test
  fun aNewerAction_keepsTheNotice_whenAnOlderOneGoesThrough() = runTest {
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)
    advanceTimeBy(3_000)
    val newer = pending.submit(TEST_ACCOUNT, "2", "DEMO-2", IssueAction.Archive)

    advanceTimeBy(2_500)
    runCurrent()

    assertEquals(listOf("1"), api.updateCalls.map { it.first })
    assertEquals(ActionNotice.Pending(newer), pending.notice.value)
  }

  @Test
  fun clear_dropsEverythingUnsent() = runTest {
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)

    pending.clear()
    waitOutTheUndoWindow()

    assertTrue(api.updateCalls.isEmpty())
    assertTrue(pending.overlays.value.isEmpty())
    assertNull(pending.notice.value)
  }

  @Test
  fun clear_whileSending_writesNothingToTheCache() = runTest {
    cacheLists(InboxTab.Unresolved)
    val gateOpen = CompletableDeferred<Unit>()
    api.updateGate = gateOpen
    val pending = pending()
    pending.submit(TEST_ACCOUNT, "1", "DEMO-1", IssueAction.Resolve)
    waitOutTheUndoWindow()

    pending.clear()
    gateOpen.complete(Unit)
    runCurrent()

    assertEquals(listOf("1", "2"), ids(InboxTab.Unresolved))
    assertTrue(pending.overlays.value.isEmpty())
  }
}
