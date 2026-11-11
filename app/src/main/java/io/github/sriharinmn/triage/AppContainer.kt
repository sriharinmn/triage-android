package io.github.sriharinmn.triage

import android.content.Context
import io.github.sriharinmn.triage.data.AccountDataCleaner
import io.github.sriharinmn.triage.data.AccountScoped
import io.github.sriharinmn.triage.data.actions.IssueActionRepository
import io.github.sriharinmn.triage.data.actions.PendingActions
import io.github.sriharinmn.triage.data.members.MembersRepository
import io.github.sriharinmn.triage.data.settings.AccountPrefsStore
import io.github.sriharinmn.triage.data.settings.DataStoreAccountPrefsStore
import io.github.sriharinmn.triage.data.settings.accountPrefsDataStore
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.data.account.DataStoreAccountStore
import io.github.sriharinmn.triage.data.account.KeystoreTokenCipher
import io.github.sriharinmn.triage.data.account.accountDataStore
import io.github.sriharinmn.triage.data.account.asAccountState
import io.github.sriharinmn.triage.data.detail.IssueDetailRepository
import io.github.sriharinmn.triage.data.issues.IssueCache
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.settings.DataStoreInboxSettingsStore
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore
import io.github.sriharinmn.triage.data.settings.inboxSettingsDataStore
import io.github.sriharinmn.triage.local.RoomIssueCache
import io.github.sriharinmn.triage.local.TriageDatabase
import io.github.sriharinmn.triage.remote.OkHttpSentryApi
import io.github.sriharinmn.triage.remote.SentryApi
import androidx.work.WorkManager
import io.github.sriharinmn.triage.data.alerts.WorkManagerAlertWork
import io.github.sriharinmn.triage.ui.alerts.AlertNotifier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/** Manual dependency injection: one instance per process, owned by [TriageApplication]. */
class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  /** Lives as long as the process, for state that outlives every screen. */
  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val okHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      // Caps the whole call (including redirects) so a slow server can't hang a screen indefinitely.
      .callTimeout(45, TimeUnit.SECONDS)
      // Never follow a redirect from https down to http (or across schemes) with the token attached.
      .followSslRedirects(false)
      .build()

  val sentryApi: SentryApi = OkHttpSentryApi(okHttpClient, userAgent = "Triage-Android/${BuildConfig.VERSION_NAME}")

  // Room opens the database lazily, on the first query.
  private val database = TriageDatabase.create(appContext)

  /** One hold for every Sentry call: Sentry rate-limits the token, not one screen. */
  val rateLimitGate = RateLimitGate()

  private val issueCache: IssueCache = RoomIssueCache(database)

  val issueRepository = IssueRepository(sentryApi, issueCache, gate = rateLimitGate)

  val issueDetailRepository = IssueDetailRepository(sentryApi, issueCache, rateLimitGate)

  val inboxSettings: InboxSettingsStore = DataStoreInboxSettingsStore(appContext.inboxSettingsDataStore)

  private val accountPrefs: AccountPrefsStore = DataStoreAccountPrefsStore(appContext.accountPrefsDataStore)

  /** Issue actions wait out their Undo window here, on the app's scope, so leaving a screen never cancels one. */
  val pendingActions = PendingActions(IssueActionRepository(sentryApi, rateLimitGate), issueRepository, applicationScope)

  val members = MembersRepository(sentryApi, rateLimitGate, accountPrefs)

  /** Triage's notifications: alerts, and the Undo and failure states of notification actions. */
  val alertNotifier = AlertNotifier(appContext)

  /** The periodic check and the notification actions waiting out their Undo window, in WorkManager. */
  val alertWork = WorkManagerAlertWork { WorkManager.getInstance(appContext) }

  private val accountDataCleaner =
    AccountDataCleaner(
      issueRepository,
      inboxSettings,
      others =
        listOf(AccountScoped { pendingActions.clear() }, AccountScoped { members.clear() }, AccountScoped { accountPrefs.clear() }),
    )

  val accountRepository =
    AccountRepository(
      api = sentryApi,
      store = DataStoreAccountStore(appContext.accountDataStore, KeystoreTokenCipher()),
      forgetPreviousAccountData = accountDataCleaner::forgetAccountData,
    )

  val accountState: StateFlow<AccountState> = accountRepository.account.asAccountState(applicationScope)

  val disconnector = Disconnector(accountRepository, accountDataCleaner)
}
