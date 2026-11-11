package io.github.sriharinmn.triage.data.alerts

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
