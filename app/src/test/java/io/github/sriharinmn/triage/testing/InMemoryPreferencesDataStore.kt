package io.github.sriharinmn.triage.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A preferences DataStore kept in memory, for JVM tests of code built on DataStore. A file-backed DataStore can't
 * replace its file on Windows in plain JVM tests (it falls back to File.renameTo), and file I/O is DataStore's own concern.
 */
class InMemoryPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
  private val state = MutableStateFlow(initial)
  private val writes = Mutex()

  override val data: Flow<Preferences> = state

  override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
    writes.withLock { transform(state.value).also { state.value = it } }
}
