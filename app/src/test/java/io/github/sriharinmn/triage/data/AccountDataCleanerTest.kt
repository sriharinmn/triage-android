package io.github.sriharinmn.triage.data

import io.github.sriharinmn.triage.data.account.AccountRepository
import io.github.sriharinmn.triage.data.account.SentryHost
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.InboxTab
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.issues.LoadResult
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.testing.EU_ORG
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountStore
import io.github.sriharinmn.triage.testing.InMemoryInboxSettingsStore
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.issueDto
import io.github.sriharinmn.triage.testing.projectDto
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Connecting starts clean even if the previous account vanished without a Disconnect (e.g. a lost Keystore key). */
class AccountDataCleanerTest {
  private val api = FakeSentryApi()
  private val accountStore = InMemoryAccountStore()
  private val cache = InMemoryIssueCache()
  private val issues = IssueRepository(api, cache)
  private val settings = InMemoryInboxSettingsStore()
  private val cleaner = AccountDataCleaner(issues, settings)

  // Wired as AppContainer does.
  private val accounts = AccountRepository(api, accountStore, forgetPreviousAccountData = cleaner::forgetAccountData)

  @Test
  fun connect_dropsThePreviousAccountsIssuesFiltersAndRateLimit() = runTest {
    api.issuesHandler = { ApiResult.Success(Page(listOf(issueDto("1")), null)) }
    issues.refresh(TEST_ACCOUNT, InboxFilter())
    api.projectsResult = ApiResult.Success(listOf(projectDto("101")))
    issues.refreshProjects(TEST_ACCOUNT)
    settings.save(InboxFilter(tab = InboxTab.Regressed, projectId = "101"))
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(600)) }
    issues.refresh(TEST_ACCOUNT, InboxFilter())

    accounts.connect(SentryHost.Cloud, "sntryu_new", EU_ORG)

    assertTrue(cache.observeIssues(InboxFilter().listKey).first().isEmpty())
    assertNull(cache.listState(InboxFilter().listKey))
    assertTrue(issues.projects.first().isEmpty())
    assertEquals(InboxFilter(), settings.current)
    val connected = checkNotNull(accountStore.current) { "the new account wasn't saved" }
    assertEquals("beta-gmbh", connected.orgSlug)
    api.issuesHandler = { ApiResult.Success(Page(emptyList(), null)) }
    val calls = api.issueCalls.size
    assertEquals(LoadResult.Success, issues.refresh(connected, InboxFilter()))
    assertEquals(calls + 1, api.issueCalls.size)
  }

  @Test
  fun connect_whenForgettingFails_throwsAndSavesNothing() = runTest {
    cache.clearError = IOException("disk")

    val thrown = runCatching { accounts.connect(SentryHost.Cloud, "sntryu_new", EU_ORG) }.exceptionOrNull()

    assertTrue("expected IOException, got $thrown", thrown is IOException)
    assertNull(accountStore.current)
    assertEquals(0, accountStore.saveCount)
  }
}
