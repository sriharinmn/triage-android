package io.github.sriharinmn.triage.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreAccountStoreRetryTest {
  /** Not encryption; just reversible, so the store's decrypt step has something to undo. */
  private class ReversingCipher : TokenCipher {
    override fun encrypt(plaintext: String) = plaintext.reversed()

    override fun decrypt(ciphertext: String) = ciphertext.reversed()
  }

  /** Fails its first read with an IOException, then serves [prefs]. */
  private class FlakyDataStore(private val prefs: Preferences) : DataStore<Preferences> {
    private var failuresLeft = 1

    override val data: Flow<Preferences> = flow {
      if (failuresLeft > 0) {
        failuresLeft--
        throw IOException("disk hiccup")
      }
      emit(prefs)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(prefs)
  }

  @Test
  fun aReadError_readsAsDisconnected_andTheFlowKeepsGoing() = runBlocking {
    val prefs =
      preferencesOf(
        DataStoreAccountStore.Keys.API_BASE_URL to "https://de.sentry.io",
        DataStoreAccountStore.Keys.WEB_BASE_URL to "https://example-org.sentry.io",
        DataStoreAccountStore.Keys.TOKEN to "sntryu_test".reversed(),
        DataStoreAccountStore.Keys.ORG_SLUG to "example-org",
        DataStoreAccountStore.Keys.ORG_NAME to "Example Org",
      )
    val store = DataStoreAccountStore(FlakyDataStore(prefs), ReversingCipher())

    val values = store.account.take(2).toList()

    assertNull(values[0])
    assertEquals("example-org", values[1]?.orgSlug)
    assertEquals("sntryu_test", values[1]?.token)
  }
}
