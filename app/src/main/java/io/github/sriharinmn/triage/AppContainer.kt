package io.github.sriharinmn.triage

import android.content.Context
import androidx.work.WorkManager
import io.github.sriharinmn.triage.data.AccountDataCleaner
import io.github.sriharinmn.triage.data.AccountScoped
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.data.account.DataStoreAccountStore
import io.github.sriharinmn.triage.data.account.KeystoreTokenCipher
import io.github.sriharinmn.triage.data.account.accountDataStore
import io.github.sriharinmn.triage.data.account.asAccountState
import io.github.sriharinmn.triage.data.actions.IssueActionRepository
import io.github.sriharinmn.triage.data.actions.PendingActions
import io.github.sriharinmn.triage.data.alerts.AlertCheckRunner
import io.github.sriharinmn.triage.data.alerts.AlertChecker
import io.github.sriharinmn.triage.data.alerts.AlertScheduler
import io.github.sriharinmn.triage.data.alerts.AlertSettingsStore
import io.github.sriharinmn.triage.data.alerts.AlertStateStore
import io.github.sriharinmn.triage.data.alerts.DataStoreAlertSettingsStore
import io.github.sriharinmn.triage.data.alerts.DataStoreAlertStateStore
import io.github.sriharinmn.triage.data.alerts.NotificationActionSender
import io.github.sriharinmn.triage.data.alerts.WorkManagerAlertWork
import io.github.sriharinmn.triage.data.alerts.alertSettingsDataStore
import io.github.sriharinmn.triage.data.alerts.alertStateDataStore
import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import io.github.sriharinmn.triage.data.appearance.AppearanceStore
import io.github.sriharinmn.triage.data.appearance.DataStoreAppearanceStore
import io.github.sriharinmn.triage.data.appearance.appearanceDataStore
import io.github.sriharinmn.triage.data.appearance.markWelcomeSeenOnceConnected
import io.github.sriharinmn.triage.data.demo.DataStoreDemoStateStore
import io.github.sriharinmn.triage.data.demo.DemoRoutingSentryApi
import io.github.sriharinmn.triage.data.demo.DemoSentryApi
import io.github.sriharinmn.triage.data.demo.DemoStarter
import io.github.sriharinmn.triage.data.demo.demoDataStore
import io.github.sriharinmn.triage.data.demo.kickDemoWhenAlertsTurnOn
import io.github.sriharinmn.triage.data.detail.IssueDetailRepository
import io.github.sriharinmn.triage.data.issues.IssueCache
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.members.MembersRepository
import io.github.sriharinmn.triage.data.settings.AccountPrefsStore
import io.github.sriharinmn.triage.data.settings.DataStoreAccountPrefsStore
import io.github.sriharinmn.triage.data.settings.DataStoreInboxSettingsStore
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore
import io.github.sriharinmn.triage.data.settings.accountPrefsDataStore
import io.github.sriharinmn.triage.data.settings.inboxSettingsDataStore
import io.github.sriharinmn.triage.local.RoomIssueCache
import io.github.sriharinmn.triage.local.TriageDatabase
import io.github.sriharinmn.triage.remote.OkHttpSentryApi
import io.github.sriharinmn.triage.remote.SentryApi
import io.github.sriharinmn.triage.ui.alerts.AlertNotifier
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Manual dependency injection: one instance per process, owned by [TriageApplication]. */
class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  /** Lives as long as the process, for state that outlives every screen. */
  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** Theme and first-launch choices for the whole phone; never forgotten with the account. */
  val appearance: AppearanceStore = DataStoreAppearanceStore(appContext.appearanceDataStore)

  /**
   * [appearance], read once at app start and shared; null until the file has been read. MainActivity holds its first
   * frame for it, so the theme never flashes.
   */
  val appearanceSettings: StateFlow<AppearanceSettings?> =
    appearance.settings.stateIn<AppearanceSettings?>(applicationScope, SharingStarted.Eagerly, null)

  private val okHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      // Caps the whole call (including redirects) so a slow server can't hang a screen indefinitely.
      .callTimeout(45, TimeUnit.SECONDS)
      // Never follow a redirect from https down to http (or across schemes) with the token attached.
      .followSslRedirects(false)
      .build()

  private val demoStateStore = DataStoreDemoStateStore(appContext.demoDataStore)

  /** The pretend Sentry behind "Try with sample data"; calls for the demo's account go to it. */
  val demoApi = DemoSentryApi(demoStateStore)

  val sentryApi: SentryApi =
    DemoRoutingSentryApi(real = OkHttpSentryApi(okHttpClient, userAgent = "Triage-Android/${BuildConfig.VERSION_NAME}"), demo = demoApi)

  // Room opens the database lazily, on the first query.
  private val database = TriageDatabase.create(appContext)

  /** One hold for every Sentry call: Sentry rate-limits the token, not one screen. Background checks share it too. */
  val rateLimitGate = RateLimitGate()

  private val issueCache: IssueCache = RoomIssueCache(database)

  val issueRepository = IssueRepository(sentryApi, issueCache, gate = rateLimitGate)

  val issueDetailRepository = IssueDetailRepository(sentryApi, issueCache, rateLimitGate)

  val inboxSettings: InboxSettingsStore = DataStoreInboxSettingsStore(appContext.inboxSettingsDataStore)

  private val accountPrefs: AccountPrefsStore = DataStoreAccountPrefsStore(appContext.accountPrefsDataStore)

  /** Triage's notifications: alerts, and the Undo and failure states of notification actions. */
  val alertNotifier = AlertNotifier(appContext)

  /** The periodic check and the notification actions waiting out their Undo window, in WorkManager. */
  val alertWork = WorkManagerAlertWork { WorkManager.getInstance(appContext) }

  val alertSettings: AlertSettingsStore = DataStoreAlertSettingsStore(appContext.alertSettingsDataStore)

  val alertState: AlertStateStore = DataStoreAlertStateStore(appContext.alertStateDataStore)

  private val issueActions = IssueActionRepository(sentryApi, rateLimitGate)

  /**
   * Issue actions wait out their Undo window here, on the app's scope, so leaving a screen never cancels one. A resolve
   * or archive that goes through also takes down that issue's alert notification.
   */
  val pendingActions =
    PendingActions(issueActions, issueRepository, applicationScope, onIssueClosed = alertNotifier::cancelIssue)

  val members = MembersRepository(sentryApi, rateLimitGate, accountPrefs)

  // Background work reads the stored account directly: in a process WorkManager just started, nothing has read it yet.
  private val accountStore = DataStoreAccountStore(appContext.accountDataStore, KeystoreTokenCipher())

  /** Runs each background check; AlertCheckWorker calls it. */
  val alertCheckRunner =
    AlertCheckRunner(
      accounts = accountStore.account,
      settingsStore = alertSettings,
      stateStore = alertState,
      knownProjectIds = { issueRepository.projects.first().map { it.id } },
      checker = AlertChecker(sentryApi, rateLimitGate),
      poster = alertNotifier,
    )

  /** Sends Resolve and Archive from notifications once their Undo window is over; NotificationActionWorker calls it. */
  val notificationActions = NotificationActionSender(accountStore::read, issueActions, issueRepository, alertNotifier)

  private val accountDataCleaner =
    AccountDataCleaner(
      issueRepository,
      inboxSettings,
      others =
        listOf(
          // First, so no queued notification action goes out with a token that is being forgotten.
          AccountScoped { alertWork.cancelAll() },
          AccountScoped { pendingActions.clear() },
          AccountScoped { members.clear() },
          AccountScoped { accountPrefs.clear() },
          // Clears alert_state and takes down Triage's notifications; a check still running saves nothing. It must run
          // before alertSettings.clear(): clearing the settings restarts the baseline counter at 0, so the stored
          // alert_state.baselineFor has to be gone first, or a reconnect could match it and skip its baseline.
          AccountScoped { alertCheckRunner.forget() },
          AccountScoped { alertSettings.clear() },
          // Leaving the demo, or connecting a real account, forgets everything the demo saved.
          AccountScoped { demoApi.clear() },
        ),
    )

  val accountRepository =
    AccountRepository(api = sentryApi, store = accountStore, forgetPreviousAccountData = accountDataCleaner::forgetAccountData)

  val accountState: StateFlow<AccountState> = accountRepository.account.asAccountState(applicationScope)

  val disconnector = Disconnector(accountRepository, accountDataCleaner)

  /** Starts the demo from Welcome or Connect. */
  val demoStarter = DemoStarter(accountRepository, demoApi, accountPrefs)

  private val alertScheduler =
    AlertScheduler(
      connected = accountState.filterIsInstance<AccountState.Ready>().map { it.account != null },
      settings = alertSettings.settings,
      work = alertWork,
    )

  /**
   * Creates the notification channels and keeps the periodic check in line with the account and the alert settings. Called
   * once the container exists, so WorkManager (whose workers come from the container) never starts before it. In the demo
   * it also tells the pretend Sentry when alerts are switched on.
   */
  fun startAlerts() {
    alertNotifier.createChannels()
    alertScheduler.start(applicationScope)
    applicationScope.launch { kickDemoWhenAlertsTurnOn(accountState, alertSettings.settings, demoApi::onAlertsTurnedOn) }
  }

  /** Get started on the welcome: saved on the app's scope, so leaving the app at once can't cancel it. */
  fun markWelcomeSeen() {
    applicationScope.launch {
      try {
        appearance.markWelcomeSeen()
      } catch (_: IOException) {
        // The welcome shows once more on the next launch without an account; nothing else depends on it.
      }
    }
  }

  /** Marks the welcome seen once an account is connected ([markWelcomeSeenOnceConnected]). Called once, at app start. */
  fun startWelcomeTracking() {
    applicationScope.launch { markWelcomeSeenOnceConnected(accountState, appearance) }
  }
}
