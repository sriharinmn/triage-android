package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.toChanges
import io.github.sriharinmn.triage.data.members.Member
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemoUpdatesTest {
  private val now = Instant.parse("2026-11-16T04:30:00Z")
  private val state = freshDemoState(now - Duration.ofHours(1))
  private val newIssue = state.issues.single { it.contentKey == "api-postal-code" }

  private fun after(action: IssueAction, id: String = newIssue.id) =
    checkNotNull(state.updated(id, action.toChanges(), now)).issues.single { it.id == id }

  @Test
  fun resolve_stopsEvents_andTakesItOutOfReview() {
    val resolved = after(IssueAction.Resolve)
    assertEquals(DemoStatus.Resolved, resolved.status)
    assertEquals(listOf(DemoQuietSpan(from = now.epochMs)), resolved.quietSpans)
    assertNull(resolved.inboxSince)
  }

  @Test
  fun archive_archivesUntilEscalating() {
    val archived = after(IssueAction.Archive)
    assertEquals(DemoStatus.Archived, archived.status)
    assertEquals(now.epochMs, archived.archivedAt)
    assertNull(archived.inboxSince)
  }

  @Test
  fun assign_andUnassign() {
    assertEquals("1003", after(IssueAction.Assign(Member("1003", "Diego Alvarez", null))).assigneeId)
    val assigned = state.issues.single { it.contentKey == "android-socket-timeout" }
    assertNull(after(IssueAction.Assign(null), id = assigned.id).assigneeId)
  }

  @Test
  fun markReviewed_onlyLeavesReview() {
    val reviewed = after(IssueAction.MarkReviewed)
    assertNull(reviewed.inboxSince)
    assertEquals(DemoStatus.Unresolved, reviewed.status)
  }

  @Test
  fun anUnknownIssue_isNull_andUnknownFieldsAreIgnored() {
    assertNull(state.updated("no-such-issue", IssueAction.Resolve.toChanges(), now))
    assertEquals(state, state.updated(newIssue.id, buildJsonObject { put("hasSeen", JsonPrimitive(true)) }, now))
  }
}
