package io.github.sriharinmn.triage.ui.detail

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.sriharinmn.triage.data.detail.Breadcrumb
import io.github.sriharinmn.triage.data.detail.ContextLine
import io.github.sriharinmn.triage.data.detail.EventContext
import io.github.sriharinmn.triage.data.detail.EventRequest
import io.github.sriharinmn.triage.data.detail.EventStack
import io.github.sriharinmn.triage.data.detail.EventTag
import io.github.sriharinmn.triage.data.detail.EventUser
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.StackFrame
import io.github.sriharinmn.triage.data.detail.TagSummary
import io.github.sriharinmn.triage.data.issues.IssueLevel
import io.github.sriharinmn.triage.theme.TriageTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The event sections on their own, with fake data; nothing touches the network or the phone's storage. */
class EventSectionsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()

  private fun frame(function: String, inApp: Boolean, line: Int = 1, code: List<ContextLine> = emptyList()) =
    StackFrame(function = function, module = null, file = "shop/$function.js", lineNo = line, colNo = null, inApp = inApp, context = code)

  private val crash =
    EventStack(
      type = "TypeError",
      value = "Cannot read properties of undefined",
      threadName = null,
      frames =
        listOf(
          frame("cartTotal", inApp = true, line = 42, code = listOf(ContextLine(41, "const item = items[i]"), ContextLine(42, "total += item.price"))),
          frame("handleCheckout", inApp = true),
          frame("layerHandle", inApp = false),
          frame("processTicks", inApp = false),
        ),
    )

  private fun event(
    stacks: List<EventStack> = listOf(crash),
    message: String? = null,
    breadcrumbs: List<Breadcrumb> = emptyList(),
    tags: List<EventTag> = emptyList(),
    context: EventContext = EventContext(),
  ) =
    IssueEvent(
      id = "e1",
      dateCreated = null,
      release = null,
      environment = null,
      stacks = stacks,
      message = message,
      breadcrumbs = breadcrumbs,
      tags = tags,
      context = context,
    )

  private fun show(
    event: IssueEvent,
    expanded: Set<FrameRef> = emptySet(),
    revealed: Set<Int> = emptySet(),
    showAllBreadcrumbs: Boolean = false,
    tags: List<TagSummary>? = null,
  ) {
    composeTestRule.setContent {
      TriageTheme {
        LazyColumn(Modifier.testTag(DetailTags.LIST)) {
          stackTraceSection(
            event,
            expanded,
            revealed,
            onFrameToggled = { calls += "frame:${it.stack}:${it.frame}" },
            onShowLibraryFrames = { calls += "library:$it" },
          )
          breadcrumbsSection(event.breadcrumbs, showAllBreadcrumbs, onShowAll = { calls += "crumbs" })
          tagsSection(tags, event.tags)
          detailsSection(event.context)
        }
      }
    }
  }

  private fun scrollTo(text: String) =
    composeTestRule.onNodeWithTag(DetailTags.LIST).performScrollToNode(hasText(text, substring = true))

  @Test
  fun onlyTheAppsOwnFramesShow_untilLibraryFramesAreRevealed() {
    show(event())

    composeTestRule.onNodeWithText("TypeError: Cannot read properties of undefined").assertIsDisplayed()
    composeTestRule.onNodeWithText("cartTotal").assertIsDisplayed()
    composeTestRule.onNodeWithText("handleCheckout").assertIsDisplayed()
    composeTestRule.onNodeWithText("layerHandle").assertDoesNotExist()
    composeTestRule.onNodeWithText("Show 2 library frames").performClick()
    assertEquals(listOf("library:0"), calls)
  }

  @Test
  fun aRevealedStack_showsEveryFrame() {
    show(event(), revealed = setOf(0))

    composeTestRule.onNodeWithText("layerHandle").assertIsDisplayed()
    composeTestRule.onNodeWithText("Show 2 library frames").assertDoesNotExist()
  }

  @Test
  fun aStackWithoutTheAppsFrames_showsThemAll() {
    show(event(stacks = listOf(crash.copy(frames = crash.frames.map { it.copy(inApp = false) }))))

    composeTestRule.onNodeWithText("layerHandle").assertIsDisplayed()
    composeTestRule.onNodeWithText("library frames", substring = true).assertDoesNotExist()
  }

  @Test
  fun anExpandedFrame_showsItsCode_withTheErrorLineMarked() {
    show(event(), expanded = setOf(FrameRef(0, 0)))

    composeTestRule.onNodeWithText("total += item.price", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Line 42, where the error happened: total += item.price").assertExists()
    composeTestRule.onNodeWithTag(DetailTags.frame(FrameRef(0, 0))).performClick()
    assertEquals(listOf("frame:0:0"), calls)
  }

  @Test
  fun aFrameWithCode_isAnnouncedAsAButton() {
    show(event())

    composeTestRule
      .onNodeWithTag(DetailTags.frame(FrameRef(0, 0)))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
  }

  @Test
  fun aCause_isLabelled() {
    val cause = EventStack(type = "IOException", value = "timeout", threadName = null, frames = emptyList())
    show(event(stacks = listOf(crash, cause)))

    scrollTo("Caused by")
    composeTestRule.onNodeWithText("IOException: timeout").assertIsDisplayed()
  }

  @Test
  fun aThreadsStack_isNamedAfterTheThread() {
    show(event(stacks = listOf(EventStack(null, null, "FinalizerWatchdogDaemon", listOf(frame("finalize", inApp = true))))))

    composeTestRule.onNodeWithText("Thread FinalizerWatchdogDaemon").assertIsDisplayed()
  }

  @Test
  fun aMessageEvent_showsItsMessage() {
    show(event(stacks = emptyList(), message = "Slow checkout"))

    composeTestRule.onNodeWithText("Message").assertIsDisplayed()
    composeTestRule.onNodeWithText("Slow checkout").assertIsDisplayed()
  }

  @Test
  fun anEventWithoutAStackOrMessage_saysSo() {
    show(event(stacks = emptyList()))

    composeTestRule.onNodeWithText("This event has no stack trace.").assertIsDisplayed()
  }

  @Test
  fun breadcrumbs_showFiveUntilAllAreAskedFor() {
    val crumbs = (1..7).map { Breadcrumb(timestamp = null, category = "ui.click", level = IssueLevel.Info, text = "click $it") }
    show(event(stacks = emptyList(), breadcrumbs = crumbs))

    scrollTo("click 5")
    composeTestRule.onNodeWithText("click 6", substring = true).assertDoesNotExist()
    scrollTo("Show all 7")
    composeTestRule.onNodeWithText("Show all 7").performClick()
    assertEquals(listOf("crumbs"), calls)
  }

  @Test
  fun allBreadcrumbs_showOnceAskedFor() {
    val crumbs = (1..7).map { Breadcrumb(timestamp = null, category = "ui.click", level = IssueLevel.Info, text = "click $it") }
    show(event(stacks = emptyList(), breadcrumbs = crumbs), showAllBreadcrumbs = true)

    scrollTo("click 7")
    composeTestRule.onNodeWithText("Show all 7").assertDoesNotExist()
  }

  @Test
  fun aBreadcrumbsLevel_isReadOut() {
    show(event(stacks = emptyList(), breadcrumbs = listOf(Breadcrumb(null, "exception", IssueLevel.Error, "boom"))))

    composeTestRule.onNodeWithContentDescription("Error").assertExists()
  }

  @Test
  fun tags_showTheirTopValuesShare() {
    show(event(stacks = emptyList()), tags = listOf(TagSummary("browser", "Chrome 128", 858, 1100)))

    scrollTo("Chrome 128")
    composeTestRule.onNodeWithText("78%").assertIsDisplayed()
  }

  @Test
  fun aTagKeyRepeatedBySentry_doesNotBreakTheList() {
    show(
      event(stacks = emptyList()),
      tags = listOf(TagSummary("browser", "Chrome 128", 858, 1100), TagSummary("browser", "Firefox 131", 120, 1100)),
    )

    scrollTo("Firefox 131")
    composeTestRule.onNodeWithText("Firefox 131").assertIsDisplayed()
  }

  @Test
  fun withoutTagStatistics_theEventsOwnTagsShow() {
    show(event(stacks = emptyList(), tags = listOf(EventTag("route", "POST /api/checkout"))), tags = null)

    scrollTo("POST /api/checkout")
    composeTestRule.onNodeWithText("route").assertIsDisplayed()
  }

  @Test
  fun details_showTheUser_andOnlyTheRequestsMethodAndUrl() {
    val context =
      EventContext(
        device = "Google Pixel 8",
        user = EventUser(id = "4821", email = "user@example.com", username = null, ipAddress = "203.0.113.7"),
        request = EventRequest(method = "POST", url = "https://shop.example.test/api/checkout"),
      )
    show(event(stacks = emptyList(), context = context))

    scrollTo("Google Pixel 8")
    composeTestRule.onNodeWithText("id 4821 · user@example.com · 203.0.113.7").assertIsDisplayed()
    composeTestRule.onNodeWithText("POST https://shop.example.test/api/checkout").assertIsDisplayed()
  }
}
