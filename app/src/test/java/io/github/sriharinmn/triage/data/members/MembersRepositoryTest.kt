package io.github.sriharinmn.triage.data.members

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.detail.DetailError
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.MemberDto
import io.github.sriharinmn.triage.remote.MemberUserDto
import io.github.sriharinmn.triage.testing.FakeSentryApi
import io.github.sriharinmn.triage.testing.InMemoryAccountPrefsStore
import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import io.github.sriharinmn.triage.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MembersRepositoryTest {
  private val api = FakeSentryApi()
  private val prefs = InMemoryAccountPrefsStore()
  private val repository = MembersRepository(api, RateLimitGate(TestClock()::now), prefs)

  private fun member(id: String, name: String?, email: String?, userId: String? = "u$id") =
    MemberDto(id = id, name = name, email = email, user = userId?.let { MemberUserDto(id = it, name = name, email = email) })

  @Test
  fun members_dropInvites_andAreSortedByName() = runTest {
    api.membersResult =
      ApiResult.Success(
        listOf(member("1", "ben okafor", "ben@example.com"), member("2", null, "new@example.com", userId = null), member("3", "Ana Silva", "ana@example.com"))
      )

    val result = repository.members(TEST_ACCOUNT)

    assertEquals(
      listOf(Member("u3", "Ana Silva", "ana@example.com"), Member("u1", "ben okafor", "ben@example.com")),
      (result as DetailResult.Success).value,
    )
  }

  @Test
  fun aMemberWithoutAName_isShownByEmail() {
    assertEquals(Member("u1", "x@example.com", "x@example.com"), member("1", null, "x@example.com").toMember())
  }

  @Test
  fun members_areLoadedOncePerSession_untilCleared() = runTest {
    api.membersResult = ApiResult.Success(listOf(member("1", "Ana", null)))
    repository.members(TEST_ACCOUNT)
    repository.members(TEST_ACCOUNT)
    assertEquals(1, api.memberCalls.size)

    repository.clear()
    repository.members(TEST_ACCOUNT)

    assertEquals(2, api.memberCalls.size)
  }

  @Test
  fun failures_mapForTheScreen_andAreNotCached() = runTest {
    api.membersResult = ApiResult.Failure(ApiError.Forbidden)
    assertEquals(DetailResult.Failure(DetailError.Forbidden), repository.members(TEST_ACCOUNT))

    api.membersResult = ApiResult.Success(emptyList())
    assertEquals(DetailResult.Success(emptyList<Member>()), repository.members(TEST_ACCOUNT))
  }

  @Test
  fun me_isRemembered_andCanBeForgotten() = runTest {
    repository.rememberMe("u3")
    assertEquals("u3", repository.myUserId.first())

    repository.rememberMe(null)

    assertNull(repository.myUserId.first())
  }
}
