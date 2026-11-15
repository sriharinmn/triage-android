package io.github.sriharinmn.triage.data.demo

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.sriharinmn.triage.testing.InMemoryPreferencesDataStore
import io.github.sriharinmn.triage.testing.UnreadableDataStore
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DataStoreDemoStateStoreTest {
  private val state = freshDemoState(Instant.parse("2026-11-16T03:30:00Z"))

  @Test
  fun nothingSaved_readsAsNoDemo() = runTest {
    assertNull(DataStoreDemoStateStore(InMemoryPreferencesDataStore()).load())
  }

  @Test
  fun aSavedDemo_readsBackTheSame_andClearForgetsIt() = runTest {
    val store = DataStoreDemoStateStore(InMemoryPreferencesDataStore())

    store.save(state)
    assertEquals(state, store.load())

    store.clear()
    assertNull(store.load())
  }

  @Test
  fun somethingThatIsntADemo_readsAsNoDemo() = runTest {
    val garbage = mutablePreferencesOf(stringPreferencesKey("state") to "{not json")
    assertNull(DataStoreDemoStateStore(InMemoryPreferencesDataStore(garbage)).load())
  }

  @Test
  fun anUnreadableFile_throws() = runTest {
    try {
      DataStoreDemoStateStore(UnreadableDataStore()).load()
      fail("expected an IOException")
    } catch (_: IOException) {
      // DemoSentryApi turns this into a network-style error the screens already explain.
    }
  }
}
