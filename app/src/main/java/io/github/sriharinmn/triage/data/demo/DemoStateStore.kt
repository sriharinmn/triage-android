package io.github.sriharinmn.triage.data.demo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Where the demo's [DemoState] is kept. */
interface DemoStateStore {
  /** The saved demo, or null if there is none or it can't be read as one. Throws an IOException if the file can't be read. */
  suspend fun load(): DemoState?

  suspend fun save(state: DemoState)

  suspend fun clear()
}

internal val Context.demoDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "demo",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The demo as one JSON string in DataStore file `demo`: a few KB, rewritten whole on every change. */
class DataStoreDemoStateStore(private val dataStore: DataStore<Preferences>) : DemoStateStore {
  override suspend fun load(): DemoState? {
    val json = dataStore.data.first()[STATE] ?: return null
    return try {
      DemoJson.decodeFromString(DemoState.serializer(), json)
    } catch (_: IllegalArgumentException) {
      // SerializationException is an IllegalArgumentException: the saved text isn't a demo this version can read.
      null
    }
  }

  override suspend fun save(state: DemoState) {
    dataStore.edit { it[STATE] = DemoJson.encodeToString(DemoState.serializer(), state) }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  private companion object {
    val STATE = stringPreferencesKey("state")
    // Defaults are written too, so a later version that changes a default still reads old demos as they were saved.
    val DemoJson = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }
  }
}
