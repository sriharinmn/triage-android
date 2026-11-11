package io.github.sriharinmn.triage.testing

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A preferences file that can't be read, as a corrupt one reads before DataStore's corruption handler replaces it. */
class UnreadableDataStore : DataStore<Preferences> {
  override val data: Flow<Preferences> = flow { throw CorruptionException("unreadable") }

  override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
    throw CorruptionException("unreadable")
}
