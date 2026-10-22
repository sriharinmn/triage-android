package io.github.sriharinmn.triage.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

class DataStoreAccountStore(
  private val dataStore: DataStore<Preferences>,
  private val cipher: TokenCipher,
) : AccountStore {

  override val account: Flow<Account?> =
    dataStore.data.map { prefs ->
      val encryptedToken = prefs[Keys.TOKEN] ?: return@map null
      // A token we can't decrypt (e.g. Keystore key wiped) is treated as disconnected.
      val token = runCatching { cipher.decrypt(encryptedToken) }.getOrNull() ?: return@map null
      Account(
        apiBaseUrl = prefs[Keys.API_BASE_URL] ?: return@map null,
        webBaseUrl = prefs[Keys.WEB_BASE_URL] ?: return@map null,
        token = token,
        orgSlug = prefs[Keys.ORG_SLUG] ?: return@map null,
        orgName = prefs[Keys.ORG_NAME] ?: return@map null,
      )
    }

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

  internal object Keys {
    val API_BASE_URL = stringPreferencesKey("api_base_url")
    val WEB_BASE_URL = stringPreferencesKey("web_base_url")
    val TOKEN = stringPreferencesKey("token_encrypted")
    val ORG_SLUG = stringPreferencesKey("org_slug")
    val ORG_NAME = stringPreferencesKey("org_name")
  }
}
