package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.issues.CachedListState
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.issues.Project
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the fake to the same behavior RoomIssueCacheTest asserts for the real cache. */
class InMemoryIssueCacheTest {
  private val cache = InMemoryIssueCache()
  private val t0 = Instant.parse("2026-10-22T10:00:00Z")

  private fun issue(id: String, title: String = "Issue $id") =
    Issue(
      id = id,
      shortId = "DEMO-$id",
      title = title,
      culprit = "handler in app",
      level = IssueLevel.Error,
      badge = null,
      isUnhandled = false,
      eventCount = 3,
      userCount = 1,
      firstSeen = t0,
      lastSeen = t0,
      permalink = "https://example-org.sentry.io/issues/$id/",
      projectId = "101",
      projectSlug = "demo",
      hourlyEvents = listOf(0, 1, 2),
    )

  private suspend fun ids(listKey: String) = cache.observeIssues(listKey).first().map { it.id }

  @Test
  fun replaceList_keepsOrderAndState() = runTest {
    cache.replaceList("a", listOf(issue("3"), issue("1"), issue("2")), "c1", t0)

    assertEquals(listOf("3", "1", "2"), ids("a"))
    assertEquals(CachedListState("c1", t0), cache.listState("a"))
    assertEquals(CachedListState("c1", t0), cache.observeListState("a").first())
  }

  @Test
  fun replaceList_dropsThePreviousEntries() = runTest {
    cache.replaceList("a", listOf(issue("1"), issue("2")), "c1", t0)
    cache.replaceList("a", listOf(issue("9")), null, t0.plusSeconds(60))

    assertEquals(listOf("9"), ids("a"))
    assertEquals(CachedListState(null, t0.plusSeconds(60)), cache.listState("a"))
  }

  @Test
  fun replaceList_withARepeatedIssue_keepsTheFirst() = runTest {
    cache.replaceList("a", listOf(issue("1", title = "First"), issue("2"), issue("1", title = "Second")), null, t0)

    assertEquals(listOf("1", "2"), ids("a"))
    assertEquals("First", cache.observeIssues("a").first()[0].title)
  }

  @Test
  fun appendPage_continuesAfterTheLastIssueAndSkipsDuplicates() = runTest {
    cache.replaceList("a", listOf(issue("1"), issue("2")), "c1", t0)
    cache.appendPage("a", listOf(issue("2", title = "Updated 2"), issue("3"), issue("3")), null)

    assertEquals(listOf("1", "2", "3"), ids("a"))
    assertNull(cache.listState("a")?.nextCursor)
  }

  @Test
  fun appendPage_refreshesTheDataOfARepeatedIssueAndKeepsItsPosition() = runTest {
    cache.replaceList("a", listOf(issue("1"), issue("2")), "c1", t0)
    cache.appendPage("a", listOf(issue("2", title = "Updated 2"), issue("3")), "c2")

    val issues = cache.observeIssues("a").first()
    assertEquals(listOf("1", "2", "3"), issues.map { it.id })
    assertEquals("Updated 2", issues[1].title)
    assertEquals(CachedListState("c2", t0), cache.listState("a"))
  }

  @Test
  fun appendPage_twice_keepsPositionsInOrder() = runTest {
    cache.replaceList("a", listOf(issue("1")), "c1", t0)
    cache.appendPage("a", listOf(issue("2")), "c2")
    cache.appendPage("a", listOf(issue("3")), null)

    assertEquals(listOf("1", "2", "3"), ids("a"))
  }

  @Test
  fun appendPage_toAListNeverLoaded_doesNothing() = runTest {
    cache.appendPage("never", listOf(issue("1")), "c")

    assertTrue(ids("never").isEmpty())
    assertNull(cache.listState("never"))
  }

  @Test
  fun anIssueInTwoLists_showsItsLatestDataInBoth() = runTest {
    cache.replaceList("a", listOf(issue("1")), null, t0)
    cache.replaceList("b", listOf(issue("1", title = "Newer")), null, t0)

    assertEquals("Newer", cache.observeIssues("a").first().single().title)
    assertEquals("Newer", cache.observeIssues("b").first().single().title)
  }

  @Test
  fun prune_dropsStaleListsAndIssuesNoListUses() = runTest {
    cache.replaceList("old", listOf(issue("1"), issue("2", title = "Old copy")), null, t0)
    cache.replaceList("new", listOf(issue("2", title = "Shared")), null, t0.plus(Duration.ofDays(8)))

    cache.prune(t0.plus(Duration.ofDays(1)))

    assertNull(cache.listState("old"))
    assertTrue(ids("old").isEmpty())
    assertEquals(listOf("2"), ids("new"))
    assertEquals("Shared", cache.observeIssues("new").first().single().title)
    // Issue 1 was only in the stale list, so its row is gone; issue 2 is shared, so it stays.
    assertEquals(setOf("2"), cache.storedIssueIds)
  }

  @Test
  fun prune_keepsListsRefreshedAtOrAfterTheCutoff() = runTest {
    cache.replaceList("edge", listOf(issue("1")), null, t0)

    cache.prune(t0)

    assertEquals(listOf("1"), ids("edge"))
    assertEquals(CachedListState(null, t0), cache.listState("edge"))
  }

  @Test
  fun projects_roundTripInOrder() = runTest {
    val projects =
      listOf(
        Project("102", "android-app", "Android App", "android", listOf("production", "staging")),
        Project("101", "shop-api", "Shop API", null, emptyList()),
      )
    cache.replaceProjects(projects)

    assertEquals(projects, cache.observeProjects().first())
  }

  @Test
  fun replaceProjects_withARepeatedProject_keepsTheFirst() = runTest {
    val first = Project("101", "shop-api", "Shop API", null, emptyList())
    val repeat = Project("101", "shop-api", "Shop API (again)", null, emptyList())
    val other = Project("102", "android-app", "Android App", "android", emptyList())
    cache.replaceProjects(listOf(first, other, repeat))

    assertEquals(listOf(first, other), cache.observeProjects().first())
  }

  @Test
  fun clear_forgetsEverything() = runTest {
    cache.replaceList("a", listOf(issue("1")), "c", t0)
    cache.replaceProjects(listOf(Project("1", "p", "P", null, emptyList())))

    cache.clear()

    assertTrue(ids("a").isEmpty())
    assertNull(cache.listState("a"))
    assertTrue(cache.observeProjects().first().isEmpty())
    assertTrue(cache.storedIssueIds.isEmpty())
  }

  @Test
  fun clear_throwsTheConfiguredError() = runTest {
    cache.replaceList("a", listOf(issue("1")), "c", t0)
    val failure = IllegalStateException("disk full")
    cache.clearError = failure

    val thrown = runCatching { cache.clear() }.exceptionOrNull()

    assertSame(failure, thrown)
    assertEquals(listOf("1"), ids("a"))
  }

  @Test
  fun issue_findsACachedIssueById() = runTest {
    cache.replaceList("a", listOf(issue("1", title = "First")), null, t0)

    assertEquals("First", cache.issue("1")?.title)
    assertNull(cache.issue("2"))
  }

  @Test
  fun removeFromLists_dropsTheIssueFromMatchingListsOnly() = runTest {
    cache.replaceList("ForReview|*|*|LastSeen", listOf(issue("1"), issue("2")), null, t0)
    cache.replaceList("Unresolved|*|*|LastSeen", listOf(issue("1")), null, t0)

    cache.removeFromLists("1", "ForReview|")

    assertEquals(listOf("2"), cache.observeIssues("ForReview|*|*|LastSeen").first().map { it.id })
    assertEquals(listOf("1"), cache.observeIssues("Unresolved|*|*|LastSeen").first().map { it.id })

    cache.removeFromLists("1", "")

    assertTrue(cache.observeIssues("Unresolved|*|*|LastSeen").first().isEmpty())
    assertTrue("1" !in cache.storedIssueIds)
  }
}
