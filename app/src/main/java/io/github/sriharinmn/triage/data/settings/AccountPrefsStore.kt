package io.github.sriharinmn.triage.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Small per-account choices, forgotten with the account: which organization member the user is. */
interface AccountPrefsStore {
  /** The user's own Sentry user id, answered once in "Which one is you?"; null until then. */
  val myUserId: Flow<String?>

  suspend fun saveMyUserId(userId: String?)

  suspend fun clear()
}

internal val Context.accountPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "account_prefs",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreAccountPrefsStore(private val dataStore: DataStore<Preferences>) : AccountPrefsStore {
  override val myUserId: Flow<String?> =
    dataStore.data
      // A convenience: if it can't be read, the assignee sheet simply asks again.
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { it[MY_USER_ID] }

  override suspend fun saveMyUserId(userId: String?) {
    dataStore.edit { if (userId != null) it[MY_USER_ID] = userId else it.remove(MY_USER_ID) }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  private companion object {
    val MY_USER_ID = stringPreferencesKey("my_user_id")
  }
}
