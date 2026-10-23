package io.github.sriharinmn.triage

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The real signed-in back stack with stand-in screens: opening an issue and coming back keeps the inbox as it was. */
class TriageNavDisplayTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun showStandIns(
    issueDetail: @Composable (issueId: String, onBack: () -> Unit) -> Unit = { issueId, onBack ->
      Column {
        Text("Detail of $issueId")
        Button(onClick = onBack) { Text("Back") }
      }
    }
  ) {
    composeTestRule.setContent {
      TriageNavDisplay(
        inbox = { onOpenIssue ->
          LazyColumn(Modifier.testTag("inbox_list")) {
            items(100) { i -> Text("Issue $i", Modifier.fillMaxWidth().clickable { onOpenIssue("$i") }.padding(16.dp)) }
          }
        },
        issueDetail = issueDetail,
      )
    }
  }

  @Test
  fun openingAnIssue_andComingBack_keepsTheInboxScrollPosition() {
    showStandIns()
    composeTestRule.onNodeWithTag("inbox_list").performScrollToNode(hasText("Issue 60"))
    composeTestRule.onNodeWithText("Issue 60").performClick()
    composeTestRule.onNodeWithText("Detail of 60").assertIsDisplayed()

    composeTestRule.onNodeWithText("Back").performClick()

    composeTestRule.onNodeWithText("Issue 60").assertIsDisplayed()
    composeTestRule.onNodeWithText("Issue 0").assertDoesNotExist()
  }

  @Test
  fun systemBack_returnsToTheInbox() {
    showStandIns()
    composeTestRule.onNodeWithText("Issue 3").performClick()
    composeTestRule.onNodeWithText("Detail of 3").assertIsDisplayed()

    composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    composeTestRule.onNodeWithText("Issue 3").assertIsDisplayed()
    composeTestRule.onNodeWithText("Detail of 3").assertDoesNotExist()
  }

  @Test
  fun aDoubleBack_fromTheDetail_staysOnTheInbox() {
    // A keyboard Enter or a TalkBack double-tap can fire the exiting detail's back twice; one click stands in for both.
    showStandIns { issueId, onBack ->
      Column {
        Text("Detail of $issueId")
        Button(
          onClick = {
            onBack()
            onBack()
          }
        ) {
          Text("Back twice")
        }
      }
    }
    composeTestRule.onNodeWithText("Issue 3").performClick()
    composeTestRule.onNodeWithText("Detail of 3").assertIsDisplayed()

    composeTestRule.onNodeWithText("Back twice").performClick()

    composeTestRule.onNodeWithText("Issue 3").assertIsDisplayed()
    composeTestRule.onNodeWithText("Detail of 3").assertDoesNotExist()
  }

  @Test
  fun goingBack_clearsTheDetailsViewModel() {
    val seen = mutableListOf<ProbeViewModel>()
    showStandIns { issueId, onBack ->
      val vm: ProbeViewModel = viewModel { ProbeViewModel() }
      if (seen.none { it === vm }) seen += vm
      Column {
        Text("Detail of $issueId")
        Button(onClick = onBack) { Text("Back") }
      }
    }
    composeTestRule.onNodeWithText("Issue 3").performClick()
    composeTestRule.onNodeWithText("Detail of 3").assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(1, seen.size)
      assertFalse(seen[0].cleared)
    }

    composeTestRule.onNodeWithText("Back").performClick()

    composeTestRule.onNodeWithText("Detail of 3").assertDoesNotExist()
    // The event data lives in this ViewModel, so leaving the screen must drop it.
    composeTestRule.runOnIdle { assertTrue("leaving the detail must clear its ViewModel", seen[0].cleared) }
  }
}
