package io.github.sriharinmn.triage.ui.alerts

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.alerts.ActionNotifier
import io.github.sriharinmn.triage.data.alerts.AlertAction
import io.github.sriharinmn.triage.data.alerts.AlertFinding
import io.github.sriharinmn.triage.data.alerts.AlertKind
import io.github.sriharinmn.triage.data.alerts.AlertPost
import io.github.sriharinmn.triage.data.alerts.AlertPoster
import io.github.sriharinmn.triage.data.alerts.NotificationActionRequest
import io.github.sriharinmn.triage.data.alerts.inboxTab
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.ui.format.compactCount
import io.github.sriharinmn.triage.ui.format.toQuantity

/**
 * Triage's notifications. One channel per kind, so sound and importance can be set per kind in Android's settings, plus
 * an "Alert status" channel. Single issues carry Resolve / Archive / Open; summaries open the inbox; an action shows
 * "Resolving DEMO-3… Undo" and, if it fails, why. Titles come only from a check's answer in memory and are hidden on the
 * lock screen. Every PendingIntent is immutable and explicit. [channelPrefix] gives device tests their own channels.
 */
class AlertNotifier(private val context: Context, private val channelPrefix: String = "") : AlertPoster, ActionNotifier {
  private val manager = NotificationManagerCompat.from(context)

  /** Creates Triage's channels (or updates their names); safe to call on every start. */
  fun createChannels() {
    manager.createNotificationChannelsCompat(
      listOf(
        channel(AlertKind.New.channel, R.string.alerts_channel_new),
        channel(AlertKind.Regressed.channel, R.string.alerts_channel_regressed),
        channel(AlertKind.Escalating.channel, R.string.alerts_channel_escalating),
        channel(STATUS_CHANNEL, R.string.alerts_channel_status, R.string.alerts_channel_status_description),
      )
    )
  }

  override fun canPost(): Boolean = manager.areNotificationsEnabled()

  override fun post(orgSlug: String, post: AlertPost) {
    when (post) {
      is AlertPost.Singles -> {
        post.findings.forEach { notify(issueTag(it.issueId), ISSUE_NOTIFICATION, buildSingle(orgSlug, it)) }
        // Stacks every issue notification under one heading.
        notify(null, GROUP_SUMMARY_NOTIFICATION, buildGroupSummary())
      }
      // One at a time: a newer summary replaces the older one.
      is AlertPost.Summary -> notify(null, SUMMARY_NOTIFICATION, buildSummary(orgSlug, post))
    }
  }

  override fun postAuthProblem() {
    notify(null, AUTH_NOTIFICATION, buildAuthProblem())
  }

  override fun cancelAll() {
    manager.cancelAll()
  }

  /** Resolve or Archive was tapped: "Resolving DEMO-3… Undo" in its place, silently, keeping the original for [restore]. */
  fun showPending(request: NotificationActionRequest) {
    notify(issueTag(request.issueId), ISSUE_NOTIFICATION, buildPending(request, activeIssueNotification(request.issueId)))
  }

  /** Undo: the original notification comes back without ringing again; if it can't be found, the issue's goes. */
  fun restore(issueId: String) {
    val original =
      activeIssueNotification(issueId)?.let { BundleCompat.getParcelable(it.extras, EXTRA_ORIGINAL, Notification::class.java) }
    if (original != null) notify(issueTag(issueId), ISSUE_NOTIFICATION, original) else cancelIssue(issueId)
  }

  override fun showSending(request: NotificationActionRequest) {
    notify(issueTag(request.issueId), ISSUE_NOTIFICATION, statusBuilder(progressTitle(request)).build())
  }

  override fun showFailed(request: NotificationActionRequest, error: DetailError) {
    notify(issueTag(request.issueId), ISSUE_NOTIFICATION, buildFailed(request, error))
  }

  override fun cancelIssue(issueId: String) {
    val tag = issueTag(issueId)
    manager.cancel(tag, ISSUE_NOTIFICATION)
    // Otherwise the group heading stays behind on its own. Android may still list the one just cancelled, so skip it.
    val othersLeft = manager.activeNotifications.any { it.tag != tag && it.tag?.startsWith(ISSUE_TAG_PREFIX) == true }
    if (!othersLeft) manager.cancel(GROUP_SUMMARY_NOTIFICATION)
  }

  internal fun buildSingle(orgSlug: String, finding: AlertFinding): Notification {
    val open = openIssue(orgSlug, finding.issueId)
    val resolve = NotificationActionRequest(finding.issueId, finding.shortId, orgSlug, AlertAction.Resolve)
    val counts =
      listOf(
        context.resources.getQuantityString(R.plurals.issue_events, finding.eventCount.toQuantity(), compactCount(finding.eventCount)),
        context.resources.getQuantityString(R.plurals.issue_users, finding.userCount.toQuantity(), compactCount(finding.userCount)),
      ).joinToString(" · ")
    val channel = channelId(finding.kind.channel)
    return NotificationCompat.Builder(context, channel)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.alerts_single_title, context.getString(finding.kind.label), finding.shortId))
      .setContentText(finding.title)
      .setSubText(finding.projectSlug)
      .setStyle(NotificationCompat.BigTextStyle().bigText("${finding.title}\n$counts"))
      .setContentIntent(open)
      .addAction(R.drawable.ic_check, context.getString(R.string.action_resolve), broadcast(NotificationIntents.ACTION_RESOLVE, resolve))
      .addAction(
        R.drawable.ic_archive,
        context.getString(R.string.action_archive),
        broadcast(NotificationIntents.ACTION_ARCHIVE, resolve.copy(action = AlertAction.Archive)),
      )
      .addAction(R.drawable.ic_open_in_new, context.getString(R.string.alerts_open), open)
      .setAutoCancel(true)
      // Undo puts this back by posting it again; it must not ring a second time.
      .setOnlyAlertOnce(true)
      .setGroup(GROUP_KEY)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion(channel, mapOf(finding.kind to 1)))
      .build()
  }

  internal fun buildSummary(orgSlug: String, summary: AlertPost.Summary): Notification {
    val channel = channelId(summary.strongest.channel)
    return NotificationCompat.Builder(context, channel)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(countsText(summary.counts, summary.projectSlug))
      .setContentText(context.getString(R.string.alerts_summary_text))
      .setContentIntent(activity(openInboxIntent(context, orgSlug, summary.strongest.inboxTab)))
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion(channel, summary.counts))
      .build()
  }

  /** "Resolving DEMO-3…" with Undo; [original] (the notification it replaces) rides along for [restore]. */
  internal fun buildPending(request: NotificationActionRequest, original: Notification?): Notification =
    statusBuilder(progressTitle(request))
      .addAction(R.drawable.ic_arrow_back, context.getString(R.string.action_undo), broadcast(NotificationIntents.ACTION_UNDO, request))
      .apply { if (original != null) addExtras(bundleOf(EXTRA_ORIGINAL to original)) }
      .build()

  internal fun buildFailed(request: NotificationActionRequest, error: DetailError): Notification {
    val open = openIssue(request.orgSlug, request.issueId)
    val builder =
      if (error == DetailError.NotFound) {
        statusBuilder(context.getString(R.string.action_error_not_found, request.shortId))
      } else {
        val reason = failureReason(error)
        statusBuilder(context.getString(failedTitle(request.action), request.shortId))
          .setContentText(reason)
          .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
      }
    return builder
      .setContentIntent(open)
      .addAction(R.drawable.ic_open_in_new, context.getString(R.string.alerts_open), open)
      .setAutoCancel(true)
      .build()
  }

  internal fun buildAuthProblem(): Notification =
    NotificationCompat.Builder(context, channelId(STATUS_CHANNEL))
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.alerts_auth_title))
      .setContentText(context.getString(R.string.alerts_auth_text))
      .setContentIntent(activity(launchIntent(context)))
      .setAutoCancel(true)
      .build()

  private fun buildGroupSummary(): Notification =
    NotificationCompat.Builder(context, channelId(STATUS_CHANNEL))
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.alerts_group_title))
      .setGroup(GROUP_KEY)
      .setGroupSummary(true)
      // The issues ring; their heading never does.
      .setSilent(true)
      .setAutoCancel(true)
      .build()

  /** An issue's notification while its action goes out or after it failed: same place in the shade, no sound. */
  private fun statusBuilder(title: String): NotificationCompat.Builder =
    NotificationCompat.Builder(context, channelId(STATUS_CHANNEL))
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setGroup(GROUP_KEY)
      .setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

  /** What the lock screen shows instead: "2 new issues", no titles and no project. */
  private fun publicVersion(channel: String, counts: Map<AlertKind, Int>): Notification =
    NotificationCompat.Builder(context, channel)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(countsText(counts, projectSlug = null))
      .build()

  /** "12 new issues in triage-demo, 1 regressed": the first kind in full (with the project, if any), then the others. */
  private fun countsText(counts: Map<AlertKind, Int>, projectSlug: String?): String =
    AlertKind.entries
      .filter { (counts[it] ?: 0) > 0 }
      .mapIndexed { index, kind ->
        val count = counts.getValue(kind)
        if (index > 0) {
          context.resources.getQuantityString(kind.shortCountPlural, count, count)
        } else {
          val lead = context.resources.getQuantityString(kind.countPlural, count, count)
          if (projectSlug == null) lead else context.getString(R.string.alerts_summary_in_project, lead, projectSlug)
        }
      }
      .joinToString(", ")

  private fun progressTitle(request: NotificationActionRequest): String =
    context.getString(
      when (request.action) {
        AlertAction.Resolve -> R.string.alerts_resolving
        AlertAction.Archive -> R.string.alerts_archiving
      },
      request.shortId,
    )

  @StringRes
  private fun failedTitle(action: AlertAction): Int =
    when (action) {
      AlertAction.Resolve -> R.string.action_resolve_failed
      AlertAction.Archive -> R.string.action_archive_failed
    }

  /** The same plain words as the in-app failure notice (`ui/common/ActionNotices.kt`). */
  private fun failureReason(error: DetailError): String =
    when (error) {
      DetailError.Forbidden -> context.getString(R.string.action_error_forbidden)
      DetailError.Unauthorized -> context.getString(R.string.inbox_error_unauthorized)
      is DetailError.RateLimited -> context.getString(R.string.inbox_error_rate_limited, error.retryAfterSeconds.coerceAtLeast(1))
      DetailError.Network -> context.getString(R.string.inbox_error_network)
      DetailError.NotFound,
      DetailError.Server,
      DetailError.TooLarge -> context.getString(R.string.inbox_error_server)
    }

  private fun openIssue(orgSlug: String, issueId: String): PendingIntent = activity(openIssueIntent(context, orgSlug, issueId))

  private fun activity(intent: Intent): PendingIntent =
    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

  private fun broadcast(intentAction: String, request: NotificationActionRequest): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      0,
      notificationActionIntent(context, intentAction, request),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun activeIssueNotification(issueId: String): Notification? =
    manager.activeNotifications.firstOrNull { it.tag == issueTag(issueId) && it.id == ISSUE_NOTIFICATION }?.notification

  // Android won't show a post while notifications are blocked or the permission is missing; canPost() checks first.
  @SuppressLint("MissingPermission")
  private fun notify(tag: String?, id: Int, notification: Notification) {
    if (canPost()) manager.notify(tag, id, notification)
  }

  private fun channel(base: String, @StringRes name: Int, @StringRes description: Int? = null): NotificationChannelCompat =
    NotificationChannelCompat.Builder(channelId(base), NotificationManagerCompat.IMPORTANCE_DEFAULT)
      .setName(context.getString(name))
      .apply { if (description != null) setDescription(context.getString(description)) }
      .build()

  private fun channelId(base: String): String = channelPrefix + base

  internal companion object {
    const val STATUS_CHANNEL = "alerts_status"
    const val GROUP_KEY = "io.github.sriharinmn.triage.ISSUES"
    const val ISSUE_TAG_PREFIX = "issue:"

    /** Each issue's notification is (its tag, this id); the others have no tag. */
    const val ISSUE_NOTIFICATION = 1
    const val GROUP_SUMMARY_NOTIFICATION = 2
    const val SUMMARY_NOTIFICATION = 3
    const val AUTH_NOTIFICATION = 4

    const val EXTRA_ORIGINAL = "io.github.sriharinmn.triage.ORIGINAL"

    fun issueTag(issueId: String): String = ISSUE_TAG_PREFIX + issueId
  }
}

private val AlertKind.channel: String
  get() =
    when (this) {
      AlertKind.New -> "alerts_new"
      AlertKind.Regressed -> "alerts_regressed"
      AlertKind.Escalating -> "alerts_escalating"
    }

@get:StringRes
private val AlertKind.label: Int
  get() =
    when (this) {
      AlertKind.New -> R.string.issue_badge_new
      AlertKind.Regressed -> R.string.issue_badge_regressed
      AlertKind.Escalating -> R.string.issue_badge_escalating
    }

@get:PluralsRes
private val AlertKind.countPlural: Int
  get() =
    when (this) {
      AlertKind.New -> R.plurals.alerts_count_new
      AlertKind.Regressed -> R.plurals.alerts_count_regressed
      AlertKind.Escalating -> R.plurals.alerts_count_escalating
    }

@get:PluralsRes
private val AlertKind.shortCountPlural: Int
  get() =
    when (this) {
      AlertKind.New -> R.plurals.alerts_count_new_short
      AlertKind.Regressed -> R.plurals.alerts_count_regressed_short
      AlertKind.Escalating -> R.plurals.alerts_count_escalating_short
    }
