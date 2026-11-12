package io.github.sriharinmn.triage

import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.ui.alerts.DeepLink
import io.github.sriharinmn.triage.ui.inbox.InboxOpening
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStartTest {
  private val plainInbox = Start(stack = listOf(Inbox), inboxOpening = null)

  @Test
  fun anIssueLinkForThisOrg_opensTheIssueOnTopOfTheInbox() {
    val start = startFor(DeepLink.OpenIssue("example-org", "42"), "example-org")

    assertEquals(Start(stack = listOf(Inbox, IssueDetail("42")), inboxOpening = null), start)
  }

  @Test
  fun anInboxLinkForThisOrg_opensTheInboxOnThatTab() {
    val start = startFor(DeepLink.OpenInbox("example-org", InboxTab.Escalating, "42"), "example-org")

    assertEquals(Start(stack = listOf(Inbox), inboxOpening = InboxOpening(InboxTab.Escalating, "42")), start)
  }

  @Test
  fun aLinkForAnotherOrg_isIgnored() {
    assertEquals(plainInbox, startFor(DeepLink.OpenIssue("other-org", "42"), "example-org"))
    assertEquals(plainInbox, startFor(DeepLink.OpenInbox("other-org", InboxTab.Regressed, null), "example-org"))
  }

  @Test
  fun noLink_startsOnThePlainInbox() {
    assertEquals(plainInbox, startFor(null, "example-org"))
  }
}
