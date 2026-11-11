package io.github.sriharinmn.triage.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.actions.Assignment
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.IssueOverlay
import io.github.sriharinmn.triage.data.actions.SubmittedAction
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.EventContext
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.EventStack
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.IssueStatus
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.data.detail.StackFrame
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.members.Member
import io.github.sriharinmn.triage.theme.TriageTheme
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The detail screen's states with fake data; nothing touches the network or the phone's storage. */
class IssueDetailContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val now = Instant.parse("2026-10-22T12:00:00Z")
  private val calls = mutableListOf<String>()

  private val summary =
    IssueSummary(
      id = "4201",
      shortId = "TRIAGE-DEMO-1A",
      title = "TypeError: Cannot read 'price'",
      culprit = "cartTotal(shop:cart)",
      level = IssueLevel.Error,
      badge = IssueBadge.Regressed,
      isUnhandled = true,
      status = IssueStatus.Unresolved,
      eventCount = 1204,
      userCount = 340,
      firstSeen = now - Duration.ofDays(3),
      lastSeen = now.minusSeconds(120),
      permalink = null,
      hourlyEvents = List(24) { it.toLong() },
      dailyEvents = List(30) { 10L },
      firstRelease = "1.4.0",
      lastRelease = "1.4.2",
      tagKeys = emptyList(),
    )

  private val event =
    IssueEvent(
      id = "e1",
      dateCreated = now.minusSeconds(120),
      release = "1.4.2",
      environment = "production",
      stacks =
        listOf(
          EventStack(
            type = "TypeError",
            value = "Cannot read properties of undefined",
            threadName = null,
            frames = listOf(StackFrame("cartTotal", null, "shop/cart.js", 42, null, true, emptyList())),
          )
        ),
      message = null,
      breadcrumbs = emptyList(),
      tags = emptyList(),
      context = EventContext(),
    )

  private val loaded =
    IssueDetailUiState(
      issueId = "4201",
      summary = summary,
      isLoadingSummary = false,
      events = mapOf(EventKind.Recommended to event),
      isLoadingEvent = false,
    )

  private fun show(state: IssueDetailUiState, actions: DetailActionHandlers = DetailActionHandlers()) {
    composeTestRule.setContent {
      TriageTheme {
        IssueDetailContent(
          state = state,
          now = now,
          onBack = { calls += "back" },
          onOpenInBrowser = { calls += "open" },
          onEventKindSelected = { calls += "kind:${it.name}" },
          onRetry = { calls += "retry" },
          onFrameToggled = { calls += "frame" },
          onShowLibraryFrames = { calls += "library" },
          onShowAllBreadcrumbs = { calls += "crumbs" },
          onReconnectConfirmed = { calls += "reconnect" },
          onDisconnectFailureShown = {},
          actions = actions,
        )
      }
    }
  }

  @Test
  fun loaded_showsTheSummaryTheEventAndItsStack() {
    show(loaded)

    composeTestRule.onNodeWithText("TRIAGE-DEMO-1A").assertIsDisplayed()
    composeTestRule.onNodeWithText("TypeError: Cannot read 'price'").assertIsDisplayed()
    composeTestRule.onNodeWithText("Error").assertIsDisplayed()
    composeTestRule.onNodeWithText("Regressed").assertIsDisplayed()
    composeTestRule.onNodeWithText("Unhandled").assertIsDisplayed()
    composeTestRule.onNodeWithText("1.2k events · 340 users").assertIsDisplayed()
    composeTestRule.onNodeWithText("First seen 3d ago · 1.4.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("2m ago · release 1.4.2 · production").assertIsDisplayed()
    composeTestRule.onNodeWithText("cartTotal").assertIsDisplayed()
  }

  @Test
  fun theCachedSummary_showsWhileTheEventLoads() {
    show(loaded.copy(isLoadingSummary = true, events = emptyMap(), isLoadingEvent = true))

    composeTestRule.onNodeWithText("TypeError: Cannot read 'price'").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Loading event…").assertExists()
  }

  @Test
  fun withoutASummaryYet_aSpinnerShows() {
    show(IssueDetailUiState(issueId = "4201"))

    composeTestRule.onNodeWithContentDescription("Loading issue…").assertIsDisplayed()
  }

  @Test
  fun aGoneIssue_offersTheWayBack() {
    show(loaded.copy(summaryError = DetailError.NotFound))

    composeTestRule.onNodeWithText("This issue no longer exists. It may have been merged or deleted.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Back to inbox").performClick()
    assertEquals(listOf("back"), calls)
  }

  @Test
  fun offlineWithoutASummary_offersRetry() {
    show(IssueDetailUiState(issueId = "4201", isLoadingSummary = false, summaryError = DetailError.Network))

    composeTestRule.onNodeWithText("Couldn't reach Sentry. Check your connection.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").performClick()
    assertEquals(listOf("retry"), calls)
  }

  @Test
  fun offline_keepsTheSummary_andRetriesTheEvent() {
    show(loaded.copy(events = emptyMap(), eventError = DetailError.Network, summaryError = DetailError.Network))

    composeTestRule.onNodeWithText("TypeError: Cannot read 'price'").assertIsDisplayed()
    composeTestRule.onNodeWithText("Couldn't reach Sentry. Check your connection.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DetailTags.RETRY).performClick()
    assertEquals(listOf("retry"), calls)
  }

  @Test
  fun aTooLargeEvent_offersTheBrowser() {
    show(loaded.copy(events = emptyMap(), eventError = DetailError.TooLarge))

    composeTestRule.onNodeWithText("This event is too large to show here.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Open in browser").performClick()
    assertEquals(listOf("open"), calls)
  }

  @Test
  fun aTooLargeIssue_offersTheBrowserInsteadOfRetry() {
    show(IssueDetailUiState(issueId = "4201", isLoadingSummary = false, summaryError = DetailError.TooLarge))

    composeTestRule.onNodeWithText("This issue is too large to show here.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    composeTestRule.onNodeWithText("Open in browser").performClick()
    assertEquals(listOf("open"), calls)
  }

  @Test
  fun aMissingEvent_hasNoRetry() {
    show(loaded.copy(events = emptyMap(), eventError = DetailError.NotFound))

    composeTestRule.onNodeWithText("Sentry has no event of this kind any more.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DetailTags.RETRY).assertDoesNotExist()
  }

  @Test
  fun aRateLimit_saysHowLongToWait() {
    show(loaded.copy(events = emptyMap(), eventError = DetailError.RateLimited(30)))

    composeTestRule.onNodeWithText("Sentry asked us to slow down. Try again in 30 s.").assertIsDisplayed()
  }

  @Test
  fun eventChips_switchKinds() {
    show(loaded)

    composeTestRule.onNodeWithTag(DetailTags.eventChip(EventKind.Latest)).performClick()

    assertEquals(listOf("kind:Latest"), calls)
  }

  @Test
  fun aRejectedToken_asksBeforeReconnecting() {
    show(loaded.copy(summaryError = DetailError.Unauthorized))

    composeTestRule.onNodeWithTag(DetailTags.RECONNECT).performClick()
    composeTestRule.onNodeWithText("Disconnect from Sentry?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Disconnect").performClick()

    assertEquals(listOf("reconnect"), calls)
  }

  @Test
  fun aRejectedTokenWithoutASummary_reconnectsFromThePanel() {
    show(IssueDetailUiState(issueId = "4201", isLoadingSummary = false, summaryError = DetailError.Forbidden))

    composeTestRule.onNodeWithText("Reconnect").performClick()
    composeTestRule.onNodeWithText("Disconnect").performClick()

    assertEquals(listOf("reconnect"), calls)
  }

  @Test
  fun theTopBar_goesBackAndOpensTheBrowser() {
    show(loaded)

    composeTestRule.onNodeWithTag(DetailTags.BACK).performClick()
    composeTestRule.onNodeWithTag(DetailTags.OPEN_IN_BROWSER).performClick()

    assertEquals(listOf("back", "open"), calls)
  }

  @Test
  fun theTrend_switchesTo30Days() {
    show(loaded)

    composeTestRule.onNodeWithTag(DetailTags.TREND_30D).performClick()

    composeTestRule.onNodeWithContentDescription("300 events in the last 30 days").assertExists()
  }

  @Test
  fun the30DayTrend_waitsForFreshData() {
    show(loaded.copy(summary = summary.copy(dailyEvents = emptyList())))

    composeTestRule.onNodeWithTag(DetailTags.TREND_30D).assertIsNotEnabled()
  }

  @Test
  fun aResolvedIssue_isLabelled() {
    show(loaded.copy(summary = summary.copy(status = IssueStatus.Resolved)))

    composeTestRule.onNodeWithText("Resolved").assertIsDisplayed()
  }

  private val ana = Member("41", "Ana Silva", "ana@example.com")
  private val ben = Member("42", "Ben Okafor", "ben@example.com")

  private fun recording() =
    DetailActionHandlers(
      onResolve = { calls += "resolve" },
      onArchive = { calls += "archive" },
      onAssign = { calls += "assign" },
      onMarkReviewed = { calls += "reviewed" },
      onMemberChosen = { calls += "member:${it.userId}" },
      onUnassign = { calls += "unassign" },
      onNotMe = { calls += "notMe" },
      onRetryMembers = { calls += "retryMembers" },
      onCloseHandled = { calls += "closeHandled" },
      onUndo = { calls += "undo:${it.action.id}" },
    )

  @Test
  fun theActionBar_offersResolveArchiveAssign_andReviewedOnlyWhenUpForReview() {
    show(loaded, actions = recording())

    composeTestRule.onNodeWithTag(DetailTags.RESOLVE).performClick()
    composeTestRule.onNodeWithTag(DetailTags.ARCHIVE).performClick()
    composeTestRule.onNodeWithTag(DetailTags.ASSIGN).performClick()
    composeTestRule.onNodeWithTag(DetailTags.MARK_REVIEWED).assertDoesNotExist()

    assertEquals(listOf("resolve", "archive", "assign"), calls)
  }

  @Test
  fun reviewed_isOfferedWhileUpForReview() {
    show(loaded.copy(summary = summary.copy(isUpForReview = true)), actions = recording())

    composeTestRule.onNodeWithTag(DetailTags.MARK_REVIEWED).performClick()

    assertEquals(listOf("reviewed"), calls)
  }

  @Test
  fun noActions_withoutASummary() {
    show(IssueDetailUiState(issueId = "4201"), actions = recording())

    composeTestRule.onNodeWithTag(DetailTags.RESOLVE).assertDoesNotExist()
  }

  @Test
  fun afterResolve_theScreenCloses() {
    show(loaded.copy(closeRequested = true), actions = recording())

    composeTestRule.waitForIdle()
    assertEquals(listOf("closeHandled", "back"), calls)
  }

  @Test
  fun theAssignee_isShown_whenKnown() {
    show(loaded.copy(summary = summary.copy(isFresh = true, assignedTo = "Ana Silva")))
    composeTestRule.onNodeWithText("Assigned to Ana Silva").assertIsDisplayed()
  }

  @Test
  fun aPendingAssignment_showsAtOnce_overSentrysAssignee() {
    show(loaded.copy(summary = summary.copy(isFresh = true), overlay = IssueOverlay(assignment = Assignment(ben))))
    composeTestRule.onNodeWithText("Assigned to Ben Okafor").assertIsDisplayed()
  }

  @Test
  fun theSheet_asksWhoIsMe_first() {
    show(loaded.copy(assignSheet = AssignSheetState(isLoading = false, members = listOf(ana, ben))), actions = recording())

    composeTestRule.onNodeWithText("Which one is you?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Ana Silva").performClick()

    assertEquals(listOf("member:41"), calls)
  }

  @Test
  fun theSheet_offersMeUnassignedAndEveryoneElse() {
    show(loaded.copy(assignSheet = AssignSheetState(isLoading = false, members = listOf(ana, ben), me = ana)), actions = recording())

    composeTestRule.onNodeWithText("Assign to me (Ana Silva)").performClick()
    composeTestRule.onNodeWithText("Unassigned").performClick()
    composeTestRule.onNodeWithText("Ben Okafor").performClick()
    composeTestRule.onNodeWithTag(DetailTags.NOT_ME).performClick()

    assertEquals(listOf("member:41", "unassign", "member:42", "notMe"), calls)
  }

  @Test
  fun membersThatFailToLoad_offerRetry_andUnassignedStillWorks() {
    show(loaded.copy(assignSheet = AssignSheetState(isLoading = false, error = DetailError.Network)), actions = recording())

    composeTestRule.onNodeWithText("Retry").performClick()
    composeTestRule.onNodeWithText("Unassigned").performClick()

    assertEquals(listOf("retryMembers", "unassign"), calls)
  }

  @Test
  fun aPendingAssignment_offersUndo() {
    show(loaded.copy(notice = ActionNotice.Pending(SubmittedAction(5, "4201", "TRIAGE-DEMO-1A", IssueAction.Assign(ana)))), actions = recording())

    composeTestRule.onNodeWithText("Assigned TRIAGE-DEMO-1A to Ana Silva").assertIsDisplayed()
    composeTestRule.onNodeWithText("Undo").performClick()

    assertTrue("undo:5" in calls)
  }
}
