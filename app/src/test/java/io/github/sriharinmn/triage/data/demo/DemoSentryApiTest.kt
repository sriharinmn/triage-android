package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.toChanges
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.IssueListParams
import io.github.sriharinmn.triage.testing.InMemoryDemoStateStore
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSentryApiTest {
  private val start = Instant.parse("2026-11-16T03:30:00Z")
  private val clock = TestClock(start)
  private val store = InMemoryDemoStateStore()
  private val zone = ZoneId.of("Asia/Kolkata")
  private val api = DemoSentryApi(store, clock::now) { zone }

  private val base = DEMO_BASE_URL
  private val token = DemoAccount.token
  private val org = DemoAccount.orgSlug

  private fun params(query: String = InboxTab.Unresolved.query, limit: Int = 25) =
    IssueListParams(query = query, projectIds = emptyList(), environment = null, sort = "date", limit = limit)

  private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success).value

  @Test
  fun theFirstCall_withNoDemoSaved_startsOneNow() = runTest {
    val page = api.listIssues(base, token, org, params(limit = 10), cursor = null).value()

    assertEquals(10, page.items.size)
    assertNotNull(page.nextCursor)
    assertEquals(start.toEpochMilli(), store.current?.startedAt)
  }

  @Test
  fun pages_continueFromTheirCursor() = runTest {
    api.startFresh()
    val first = api.listIssues(base, token, org, params(limit = 15), cursor = null).value()
    val second = api.listIssues(base, token, org, params(limit = 15), cursor = first.nextCursor).value()

    assertNull(second.nextCursor)
    assertEquals(26, first.items.size + second.items.size)
    assertTrue(first.items.map { it.id }.intersect(second.items.map { it.id }.toSet()).isEmpty())
  }

  @Test
  fun readsBetweenHappenings_saveNothing() = runTest {
    api.startFresh()
    val saves = store.saves

    api.listIssues(base, token, org, params(), cursor = null)
    api.listProjects(base, token, org)

    assertEquals(saves, store.saves)
  }

  @Test
  fun aResolve_isKept_andOutlivesTheApi() = runTest {
    api.startFresh()
    val issue = api.listIssues(base, token, org, params(), cursor = null).value().items.first()

    assertEquals(ApiResult.Success(Unit), api.updateIssue(base, token, org, issue.id, IssueAction.Resolve.toChanges()))

    val later = DemoSentryApi(store, clock::now) { zone }
    assertEquals("resolved", later.getIssue(base, token, org, issue.id).value().status)
    val unresolved = later.listIssues(base, token, org, params(limit = 100), cursor = null).value().items
    assertTrue(unresolved.none { it.id == issue.id })
  }

  @Test
  fun theDetail_itsEventsAndTags() = runTest {
    api.startFresh()
    val id = store.current!!.issues.single { it.contentKey == "android-checkout-npe" }.id

    assertEquals("Priya Nair", api.getIssue(base, token, org, id).value().assignedTo?.name)
    val latest = api.getIssueEvent(base, token, org, id, "latest").value()
    val recommended = api.getIssueEvent(base, token, org, id, "recommended").value()
    val oldest = api.getIssueEvent(base, token, org, id, "oldest").value()
    assertEquals(latest, recommended)
    assertTrue(Instant.parse(oldest.dateCreated) < Instant.parse(latest.dateCreated))
    assertEquals("Pixel 8", api.getIssueTag(base, token, org, id, "device").value().topValues.first().value)
  }

  @Test
  fun unknownIssuesAndTags_are404() = runTest {
    api.startFresh()
    val id = store.current!!.issues.first().id
    val notFound = ApiResult.Failure(ApiError.Http(404))

    assertEquals(notFound, api.getIssue(base, token, org, "nope"))
    assertEquals(notFound, api.getIssueEvent(base, token, org, "nope", "latest"))
    assertEquals(notFound, api.getIssueTag(base, token, org, id, "nope"))
    assertEquals(notFound, api.updateIssue(base, token, org, "nope", IssueAction.Resolve.toChanges()))
  }

  @Test
  fun orgsProjectsAndMembers() = runTest {
    assertEquals(ApiResult.Failure(ApiError.Unauthorized), api.listOrganizations(base, token))
    assertEquals(listOf("shop-android", "shop-web", "shop-api"), api.listProjects(base, token, org).value().map { it.slug })
    assertEquals(5, api.listMembers(base, token, org).value().size)
  }

  @Test
  fun time_bringsNewIssues() = runTest {
    api.startFresh()
    val newQuery = "is:unresolved firstSeen:>2026-11-16T03:30:00+00:00"
    assertTrue(api.listIssues(base, token, org, params(newQuery), cursor = null).value().items.isEmpty())

    clock.advance(Duration.ofMinutes(30))

    assertEquals(1, api.listIssues(base, token, org, params(newQuery), cursor = null).value().items.size)
  }

  @Test
  fun alertsOn_schedulesAKick_butOnlyForADemo() = runTest {
    api.onAlertsTurnedOn()
    assertNull(store.current)

    api.startFresh()
    clock.advance(Duration.ofHours(2))
    api.onAlertsTurnedOn()
    // Either a kick 20 minutes from now, or nothing because something already happens within 45 minutes.
    val kick = store.current!!.kickAt
    assertTrue(kick == null || kick == (clock.now() + Duration.ofMinutes(20)).toEpochMilli())
  }

  @Test
  fun clear_forgetsTheDemo_andStartFreshBeginsAgain() = runTest {
    api.startFresh()
    clock.advance(Duration.ofHours(1))
    api.startFresh()
    assertEquals(clock.now().toEpochMilli(), store.current?.startedAt)

    api.clear()
    assertNull(store.current)
  }

  @Test
  fun aFileThatCantBeRead_isANetworkStyleError() = runTest {
    val broken = DemoSentryApi(DataStoreDemoStateStore(UnreadableDataStore()), clock::now) { zone }

    val result = broken.listIssues(base, token, org, params(), cursor = null)

    assertTrue((result as ApiResult.Failure).error is ApiError.Network)
  }

  @Test
  fun theRealStore_worksToo() = runTest {
    val real = DemoSentryApi(DataStoreDemoStateStore(InMemoryPreferencesDataStore()), clock::now) { zone }
    real.startFresh()
    // 30 starting issues, minus 2 archived and 2 resolved: 26 unresolved, so one page of 100 holds them.
    assertEquals(26, real.listIssues(base, token, org, params(limit = 100), cursor = null).value().items.size)
  }
}
