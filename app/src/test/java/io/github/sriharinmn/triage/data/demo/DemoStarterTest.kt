package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountPrefsStore
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.InMemoryDemoStateStore
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class DemoStarterTest {
  private val clock = TestClock(Instant.parse("2026-11-16T03:30:00Z"))
  private val accountStore = InMemoryAccountStore(TEST_ACCOUNT)
  private val prefs = InMemoryAccountPrefsStore(initial = "someone-else")
  private val demoStore = InMemoryDemoStateStore()
  private val steps = mutableListOf<String>()
  private val accounts =
    AccountRepository(FakeSentryApi(), accountStore, forgetPreviousAccountData = {
      steps += "forget"
      prefs.clear()
    })

  @Test
  fun start_forgetsThePreviousAccount_writesAFreshDemo_makesSamMe_thenStoresTheDemoAccount() = runTest {
    val demo = DemoSentryApi(demoStore, clock::now)

    DemoStarter(accounts, demo, prefs).start()

    assertEquals(listOf("forget"), steps)
    assertEquals(DemoAccount, accountStore.current)
    assertEquals(clock.now().toEpochMilli(), demoStore.current?.startedAt)
    assertEquals("1001", prefs.current)
  }

  @Test
  fun ifTheDemoCantBeWritten_noAccountIsStored() = runTest {
    val brokenStore =
      object : DemoStateStore {
        override suspend fun load(): DemoState? = null

        override suspend fun save(state: DemoState) = throw IOException("full")

        override suspend fun clear() = Unit
      }

    try {
      DemoStarter(accounts, DemoSentryApi(brokenStore, clock::now), prefs).start()
      fail("expected an IOException")
    } catch (_: IOException) {
      // The button shows that it failed.
    }

    // The previous account's data was forgotten first, as on every connect; its stored account stays until replaced.
    assertNotNull(accountStore.current)
    assertEquals(TEST_ACCOUNT, accountStore.current)
  }
}
