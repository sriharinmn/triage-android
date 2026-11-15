package io.github.sriharinmn.triage.ui.inbox

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.theme.TriageTheme
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class IssueListTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val now = Instant.parse("2026-10-22T12:00:00Z")

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

  private val twelve = ('a'..'l').map { issue(it.toString()) }

  private fun inbox(issues: List<Issue>, filter: InboxFilter = InboxFilter()) =
    InboxUiState(orgName = "Example Org", issues = issues, filter = filter, refreshedAt = now)

  private fun show(state: MutableState<InboxUiState>) {
    composeTestRule.setContent {
      TriageTheme {
        IssueList(
          state = state.value,
          now = now,
          onIssueClick = {},
          onResolve = {},
          onArchive = {},
          onLoadMore = {},
          onRetryLoadMore = {},
        )
      }
    }
  }

  @Test
  fun aRefreshThatPutsANewIssueFirst_showsIt_whenTheListWasAtTheTop() {
    val state = mutableStateOf(inbox(twelve))
    show(state)
    composeTestRule.onNodeWithTag(InboxTags.row("a")).assertIsDisplayed()

    state.value = inbox(listOf(issue("n")) + twelve)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("n")).assertIsDisplayed()
  }

  @Test
  fun aRefresh_leavesTheRowsInPlace_whenScrolledDown() {
    val state = mutableStateOf(inbox(twelve))
    show(state)
    composeTestRule.onNodeWithTag(InboxTags.LIST).performScrollToIndex(6)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(InboxTags.row("g")).assertIsDisplayed()

    state.value = inbox(listOf(issue("n")) + twelve)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("n")).assertIsNotDisplayed()
    composeTestRule.onNodeWithTag(InboxTags.row("g")).assertIsDisplayed()
  }

  @Test
  fun anotherTab_opensAtItsTop() {
    val state = mutableStateOf(inbox(twelve))
    show(state)
    composeTestRule.onNodeWithTag(InboxTags.LIST).performScrollToIndex(6)
    composeTestRule.waitForIdle()

    val other = listOf("x", "y", "z", "w", "v", "u", "t", "s", "r", "q", "p", "o").map { issue(it) }
    state.value = inbox(other, InboxFilter(tab = InboxTab.Regressed))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("x")).assertIsDisplayed()
  }
}
