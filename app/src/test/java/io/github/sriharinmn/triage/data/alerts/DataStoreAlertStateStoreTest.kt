package io.github.sriharinmn.triage.data.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreAlertStateStoreTest {
  private val dataStore = InMemoryPreferencesDataStore()
  private val store = DataStoreAlertStateStore(dataStore)
  private val checkedAt = Instant.parse("2026-11-11T10:42:00Z")
  private val full =
    AlertState(
      lastSuccessAt = checkedAt,
      lastAttemptFailed = true,
      baselineFor = 3,
      regressed = setOf("2", "4"),
      escalating = setOf("5"),
      alertedNew = mapOf("1" to Instant.parse("2026-11-11T10:30:00Z")),
      backlog = mapOf("6" to AlertKind.Escalating, "7" to AlertKind.New),
      authProblemNotified = true,
      heldUntil = Instant.parse("2026-11-11T10:50:00Z"),
    )

  @Test
  fun nothingSaved_readsAsEmpty() = runTest {
    assertEquals(AlertState(), store.state.first())
  }

  @Test
  fun save_roundTripsEverything() = runTest {
    store.save(full)

    assertEquals(full, store.state.first())
  }

  @Test
  fun save_replacesThePreviousState() = runTest {
    store.save(full)
    val next = AlertState(lastSuccessAt = checkedAt.plusSeconds(900), baselineFor = 3, regressed = setOf("4"))

    store.save(next)

    assertEquals(next, store.state.first())
  }

  @Test
  fun clearAuthProblem_clearsOnlyThatFlag() = runTest {
    store.save(full)

    store.clearAuthProblem()

    assertEquals(full.copy(authProblemNotified = false), store.state.first())
  }

  @Test
  fun entriesThisVersionCannotRead_areSkipped() = runTest {
    dataStore.edit {
      it[stringSetPreferencesKey("alerted_new")] = setOf("1|not-a-time", "2|1762857000000", "no-separator")
      it[stringSetPreferencesKey("backlog")] = setOf("3|Fatal", "4|Regressed")
    }

    val state = store.state.first()

    assertEquals(mapOf("2" to Instant.ofEpochMilli(1762857000000)), state.alertedNew)
    assertEquals(mapOf("4" to AlertKind.Regressed), state.backlog)
  }

  @Test
  fun clear_forgetsEverything() = runTest {
    store.save(full)

    store.clear()

    assertEquals(AlertState(), store.state.first())
  }

  @Test
  fun anUnreadableFile_readsAsEmpty() = runTest {
    assertEquals(AlertState(), DataStoreAlertStateStore(UnreadableDataStore()).state.first())
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
      // Like a real DataStore, the flow stays open for later changes.
      awaitCancellation()
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(prefs)
  }

  @Test
  fun aReadError_readsAsEmpty_thenTheFlowKeepsGoingWithTheRealState() = runTest {
    val prefs = preferencesOf(booleanPreferencesKey("last_attempt_failed") to true)
    val values = mutableListOf<AlertState>()
    val job = launch { DataStoreAlertStateStore(FlakyDataStore(prefs)).state.toList(values) }

    advanceUntilIdle()

    assertEquals(2, values.size)
    assertEquals(AlertState(), values[0])
    assertTrue(values[1].lastAttemptFailed)
    assertTrue("the flow must not complete after a read error", job.isActive)
    job.cancel()
  }
}
