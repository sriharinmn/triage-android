package io.github.sriharinmn.triage.data.demo.content

import java.time.Duration
import java.time.Instant

const val SHOP_ANDROID = "shop-android"
const val SHOP_WEB = "shop-web"
const val SHOP_API = "shop-api"

/** One of the sample org's projects. Its issues' short ids are `<shortIdPrefix>-<n>`, counting up from [firstShortNumber]. */
data class DemoProject(
  val id: String,
  val slug: String,
  val name: String,
  val platform: String,
  val shortIdPrefix: String,
  val firstShortNumber: Int,
  /** Oldest first. */
  val releases: List<DemoRelease>,
  val environments: List<String> = listOf("production", "staging"),
)

/** A release that ships [at] after the demo starts (negative: before it). */
data class DemoRelease(val version: String, val shortVersion: String, val at: Duration)

/** The release that was out at [time] in a demo that started at [startedAt]: the newest one shipped by then, else the oldest. */
fun DemoProject.releaseAt(time: Instant, startedAt: Instant): DemoRelease =
  releases.lastOrNull { startedAt + it.at <= time } ?: releases.first()

/** Someone in the sample org. */
data class DemoPerson(val userId: String, val name: String, val email: String)

/** How a starting issue stands when the demo starts; each [ago] counts back from the start. */
sealed interface DemoStart {
  /** Unresolved: "new" while it's less than 7 days old, then "ongoing". */
  data object Unresolved : DemoStart

  data class Regressed(val ago: Duration) : DemoStart

  data class Escalating(val ago: Duration) : DemoStart

  /** Archived until escalating. */
  data class Archived(val ago: Duration) : DemoStart

  data class Resolved(val ago: Duration) : DemoStart
}

/** One sample issue: what Sentry would show for it. */
data class DemoIssueSpec(
  /** Unique and stable: the saved demo state refers to content by it. */
  val key: String,
  val projectSlug: String,
  val title: String,
  val culprit: String,
  /** fatal, error, warning or info. */
  val level: String,
  val isUnhandled: Boolean,
  /** Average events an hour; the hour of day and a burst change it (see IssueTraffic). */
  val ratePerHour: Double,
  /** Users as a share of events. */
  val userShare: Double,
  /** Tag key → (value, share) pairs, most common first, shares adding up to 1. Always has `environment`. */
  val tags: Map<String, List<Pair<String, Double>>>,
  val event: DemoEventSpec,
)

/** An issue that is there when the demo starts. */
data class DemoStartingIssue(
  val spec: DemoIssueSpec,
  /** How long before the start the issue was first seen. */
  val firstSeenAgo: Duration,
  val start: DemoStart = DemoStart.Unresolved,
  /** A [DemoPerson.userId], or null for unassigned. */
  val assigneeId: String? = null,
)

/** The issue's event: what the detail screen's tabs show. */
data class DemoEventSpec(
  /** Sentry's order: the cause first, the exception that was thrown last. Empty for a message or thread-only event. */
  val exceptions: List<DemoException> = emptyList(),
  /** An ANR's threads; the crashed one's frames are shown. */
  val threads: List<DemoThread> = emptyList(),
  val message: String? = null,
  /** Oldest first. */
  val breadcrumbs: List<DemoBreadcrumb> = emptyList(),
  val request: DemoRequest? = null,
  /** Sentry context name (device, os, app, browser, runtime) → its fields. */
  val contexts: Map<String, Map<String, String>> = emptyMap(),
  /** Makes the event's user `user-<n>` (1..254), or null for no user. */
  val userIndex: Int? = null,
)

data class DemoException(val type: String, val value: String, val module: String? = null, val frames: List<DemoFrame>)

data class DemoThread(val id: Int, val name: String, val crashed: Boolean, val frames: List<DemoFrame>)

/** One stack frame. Frames are listed oldest call first, as Sentry sends them. */
data class DemoFrame(
  val function: String,
  val module: String? = null,
  val filename: String? = null,
  val lineNo: Int? = null,
  val colNo: Int? = null,
  val inApp: Boolean = true,
  /** (line number, source line) pairs around [lineNo]. */
  val context: List<Pair<Int, String>> = emptyList(),
)

/** A breadcrumb [secondsBefore] the event. Types `http` and `navigation` read [data]; others show [message]. */
data class DemoBreadcrumb(
  val secondsBefore: Long,
  val category: String,
  val message: String? = null,
  val level: String = "info",
  val type: String = "default",
  val data: Map<String, String> = emptyMap(),
)

data class DemoRequest(val method: String, val url: String)
