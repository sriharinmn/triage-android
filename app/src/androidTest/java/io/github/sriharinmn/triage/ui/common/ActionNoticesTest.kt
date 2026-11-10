package io.github.sriharinmn.triage.ui.common

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.sriharinmn.triage.data.actions.ActionNotice
import io.github.sriharinmn.triage.data.actions.IssueAction
import io.github.sriharinmn.triage.data.actions.SubmittedAction
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.members.Member
import io.github.sriharinmn.triage.theme.TriageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActionNoticesTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()

  private fun show(notice: MutableState<ActionNotice?>) {
    composeTestRule.setContent {
      TriageTheme {
        val host = remember { SnackbarHostState() }
        Scaffold(snackbarHost = { SnackbarHost(host) }) { _ ->
          ActionNoticeEffect(
            notice.value,
            host,
            onUndo = { calls += "undo:${it.action.id}" },
            onRetry = { calls += "retry:${it.action.id}" },
            onDismissed = { calls += "dismissed:${it.action.id}" },
          )
        }
      }
    }
  }

  @Test
  fun aPendingAction_offersUndo_untilItIsSent() {
    val notice = mutableStateOf<ActionNotice?>(ActionNotice.Pending(SubmittedAction(1, "1", "DEMO-1", IssueAction.Resolve)))
    show(notice)

    composeTestRule.onNodeWithText("Resolved DEMO-1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Undo").performClick()
    assertEquals(listOf("undo:1"), calls)
  }

  @Test
  fun theSnackbarGoesAway_whenTheActionIsSent() {
    val notice = mutableStateOf<ActionNotice?>(ActionNotice.Pending(SubmittedAction(1, "1", "DEMO-1", IssueAction.Archive)))
    show(notice)
    composeTestRule.onNodeWithText("Archived DEMO-1").assertIsDisplayed()

    notice.value = null

    composeTestRule.onNodeWithText("Archived DEMO-1").assertDoesNotExist()
    assertEquals(emptyList<String>(), calls)
  }

  @Test
  fun anAssignment_namesTheAssignee() {
    val ana = Member("41", "Ana Silva", null)
    show(mutableStateOf(ActionNotice.Pending(SubmittedAction(2, "1", "DEMO-1", IssueAction.Assign(ana)))))

    composeTestRule.onNodeWithText("Assigned DEMO-1 to Ana Silva").assertIsDisplayed()
  }

  @Test
  fun aFailure_saysWhy_andOffersRetry() {
    show(mutableStateOf(ActionNotice.Failed(SubmittedAction(3, "1", "DEMO-1", IssueAction.Resolve), DetailError.Network)))

    composeTestRule.onNodeWithText("Couldn't resolve DEMO-1. Couldn't reach Sentry. Check your connection.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").performClick()
    assertEquals(listOf("retry:3"), calls)
  }

  @Test
  fun aTokenThatCantWrite_saysSo() {
    show(mutableStateOf(ActionNotice.Failed(SubmittedAction(4, "1", "DEMO-1", IssueAction.Archive), DetailError.Forbidden)))

    composeTestRule
      .onNodeWithText("Couldn't archive DEMO-1. Your token can't change issues.", substring = true)
      .assertIsDisplayed()
  }
}
