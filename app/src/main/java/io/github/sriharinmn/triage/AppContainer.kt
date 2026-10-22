package io.github.sriharinmn.triage

import android.content.Context
import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.DataStoreAccountStore
import io.github.sriharinmn.triage.data.account.KeystoreTokenCipher
import io.github.sriharinmn.triage.data.account.accountDataStore
import io.github.sriharinmn.triage.remote.OkHttpSentryApi
import io.github.sriharinmn.triage.remote.SentryApi
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Manual dependency injection: one instance per process, owned by [TriageApplication]. */
class AppContainer(context: Context) {
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
      store = DataStoreAccountStore(context.applicationContext.accountDataStore, KeystoreTokenCipher()),
    )
}
