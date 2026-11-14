package io.github.sriharinmn.triage.data.demo.content

import java.net.URI
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoWorldTest {
  private val allSpecs = DemoWorld.startingIssues.map { it.spec } + DemoWorld.arrivals

  private fun starting(slug: String) = DemoWorld.startingIssues.filter { it.spec.projectSlug == slug }

  private fun arrivals(slug: String) = DemoWorld.arrivals.filter { it.projectSlug == slug }

  @Test
  fun theOrg_people_andProjects() {
    assertEquals("example-shop", DemoWorld.ORG_SLUG)
    assertEquals("Example Shop", DemoWorld.ORG_NAME)
    assertEquals(DemoPerson("1001", "Sam Rivera", "sam@example.com"), DemoWorld.me)
    assertEquals(listOf("1001", "1002", "1003", "1004", "1005"), DemoWorld.people.map { it.userId })
    assertEquals(listOf(SHOP_ANDROID, SHOP_WEB, SHOP_API), DemoWorld.projects.map { it.slug })
    assertEquals(listOf("android", "javascript-react", "python"), DemoWorld.projects.map { it.platform })
  }

  @Test
  fun everyKeyIsUnique_andFindable() {
    assertEquals(allSpecs.size, allSpecs.map { it.key }.toSet().size)
    allSpecs.forEach { assertSame(it, DemoWorld.spec(it.key)) }
  }

  @Test
  fun everyIssueIsInAKnownProject_withAKnownLevel() {
    val slugs = DemoWorld.projects.map { it.slug }.toSet()
    allSpecs.forEach { spec ->
      assertTrue(spec.key, spec.projectSlug in slugs)
      assertTrue(spec.key, spec.level in setOf("fatal", "error", "warning", "info"))
    }
  }

  @Test
  fun tagSharesAddUpToOne_mostCommonFirst_withAnEnvironmentTheProjectHas() {
    allSpecs.forEach { spec ->
      assertTrue(spec.key, "environment" in spec.tags)
      spec.tags.forEach { (key, shares) ->
        assertTrue("${spec.key} $key", shares.size in 1..6)
        assertEquals("${spec.key} $key", 1.0, shares.sumOf { it.second }, 1e-9)
        assertEquals("${spec.key} $key", shares.map { it.second }.sortedDescending(), shares.map { it.second })
      }
      val environments = DemoWorld.project(spec.projectSlug).environments
      spec.tags.getValue("environment").forEach { (environment, _) -> assertTrue(spec.key, environment in environments) }
    }
  }

  @Test
  fun everyEventHasSomethingToShow() {
    allSpecs.forEach { spec ->
      val event = spec.event
      val stacks = event.exceptions.map { it.frames } + event.threads.filter { it.crashed }.map { it.frames }
      assertTrue(spec.key, stacks.isNotEmpty() || event.message != null)
      stacks.forEach { frames -> assertTrue(spec.key, frames.isNotEmpty()) }
      if (stacks.isNotEmpty()) assertTrue(spec.key, stacks.flatten().count { it.inApp } >= 2)
      assertTrue(spec.key, event.breadcrumbs.size in 4..10)
      val offsets = event.breadcrumbs.map { it.secondsBefore }
      assertEquals(spec.key, offsets.sortedDescending(), offsets)
      assertTrue(spec.key, offsets.last() <= 5)
      event.userIndex?.let { assertTrue(spec.key, it in 1..254) }
    }
  }

  @Test
  fun ratesAndShares_areSensible() {
    allSpecs.forEach { spec ->
      assertTrue(spec.key, spec.ratePerHour in 0.05..30.0)
      assertTrue(spec.key, spec.userShare in 0.01..1.0)
    }
  }

  @Test
  fun startingStates_holdTogether() {
    DemoWorld.startingIssues.forEach { issue ->
      val key = issue.spec.key
      assertTrue(key, issue.firstSeenAgo >= Duration.ofHours(1))
      when (val start = issue.start) {
        // A regressed issue was seen, then resolved for a while (3 days in the seed), then came back.
        is DemoStart.Regressed -> assertTrue(key, issue.firstSeenAgo > start.ago + Duration.ofDays(3))
        is DemoStart.Escalating -> assertTrue(key, issue.firstSeenAgo > start.ago)
        is DemoStart.Archived -> assertTrue(key, issue.firstSeenAgo > start.ago)
        is DemoStart.Resolved -> assertTrue(key, issue.firstSeenAgo > start.ago)
        DemoStart.Unresolved -> Unit
      }
      issue.assigneeId?.let { assertNotNull(key, DemoWorld.person(it)) }
    }
  }

  @Test
  fun everythingIsMadeUp() {
    DemoWorld.people.forEach { assertTrue(it.email, it.email.endsWith("@example.com")) }
    val exampleUrl = Regex("^https://([a-z0-9-]+\\.)*example\\.com(/|$)")
    allSpecs
      .flatMap { spec -> listOfNotNull(spec.event.request?.url) + spec.event.breadcrumbs.mapNotNull { it.data["url"] } }
      .forEach { url -> assertTrue(url, exampleUrl.containsMatchIn(url)) }
  }

  @Test
  fun releasesAreInOrder_andOneShipsDuringTheTest() {
    DemoWorld.projects.forEach { project ->
      val times = project.releases.map { it.at }
      assertEquals(project.slug, times.sorted(), times)
      assertTrue(project.slug, times.first().isNegative)
      assertTrue(project.slug, times.last() > Duration.ZERO)
    }
  }

  @Test
  fun theAndroidProject_hasItsIssues() {
    assertEquals(11, starting(SHOP_ANDROID).size)
    assertEquals(5, arrivals(SHOP_ANDROID).size)
  }

  @Test
  fun eachTitle_isTheThrownException() {
    // Sentry titles an event by its last exception: the type, then the value's first line.
    allSpecs
      .filter { it.event.exceptions.isNotEmpty() }
      .forEach { spec ->
        val thrown = spec.event.exceptions.last()
        assertEquals(spec.key, "${thrown.type}: ${thrown.value.lines().first()}", spec.title)
      }
  }

  @Test
  fun eachCulprit_isTheTopmostAppFrame() {
    allSpecs.forEach { spec ->
      val event = spec.event
      val frames = event.exceptions.lastOrNull()?.frames ?: event.threads.firstOrNull { it.crashed }?.frames ?: return@forEach
      val top = frames.last { it.inApp }
      // A JavaScript frame has no module; Sentry names its file.
      assertEquals(spec.key, "${top.module ?: top.filename} in ${top.function}", spec.culprit)
    }
  }

  @Test
  fun eachEventsTopTags_matchItsContextsAndRequest() {
    allSpecs.forEach { spec ->
      val event = spec.event
      fun top(tag: String) = spec.tags[tag]?.first()?.first
      top("device")?.let { assertEquals(spec.key, event.contexts["device"]?.get("model"), it) }
      top("os")?.let { assertNamesVersion(spec.key, it, event.contexts["os"]) }
      top("browser")?.let { assertNamesVersion(spec.key, it, event.contexts["browser"]) }
      top("url")?.let { assertEquals(spec.key, event.request?.url, it) }
      val request = event.request
      val route = top("transaction")
      if (request != null && route != null) {
        val path = URI(request.url).path.split("/")
        val parts = route.split("/")
        assertTrue(
          "${spec.key}: $route doesn't route ${request.url}",
          parts.size == path.size && parts.zip(path).all { (part, segment) -> part == segment || part.startsWith("{") },
        )
      }
    }
  }

  @Test
  fun everyEventHasItsOwnUser() {
    val users = allSpecs.mapNotNull { it.event.userIndex }
    assertEquals(users.size, users.toSet().size)
  }

  /** A tag like "Chrome 131" names the [context]'s browser or OS and its version, or the start of it ("131.0.0"). */
  private fun assertNamesVersion(key: String, tag: String, context: Map<String, String>?) {
    val named = "${context?.get("name")} ${context?.get("version")}"
    assertTrue("$key: $tag vs $named", named == tag || named.startsWith("$tag."))
  }
}
