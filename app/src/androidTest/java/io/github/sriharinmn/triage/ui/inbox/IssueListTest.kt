package io.github.sriharinmn.triage.ui.inbox

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import io.github.sriharinmn.triage.data.issues.Issue
import io.github.sriharinmn.triage.theme.TriageTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The list on its own: what a refresh does to the scroll position. Filter changes are tested through the inbox itself. */
class IssueListTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val now = Instant.parse("2026-10-22T12:00:00Z")

  /** 40 rows: the list scrolls on any screen, and index 6 can be the first visible row. */
  private val forty = List(40) { testIssue("i$it", now) }

  private fun inbox(issues: List<Issue>) = InboxUiState(orgName = "Example Org", issues = issues, refreshedAt = now)

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

  private fun top(tag: String) = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

  // Without the fix, LazyColumn keeps i0 as the first row and the new issue sits above the viewport: the display and top
  // assertions below fail.
  @Test
  fun aRefreshThatPutsANewIssueFirst_showsIt_whenTheListWasAtTheTop() {
    val state = mutableStateOf(inbox(forty))
    show(state)
    composeTestRule.waitForIdle()
    assertEquals(top(InboxTags.LIST), top(InboxTags.row("i0")), 1f)

    state.value = inbox(listOf(testIssue("new", now)) + forty)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("new")).assertIsDisplayed()
    assertEquals(top(InboxTags.LIST), top(InboxTags.row("new")), 1f)
  }

  // This guards against over-fixing: a version that always jumps to the top would show the new issue and fail here.
  @Test
  fun aRefresh_leavesTheRowsInPlace_whenScrolledDown() {
    val state = mutableStateOf(inbox(forty))
    show(state)
    composeTestRule.onNodeWithTag(InboxTags.LIST).performScrollToIndex(6)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(InboxTags.row("i0")).assertDoesNotExist()
    assertEquals(top(InboxTags.LIST), top(InboxTags.row("i6")), 1f)
    val before = top(InboxTags.row("i6"))

    state.value = inbox(listOf(testIssue("new", now)) + forty)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(InboxTags.row("new")).assertIsNotDisplayed()
    assertEquals(before, top(InboxTags.row("i6")), 1f)
  }
}
