package io.github.sriharinmn.triage.ui.alerts

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sriharinmn.triage.MainActivity
import io.github.sriharinmn.triage.data.alerts.AlertAction
import io.github.sriharinmn.triage.data.alerts.NotificationActionRequest
import io.github.sriharinmn.triage.data.issues.InboxTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIntentsTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val request = NotificationActionRequest("6012345678", "DEMO-3", "example-org", AlertAction.Archive)

  @Test
  fun anIssueIntent_opensThatIssue_inAFreshTask() {
    val intent = openIssueIntent(context, "example-org", "6012345678")

    assertEquals(DeepLink.OpenIssue("example-org", "6012345678"), intent.toDeepLink())
    assertEquals(MainActivity::class.java.name, intent.component?.className)
    val freshTask = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    assertEquals(freshTask, intent.flags and freshTask)
  }

  @Test
  fun anInboxIntent_opensThatTab() {
    assertEquals(
      DeepLink.OpenInbox("example-org", InboxTab.Escalating),
      openInboxIntent(context, "example-org", InboxTab.Escalating).toDeepLink(),
    )
  }

  @Test
  fun otherIntents_haveNoDeepLink() {
    assertNull(launchIntent(context).toDeepLink())
    assertNull(Intent(context, MainActivity::class.java).toDeepLink())
    assertNull(
      openInboxIntent(context, "example-org", InboxTab.Regressed).putExtra(NotificationIntents.EXTRA_TAB, "Archived").toDeepLink()
    )
  }

  @Test
  fun anActionIntent_carriesItsRequest_toTheReceiver() {
    val intent = notificationActionIntent(context, NotificationIntents.ACTION_ARCHIVE, request)

    assertEquals(request, intent.toActionRequest())
    assertEquals(NotificationActionReceiver::class.java.name, intent.component?.className)
  }

  @Test
  fun theIntents_carryIdsTheActionAndTheOrg_only() {
    assertEquals(setOf("issue_id", "org_slug"), openIssueIntent(context, "example-org", "1").extras!!.keySet())
    assertEquals(setOf("tab", "org_slug"), openInboxIntent(context, "example-org", InboxTab.Regressed).extras!!.keySet())
    assertEquals(
      setOf("issue_id", "short_id", "org_slug", "action"),
      notificationActionIntent(context, NotificationIntents.ACTION_UNDO, request).extras!!.keySet(),
    )
  }

  @Test
  fun eachIssueAndButton_getsItsOwnPendingIntent() {
    // Android tells PendingIntents apart by everything but their extras.
    val resolve = notificationActionIntent(context, NotificationIntents.ACTION_RESOLVE, request.copy(action = AlertAction.Resolve))
    val archive = notificationActionIntent(context, NotificationIntents.ACTION_ARCHIVE, request)
    val otherIssue =
      notificationActionIntent(
        context,
        NotificationIntents.ACTION_RESOLVE,
        request.copy(issueId = "6012345679", action = AlertAction.Resolve),
      )

    assertFalse(resolve.filterEquals(archive))
    assertFalse(resolve.filterEquals(otherIssue))
    assertFalse(openIssueIntent(context, "example-org", "1").filterEquals(openIssueIntent(context, "example-org", "2")))
  }

  @Test
  fun anIncompleteActionIntent_isIgnored() {
    val withoutOrg =
      notificationActionIntent(context, NotificationIntents.ACTION_RESOLVE, request).apply {
        removeExtra(NotificationIntents.EXTRA_ORG_SLUG)
      }
    val unknownAction =
      notificationActionIntent(context, NotificationIntents.ACTION_RESOLVE, request)
        .putExtra(NotificationIntents.EXTRA_ACTION, "Delete")

    assertNull(withoutOrg.toActionRequest())
    assertNull(unknownAction.toActionRequest())
  }
}
