package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.issues.InboxTab
import java.time.Instant

/**
 * What an alert is about. A later entry is stronger: an issue found as several kinds in one check is alerted once, with
 * the strongest (Escalating > Regressed > New).
 */
enum class AlertKind {
  New,
  Regressed,
  Escalating,
}

/** The inbox tab a summary of this kind opens. Sentry has no "new" tab; new issues are on Unresolved. */
val AlertKind.inboxTab: InboxTab
  get() =
    when (this) {
      AlertKind.New -> InboxTab.Unresolved
      AlertKind.Regressed -> InboxTab.Regressed
      AlertKind.Escalating -> InboxTab.Escalating
    }

/** How often the background check runs; 15 minutes is WorkManager's minimum. */
enum class CheckInterval(val minutes: Long) {
  Minutes15(15),
  Minutes30(30),
  Hour1(60),
}

/** The user's alert choices (DataStore file `alert_settings`). */
data class AlertSettings(
  /** The master switch: off until the user turns alerts on from the inbox card or the Alerts screen. */
  val enabled: Boolean = false,
  val interval: CheckInterval = CheckInterval.Minutes15,
  val kinds: Set<AlertKind> = AlertKind.entries.toSet(),
  /** The projects switched off. Kept this way round so a project added later starts on. */
  val projectsOff: Set<String> = emptySet(),
  val quietHours: QuietHours = QuietHours(),
  /** The inbox's one-time "Get alerts…" card was answered (Turn on or Not now). */
  val cardDismissed: Boolean = false,
  /**
   * Goes up whenever alerts, a kind or a project is switched on. A check whose state answers an older number only records
   * what is there (a baseline), so issues that were already regressed or escalating don't all alert at once.
   */
  val baselineRequest: Long = 0,
)

/**
 * What the background check remembers between runs (DataStore file `alert_state`). Issue ids, kinds and times only: no
 * titles, no event data.
 */
data class AlertState(
  /** When a check last got answers from Sentry; the New search looks back from here. */
  val lastSuccessAt: Instant? = null,
  /** The latest check couldn't reach Sentry or read its answer. */
  val lastAttemptFailed: Boolean = false,
  /** The [AlertSettings.baselineRequest] the sets below were recorded for; null before the first check. */
  val baselineFor: Long? = null,
  /** Issues that were regressed at the last check. */
  val regressed: Set<String> = emptySet(),
  /** Issues that were escalating at the last check. */
  val escalating: Set<String> = emptySet(),
  /** New issues already alerted (or recorded by a baseline), with when Sentry first saw them; kept for 24 hours. */
  val alertedNew: Map<String, Instant> = emptyMap(),
  /** Issues found during quiet hours, with their strongest kind; shown as one summary when the window ends. */
  val backlog: Map<String, AlertKind> = emptyMap(),
  /** Sentry rejected the token (401 or 403) and the user was told once; checks wait for an inbox load or a reconnect. */
  val authProblemNotified: Boolean = false,
  /**
   * Sentry answered a check with 429: no check asks it anything before this. Saved because each background check
   * usually runs in a fresh process, whose in-memory rate-limit gate knows nothing of the last one.
   */
  val heldUntil: Instant? = null,
)
