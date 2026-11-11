package io.github.sriharinmn.triage

import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.ui.alerts.DeepLink
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStartTest {
  private val plainInbox = Start(stack = listOf(Inbox), inboxTab = null)

  @Test
  fun anIssueLinkForThisOrg_opensTheIssueOnTopOfTheInbox() {
    val start = startFor(DeepLink.OpenIssue("example-org", "42"), "example-org")

    assertEquals(Start(stack = listOf(Inbox, IssueDetail("42")), inboxTab = null), start)
  }

  @Test
  fun anInboxLinkForThisOrg_opensTheInboxOnThatTab() {
    val start = startFor(DeepLink.OpenInbox("example-org", InboxTab.Escalating), "example-org")

    assertEquals(Start(stack = listOf(Inbox), inboxTab = InboxTab.Escalating), start)
  }

  @Test
  fun aLinkForAnotherOrg_isIgnored() {
    assertEquals(plainInbox, startFor(DeepLink.OpenIssue("other-org", "42"), "example-org"))
    assertEquals(plainInbox, startFor(DeepLink.OpenInbox("other-org", InboxTab.Regressed), "example-org"))
  }

  @Test
  fun noLink_startsOnThePlainInbox() {
    assertEquals(plainInbox, startFor(null, "example-org"))
  }
}
