package io.github.sriharinmn.triage.data.members

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.detail.call
import io.github.sriharinmn.triage.data.settings.AccountPrefsStore
import io.github.sriharinmn.triage.remote.MemberDto
import io.github.sriharinmn.triage.remote.SentryApi
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow

/**
 * The organization's members, for assigning issues, and which of them the user is. Sentry's documented API can't say
 * who owns a token, so the user answers "Which one is you?" once and [rememberMe] keeps the answer.
 */
class MembersRepository(
  private val api: SentryApi,
  private val gate: RateLimitGate,
  private val prefs: AccountPrefsStore,
) {
  // Members change rarely; loading them once per session keeps the assignee sheet instant after the first time.
  private val loaded = AtomicReference<List<Member>?>(null)

  val myUserId: Flow<String?> = prefs.myUserId

  /** Everyone who can be assigned, sorted by name; invites that haven't been accepted can't, so they're left out. */
  suspend fun members(account: Account): DetailResult<List<Member>> {
    loaded.get()?.let { return DetailResult.Success(it) }
    return when (val result = gate.call { api.listMembers(account.apiBaseUrl, account.token, account.orgSlug) }) {
      is DetailResult.Success -> {
        val members = result.value.mapNotNull { it.toMember() }.sortedBy { it.name.lowercase() }
        loaded.set(members)
        DetailResult.Success(members)
      }
      is DetailResult.Failure -> result
    }
  }

  /** Remembers which member the user is; null forgets it ("Not you? Change"). */
  suspend fun rememberMe(userId: String?) = prefs.saveMyUserId(userId)

  /** Forgets the loaded members, so the next account loads its own. */
  fun clear() {
    loaded.set(null)
  }
}

/** A member who can be assigned, named by name or else email; null for an invite with no user yet. */
internal fun MemberDto.toMember(): Member? {
  val user = user ?: return null
  val email = user.email ?: email
  val name = listOf(user.name, name, email).firstOrNull { !it.isNullOrBlank() } ?: user.id
  return Member(userId = user.id, name = name, email = email)
}
