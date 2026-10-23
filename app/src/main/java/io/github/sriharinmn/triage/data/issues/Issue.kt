package io.github.sriharinmn.triage.data.issues

import java.time.Instant

/** Sentry's event level for an issue; [Unknown] covers anything Sentry adds later. */
enum class IssueLevel {
  Fatal,
  Error,
  Warning,
  Info,
  Debug,
  Unknown;

  companion object {
    fun fromApi(value: String?): IssueLevel =
      when (value?.lowercase()) {
        "fatal" -> Fatal
        "error" -> Error
        "warning" -> Warning
        "info" -> Info
        "debug" -> Debug
        else -> Unknown
      }
  }
}

/** The status label shown on a row; ongoing issues have none. */
enum class IssueBadge {
  New,
  Regressed,
  Escalating,
}

/** One Sentry issue as the inbox shows it. */
data class Issue(
  val id: String,
  val shortId: String,
  val title: String,
  /** Where it happened, e.g. "cartTotal(shop:cart)"; null when Sentry has none. */
  val culprit: String?,
  val level: IssueLevel,
  val badge: IssueBadge?,
  val isUnhandled: Boolean,
  val eventCount: Long,
  val userCount: Long,
  val firstSeen: Instant?,
  val lastSeen: Instant?,
  val permalink: String?,
  val projectId: String,
  val projectSlug: String,
  /** Events per hour over the last 24 hours, oldest first; empty when Sentry sent no stats. */
  val hourlyEvents: List<Long>,
)
