package io.github.sriharinmn.triage.data

import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.issues.toIssue
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.InMemoryInboxSettingsStore
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.issueDto
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectorTest {
  private val api = FakeSentryApi()
  private val accountStore = InMemoryAccountStore(TEST_ACCOUNT)
  private val cache = InMemoryIssueCache()
  private val settings = InMemoryInboxSettingsStore(InboxFilter(tab = InboxTab.Regressed))
  private val disconnector =
    Disconnector(AccountRepository(api, accountStore), AccountDataCleaner(IssueRepository(api, cache), settings))

  @Test
  fun disconnect_forgetsTheCacheTheSettingsAndTheAccount() = runTest {
    cache.replaceList("k", listOf(issueDto("1").toIssue()), null, Instant.EPOCH)

    disconnector.disconnect()

    assertTrue(cache.observeIssues("k").first().isEmpty())
    assertEquals(InboxFilter(), settings.current)
    assertNull(accountStore.current)
  }

  @Test
  fun aCacheFailure_keepsTheAccountSoTheUserCanRetry() = runTest {
    cache.clearError = IOException("disk")

    val thrown = runCatching { disconnector.disconnect() }.exceptionOrNull()

    assertTrue("expected IOException, got $thrown", thrown is IOException)
    assertNotNull(accountStore.current)
  }
}
