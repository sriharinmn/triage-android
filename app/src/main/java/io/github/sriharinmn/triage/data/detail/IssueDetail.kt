package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.issues.IssueBadge
import io.github.sriharinmn.triage.data.issues.IssueLevel
import java.time.Instant

/** Which of an issue's events the detail screen shows; [apiId] is what Sentry's event endpoint accepts. */
enum class EventKind(val apiId: String) {
  Recommended("recommended"),
  Latest("latest"),
  Oldest("oldest"),
}

/** Whether the issue is still open; only [Resolved] and [Archived] get a label on screen. */
enum class IssueStatus {
  Unresolved,
  Resolved,
  Archived;

  companion object {
    /** Sentry's "ignored" is "Archived" in its UI; anything unknown counts as unresolved. */
    fun fromApi(value: String?): IssueStatus =
      when (value) {
        "resolved" -> Resolved
        "ignored" -> Archived
        else -> Unresolved
      }
  }
}

/** The top of the detail screen: built from the cached inbox row at first, then from Sentry's fresh issue. */
data class IssueSummary(
  val id: String,
  val shortId: String,
  val title: String,
  val culprit: String?,
  val level: IssueLevel,
  val badge: IssueBadge?,
  val isUnhandled: Boolean,
  val status: IssueStatus,
  val eventCount: Long,
  val userCount: Long,
  val firstSeen: Instant?,
  val lastSeen: Instant?,
  val permalink: String?,
  /** Events per hour over the last 24 hours, oldest first; empty when Sentry sent no stats. */
  val hourlyEvents: List<Long>,
  /** Events per day over the last 30 days, oldest first; empty until fresh data arrives. */
  val dailyEvents: List<Long>,
  val firstRelease: String?,
  val lastRelease: String?,
  /** The issue's tag keys in Sentry's order; empty until fresh data arrives. */
  val tagKeys: List<String>,
  /** Who it is assigned to: a person's name, or "#team"; null while unassigned or not yet known. */
  val assignedTo: String? = null,
  /** Sentry has it up for review (For review); false until the fresh issue arrives. */
  val isUpForReview: Boolean = false,
  /** True once this came from Sentry's fresh issue rather than the cached inbox row. */
  val isFresh: Boolean = false,
)

/** One event of an issue, reduced to what the screen shows. Lives in memory only: it can hold end users' data. */
data class IssueEvent(
  val id: String?,
  val dateCreated: Instant?,
  val release: String?,
  val environment: String?,
  /** The exceptions (the thrown one first, then its causes), or else the crashed thread's stack; empty if neither. */
  val stacks: List<EventStack>,
  /** The event's log message, if it has one. */
  val message: String?,
  /** Newest first. */
  val breadcrumbs: List<Breadcrumb>,
  val tags: List<EventTag>,
  val context: EventContext,
)

/** An exception with its frames, or a thread's stack (then [type] and [value] are null and [threadName] is set). */
data class EventStack(
  val type: String?,
  val value: String?,
  val threadName: String?,
  /** Newest call first: the crash site is the first frame. */
  val frames: List<StackFrame>,
)

/** One call in a stack. */
data class StackFrame(
  val function: String?,
  /** Java class or JS module, e.g. "com.example.shop.ui.ProfileFragment" or "shop:cart". */
  val module: String?,
  /** File name or path as the SDK sent it. */
  val file: String?,
  val lineNo: Int?,
  val colNo: Int?,
  /** True for the app's own code (Sentry's "in app"). */
  val inApp: Boolean,
  /** Source lines around [lineNo], in file order; empty when Sentry has none. */
  val context: List<ContextLine>,
)

/** One source line of a frame's context. */
data class ContextLine(val lineNo: Int, val text: String)

/** Something that happened before the event. */
data class Breadcrumb(
  val timestamp: Instant?,
  val category: String?,
  val level: IssueLevel,
  /** The message, "GET url → 200" for HTTP, or "from → to" for navigation; null when there's nothing to show. */
  val text: String?,
)

/** One tag of this event, e.g. environment = production. */
data class EventTag(val key: String, val value: String)

/** Short descriptions from the event's contexts; each is null when the event doesn't say. */
data class EventContext(
  /** "Google Pixel 8" */
  val device: String? = null,
  /** "Android 14" */
  val os: String? = null,
  /** "node v20.11.0" */
  val runtime: String? = null,
  /** "Chrome 128" */
  val browser: String? = null,
  /** "2.3.1 (231)" */
  val app: String? = null,
  val user: EventUser? = null,
  val request: EventRequest? = null,
)

/** Who hit the error, exactly as Sentry reports them. */
data class EventUser(val id: String?, val email: String?, val username: String?, val ipAddress: String?)

/** The request's method and URL only; headers, cookies and body are deliberately left out. */
data class EventRequest(val method: String?, val url: String)

/** The most common value of one tag across all of the issue's events. */
data class TagSummary(val key: String, val topValue: String, val count: Long, val total: Long) {
  /** Whole percent, 0..100. */
  val percent: Int
    get() = if (total <= 0) 0 else (count * 100 / total).toInt().coerceIn(0, 100)
}
