package io.github.sriharinmn.triage.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.github.sriharinmn.triage.theme.TriageTheme
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
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

  private fun show(state: IssueDetailUiState) {
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
}
