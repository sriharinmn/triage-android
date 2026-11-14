package io.github.sriharinmn.triage.data.demo

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * What the demo saves (DataStore file `demo`): what the tester changed and what has happened so far. Counts, times,
 * charts and events are worked out from it, the content and the clock, never stored. Times are epoch milliseconds.
 */
@Serializable
data class DemoState(
  val startedAt: Long,
  /** Happenings up to here have been applied. */
  val processedUntil: Long,
  /** How many happenings have been applied; picks the next one's kind. */
  val happeningCount: Int = 0,
  /** How many issues have arrived; picks the next arrival and its id. */
  val arrivalCount: Int = 0,
  /** The latest alerts-on happening, once one was asked for. */
  val kickAt: Long? = null,
  /** Each project's next short-id number. */
  val nextShortNumber: Map<String, Int> = emptyMap(),
  val issues: List<DemoIssueState> = emptyList(),
)

@Serializable
enum class DemoStatus {
  Unresolved,
  Resolved,
  Archived,
}

/** A stretch without events while the issue was resolved; open (no [to]) while it still is. */
@Serializable
data class DemoQuietSpan(val from: Long, val to: Long? = null)

/** One demo issue's saved facts. */
@Serializable
data class DemoIssueState(
  val id: String,
  val shortId: String,
  /** The content's key (DemoIssueSpec.key). Arrivals reuse content, so several issues can share one. */
  val contentKey: String,
  val firstSeen: Long,
  val status: DemoStatus = DemoStatus.Unresolved,
  val archivedAt: Long? = null,
  val regressedAt: Long? = null,
  val escalatedAt: Long? = null,
  /** Up for review since; null once reviewed, resolved or archived. */
  val inboxSince: Long? = null,
  val assigneeId: String? = null,
  val quietSpans: List<DemoQuietSpan> = emptyList(),
  val bursts: List<Long> = emptyList(),
)

internal val Instant.epochMs: Long
  get() = toEpochMilli()

internal fun instant(ms: Long): Instant = Instant.ofEpochMilli(ms)

/** Sentry's window for "new", "regressed", "escalating" and "for review". */
internal val DEMO_WEEK: Duration = Duration.ofDays(7)

/** Ids of issues there from the start count up from here; arrivals from [ARRIVAL_ID_BASE]. Both look like Sentry's ids. */
internal const val STARTING_ID_BASE = 5_820_100_001L

internal const val ARRIVAL_ID_BASE = 5_820_200_001L
