package io.github.sriharinmn.triage.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sriharinmn.triage.data.issues.CachedListState
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.issues.Project
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against an in-memory database only; never the app's real triage.db. */
@RunWith(AndroidJUnit4::class)
class RoomIssueCacheTest {
  private val database =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TriageDatabase::class.java)
      .build()
  private val cache = RoomIssueCache(database)
  private val t0 = Instant.parse("2026-10-22T10:00:00Z")

  @After
  fun tearDown() {
    database.close()
  }

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
  fun replaceList_keepsOrderAndState() = runBlocking {
    cache.replaceList("a", listOf(issue("3"), issue("1"), issue("2")), "c1", t0)

    assertEquals(listOf("3", "1", "2"), ids("a"))
    assertEquals(CachedListState("c1", t0), cache.listState("a"))
    assertEquals(CachedListState("c1", t0), cache.observeListState("a").first())
  }

  @Test
  fun replaceList_dropsThePreviousEntries() = runBlocking {
    cache.replaceList("a", listOf(issue("1"), issue("2")), "c1", t0)
    cache.replaceList("a", listOf(issue("9")), null, t0.plusSeconds(60))

    assertEquals(listOf("9"), ids("a"))
    assertEquals(CachedListState(null, t0.plusSeconds(60)), cache.listState("a"))
  }

  @Test
  fun appendPage_continuesAfterTheLastIssueAndSkipsDuplicates() = runBlocking {
    cache.replaceList("a", listOf(issue("1"), issue("2")), "c1", t0)
    cache.appendPage("a", listOf(issue("2", title = "Updated 2"), issue("3"), issue("3")), null)

    assertEquals(listOf("1", "2", "3"), ids("a"))
    assertEquals("Updated 2", cache.observeIssues("a").first()[1].title)
    assertNull(cache.listState("a")?.nextCursor)
  }

  @Test
  fun appendPage_toAListNeverLoaded_doesNothing() = runBlocking {
    cache.appendPage("never", listOf(issue("1")), "c")

    assertTrue(ids("never").isEmpty())
    assertNull(cache.listState("never"))
  }

  @Test
  fun everyIssueFieldRoundTrips() = runBlocking {
    val full =
      issue("1").copy(
        level = IssueLevel.Fatal,
        badge = IssueBadge.Regressed,
        isUnhandled = true,
        eventCount = 12_345_678_901,
        culprit = null,
        firstSeen = null,
        permalink = null,
        hourlyEvents = emptyList(),
      )
    cache.replaceList("a", listOf(full), null, t0)

    assertEquals(listOf(full), cache.observeIssues("a").first())
  }

  @Test
  fun anIssueInTwoLists_showsItsLatestDataInBoth() = runBlocking {
    cache.replaceList("a", listOf(issue("1")), null, t0)
    cache.replaceList("b", listOf(issue("1", title = "Newer")), null, t0)

    assertEquals("Newer", cache.observeIssues("a").first().single().title)
  }

  @Test
  fun prune_dropsStaleListsAndIssuesNoListUses() = runBlocking {
    cache.replaceList("old", listOf(issue("1"), issue("2")), null, t0)
    cache.replaceList("new", listOf(issue("2")), null, t0.plus(Duration.ofDays(8)))

    cache.prune(t0.plus(Duration.ofDays(1)))

    assertNull(cache.listState("old"))
    assertTrue(ids("old").isEmpty())
    assertEquals(listOf("2"), ids("new"))
    // Issue 1 is gone from the table: re-adding a list that references it would find nothing.
    database.cacheDao().insertEntries(listOf(IssueListEntryEntity("probe", 0, "1")))
    assertTrue(ids("probe").isEmpty())
  }

  @Test
  fun projects_roundTripInOrder() = runBlocking {
    val projects =
      listOf(
        Project("102", "android-app", "Android App", "android", listOf("production")),
        Project("101", "shop-api", "Shop API", null, emptyList()),
      )
    cache.replaceProjects(projects)

    assertEquals(projects, cache.observeProjects().first())
  }

  @Test
  fun clear_forgetsEverything() = runBlocking {
    cache.replaceList("a", listOf(issue("1")), "c", t0)
    cache.replaceProjects(listOf(Project("1", "p", "P", null, emptyList())))

    cache.clear()

    assertTrue(ids("a").isEmpty())
    assertNull(cache.listState("a"))
    assertTrue(cache.observeProjects().first().isEmpty())
  }
}
