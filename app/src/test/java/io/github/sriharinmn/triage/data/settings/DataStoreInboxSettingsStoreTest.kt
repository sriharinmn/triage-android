package io.github.sriharinmn.triage.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueSort
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreInboxSettingsStoreTest {
  private val dataStore = InMemoryPreferencesDataStore()
  private val store = DataStoreInboxSettingsStore(dataStore)

  @Test
  fun nothingSaved_readsTheDefaults() = runTest {
    assertEquals(InboxFilter(), store.filter.first())
  }

  @Test
  fun save_roundTrips() = runTest {
    val filter = InboxFilter(InboxTab.Escalating, projectId = "102", environment = "production", sort = IssueSort.Users)

    store.save(filter)

    assertEquals(filter, store.filter.first())
  }

  @Test
  fun choosingAllAgain_forgetsTheProjectAndEnvironment() = runTest {
    store.save(InboxFilter(projectId = "102", environment = "production"))

    store.save(InboxFilter())

    assertEquals(InboxFilter(), store.filter.first())
  }

  @Test
  fun unknownNames_fromAnOlderOrNewerApp_fallBackToDefaults() = runTest {
    dataStore.edit {
      it[stringPreferencesKey("tab")] = "Archived"
      it[stringPreferencesKey("sort")] = "Trends"
    }

    assertEquals(InboxFilter(), store.filter.first())
  }

  @Test
  fun clear_restoresTheDefaults() = runTest {
    store.save(InboxFilter(InboxTab.Regressed, projectId = "1"))

    store.clear()

    assertEquals(InboxFilter(), store.filter.first())
  }
}
