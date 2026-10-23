package io.github.sriharinmn.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionViewModelStoresTest {
  private val stores = SessionViewModelStores()

  @Test
  fun storeFor_returnsTheSameStoreForTheSameKey() {
    assertSame(stores.storeFor("a"), stores.storeFor("a"))
    assertNotSame(stores.storeFor("a"), stores.storeFor("b"))
  }

  @Test
  fun retainOnly_clearsOtherSessionsAndKeepsTheCurrentOne() {
    val inA = probeIn(stores.storeFor("a"))
    val inB = probeIn(stores.storeFor("b"))

    stores.retainOnly("a")

    assertTrue("b is not the current session", inB.cleared)
    assertFalse("a is the current session", inA.cleared)
    assertSame("a keeps its store, and its ViewModel", inA, probeIn(stores.storeFor("a")))
  }

  @Test
  fun retainOnly_null_clearsEverySession() {
    val inA = probeIn(stores.storeFor("a"))
    val inB = probeIn(stores.storeFor("b"))

    stores.retainOnly(null)

    assertTrue(inA.cleared)
    assertTrue(inB.cleared)
  }

  @Test
  fun aClearedSession_getsANewEmptyStore() {
    val oldStore = stores.storeFor("a")
    val old = probeIn(oldStore)
    stores.retainOnly(null)

    val newStore = stores.storeFor("a")
    val fresh = probeIn(newStore)

    assertNotSame(oldStore, newStore)
    assertNotSame("a reconnect must not get the old ViewModel back", old, fresh)
    assertFalse(fresh.cleared)
  }

  @Test
  fun onCleared_clearsEverySession() {
    val inA = probeIn(stores.storeFor("a"))

    // Clearing the activity's store is what calls ViewModel.onCleared.
    val activityStore = ViewModelStore()
    ViewModelProvider.create(activityStore, factoryOf { stores })[SessionViewModelStores::class.java]
    activityStore.clear()

    assertTrue(inA.cleared)
  }

  private fun probeIn(store: ViewModelStore): Probe = ViewModelProvider.create(store, factoryOf { Probe() })[Probe::class.java]

  private fun <V : ViewModel> factoryOf(make: () -> V) =
    object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = make() as T
    }

  private class Probe : ViewModel() {
    var cleared = false

    override fun onCleared() {
      cleared = true
    }
  }
}
