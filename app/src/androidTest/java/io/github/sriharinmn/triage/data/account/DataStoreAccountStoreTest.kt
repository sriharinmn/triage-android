package io.github.sriharinmn.triage.data.account

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreAccountStoreTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val fileName = "account_store_test_${UUID.randomUUID()}"
  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private val dataStore =
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { context.preferencesDataStoreFile(fileName) })
  private val store = DataStoreAccountStore(dataStore, KeystoreTokenCipher(alias = "triage_test_key"))

  private val account =
    Account(
      apiBaseUrl = "https://de.sentry.io",
      webBaseUrl = "https://beta-gmbh.sentry.io",
      token = "sntryu_secret",
      orgSlug = "beta-gmbh",
      orgName = "Beta GmbH",
    )

  @After
  fun tearDown() {
    scope.cancel()
    context.preferencesDataStoreFile(fileName).delete()
    // Don't leave the test's Keystore key behind on the owner's phone.
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("triage_test_key")
  }

  @Test
  fun emptyStore_hasNoAccount() = runBlocking {
    assertNull(store.account.first())
  }

  @Test
  fun save_roundTripsAndStoresTokenEncrypted() = runBlocking {
    store.save(account)

    assertEquals(account, store.account.first())
    val raw = dataStore.data.first()[DataStoreAccountStore.Keys.TOKEN]
    assertNotNull(raw)
    assertFalse(raw!!.contains("sntryu_secret"))
  }

  @Test
  fun clear_removesAccount() = runBlocking {
    store.save(account)
    store.clear()
    assertNull(store.account.first())
  }

  @Test
  fun undecryptableToken_readsAsDisconnected() = runBlocking {
    store.save(account)
    dataStore.edit { it[DataStoreAccountStore.Keys.TOKEN] = "not-valid-base64-ciphertext!!" }

    assertNull(store.account.first())
  }

  @Test
  fun corruptFile_readsAsDisconnected() = runBlocking {
    val corruptName = "account_store_corrupt_${UUID.randomUUID()}"
    val corruptFile = context.preferencesDataStoreFile(corruptName)
    // A fresh install may not have created the datastore directory yet.
    corruptFile.parentFile?.mkdirs()
    corruptFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
    val corruptScope = CoroutineScope(Dispatchers.IO + Job())
    try {
      // No corruption handler on purpose: the raw read throws CorruptionException (an IOException).
      val corruptStore =
        DataStoreAccountStore(
          PreferenceDataStoreFactory.create(scope = corruptScope, produceFile = { corruptFile }),
          KeystoreTokenCipher(alias = "triage_test_key"),
        )

      assertNull(corruptStore.account.first())
    } finally {
      corruptScope.cancel()
      corruptFile.delete()
    }
  }
}
