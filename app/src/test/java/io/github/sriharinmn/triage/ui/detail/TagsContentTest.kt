package io.github.sriharinmn.triage.ui.detail

import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.EventContext
import io.github.sriharinmn.triage.data.detail.EventKind
import io.github.sriharinmn.triage.data.detail.EventTag
import io.github.sriharinmn.triage.data.detail.IssueEvent
import io.github.sriharinmn.triage.data.detail.TagSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class TagsContentTest {
  private val browser = TagSummary("browser", "Chrome 128", 858, 1100)
  private val route = EventTag("route", "POST /api/checkout")

  private fun event(tags: List<EventTag> = emptyList()) =
    IssueEvent(
      id = "e1",
      dateCreated = null,
      release = null,
      environment = null,
      stacks = emptyList(),
      message = null,
      breadcrumbs = emptyList(),
      tags = tags,
      context = EventContext(),
    )

  /** Both requests answered: the issue's summary and the Recommended event. */
  private val loaded =
    IssueDetailUiState(issueId = "4201", isLoadingSummary = false, isLoadingEvent = false, events = mapOf(EventKind.Recommended to event()))

  /** A cached issue opened offline: the fresh issue and the event both failed, so the tags were never asked for. */
  private val offline =
    IssueDetailUiState(
      issueId = "4201",
      isLoadingSummary = false,
      summaryError = DetailError.Network,
      isLoadingEvent = false,
      eventError = DetailError.Network,
    )

  @Test
  fun whileTheIssueAndItsEventLoad_aSpinnerShows() {
    assertEquals(TagsContent.Loading, IssueDetailUiState(issueId = "4201").tagsTabContent())
    assertEquals(TagsContent.Loading, offline.copy(isLoadingEvent = true, eventError = null).tagsTabContent())
  }

  @Test
  fun offline_withBothFailed_theTabSaysWhy_notThatThereAreNoTags() {
    assertEquals(TagsContent.Failed(DetailError.Network), offline.tagsTabContent())
  }

  @Test
  fun theIssuesError_comesBeforeTheEvents() {
    assertEquals(TagsContent.Failed(DetailError.RateLimited(30)), offline.copy(summaryError = DetailError.RateLimited(30)).tagsTabContent())
    assertEquals(TagsContent.Failed(DetailError.Server), offline.copy(summaryError = null, eventError = DetailError.Server).tagsTabContent())
  }

  @Test
  fun anIssueThatFailedToRefresh_withAnEventWithoutTags_saysWhy() {
    // The tags load only after the fresh issue, so they never came; "no tags" would be a guess.
    assertEquals(TagsContent.Failed(DetailError.Network), loaded.copy(summaryError = DetailError.Network).tagsTabContent())
  }

  @Test
  fun aRejectedToken_showsNothing_becauseTheReconnectBannerSaysWhy() {
    assertEquals(TagsContent.Hidden, offline.copy(summaryError = DetailError.Unauthorized).tagsTabContent())
    assertEquals(TagsContent.Hidden, offline.copy(summaryError = null, eventError = DetailError.Forbidden).tagsTabContent())
  }

  @Test
  fun loadedWithoutAny_saysThereAreNoTags() {
    assertEquals(TagsContent.Empty, loaded.copy(tags = emptyList()).tagsTabContent())
    // Nothing asked for and nothing wrong (a cached issue whose fresh copy hasn't come): not a failure.
    assertEquals(TagsContent.Empty, loaded.tagsTabContent())
  }

  @Test
  fun theIssuesTags_showWithTheirTopValues() {
    val withBoth = loaded.copy(tags = listOf(browser), events = mapOf(EventKind.Recommended to event(listOf(route))))

    assertEquals(TagsContent.Distribution(listOf(browser)), withBoth.tagsTabContent())
  }

  @Test
  fun withoutTheIssuesTags_theEventsOwnShow_evenOffline() {
    val eventWithTags = mapOf(EventKind.Recommended to event(listOf(route)))

    assertEquals(TagsContent.EventTags(listOf(route)), loaded.copy(events = eventWithTags).tagsTabContent())
    assertEquals(TagsContent.EventTags(listOf(route)), loaded.copy(events = eventWithTags, summaryError = DetailError.Network).tagsTabContent())
  }

  @Test
  fun anEventWithoutTags_whileTheIssuesTagsLoad_showsASpinner() {
    assertEquals(TagsContent.Loading, loaded.copy(isLoadingTags = true).tagsTabContent())
  }

  @Test
  fun aRetryOfTheIssue_withATaglessEvent_showsASpinner() {
    assertEquals(TagsContent.Loading, loaded.copy(isLoadingSummary = true).tagsTabContent())
  }

  @Test
  fun tagsThatFailed_withAFreshIssue_saySo() {
    assertEquals(TagsContent.Failed(DetailError.Network), loaded.copy(tagsError = DetailError.Network).tagsTabContent())
  }

  @Test
  fun anEventThatIsMissingOrTooLarge_isNotATagsFailure() {
    assertEquals(TagsContent.Empty, loaded.copy(eventError = DetailError.NotFound).tagsTabContent())
    assertEquals(TagsContent.Empty, loaded.copy(eventError = DetailError.TooLarge).tagsTabContent())
  }

  @Test
  fun anIssueThatIsTooLarge_failsTheTab() {
    assertEquals(TagsContent.Failed(DetailError.TooLarge), loaded.copy(summaryError = DetailError.TooLarge).tagsTabContent())
  }
}
