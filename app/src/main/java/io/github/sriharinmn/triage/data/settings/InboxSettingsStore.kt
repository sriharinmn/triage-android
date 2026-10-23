package io.github.sriharinmn.triage.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueSort
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Remembers the inbox's tab and filters between launches. */
interface InboxSettingsStore {
  val filter: Flow<InboxFilter>

  suspend fun save(filter: InboxFilter)

  suspend fun clear()
}

internal val Context.inboxSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "inbox_settings",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreInboxSettingsStore(private val dataStore: DataStore<Preferences>) : InboxSettingsStore {
  override val filter: Flow<InboxFilter> =
    dataStore.data
      // These are conveniences: unreadable settings fall back to the defaults.
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { prefs ->
        InboxFilter(
          tab = InboxTab.entries.firstOrNull { it.name == prefs[Keys.TAB] } ?: InboxTab.Unresolved,
          projectId = prefs[Keys.PROJECT_ID],
          environment = prefs[Keys.ENVIRONMENT],
          sort = IssueSort.entries.firstOrNull { it.name == prefs[Keys.SORT] } ?: IssueSort.LastSeen,
        )
      }

  override suspend fun save(filter: InboxFilter) {
    dataStore.edit { prefs ->
      prefs[Keys.TAB] = filter.tab.name
      prefs[Keys.SORT] = filter.sort.name
      val projectId = filter.projectId
      if (projectId != null) prefs[Keys.PROJECT_ID] = projectId else prefs.remove(Keys.PROJECT_ID)
      val environment = filter.environment
      if (environment != null) prefs[Keys.ENVIRONMENT] = environment else prefs.remove(Keys.ENVIRONMENT)
    }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  private object Keys {
    val TAB = stringPreferencesKey("tab")
    val PROJECT_ID = stringPreferencesKey("project_id")
    val ENVIRONMENT = stringPreferencesKey("environment")
    val SORT = stringPreferencesKey("sort")
  }
}
