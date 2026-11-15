package io.github.sriharinmn.triage.ui.inbox

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.SubmittedAction
import io.github.sriharinmn.triage.data.issues.InboxError
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.Project
import io.github.sriharinmn.triage.theme.TriageTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InboxContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val now = Instant.parse("2026-10-22T12:00:00Z")
  private val calls = mutableListOf<String>()

  private fun issue(id: String) = testIssue(id, now)

  private val loaded = InboxUiState(orgName = "Example Org", issues = listOf(issue("1"), issue("2")), refreshedAt = now)

  private fun show(state: InboxUiState, fontScale: Float = 1f) = show(mutableStateOf(state), fontScale)

  /** Shows the inbox for whatever [state] holds, so a test can change the state after the first composition. */
  private fun show(state: MutableState<InboxUiState>, fontScale: Float = 1f) {
    composeTestRule.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
        TriageTheme {
          InboxContent(
            state = state.value,
            now = now,
            onTabSelected = { calls += "tab:${it.name}" },
            onProjectSelected = { calls += "project:$it" },
            onEnvironmentSelected = { calls += "environment:$it" },
            onSortSelected = { calls += "sort:${it.name}" },
            onClearFilters = { calls += "clearFilters" },
            onRefresh = { calls += "refresh" },
            onLoadMore = { calls += "loadMore" },
            onRetryLoadMore = { calls += "retryLoadMore" },
            onIssueClick = { calls += "open:${it.id}" },
            onResolve = { calls += "resolve:${it.id}" },
            onArchive = { calls += "archive:${it.id}" },
            onUndo = { calls += "undo:${it.action.id}" },
            onRetryAction = { calls += "retryAction:${it.action.id}" },
            onNoticeDismissed = { calls += "noticeDismissed:${it.action.id}" },
            onDisconnectConfirmed = { calls += "disconnect" },
            onDisconnectFailureShown = {},
            onAlertsCardTurnOn = { calls += "alertsTurnOn" },
            onAlertsCardNotNow = { calls += "alertsNotNow" },
            onOpenSettings = { calls += "openSettings" },
            onConnectInsteadOfDemo = { calls += "connectInsteadOfDemo" },
          )
        }
      }
    }
  }

  @Test
  fun aRow_readsAsOneSentence() {
    // issue("1") has 0, 1, ... 23 events an hour: the last three hours are well over an eighth of the day, so it's Hot.
    show(loaded)

    composeTestRule
      .onNodeWithTag(InboxTags.row("1"))
      .assert(hasContentDescription("Error. Error 1. 48 events, busier than usual, 11 users. New. 3 minutes ago."))
  }

  @Test
  fun aQuietRow_isNotCalledBusy() {
    show(loaded.copy(issues = listOf(issue("3").copy(hourlyEvents = List(24) { 0L }, badge = null))))

    composeTestRule
      .onNodeWithTag(InboxTags.row("3"))
      .assert(hasContentDescription("Error. Error 3. 48 events, 11 users. 3 minutes ago."))
  }

  @Test
  fun atTwiceTheFontSize_aRowStillReads_andSwipes() {
    show(loaded, fontScale = 2f)

    composeTestRule.onNodeWithTag(InboxTags.row("1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(InboxTags.row("1")).performTouchInput { swipeRight() }
    composeTestRule.waitForIdle()

    assertTrue("resolve:1" in calls)
  }

  @Test
  fun tappingARow_opensThatIssue() {
    show(loaded)

    composeTestRule.onNodeWithTag(InboxTags.row("2")).performClick()

    assertEquals(listOf("open:2"), calls)
  }

  @Test
  fun swipingRight_resolves_andSwipingLeft_archives() {
    show(loaded)

    composeTestRule.onNodeWithTag(InboxTags.row("1")).performTouchInput { swipeRight() }
    composeTestRule.onNodeWithTag(InboxTags.row("2")).performTouchInput { swipeLeft() }
    // The action fires when the swipe settles, not when the finger lifts.
    composeTestRule.waitForIdle()

    assertEquals(listOf("resolve:1", "archive:2"), calls.filter { it.startsWith("resolve") || it.startsWith("archive") })
  }

  @Test
  fun aSwipedRow_theViewModelIgnored_settlesBackAndCanBeSwipedAgain() {
    // The callbacks never remove the row, like a ViewModel that ignores the call.
    show(loaded)

    composeTestRule.onNodeWithTag(InboxTags.row("1")).performTouchInput { swipeRight() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(InboxTags.row("1")).performTouchInput { swipeRight() }
    composeTestRule.waitForIdle()

    assertEquals(listOf("resolve:1", "resolve:1"), calls)
  }

  @Test
  fun aSwipedRow_thatComesBackAfterUndo_isBackInPlace() {
    val state = mutableStateOf(loaded)
    show(state)
    composeTestRule.waitForIdle()
    val leftBefore = composeTestRule.onNodeWithTag(InboxTags.row("1")).fetchSemanticsNode().boundsInRoot.left

    composeTestRule.onNodeWithTag(InboxTags.row("1")).performTouchInput { swipeRight() }
    composeTestRule.runOnIdle { state.value = state.value.copy(issues = listOf(issue("2"))) }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(InboxTags.row("1")).assertDoesNotExist()

    composeTestRule.runOnIdle { state.value = state.value.copy(issues = listOf(issue("1"), issue("2"))) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("1")).assertIsDisplayed()
    val leftAfter = composeTestRule.onNodeWithTag(InboxTags.row("1")).fetchSemanticsNode().boundsInRoot.left
    assertEquals(leftBefore, leftAfter, 0.5f)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun talkBackUsers_getTheSameActions() {
    show(loaded)

    composeTestRule.onNodeWithTag(InboxTags.row("1")).performCustomAccessibilityActionWithLabel("Resolve")
    composeTestRule.onNodeWithTag(InboxTags.row("2")).performCustomAccessibilityActionWithLabel("Archive")

    assertEquals(listOf("resolve:1", "archive:2"), calls)
  }

  @Test
  fun aPendingAction_offersUndo() {
    show(loaded.copy(notice = ActionNotice.Pending(SubmittedAction(7, "1", "DEMO-1", IssueAction.Resolve))))

    composeTestRule.onNodeWithText("Resolved DEMO-1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Undo").performClick()

    assertTrue("undo:7" in calls)
  }

  @Test
  fun anUndo_neverWaitsBehindAnErrorSnackbar() {
    show(loaded.copy(error = InboxError.Server, notice = ActionNotice.Pending(SubmittedAction(8, "1", "DEMO-1", IssueAction.Resolve))))

    composeTestRule.onNodeWithText("Resolved DEMO-1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Undo").performClick()

    assertTrue("undo:8" in calls)
  }

  @Test
  fun anEmptyTab_saysSoKindly() {
    show(InboxUiState(orgName = "Example Org", filter = InboxFilter(tab = InboxTab.Escalating), refreshedAt = now))

    composeTestRule.onNodeWithText("Nothing escalating").assertIsDisplayed()
  }

  @Test
  fun noCacheAndNoNetwork_offersRetry() {
    show(InboxUiState(orgName = "Example Org", error = InboxError.Network))

    composeTestRule.onNodeWithText(CANT_REACH).assertIsDisplayed()
    composeTestRule.onNodeWithTag(InboxTags.ERROR_ACTION).performClick()

    assertEquals(listOf("refresh"), calls)
  }

  @Test
  fun aFullPhone_offersRetryNotReconnect() {
    show(InboxUiState(orgName = "Example Org", error = InboxError.Storage))

    composeTestRule
      .onNodeWithText("Couldn't save issues on this phone. Free up some storage, then try again.")
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Reconnect").assertDoesNotExist()
    composeTestRule.onNodeWithTag(InboxTags.ERROR_ACTION).performClick()

    assertEquals(listOf("refresh"), calls)
  }

  @Test
  fun aRejectedToken_offersReconnect() {
    show(InboxUiState(orgName = "Example Org", error = InboxError.Unauthorized))

    composeTestRule.onNodeWithText("Reconnect").performClick()
    assertTrue(calls.isEmpty())
    composeTestRule.onNodeWithText("Disconnect from Sentry?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Disconnect").performClick()

    assertEquals(listOf("disconnect"), calls)
  }

  @Test
  fun refreshingWithNoCache_showsTheSpinnerNotTheOldError() {
    show(InboxUiState(orgName = "Example Org", error = InboxError.Network, isRefreshing = true))

    composeTestRule.onNodeWithText(CANT_REACH).assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("Loading issues…").assertIsDisplayed()
  }

  @Test
  fun aRepeatedFailure_showsTheSnackbarAgain() {
    val state = mutableStateOf(loaded.copy(error = InboxError.Server))
    show(state)
    waitForText(SERVER_ERROR, present = true)

    // The same error stays set while the next refresh runs; starting it dismisses the old snackbar.
    composeTestRule.runOnIdle { state.value = state.value.copy(isRefreshing = true) }
    waitForText(SERVER_ERROR, present = false)

    // The retry fails with the very same error, and the snackbar comes back.
    composeTestRule.runOnIdle { state.value = state.value.copy(isRefreshing = false) }
    waitForText(SERVER_ERROR, present = true)
  }

  @Test
  fun loadMore_isRequestedNearTheEnd_whenNotRefreshing() {
    show(loaded.copy(hasMore = true))
    composeTestRule.waitForIdle()

    assertTrue("loadMore" in calls)
  }

  @Test
  fun loadMore_isNotRequested_whileRefreshing() {
    show(loaded.copy(hasMore = true, isRefreshing = true))
    composeTestRule.waitForIdle()

    assertTrue("loadMore" !in calls)
  }

  @Test
  fun aCachedListWithAnError_saysItCouldNotRefresh() {
    show(loaded.copy(error = InboxError.Server))

    composeTestRule.onNodeWithText("couldn't refresh", substring = true).assertIsDisplayed()
  }

  @Test
  fun offlineWithACachedList_showsTheBanner_insteadOfASnackbar() {
    show(loaded.copy(error = InboxError.Network))

    composeTestRule.onNodeWithTag(InboxTags.OFFLINE_BANNER).assertIsDisplayed()
    composeTestRule.onNodeWithText("Offline · showing issues from", substring = true).assertIsDisplayed()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithText(NETWORK_ERROR).fetchSemanticsNodes().isEmpty())
    composeTestRule.onNodeWithText("Retry").performClick()

    assertTrue("refresh" in calls)
  }

  @Test
  fun anEmptyUnresolvedTab_isAllClear() {
    show(InboxUiState(orgName = "Example Org", refreshedAt = now))

    composeTestRule.onNodeWithText("All clear").assertIsDisplayed()
    composeTestRule.onNodeWithText("No unresolved issues in the last 14 days.").assertIsDisplayed()
  }

  @Test
  fun filtersThatHideEverything_offerToClearThem() {
    show(InboxUiState(orgName = "Example Org", filter = InboxFilter(environment = "staging"), refreshedAt = now))

    composeTestRule.onNodeWithText("No issues match these filters").assertIsDisplayed()
    composeTestRule.onNodeWithTag(InboxTags.CLEAR_FILTERS).performClick()

    assertEquals(listOf("clearFilters"), calls)
  }

  @Test
  fun atTwiceTheFontSize_theChipsScroll_andKeepTheirFullLabels() {
    show(loaded, fontScale = 2f)

    composeTestRule.onNodeWithTag(InboxTags.SORT_FILTER).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("All environments").assertExists()
  }

  @Test
  fun aFailedPage_offersRetry() {
    show(loaded.copy(hasMore = true, loadMoreFailed = true))

    composeTestRule.onNodeWithTag(InboxTags.LOAD_MORE_RETRY).performClick()

    assertTrue("retryLoadMore" in calls)
  }

  @Test
  fun tappingATab_selectsIt() {
    show(loaded)

    composeTestRule.onNodeWithText("Regressed").performClick()

    assertTrue("tab:Regressed" in calls)
  }

  @Test
  fun theProjectChip_opensASheetAndPicksAProject() {
    val projects =
      listOf(
        Project("101", "shop-api", "Shop API", "node", listOf("production")),
        Project("102", "android-app", "Android App", "android", listOf("production")),
      )
    show(loaded.copy(projects = projects))

    composeTestRule.onNodeWithTag(InboxTags.PROJECT_FILTER).performClick()
    composeTestRule.onNodeWithText("android-app").performClick()

    assertTrue("project:102" in calls)
  }

  @Test
  fun theAlertsCard_showsAboveTheList_andItsButtonsCallBack() {
    show(loaded.copy(showAlertsCard = true))

    composeTestRule.onNodeWithText("Get alerts for new, regressed and escalating issues?").assertIsDisplayed()
    composeTestRule.onNodeWithTag(InboxTags.ALERTS_CARD_TURN_ON).performClick()
    composeTestRule.onNodeWithTag(InboxTags.ALERTS_CARD_NOT_NOW).performClick()

    assertEquals(listOf("alertsTurnOn", "alertsNotNow"), calls)
  }

  @Test
  fun withoutTheFlag_thereIsNoCard() {
    show(loaded)

    composeTestRule.onNodeWithTag(InboxTags.ALERTS_CARD).assertDoesNotExist()
  }

  @Test
  fun theGear_opensSettings() {
    show(loaded)

    composeTestRule.onNodeWithContentDescription("Settings").performClick()

    assertEquals(listOf("openSettings"), calls)
  }

  @Test
  fun theDemo_hasAChip_whoseNoteExplainsIt() {
    show(loaded.copy(isDemo = true))

    composeTestRule.onNodeWithTag(DemoTags.CHIP).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("made-up shop", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DemoTags.KEEP_EXPLORING).performClick()

    composeTestRule.onNodeWithText("made-up shop", substring = true).assertDoesNotExist()
    assertFalse("connectInsteadOfDemo" in calls)
  }

  @Test
  fun theDemoNotesConnect_leavesTheDemoForConnect() {
    show(loaded.copy(isDemo = true))

    composeTestRule.onNodeWithTag(DemoTags.CHIP).performClick()
    composeTestRule.onNodeWithTag(DemoTags.CONNECT).performClick()

    assertTrue("connectInsteadOfDemo" in calls)
    composeTestRule.onNodeWithText("made-up shop", substring = true).assertDoesNotExist()
  }

  @Test
  fun aRealAccount_hasNoDemoChip() {
    show(loaded)

    composeTestRule.onNodeWithTag(DemoTags.CHIP).assertDoesNotExist()
  }

  /** Many rows, so the list scrolls on any screen and its end is out of view at the top. */
  private fun many(prefix: String) = List(40) { issue("$prefix$it") }

  private fun top(tag: String) = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

  @Test
  fun anotherTab_opensAtItsTop() {
    val state = mutableStateOf(loaded.copy(issues = many("a")))
    show(state)
    composeTestRule.onNodeWithTag(InboxTags.LIST).performScrollToIndex(6)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(InboxTags.row("a0")).assertDoesNotExist()
    assertEquals(top(InboxTags.LIST), top(InboxTags.row("a6")), 1f)

    state.value = state.value.copy(issues = many("b"), filter = InboxFilter(tab = InboxTab.Regressed))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("b0")).assertIsDisplayed()
    assertEquals(top(InboxTags.LIST), top(InboxTags.row("b0")), 1f)
  }

  @Test
  fun afterSwitchingFilter_scrollingToTheEnd_asksForMore() {
    val state = mutableStateOf(loaded.copy(issues = many("a")))
    show(state)
    state.value = state.value.copy(issues = many("b"), filter = InboxFilter(tab = InboxTab.Regressed), hasMore = true)
    composeTestRule.waitForIdle()
    assertFalse("loadMore" in calls)

    composeTestRule.onNodeWithTag(InboxTags.LIST).performScrollToIndex(39)
    composeTestRule.waitForIdle()

    assertTrue("loadMore" in calls)
  }

  @Test
  fun theTopBar_namesTheOrg() {
    show(loaded)

    composeTestRule.onNodeWithText("Example Org").assertIsDisplayed()
  }

  private fun waitForText(text: String, present: Boolean) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() == present
    }
    if (present) composeTestRule.onNodeWithText(text).assertIsDisplayed()
  }

  private companion object {
    const val NETWORK_ERROR = "Couldn't reach Sentry. Check your connection."
    const val SERVER_ERROR = "Sentry sent something unexpected. Try again."
    const val CANT_REACH = "Can't reach Sentry"
  }
}
