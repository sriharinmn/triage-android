package io.github.sriharinmn.triage.ui.alerts

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.sriharinmn.triage.MainActivity
import io.github.sriharinmn.triage.data.alerts.AlertAction
import io.github.sriharinmn.triage.data.alerts.NotificationActionRequest
import io.github.sriharinmn.triage.data.issues.InboxTab

/** Where a tapped notification opens the app. It applies only while the same org is connected. */
sealed interface DeepLink {
  val orgSlug: String

  /** An issue's detail, on top of the inbox. */
  data class OpenIssue(override val orgSlug: String, val issueId: String) : DeepLink

  /** The inbox on [tab] (from a summary). */
  data class OpenInbox(override val orgSlug: String, val tab: InboxTab) : DeepLink
}

/** The actions and extras of Triage's notification intents. They carry ids, the action and the org: never a token. */
internal object NotificationIntents {
  const val ACTION_OPEN_ISSUE = "io.github.sriharinmn.triage.action.OPEN_ISSUE"
  const val ACTION_OPEN_INBOX = "io.github.sriharinmn.triage.action.OPEN_INBOX"
  const val ACTION_RESOLVE = "io.github.sriharinmn.triage.action.RESOLVE"
  const val ACTION_ARCHIVE = "io.github.sriharinmn.triage.action.ARCHIVE"
  const val ACTION_UNDO = "io.github.sriharinmn.triage.action.UNDO"
  const val EXTRA_ISSUE_ID = "issue_id"
  const val EXTRA_SHORT_ID = "short_id"
  const val EXTRA_ORG_SLUG = "org_slug"
  const val EXTRA_ACTION = "action"
  const val EXTRA_TAB = "tab"
}

/** Opens [issueId]'s detail on top of the inbox, in a fresh task (whatever the app showed before). */
internal fun openIssueIntent(context: Context, orgSlug: String, issueId: String): Intent =
  Intent(context, MainActivity::class.java)
    .setAction(NotificationIntents.ACTION_OPEN_ISSUE)
    .setData(triageUri("issue", issueId))
    .putExtra(NotificationIntents.EXTRA_ISSUE_ID, issueId)
    .putExtra(NotificationIntents.EXTRA_ORG_SLUG, orgSlug)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

/** Opens the inbox on [tab], in a fresh task. */
internal fun openInboxIntent(context: Context, orgSlug: String, tab: InboxTab): Intent =
  Intent(context, MainActivity::class.java)
    .setAction(NotificationIntents.ACTION_OPEN_INBOX)
    .setData(triageUri("inbox", tab.name))
    .putExtra(NotificationIntents.EXTRA_TAB, tab.name)
    .putExtra(NotificationIntents.EXTRA_ORG_SLUG, orgSlug)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

/** Opens the app as its launcher icon does (back to where the user was, or the start). */
internal fun launchIntent(context: Context): Intent =
  Intent(context, MainActivity::class.java)
    .setAction(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_LAUNCHER)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** A Resolve, Archive or Undo tap ([intentAction]) on [request]'s notification, for [NotificationActionReceiver]. */
internal fun notificationActionIntent(context: Context, intentAction: String, request: NotificationActionRequest): Intent =
  Intent(context, NotificationActionReceiver::class.java)
    .setAction(intentAction)
    .setData(triageUri("issue", request.issueId))
    .putExtra(NotificationIntents.EXTRA_ISSUE_ID, request.issueId)
    .putExtra(NotificationIntents.EXTRA_SHORT_ID, request.shortId)
    .putExtra(NotificationIntents.EXTRA_ORG_SLUG, request.orgSlug)
    .putExtra(NotificationIntents.EXTRA_ACTION, request.action.name)

/** The deep link of an intent from [openIssueIntent] or [openInboxIntent]; null for any other intent (the launcher's). */
internal fun Intent.toDeepLink(): DeepLink? {
  val orgSlug = getStringExtra(NotificationIntents.EXTRA_ORG_SLUG) ?: return null
  return when (action) {
    NotificationIntents.ACTION_OPEN_ISSUE ->
      getStringExtra(NotificationIntents.EXTRA_ISSUE_ID)?.let { DeepLink.OpenIssue(orgSlug, it) }
    NotificationIntents.ACTION_OPEN_INBOX ->
      InboxTab.entries
        .firstOrNull { it.name == getStringExtra(NotificationIntents.EXTRA_TAB) }
        ?.let { DeepLink.OpenInbox(orgSlug, it) }
    else -> null
  }
}

/** The request in a notification action intent; null if anything is missing or the action is unknown. */
internal fun Intent.toActionRequest(): NotificationActionRequest? {
  val issueId = getStringExtra(NotificationIntents.EXTRA_ISSUE_ID) ?: return null
  val shortId = getStringExtra(NotificationIntents.EXTRA_SHORT_ID) ?: return null
  val orgSlug = getStringExtra(NotificationIntents.EXTRA_ORG_SLUG) ?: return null
  val action = AlertAction.entries.firstOrNull { it.name == getStringExtra(NotificationIntents.EXTRA_ACTION) } ?: return null
  return NotificationActionRequest(issueId, shortId, orgSlug, action)
}

/**
 * `triage://<kind>/<id>`: a distinct data URI per issue keeps each notification's PendingIntents apart, because Android
 * compares intents without their extras. Nothing handles this scheme; every intent here names its component.
 */
private fun triageUri(kind: String, id: String): Uri = Uri.Builder().scheme("triage").authority(kind).appendPath(id).build()
