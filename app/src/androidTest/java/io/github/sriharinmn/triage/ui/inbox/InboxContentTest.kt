package io.github.sriharinmn.triage.ui.inbox

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.sriharinmn.triage.data.issues.InboxError
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.data.issues.Project
import io.github.sriharinmn.triage.theme.TriageTheme
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InboxContentTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val now = Instant.parse("2026-10-22T12:00:00Z")
  private val calls = mutableListOf<String>()

  private fun issue(id: String) =
    Issue(
      id = id,
      shortId = "DEMO-$id",
      title = "Error $id",
      culprit = "handler in app",
      level = IssueLevel.Error,
      badge = IssueBadge.New,
      isUnhandled = false,
      eventCount = 48,
      userCount = 11,
      firstSeen = now - Duration.ofDays(5),
      lastSeen = now.minusSeconds(180),
      permalink = "https://example-org.sentry.io/issues/$id/",
      projectId = "101",
      projectSlug = "shop-api",
      hourlyEvents = List(24) { it.toLong() },
    )

  private val loaded = InboxUiState(orgName = "Example Org", issues = listOf(issue("1"), issue("2")), refreshedAt = now)

  private fun show(state: InboxUiState) = show(mutableStateOf(state))

  /** Shows the inbox for whatever [state] holds, so a test can change the state after the first composition. */
  private fun show(state: MutableState<InboxUiState>) {
    composeTestRule.setContent {
      TriageTheme {
        InboxContent(
          state = state.value,
          now = now,
          onTabSelected = { calls += "tab:${it.name}" },
          onProjectSelected = { calls += "project:$it" },
          onEnvironmentSelected = { calls += "environment:$it" },
          onSortSelected = { calls += "sort:${it.name}" },
          onRefresh = { calls += "refresh" },
          onLoadMore = { calls += "loadMore" },
          onRetryLoadMore = { calls += "retryLoadMore" },
          onIssueClick = { calls += "open:${it.id}" },
          onDisconnectConfirmed = { calls += "disconnect" },
          onDisconnectFailureShown = {},
        )
      }
    }
  }

  @Test
  fun rows_showTitleIdCountsAndTimes() {
    show(loaded)

    composeTestRule.onNodeWithText("Error 1").assertIsDisplayed()
    composeTestRule.onNodeWithTag(InboxTags.row("1")).assert(hasText("DEMO-1"))
    composeTestRule.onNodeWithTag(InboxTags.row("1")).assert(hasText("48 events"))
    composeTestRule.onNodeWithTag(InboxTags.row("1")).assert(hasText("11 users"))
    composeTestRule.onNodeWithTag(InboxTags.row("1")).assert(hasText("3m ago"))
    composeTestRule.onNodeWithTag(InboxTags.row("1")).assert(hasText("5d old"))
    composeTestRule.onNodeWithTag(InboxTags.row("1")).assert(hasText("New"))
  }

  @Test
  fun tappingARow_opensThatIssue() {
    show(loaded)

    composeTestRule.onNodeWithTag(InboxTags.row("2")).performClick()

    assertEquals(listOf("open:2"), calls)
  }

  @Test
  fun anEmptyTab_saysSoKindly() {
    show(InboxUiState(orgName = "Example Org", filter = InboxFilter(tab = InboxTab.Escalating), refreshedAt = now))

    composeTestRule.onNodeWithText("Nothing is escalating.").assertIsDisplayed()
  }

  @Test
  fun noCacheAndNoNetwork_offersRetry() {
    show(InboxUiState(orgName = "Example Org", error = InboxError.Network))

    composeTestRule.onNodeWithText("Couldn't reach Sentry. Check your connection.").assertIsDisplayed()
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

    composeTestRule.onNodeWithText(NETWORK_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("Loading issues…").assertIsDisplayed()
  }

  @Test
  fun aRepeatedFailure_showsTheSnackbarAgain() {
    val state = mutableStateOf(loaded.copy(error = InboxError.Network))
    show(state)
    waitForText(NETWORK_ERROR, present = true)

    // The same error stays set while the next refresh runs; starting it dismisses the old snackbar.
    composeTestRule.runOnIdle { state.value = state.value.copy(isRefreshing = true) }
    waitForText(NETWORK_ERROR, present = false)

    // The retry fails with the very same error, and the snackbar comes back.
    composeTestRule.runOnIdle { state.value = state.value.copy(isRefreshing = false) }
    waitForText(NETWORK_ERROR, present = true)
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
    show(loaded.copy(error = InboxError.Network))

    composeTestRule.onNodeWithText("couldn't refresh", substring = true).assertIsDisplayed()
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
  fun disconnect_asksForConfirmationFirst() {
    show(loaded)

    composeTestRule.onNodeWithTag(InboxTags.MENU).performClick()
    composeTestRule.onNodeWithText("Disconnect…").performClick()
    assertTrue(calls.isEmpty())
    composeTestRule.onNodeWithText("Disconnect").performClick()

    assertEquals(listOf("disconnect"), calls)
  }

  private fun waitForText(text: String, present: Boolean) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() == present
    }
    if (present) composeTestRule.onNodeWithText(text).assertIsDisplayed()
  }

  private companion object {
    const val NETWORK_ERROR = "Couldn't reach Sentry. Check your connection."
  }
}
