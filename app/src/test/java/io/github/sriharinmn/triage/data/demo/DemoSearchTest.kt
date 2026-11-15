package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.remote.IssueListParams
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSearchTest {
  private val now = Instant.parse("2026-11-16T04:30:00Z")
  private val views = freshDemoState(now - Duration.ofHours(1)).views(now)
  private val me = DemoWorld.me.userId

  private fun search(query: String, sort: String = "date", projectIds: List<String> = emptyList(), environment: String? = null) =
    searchIssues(views, IssueListParams(query, projectIds, environment, sort), now, me)

  @Test
  fun terms_splitOnSpaces_butNotInsideBrackets() {
    assertEquals(
      listOf("is:unresolved", "is:for_review", "assigned_or_suggested:[me, my_teams, none]"),
      searchTerms(InboxTab.ForReview.query),
    )
    assertEquals(emptyList<String>(), searchTerms("   "))
  }

  @Test
  fun queries_readTheTermsTheAppSends() {
    assertEquals(DemoQuery(unresolved = true), parseDemoQuery("is:unresolved"))
    assertEquals(DemoQuery(regressed = true), parseDemoQuery("is:regressed"))
    assertEquals(DemoQuery(escalating = true), parseDemoQuery("is:escalating"))
    assertEquals(DemoQuery(unresolved = true, forReview = true, mineOrUnassigned = true), parseDemoQuery(InboxTab.ForReview.query))
    assertEquals(
      DemoQuery(unresolved = true, firstSeenAfter = Instant.parse("2026-11-11T10:35:00Z")),
      parseDemoQuery("is:unresolved firstSeen:>2026-11-11T10:35:00+00:00"),
    )
    assertEquals(DemoQuery(), parseDemoQuery("level:error something:else"))
  }

  @Test
  fun eachInboxTab_findsItsIssues() {
    assertEquals(views.filter { it.status == "unresolved" }.map { it.state.id }.toSet(), search(InboxTab.Unresolved.query).map { it.state.id }.toSet())
    assertEquals(
      setOf("android-fragment-detached", "web-coupon-tofixed"),
      search(InboxTab.Regressed.query).map { it.spec.key }.toSet(),
    )
    assertEquals(setOf("android-anr-main", "web-chunk-load"), search(InboxTab.Escalating.query).map { it.spec.key }.toSet())
    val forReview = search(InboxTab.ForReview.query)
    assertTrue(forReview.isNotEmpty())
    // web-coupon-tofixed is up for review but assigned to Priya, so it isn't the tester's to review.
    assertTrue(forReview.none { it.spec.key == "web-coupon-tofixed" })
    assertTrue(forReview.all { it.forReview && (it.state.assigneeId == null || it.state.assigneeId == me) })
  }

  @Test
  fun firstSeenAfter_isStrictlyAfter() {
    val newest = views.maxBy { it.firstSeen }
    assertTrue(search("firstSeen:>${newest.firstSeen}").isEmpty())
    assertEquals(listOf(newest), search("firstSeen:>${newest.firstSeen - Duration.ofSeconds(1)}"))
  }

  @Test
  fun projectAndEnvironment_filter() {
    val web = DemoWorld.project("shop-web").id
    assertTrue(search("is:unresolved", projectIds = listOf(web)).all { it.project.id == web })
    assertTrue(search("is:unresolved", environment = "staging").all { "staging" in it.environments })
    assertTrue(search("is:unresolved", environment = "nowhere").isEmpty())
  }

  @Test
  fun issuesNotSeenWithinTheStatsPeriod_areLeftOut() {
    val resolvedLongAgo = searchIssues(views, IssueListParams("", emptyList(), null, "date", statsPeriod = "24h"), now, me)
    assertTrue(resolvedLongAgo.all { it.lastSeen >= now - Duration.ofHours(24) })
    assertTrue(resolvedLongAgo.size < views.size)
  }

  @Test
  fun sorts_newestOrLargestFirst() {
    assertEquals(search("", "date").map { it.lastSeen }, search("", "date").map { it.lastSeen }.sortedDescending())
    assertEquals(search("", "new").map { it.firstSeen }, search("", "new").map { it.firstSeen }.sortedDescending())
    assertEquals(search("", "freq").map { it.count }, search("", "freq").map { it.count }.sortedDescending())
    assertEquals(search("", "user").map { it.userCount }, search("", "user").map { it.userCount }.sortedDescending())
  }

  @Test
  fun pages_followTheirCursor_toTheEnd() {
    val items = (1..30).toList()

    val first = pageOf(items, limit = 25, cursor = null)
    assertEquals((1..25).toList(), first.items)
    assertEquals("demo:25", first.nextCursor)

    val second = pageOf(items, limit = 25, cursor = first.nextCursor)
    assertEquals((26..30).toList(), second.items)
    assertNull(second.nextCursor)

    assertEquals(first, pageOf(items, limit = 25, cursor = "0:100:0"))
  }
}
