package io.github.sriharinmn.triage

import android.content.Context
import io.github.sriharinmn.triage.data.Disconnector
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.AccountState
import io.github.sriharinmn.triage.data.account.DataStoreAccountStore
import io.github.sriharinmn.triage.data.account.KeystoreTokenCipher
import io.github.sriharinmn.triage.data.account.accountDataStore
import io.github.sriharinmn.triage.data.account.asAccountState
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.settings.DataStoreInboxSettingsStore
import io.github.sriharinmn.triage.data.settings.InboxSettingsStore
import io.github.sriharinmn.triage.data.settings.inboxSettingsDataStore
import io.github.sriharinmn.triage.local.RoomIssueCache
import io.github.sriharinmn.triage.local.TriageDatabase
import io.github.sriharinmn.triage.remote.OkHttpSentryApi
import io.github.sriharinmn.triage.remote.SentryApi
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

  val accountRepository =
    AccountRepository(
      api = sentryApi,
      store = DataStoreAccountStore(appContext.accountDataStore, KeystoreTokenCipher()),
    )

  val accountState: StateFlow<AccountState> = accountRepository.account.asAccountState(applicationScope)

  // Room opens the database lazily, on the first query.
  private val database = TriageDatabase.create(appContext)

  val issueRepository = IssueRepository(sentryApi, RoomIssueCache(database))

  val inboxSettings: InboxSettingsStore = DataStoreInboxSettingsStore(appContext.inboxSettingsDataStore)

  val disconnector = Disconnector(accountRepository, issueRepository, inboxSettings)
}
