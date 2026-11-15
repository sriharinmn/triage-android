package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import io.github.sriharinmn.triage.data.detail.toIssueEvent
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDtosTest {
  private val started = Instant.parse("2026-11-16T03:30:00Z")
  private val now = started + Duration.ofHours(1)
  private val state = freshDemoState(started)

  private fun view(key: String) = checkNotNull(state.issues.single { it.contentKey == key }.view(now))

  @Test
  fun anInboxRow_carriesSentrysFields() {
    val view = view("android-checkout-npe")
    val dto = view.toIssueDto(now)

    assertEquals(view.state.id, dto.id)
    assertEquals(view.state.shortId, dto.shortId)
    assertEquals(view.spec.title, dto.title)
    assertEquals("fatal", dto.level)
    assertEquals("unresolved", dto.status)
    assertEquals("ongoing", dto.substatus)
    assertTrue(dto.isUnhandled)
    assertEquals(view.count, dto.count)
    assertEquals(Instant.parse(dto.lastSeen), view.lastSeen)
    assertEquals("shop-android", dto.project.slug)
    assertEquals(24, dto.stats.getValue("24h").size)
    assertNull(dto.permalink)
  }

  @Test
  fun theDetail_hasReleasesTagsAssigneeAndReview() {
    val assigned = view("android-checkout-npe").toIssueDetailDto(now, started)
    assertEquals("com.example.shop@2.4.1+241", assigned.lastRelease?.version)
    // First seen 21 days before the start, when 2.3.0 (shipped at −34 d) was the newest release.
    assertEquals("2.3.0 (230)", assigned.firstRelease?.shortVersion)
    assertEquals(listOf("environment", "device", "os"), assigned.tags.map { it.key })
    assertTrue(assigned.tags.all { it.totalValues == assigned.count })
    assertEquals("Priya Nair", assigned.assignedTo?.name)
    assertEquals("user", assigned.assignedTo?.type)
    assertNull(assigned.inbox)
    assertEquals(setOf("24h", "30d"), assigned.stats.keys)
    assertEquals(30, assigned.stats.getValue("30d").size)

    val forReview = view("api-postal-code").toIssueDetailDto(now, started)
    assertEquals(JsonObject(emptyMap()), forReview.inbox)
    assertNull(forReview.assignedTo)
  }

  @Test
  fun everyEvent_readsThroughTheAppsOwnMapping() {
    state.views(now).forEach { view ->
      val event = view.toEventDto(EventPick.Latest, started).toIssueEvent()
      assertTrue(view.spec.key, event.stacks.isNotEmpty() || event.message != null)
      assertEquals(view.spec.key, view.spec.event.breadcrumbs.size, event.breadcrumbs.size)
      assertEquals(view.spec.key, view.lastSeen, event.dateCreated)
      assertNotNull(view.spec.key, event.release)
      assertEquals(view.spec.key, view.spec.tags.getValue("environment").first().first, event.environment)
    }
  }

  @Test
  fun latestAndOldest_differInTimeAndRelease() {
    val view = view("web-fetch-failed")
    val latest = view.toEventDto(EventPick.Latest, started)
    val oldest = view.toEventDto(EventPick.Oldest, started)

    assertEquals(view.firstSeen, Instant.parse(oldest.dateCreated))
    assertEquals(view.lastSeen, Instant.parse(latest.dateCreated))
    assertEquals(32, latest.id?.length)
    assertTrue(latest.id != oldest.id)
    assertTrue(latest.release.toString().contains("shop-web@5.13.2"))
    assertTrue(oldest.release.toString().contains("shop-web@5.12.0"))
  }

  @Test
  fun aTag_spreadsTheCountByItsShares() {
    val view = view("android-checkout-npe")
    val tag = checkNotNull(view.toTagDetailDto("device"))

    assertEquals(view.count, tag.totalValues)
    assertEquals("Pixel 8", tag.topValues.first().value)
    assertEquals(tag.topValues.map { it.count }.sortedDescending(), tag.topValues.map { it.count })
    assertNull(view.toTagDetailDto("no-such-tag"))
  }

  @Test
  fun anAndroidEvent_reportsTheAppVersionOfItsRelease() {
    val view = view("android-checkout-npe")
    val latest = view.toEventDto(EventPick.Latest, started).toIssueEvent()
    assertEquals("2.4.1 (241)", latest.context.app)
    val oldest = view.toEventDto(EventPick.Oldest, started).toIssueEvent()
    assertEquals("2.3.0 (230)", oldest.context.app)
  }

  @Test
  fun projectsAndMembers() {
    val project = DemoWorld.projects.first().toProjectDto()
    assertEquals("4501", project.id)
    assertEquals(listOf("production", "staging"), project.environments)
    assertTrue(project.hasAccess)

    val member = DemoWorld.me.toMemberDto()
    assertEquals("1001", member.user?.id)
    assertEquals("Sam Rivera", member.user?.name)
  }
}
