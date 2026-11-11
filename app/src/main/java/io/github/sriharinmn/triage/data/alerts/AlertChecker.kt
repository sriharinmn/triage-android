package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.detail.call
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.toInstantOrNull
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.IssueListParams
import io.github.sriharinmn.triage.remote.SentryApi
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One issue a check found, with what its notification shows. It lives only in memory: the stores keep ids. */
data class AlertFinding(
  val issueId: String,
  val shortId: String,
  val title: String,
  val projectSlug: String,
  val kind: AlertKind,
  val eventCount: Long,
  val userCount: Long,
)

/** How one check went. */
sealed interface CheckOutcome {
  /**
   * Sentry answered every query. [findings] (strongest first) is empty on a baseline. [capped] lists the kinds whose
   * answer filled the whole first page, so more may exist than were seen. [next] is the state to save; its backlog is
   * still the previous one, for the caller to add to or post.
   */
  data class Checked(val findings: List<AlertFinding>, val capped: Set<AlertKind>, val next: AlertState) : CheckOutcome

  /** 401 or 403: the token can't read issues any more. */
  data object AuthProblem : CheckOutcome

  /** Offline, a timeout, a 429 (or its hold), a server error, or an answer too large or unreadable: try next period. */
  data object Failed : CheckOutcome
}

/**
 * One background check: asks Sentry about each switched-on kind with the inbox tabs' own searches (first page only,
 * through the app-wide rate-limit gate) and compares the answers with what the previous check saw. No Android code, so
 * it is tested against the fake Sentry.
 */
class AlertChecker(private val api: SentryApi, private val gate: RateLimitGate) {
  suspend fun check(
    account: Account,
    settings: AlertSettings,
    state: AlertState,
    /** The projects the inbox knows (from the cache), to work out which ones are switched on. */
    knownProjectIds: List<String>,
    now: Instant,
  ): CheckOutcome {
    // Empty: every project is on, so no project filter (which also covers projects the cache hasn't seen yet).
    // Null: every project is switched off, so there is nothing to ask about.
    val projectIds: List<String>? =
      when {
        settings.projectsOff.isEmpty() -> emptyList()
        // Without the project list there is no telling which projects are on.
        knownProjectIds.isEmpty() -> return CheckOutcome.Failed
        else -> knownProjectIds.filter { it !in settings.projectsOff }.ifEmpty { null }
      }
    val pages = mutableMapOf<AlertKind, List<IssueDto>>()
    if (projectIds != null) {
      // A kind that's switched off isn't asked about at all.
      for (kind in settings.kinds.sorted()) {
        val params =
          IssueListParams(
            query = query(kind, state.lastSuccessAt, now),
            projectIds = projectIds,
            environment = null,
            sort = SORT_BY_DATE,
            limit = PAGE_SIZE,
          )
        when (val result = gate.call { api.listIssues(account.apiBaseUrl, account.token, account.orgSlug, params, cursor = null) }) {
          is DetailResult.Success -> pages[kind] = result.value.items
          is DetailResult.Failure ->
            return if (result.error in AUTH_ERRORS) CheckOutcome.AuthProblem else CheckOutcome.Failed
        }
      }
    }
    return compare(settings, state, pages, now)
  }

  private fun compare(
    settings: AlertSettings,
    state: AlertState,
    pages: Map<AlertKind, List<IssueDto>>,
    now: Instant,
  ): CheckOutcome.Checked {
    val baseline = state.baselineFor != settings.baselineRequest
    val lookbackStart = now - NEW_LOOKBACK
    val newPage = pages[AlertKind.New].orEmpty()
    val regressedPage = pages[AlertKind.Regressed].orEmpty()
    val escalatingPage = pages[AlertKind.Escalating].orEmpty()
    // Older entries can't come back: the New search never looks further back than this.
    val recentNew = state.alertedNew.filterValues { it > lookbackStart }

    val found = LinkedHashMap<String, AlertFinding>()
    fun add(issues: List<IssueDto>, kind: AlertKind) {
      for (issue in issues) {
        val earlier = found[issue.id]
        // One alert per issue, with its strongest kind.
        if (earlier == null || kind > earlier.kind) found[issue.id] = issue.toFinding(kind)
      }
    }
    add(newPage.filter { it.id !in recentNew }, AlertKind.New)
    // Only a state that wasn't there last time: an issue that stopped being regressed and regressed again alerts again.
    add(regressedPage.filter { it.id !in state.regressed }, AlertKind.Regressed)
    add(escalatingPage.filter { it.id !in state.escalating }, AlertKind.Escalating)

    val next =
      AlertState(
        lastSuccessAt = now,
        lastAttemptFailed = false,
        baselineFor = settings.baselineRequest,
        regressed = regressedPage.mapTo(mutableSetOf()) { it.id },
        escalating = escalatingPage.mapTo(mutableSetOf()) { it.id },
        alertedNew =
          (recentNew + newPage.associate { it.id to (it.firstSeen.toInstantOrNull() ?: now) }).filterValues { it > lookbackStart },
        // A baseline only means "don't alert on what already existed": issues held back by quiet hours came from earlier
        // checks and still become their summary. A backlog older than the lookback cap is stale, so it goes. A kind
        // switched off no longer shows in the summary.
        backlog =
          if (baseline && !(state.lastSuccessAt != null && state.lastSuccessAt >= lookbackStart)) {
            emptyMap()
          } else {
            state.backlog.filterValues { it in settings.kinds }
          },
        authProblemNotified = false,
      )
    return CheckOutcome.Checked(
      findings = if (baseline) emptyList() else found.values.sortedByDescending { it.kind },
      capped = pages.filterValues { it.size >= PAGE_SIZE }.keys,
      next = next,
    )
  }

  private fun query(kind: AlertKind, lastSuccessAt: Instant?, now: Instant): String =
    when (kind) {
      AlertKind.New -> "${InboxTab.Unresolved.query} firstSeen:>${SENTRY_TIME.format(newSince(lastSuccessAt, now))}"
      AlertKind.Regressed -> InboxTab.Regressed.query
      AlertKind.Escalating -> InboxTab.Escalating.query
    }

  /**
   * New looks back to the last successful check, with 10 minutes of overlap for issues Sentry was slow to list, but never
   * more than 24 hours (a phone idle for days doesn't get days of alerts).
   */
  private fun newSince(lastSuccessAt: Instant?, now: Instant): Instant {
    val earliest = now - NEW_LOOKBACK
    return (lastSuccessAt?.minus(NEW_OVERLAP) ?: earliest).coerceIn(earliest, now)
  }

  private fun IssueDto.toFinding(kind: AlertKind): AlertFinding =
    AlertFinding(
      issueId = id,
      shortId = shortId,
      title = title,
      projectSlug = project.slug,
      kind = kind,
      eventCount = count,
      userCount = userCount,
    )

  internal companion object {
    /** Sentry's first page; a full one means there may be more than were seen. */
    const val PAGE_SIZE = 100

    const val SORT_BY_DATE = "date"

    val NEW_LOOKBACK: Duration = Duration.ofHours(24)

    val NEW_OVERLAP: Duration = Duration.ofMinutes(10)

    /** Sentry's search takes ISO-8601 times with an offset, e.g. `firstSeen:>2026-11-11T10:35:00+00:00`. */
    val SENTRY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneOffset.UTC)

    val AUTH_ERRORS = setOf(DetailError.Unauthorized, DetailError.Forbidden)
  }
}
