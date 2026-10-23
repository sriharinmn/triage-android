package io.github.sriharinmn.triage.data.detail

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.issues.InboxFilter
import io.github.sriharinmn.triage.data.issues.IssueRepository
import io.github.sriharinmn.triage.data.issues.toIssue
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.EventDto
import io.github.sriharinmn.triage.remote.IssueDetailDto
import io.github.sriharinmn.triage.remote.SentryJson
import io.github.sriharinmn.triage.remote.TagDetailDto
import io.github.sriharinmn.triage.remote.TagValueDto
import io.github.sriharinmn.triage.testing.DetailCall
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryIssueCache
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import io.github.sriharinmn.triage.testing.issueDto
import io.github.sriharinmn.triage.testing.readFixture
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueDetailRepositoryTest {
  private val api = FakeSentryApi()
  private val cache = InMemoryIssueCache()
  private val clock = TestClock()
  private val gate = RateLimitGate(clock::now)
  private val repository = IssueDetailRepository(api, cache, gate)
  private val detailDto = SentryJson.decodeFromString<IssueDetailDto>(readFixture("issue_detail.json"))
  private val jsEvent = SentryJson.decodeFromString<EventDto>(readFixture("event_js.json"))

  private fun tagOf(key: String, value: String = "v-$key") =
    TagDetailDto(key = key, totalValues = 10, topValues = listOf(TagValueDto(value = value, count = 4)))

  private fun whats() = api.detailCalls.map { it.what }

  @Test
  fun cachedSummary_comesFromTheInboxCache() = runTest {
    cache.replaceList("k", listOf(issueDto("4201").toIssue()), null, clock.now())

    assertEquals("DEMO-4201", repository.cachedSummary("4201")?.shortId)
    assertNull(repository.cachedSummary("missing"))
    assertTrue(api.detailCalls.isEmpty())
  }

  @Test
  fun cachedSummary_whenTheCacheCannotBeRead_isNull() = runTest {
    cache.replaceList("k", listOf(issueDto("4201").toIssue()), null, clock.now())
    cache.readError = IOException("disk")

    assertNull(repository.cachedSummary("4201"))
  }

  @Test
  fun summary_loadsTheIssueForTheAccount() = runTest {
    api.issueDetailHandler = { ApiResult.Success(detailDto) }

    val result = repository.summary(TEST_ACCOUNT, "4201", cached = null)

    assertEquals("TRIAGE-DEMO-1A", (result as DetailResult.Success).value.shortId)
    assertEquals(listOf(DetailCall("https://de.sentry.io", "sntryu_test", "example-org", "4201", "issue")), api.detailCalls)
  }

  @Test
  fun summary_passesTheCachedSummaryOn() = runTest {
    api.issueDetailHandler = { ApiResult.Success(detailDto) }
    val cached = issueDto("4201").copy(isUnhandled = true).toIssue().toSummary()

    val result = repository.summary(TEST_ACCOUNT, "4201", cached)

    assertTrue((result as DetailResult.Success).value.isUnhandled)
  }

  @Test
  fun event_asksForTheChosenKindAndMapsIt() = runTest {
    api.eventHandler = { _, _ -> ApiResult.Success(jsEvent) }

    val result = repository.event(TEST_ACCOUNT, "4201", EventKind.Latest)

    assertEquals(listOf("event:latest"), whats())
    assertEquals("TypeError", (result as DetailResult.Success).value.stacks.single().type)
  }

  @Test
  fun apiErrors_mapToDetailErrors() = runTest {
    val cases =
      listOf(
        ApiError.Http(404) to DetailError.NotFound,
        ApiError.Unauthorized to DetailError.Unauthorized,
        ApiError.Forbidden to DetailError.Forbidden,
        ApiError.Http(500) to DetailError.Server,
        ApiError.Parse to DetailError.Server,
        ApiError.TooLarge to DetailError.TooLarge,
        ApiError.Network("offline") to DetailError.Network,
      )
    for ((apiError, expected) in cases) {
      api.eventHandler = { _, _ -> ApiResult.Failure(apiError) }
      assertEquals(DetailResult.Failure(expected), repository.event(TEST_ACCOUNT, "4201", EventKind.Recommended))
    }
  }

  @Test
  fun aRateLimit_holdsLaterCallsWithoutAskingSentry() = runTest {
    api.issueDetailHandler = { ApiResult.Failure(ApiError.RateLimited(30)) }
    api.eventHandler = { _, _ -> ApiResult.Success(jsEvent) }

    assertEquals(DetailResult.Failure(DetailError.RateLimited(30)), repository.summary(TEST_ACCOUNT, "4201", null))
    clock.advance(Duration.ofSeconds(10))
    assertEquals(DetailResult.Failure(DetailError.RateLimited(20)), repository.event(TEST_ACCOUNT, "4201", EventKind.Latest))
    assertEquals(listOf("issue"), whats())

    clock.advance(Duration.ofSeconds(20))
    assertTrue(repository.event(TEST_ACCOUNT, "4201", EventKind.Latest) is DetailResult.Success)
  }

  @Test
  fun theGateIsSharedWithTheInbox() = runTest {
    val inbox = IssueRepository(api, cache, clock::now, gate)
    api.issuesHandler = { ApiResult.Failure(ApiError.RateLimited(30)) }
    inbox.refresh(TEST_ACCOUNT, InboxFilter())

    assertEquals(DetailResult.Failure(DetailError.RateLimited(30)), repository.event(TEST_ACCOUNT, "4201", EventKind.Latest))
    assertTrue(api.detailCalls.isEmpty())
  }

  @Test
  fun tags_loadsTheChosenKeysAndKeepsTheirOrder() = runTest {
    api.tagHandler = { key -> ApiResult.Success(tagOf(key)) }

    val result = repository.tags(TEST_ACCOUNT, "4201", detailDto.tags.map { it.key })

    val tags = (result as DetailResult.Success).value
    assertEquals(listOf("environment", "release", "os", "browser", "level"), tags.map { it.key })
    assertEquals("v-os", tags[2].topValue)
    assertEquals(setOf("tag:environment", "tag:release", "tag:os", "tag:browser", "tag:level"), whats().toSet())
  }

  @Test
  fun tags_leaveOutTagsThatFailOrHaveNoValues() = runTest {
    api.tagHandler = { key ->
      when (key) {
        "release" -> ApiResult.Failure(ApiError.Http(500))
        "os" -> ApiResult.Success(TagDetailDto(key = "os"))
        else -> ApiResult.Success(tagOf(key))
      }
    }

    val result = repository.tags(TEST_ACCOUNT, "4201", detailDto.tags.map { it.key })

    assertEquals(listOf("environment", "browser", "level"), (result as DetailResult.Success).value.map { it.key })
  }

  @Test
  fun tags_failOnlyWhenEveryRequestFails() = runTest {
    api.tagHandler = { ApiResult.Failure(ApiError.Http(500)) }

    assertEquals(
      DetailResult.Failure(DetailError.Server),
      repository.tags(TEST_ACCOUNT, "4201", listOf("environment", "release")),
    )
  }

  @Test
  fun tags_withoutKeys_askNothing() = runTest {
    assertEquals(DetailResult.Success(emptyList<TagSummary>()), repository.tags(TEST_ACCOUNT, "4201", emptyList()))
    assertTrue(api.detailCalls.isEmpty())
  }
}
