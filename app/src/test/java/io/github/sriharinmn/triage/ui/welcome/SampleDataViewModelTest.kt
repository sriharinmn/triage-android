package io.github.sriharinmn.triage.ui.welcome

import io.github.sriharinmn.triage.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SampleDataViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun itStartsIdle() {
    assertEquals(SampleDataStatus.Idle, SampleDataViewModel {}.status.value)
  }

  @Test
  fun aTap_startsTheDemoOnce_evenIfTappedAgainWhileItStarts() {
    val gate = CompletableDeferred<Unit>()
    var starts = 0
    val vm = SampleDataViewModel {
      starts++
      gate.await()
    }

    vm.onTrySampleData()
    vm.onTrySampleData()

    assertEquals(1, starts)
    assertEquals(SampleDataStatus.Starting, vm.status.value)
    gate.complete(Unit)
    // On success the demo account appears and the signed-out screens (with this ViewModel) go; it never goes back to Idle.
    assertEquals(SampleDataStatus.Starting, vm.status.value)
  }

  @Test
  fun aFailure_saysSo_andCanBeTriedAgain() {
    var fail = true
    var starts = 0
    val vm = SampleDataViewModel {
      starts++
      if (fail) throw IOException("full")
    }

    vm.onTrySampleData()
    assertEquals(SampleDataStatus.Failed, vm.status.value)

    fail = false
    vm.onTrySampleData()
    assertEquals(2, starts)
    assertEquals(SampleDataStatus.Starting, vm.status.value)
  }
}
