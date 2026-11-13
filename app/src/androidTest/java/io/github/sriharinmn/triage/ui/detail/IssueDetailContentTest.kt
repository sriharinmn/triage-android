package io.github.sriharinmn.triage.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.actions.Assignment
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.IssueOverlay
import io.github.sriharinmn.triage.data.actions.SubmittedAction
import io.github.sriharinmn.triage.data.detail.Breadcrumb
import io.github.sriharinmn.triage.data.detail.ContextLine
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.EventContext
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.EventStack
import io.github.sriharinmn.triage.data.detail.EventTag
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.IssueStatus
import io.github.sriharinmn.triage.data.detail.IssueSummary
import io.github.sriharinmn.triage.data.detail.StackFrame
import io.github.sriharinmn.triage.data.detail.TagSummary
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

  /** Shows [state]; setting the returned holder's value changes what the screen shows, as the ViewModel would. */
  private fun show(
    state: IssueDetailUiState,
    actions: DetailActionHandlers = DetailActionHandlers(),
    fontScale: Float = 1f,
  ): MutableState<IssueDetailUiState> {
    val shown = mutableStateOf(state)
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
        TriageTheme {
          IssueDetailContent(
            state = shown.value,
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
    return shown
  }

  private fun scrollTo(text: String) =
    composeTestRule.onNodeWithTag(DetailTags.LIST).performScrollToNode(hasText(text, substring = true))

  private fun scrollToTag(tag: String) = composeTestRule.onNodeWithTag(DetailTags.LIST).performScrollToNode(hasTestTag(tag))

  /** The tab row sits at the top of the list, and the issue's title and stat strip are scrolled away above it. */
  private fun assertTabRowPinned() {
    val list = composeTestRule.onNodeWithTag(DetailTags.LIST).fetchSemanticsNode().boundsInRoot
    val tabRow = composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Trace)).fetchSemanticsNode().boundsInRoot
    assertEquals(list.top, tabRow.top, 0.5f)
    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Trace)).assertIsDisplayed()
    val top =
      composeTestRule.onAllNodesWithTag(DetailTags.STAT_EVENTS).fetchSemanticsNodes() +
        composeTestRule.onAllNodesWithText(summary.title).fetchSemanticsNodes()
    assertTrue("the title and stat strip should be scrolled away", top.all { it.boundsInRoot.bottom <= list.top + 0.5f })
  }

  @Test
  fun theChart_saysWhatItShows() {
    show(loaded)

    // 0 + 1 + … + 23 events, the busiest bar being the current hour.
    composeTestRule.onNodeWithContentDescription("Events, last 24 hours: 276, busiest at", substring = true).assertExists()
  }

  @Test
  fun theStatStrip_isOneRowOfFour() {
    show(loaded)

    val events = composeTestRule.onNodeWithTag(DetailTags.STAT_EVENTS).fetchSemanticsNode().boundsInRoot
    val firstSeen = composeTestRule.onNodeWithTag(DetailTags.STAT_FIRST_SEEN).fetchSemanticsNode().boundsInRoot
    assertEquals(events.top, firstSeen.top, 0.5f)
  }

  @Test
  fun atTwiceTheFontSize_theStatStripWrapsToTwoByTwo() {
    show(loaded, fontScale = 2f)

    val events = composeTestRule.onNodeWithTag(DetailTags.STAT_EVENTS).fetchSemanticsNode().boundsInRoot
    val firstSeen = composeTestRule.onNodeWithTag(DetailTags.STAT_FIRST_SEEN).fetchSemanticsNode().boundsInRoot
    assertTrue("first seen should start a second row", firstSeen.top >= events.bottom - 0.5f)
  }

  @Test
  fun loaded_showsTheSummaryTheEventAndItsStack() {
    show(loaded)

    composeTestRule.onNodeWithText("TRIAGE-DEMO-1A").assertIsDisplayed()
    composeTestRule.onNodeWithText("TypeError: Cannot read 'price'").assertIsDisplayed()
    composeTestRule.onNodeWithText("Error").assertIsDisplayed()
    composeTestRule.onNodeWithText("Regressed").assertIsDisplayed()
    composeTestRule.onNodeWithText("Unhandled").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DetailTags.STAT_EVENTS).assert(hasText("1.2k"))
    composeTestRule.onNodeWithTag(DetailTags.STAT_USERS).assert(hasText("340"))
    composeTestRule.onNodeWithTag(DetailTags.STAT_FIRST_SEEN).assert(hasText("3d"))
    composeTestRule.onNodeWithTag(DetailTags.STAT_LAST_SEEN).assert(hasText("2m"))
    // TalkBack reads words, not "3d".
    composeTestRule.onNodeWithTag(DetailTags.STAT_FIRST_SEEN).assert(hasContentDescription("First seen 3 days ago, in release 1.4.0"))
    // The last seen event's release, on the right of the eyebrow.
    composeTestRule.onNodeWithText("1.4.2").assertIsDisplayed()
    scrollTo("2m ago · release 1.4.2")
    composeTestRule.onNodeWithText("2m ago · release 1.4.2 · production").assertIsDisplayed()
    scrollToTag(DetailTags.frame(FrameRef(0, 0)))
    composeTestRule.onNodeWithText("cartTotal").assertIsDisplayed()
  }

  @Test
  fun theCachedSummary_showsWhileTheEventLoads() {
    show(loaded.copy(isLoadingSummary = true, events = emptyMap(), isLoadingEvent = true))

    composeTestRule.onNodeWithText("TypeError: Cannot read 'price'").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DetailTags.LIST).performScrollToNode(hasContentDescription("Loading event…"))
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
    scrollToTag(DetailTags.RETRY)
    composeTestRule.onNodeWithText("Couldn't reach Sentry. Check your connection.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DetailTags.RETRY).performClick()
    assertEquals(listOf("retry"), calls)
  }

  @Test
  fun offline_theTagsTab_saysWhy_andRetries() {
    show(loaded.copy(events = emptyMap(), eventError = DetailError.Network, summaryError = DetailError.Network))

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).performClick()
    scrollToTag(DetailTags.RETRY)
    composeTestRule.onNodeWithText("No tags for this issue").assertDoesNotExist()
    composeTestRule.onNodeWithTag(DetailTags.RETRY).performClick()

    assertEquals(listOf("retry"), calls)
  }

  @Test
  fun aTooLargeEvent_offersTheBrowser() {
    show(loaded.copy(events = emptyMap(), eventError = DetailError.TooLarge))
    scrollTo("This event is too large to show here.")

    composeTestRule.onNodeWithText("This event is too large to show here.").assertIsDisplayed()
    // The button sits below the help line; a tap off the list's edge would land on the action bar instead.
    scrollTo("Open in browser")
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
    scrollTo("Sentry has no event of this kind any more.")

    composeTestRule.onNodeWithText("Sentry has no event of this kind any more.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(DetailTags.RETRY).assertDoesNotExist()
  }

  @Test
  fun aRateLimit_saysHowLongToWait() {
    show(loaded.copy(events = emptyMap(), eventError = DetailError.RateLimited(30)))
    scrollTo("Try again in 30 s.")

    composeTestRule.onNodeWithText("Sentry asked us to slow down. Try again in 30 s.").assertIsDisplayed()
  }

  @Test
  fun eventChips_switchKinds() {
    show(loaded)

    scrollToTag(DetailTags.eventChip(EventKind.Latest))
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

    composeTestRule.onNodeWithContentDescription("Events, last 30 days: 300, busiest on", substring = true).assertExists()
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

  private val richEvent =
    event.copy(
      breadcrumbs = listOf(Breadcrumb(timestamp = null, category = "ui.click", level = IssueLevel.Info, text = "tapped Checkout")),
      tags = listOf(EventTag("route", "POST /api/checkout")),
      context = EventContext(device = "Google Pixel 8"),
    )

  @Test
  fun theTabs_showEachPartOfTheEvent() {
    show(loaded.copy(events = mapOf(EventKind.Recommended to richEvent)))
    scrollToTag(DetailTags.frame(FrameRef(0, 0)))
    composeTestRule.onNodeWithText("cartTotal").assertIsDisplayed()

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Breadcrumbs)).performClick()
    scrollTo("tapped Checkout")
    composeTestRule.onNodeWithText("tapped Checkout", substring = true).assertIsDisplayed()

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).performClick()
    scrollTo("POST /api/checkout")
    composeTestRule.onNodeWithText("POST /api/checkout", substring = true).assertIsDisplayed()

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Details)).performClick()
    scrollTo("Google Pixel 8")
    composeTestRule.onNodeWithText("Google Pixel 8", substring = true).assertIsDisplayed()
  }

  @Test
  fun theEventSelector_sitsOnTheEventTabs_butNotOnTags() {
    show(loaded)
    scrollToTag(DetailTags.eventChip(EventKind.Latest))

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).performClick()
    composeTestRule.onNodeWithTag(DetailTags.eventChip(EventKind.Latest)).assertDoesNotExist()

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Details)).performClick()
    scrollToTag(DetailTags.eventChip(EventKind.Latest))
  }

  @Test
  fun aTabWithNothingInIt_saysSo() {
    show(loaded)

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Breadcrumbs)).performClick()
    scrollTo("No breadcrumbs in this event")

    composeTestRule.onNodeWithText("No breadcrumbs in this event").assertIsDisplayed()
  }

  private val longTrace =
    event.copy(
      stacks =
        listOf(
          event.stacks.single().copy(frames = (0 until 40).map { StackFrame("fn$it", null, "shop/f$it.js", it, null, true, emptyList()) })
        )
    )

  @Test
  fun eachTab_keepsItsOwnScrollPosition() {
    show(loaded.copy(events = mapOf(EventKind.Recommended to longTrace)))
    scrollTo("fn39")

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).performClick()
    composeTestRule.onNodeWithText("fn39").assertDoesNotExist()
    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Trace)).performClick()

    composeTestRule.onNodeWithText("fn39").assertIsDisplayed()
    composeTestRule.onNodeWithText("fn0").assertDoesNotExist()
  }

  @Test
  fun aTabNotYetScrolled_opensWithTheTabRowPinnedWhenTheOldTabHadItPinned() {
    show(loaded.copy(events = mapOf(EventKind.Recommended to longTrace)))
    scrollTo("fn39")

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).performClick()

    // The tab row sits at the top of the list and the issue header is scrolled away.
    val list = composeTestRule.onNodeWithTag(DetailTags.LIST).fetchSemanticsNode().boundsInRoot
    val tabRow = composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).fetchSemanticsNode().boundsInRoot
    assertEquals(list.top, tabRow.top, 0.5f)
    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).assertIsDisplayed()
    val strip = composeTestRule.onAllNodesWithTag(DetailTags.STAT_EVENTS).fetchSemanticsNodes()
    assertTrue("the stat strip should be scrolled away", strip.all { it.boundsInRoot.bottom <= list.top + 0.5f })

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Trace)).performClick()
    composeTestRule.onNodeWithText("fn39").assertIsDisplayed()
  }

  @Test
  fun aShortTab_keepsTheTabRowPinnedWhenTheOldTabHadItPinned() {
    val fewTags =
      listOf(TagSummary("browser", "Chrome 130", 8, 10), TagSummary("os", "Android 16", 6, 10), TagSummary("release", "1.4.2", 10, 10))
    show(loaded.copy(events = mapOf(EventKind.Recommended to longTrace), tags = fewTags))
    scrollTo("fn39")
    assertTabRowPinned()

    composeTestRule.onNodeWithTag(DetailTags.tab(DetailTab.Tags)).performClick()

    composeTestRule.onNodeWithText("Chrome 130").assertIsDisplayed()
    assertTabRowPinned()
  }

  @Test
  fun aShortTrace_canStillPinTheTabRow() {
    show(loaded)

    composeTestRule.onNodeWithTag(DetailTags.LIST).performTouchInput { swipeUp() }

    assertTabRowPinned()
    composeTestRule.onNodeWithText("cartTotal").assertIsDisplayed()
    // The list ends there: the space under the trace is only enough to pin the tab row, not to scroll it all away.
    val frameTop = composeTestRule.onNodeWithText("cartTotal").fetchSemanticsNode().boundsInRoot.top
    composeTestRule.onNodeWithTag(DetailTags.LIST).performTouchInput { swipeUp() }
    assertEquals(frameTop, composeTestRule.onNodeWithText("cartTotal").fetchSemanticsNode().boundsInRoot.top, 0.5f)
  }

  @Test
  fun aPinnedTabRow_staysPinnedWhileTheTabsContentGrowsAndShrinks() {
    val withCode =
      event.copy(
        stacks =
          listOf(
            event.stacks.single().copy(
              frames = listOf(StackFrame("cartTotal", null, "shop/cart.js", 42, null, true, (38..46).map { ContextLine(it, "line $it") }))
            )
          )
      )
    val ui = show(loaded.copy(events = emptyMap(), isLoadingEvent = true))
    composeTestRule.onNodeWithTag(DetailTags.LIST).performTouchInput { swipeUp() }
    assertTabRowPinned()

    // The event loads: the spinner gives way to the stack.
    composeTestRule.runOnIdle { ui.value = loaded.copy(events = mapOf(EventKind.Recommended to withCode)) }
    composeTestRule.onNodeWithText("cartTotal").assertIsDisplayed()
    assertTabRowPinned()

    // The frame's code opens, then closes again.
    composeTestRule.runOnIdle { ui.value = ui.value.copy(expandedFrames = setOf(FrameRef(0, 0))) }
    composeTestRule.onNodeWithText("line 46", substring = true).assertIsDisplayed()
    assertTabRowPinned()
    composeTestRule.runOnIdle { ui.value = ui.value.copy(expandedFrames = emptySet()) }
    composeTestRule.onNodeWithText("line 46", substring = true).assertDoesNotExist()
    assertTabRowPinned()
  }

  @Test
  fun theFirstSeenCell_showsTheReleaseTheIssueWasFirstSeenIn() {
    show(loaded)

    composeTestRule.onNodeWithTag(DetailTags.STAT_FIRST_RELEASE, useUnmergedTree = true).assertIsDisplayed().assert(hasText("1.4.0"))
    composeTestRule.onNodeWithTag(DetailTags.STAT_FIRST_SEEN).assert(hasContentDescription("First seen 3 days ago, in release 1.4.0"))
  }

  @Test
  fun withoutAFirstRelease_theFirstSeenCellHasNoReleaseLine() {
    show(loaded.copy(summary = loaded.summary!!.copy(firstRelease = null)))

    composeTestRule.onAllNodesWithTag(DetailTags.STAT_FIRST_RELEASE, useUnmergedTree = true).assertCountEquals(0)
    composeTestRule.onNodeWithTag(DetailTags.STAT_FIRST_SEEN).assert(hasContentDescription("First seen 3 days ago"))
  }
}
