package io.github.sriharinmn.triage.data.alerts

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.remote.MAX_RETRY_AFTER_SECONDS
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a check's results go: Android notifications in the app, a recorder in tests. */
interface AlertPoster {
  /** False when Android won't show Triage's notifications (blocked, or the permission isn't granted). */
  fun canPost(): Boolean

  /** Shows [post]; [orgSlug] goes into its taps, so they only ever open that org's issues. */
  fun post(orgSlug: String, post: AlertPost)

  /** "Triage can't check Sentry. Open to reconnect." */
  fun postAuthProblem()

  /** Takes down every Triage notification: the account is being forgotten. */
  fun cancelAll()
}

/**
 * Runs one background check end to end: loads the account, settings and state, asks the [checker], applies quiet hours,
 * saves the new state and posts what was found. Nothing is kept in memory between runs, so process death loses nothing.
 * Checks never overlap. A check whose account was forgotten while Sentry answered ([forget]) saves and posts nothing, like
 * a load in IssueRepository.
 */
class AlertCheckRunner(
  private val accounts: Flow<Account?>,
  private val settingsStore: AlertSettingsStore,
  private val stateStore: AlertStateStore,
  /** The ids of the projects the inbox knows, from the cache. */
  private val knownProjectIds: suspend () -> List<String>,
  private val checker: AlertChecker,
  private val poster: AlertPoster,
  private val clock: () -> Instant = Instant::now,
  /** Read at every check, so quiet hours follow the phone's current time zone. */
  private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
  /** One check at a time: the periodic run and a forced one must not both alert about the same issue. */
  private val runLock = Mutex()

  /** Makes a check's generation test and its writes one step, so [forget] can't land between them. */
  private val writeLock = Mutex()

  /** Bumped by [forget]; a check that started in an older generation belongs to a forgotten account. */
  private val generation = AtomicLong()

  suspend fun runCheck() {
    runLock.withLock {
      val startedIn = generation.get()
      val settings = settingsStore.settings.first()
      if (!settings.enabled) return
      val account = accounts.first() ?: return
      val state = stateStore.state.first()
      val now = clock()
      // Reported once already; a successful inbox load or a reconnect lets checks run again.
      if (state.authProblemNotified) return
      // Sentry asked the last check to wait; a new process doesn't know, so the saved hold says it.
      // A clock that moved back can't stretch the hold past its bound, so a farther-off saved hold is ignored.
      state.heldUntil?.let { if (now < it && Duration.between(now, it).seconds <= MAX_RETRY_AFTER_SECONDS) return }
      // Nothing found could be shown, so don't spend Sentry's requests on it.
      if (!poster.canPost()) return
      val outcome = checker.check(account, settings, state, knownProjectIds(), now)
      writeLock.withLock { finish(startedIn, account, settings, state, outcome, now) }
    }
  }

  /** Forgets the account's alert state and takes down its notifications; a check still running saves nothing. */
  suspend fun forget() {
    writeLock.withLock {
      stateStore.clear()
      // Bumped after the clear: if the clear fails, the account stays connected and its checks may still save.
      generation.incrementAndGet()
    }
    poster.cancelAll()
  }

  private suspend fun finish(
    startedIn: Long,
    account: Account,
    settings: AlertSettings,
    state: AlertState,
    outcome: CheckOutcome,
    now: Instant,
  ) {
    // The account was forgotten (a disconnect, or a new connect) while Sentry answered: this result isn't the current one's.
    if (generation.get() != startedIn) return
    // Switched off meanwhile: show nothing. Switching on again starts with a baseline anyway.
    if (!settingsStore.settings.first().enabled) return
    when (outcome) {
      CheckOutcome.AuthProblem -> {
        stateStore.save(state.copy(lastAttemptFailed = true, authProblemNotified = true))
        poster.postAuthProblem()
      }
      // Kept for the next check, which usually runs in a fresh process. The bound comes from rateLimitHoldSeconds.
      is CheckOutcome.RateLimited ->
        stateStore.save(state.copy(lastAttemptFailed = true, heldUntil = now.plusSeconds(outcome.retryAfterSeconds)))
      // Skipped quietly: the Alerts screen says the last attempt failed, and the next period tries again.
      CheckOutcome.Failed -> stateStore.save(state.copy(lastAttemptFailed = true))
      is CheckOutcome.Checked -> {
        val quiet = settings.quietHours.isQuietAt(now.atZone(zone()).toLocalTime())
        val grouped = groupAlerts(outcome.findings, outcome.capped, outcome.next.backlog, quiet)
        // Saved first: if the process dies in between, these alerts are lost rather than shown twice.
        stateStore.save(outcome.next.copy(backlog = grouped.backlog))
        grouped.post?.let { poster.post(account.orgSlug, it) }
      }
    }
  }
}
