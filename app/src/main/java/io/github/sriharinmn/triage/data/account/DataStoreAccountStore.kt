package io.github.sriharinmn.triage.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "account",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreAccountStore(
  private val dataStore: DataStore<Preferences>,
  private val cipher: TokenCipher,
) : AccountStore {

  override val account: Flow<Account?> =
    dataStore.data
      // An unreadable or corrupt file (CorruptionException is an IOException) reads as disconnected.
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { prefs ->
        val encryptedToken = prefs[Keys.TOKEN] ?: return@map null
        val token = decryptOrNull(encryptedToken) ?: return@map null
        Account(
          apiBaseUrl = prefs[Keys.API_BASE_URL] ?: return@map null,
          webBaseUrl = prefs[Keys.WEB_BASE_URL] ?: return@map null,
          token = token,
          orgSlug = prefs[Keys.ORG_SLUG] ?: return@map null,
          orgName = prefs[Keys.ORG_NAME] ?: return@map null,
        )
      }
      .flowOn(Dispatchers.IO)

  override suspend fun save(account: Account) {
    val encryptedToken = withContext(Dispatchers.IO) { cipher.encrypt(account.token) }
    dataStore.edit { prefs ->
      prefs[Keys.API_BASE_URL] = account.apiBaseUrl
      prefs[Keys.WEB_BASE_URL] = account.webBaseUrl
      prefs[Keys.TOKEN] = encryptedToken
      prefs[Keys.ORG_SLUG] = account.orgSlug
      prefs[Keys.ORG_NAME] = account.orgName
    }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  /** A token we can't decrypt (Keystore key wiped or invalidated, garbled value) is treated as disconnected. */
  private fun decryptOrNull(encryptedToken: String): String? =
    try {
      cipher.decrypt(encryptedToken)
    } catch (_: GeneralSecurityException) {
      null
    } catch (_: IllegalArgumentException) {
      null // bad base64
    } catch (_: ProviderException) {
      null
    }

  internal object Keys {
    val API_BASE_URL = stringPreferencesKey("api_base_url")
    val WEB_BASE_URL = stringPreferencesKey("web_base_url")
    val TOKEN = stringPreferencesKey("token_encrypted")
    val ORG_SLUG = stringPreferencesKey("org_slug")
    val ORG_NAME = stringPreferencesKey("org_name")
  }
}
