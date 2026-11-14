package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import io.github.sriharinmn.triage.remote.ApiError
import io.github.sriharinmn.triage.remote.ApiResult
import io.github.sriharinmn.triage.remote.EventDto
import io.github.sriharinmn.triage.remote.IssueDetailDto
import io.github.sriharinmn.triage.remote.IssueDto
import io.github.sriharinmn.triage.remote.IssueListParams
import io.github.sriharinmn.triage.remote.MemberDto
import io.github.sriharinmn.triage.remote.OrganizationDto
import io.github.sriharinmn.triage.remote.Page
import io.github.sriharinmn.triage.remote.ProjectDto
import io.github.sriharinmn.triage.remote.SentryApi
import io.github.sriharinmn.triage.remote.TagDetailDto
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * The pretend Sentry behind "Try with sample data". It answers every call the app makes from the saved [DemoState],
 * the sample content and the clock, never the network.
 *
 * Each call, under one lock (the screens and the background check share this instance), loads the state, applies the
 * happenings that are due, answers, and saves if anything changed. With nothing saved it starts a fresh demo. The
 * computing happens on [computeDispatcher], never the caller's thread.
 */
class DemoSentryApi(
  private val store: DemoStateStore,
  private val clock: () -> Instant = Instant::now,
  private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val zone: () -> ZoneId = ZoneId::systemDefault,
) : SentryApi {
  private val lock = Mutex()

  /** Saves a fresh demo, started now. Throws if it can't be saved. */
  suspend fun startFresh() {
    lock.withLock { store.save(freshDemoState(clock())) }
  }

  /** Forgets the demo (leaving it, or connecting a real account). */
  suspend fun clear() {
    lock.withLock { store.clear() }
  }

  /** Alerts were just switched on: a new issue arrives about 20 minutes from now, unless something happens sooner. */
  suspend fun onAlertsTurnedOn() {
    lock.withLock {
      withContext(computeDispatcher) {
        try {
          val saved = store.load() ?: return@withContext
          val now = clock()
          val next = advance(saved, now, zone()).withAlertsKick(now, zone())
          if (next != saved) store.save(next)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // Only a convenience: the day's own happenings still come.
        }
      }
    }
  }

  /** The demo can only be started from its button, never through Connect. */
  override suspend fun listOrganizations(baseUrl: String, token: String): ApiResult<List<OrganizationDto>> =
    ApiResult.Failure(ApiError.Unauthorized)

  override suspend fun listProjects(baseUrl: String, token: String, orgSlug: String): ApiResult<List<ProjectDto>> =
    answer { state, _ -> state to ApiResult.Success(DemoWorld.projects.map { it.toProjectDto() }) }

  override suspend fun listIssues(
    baseUrl: String,
    token: String,
    orgSlug: String,
    params: IssueListParams,
    cursor: String?,
  ): ApiResult<Page<IssueDto>> =
    answer { state, now ->
      val found = searchIssues(state.views(now), params, now, DemoWorld.me.userId)
      val page = pageOf(found, params.limit, cursor)
      state to ApiResult.Success(Page(page.items.map { it.toIssueDto(now) }, page.nextCursor))
    }

  override suspend fun getIssue(baseUrl: String, token: String, orgSlug: String, issueId: String): ApiResult<IssueDetailDto> =
    answer { state, now ->
      val view = state.view(issueId, now) ?: return@answer state to NOT_FOUND
      state to ApiResult.Success(view.toIssueDetailDto(now, instant(state.startedAt)))
    }

  override suspend fun getIssueEvent(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    eventId: String,
  ): ApiResult<EventDto> =
    answer { state, now ->
      val view = state.view(issueId, now) ?: return@answer state to NOT_FOUND
      val pick = if (eventId == "oldest") EventPick.Oldest else EventPick.Latest
      state to ApiResult.Success(view.toEventDto(pick, instant(state.startedAt)))
    }

  override suspend fun getIssueTag(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    key: String,
  ): ApiResult<TagDetailDto> =
    answer { state, now ->
      val tag = state.view(issueId, now)?.toTagDetailDto(key) ?: return@answer state to NOT_FOUND
      state to ApiResult.Success(tag)
    }

  override suspend fun updateIssue(
    baseUrl: String,
    token: String,
    orgSlug: String,
    issueId: String,
    changes: JsonObject,
  ): ApiResult<Unit> =
    answer { state, now ->
      val next = state.updated(issueId, changes, now) ?: return@answer state to NOT_FOUND
      next to ApiResult.Success(Unit)
    }

  override suspend fun listMembers(baseUrl: String, token: String, orgSlug: String): ApiResult<List<MemberDto>> =
    answer { state, _ -> state to ApiResult.Success(DemoWorld.people.map { it.toMemberDto() }) }

  private suspend fun <T> answer(respond: (DemoState, Instant) -> Pair<DemoState, ApiResult<T>>): ApiResult<T> =
    lock.withLock {
      withContext(computeDispatcher) {
        try {
          val now = clock()
          val saved = store.load()
          val current = advance(saved ?: freshDemoState(now), now, zone())
          val (next, result) = respond(current, now)
          if (next != saved) store.save(next)
          result
        } catch (e: IOException) {
          // The screens explain a network error kindly, and the next call tries again.
          ApiResult.Failure(ApiError.Network("Couldn't read or save the sample data"))
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // A demo bug shows as an unreadable answer instead of crashing a tester's app.
          ApiResult.Failure(ApiError.Parse)
        }
      }
    }

  private companion object {
    val NOT_FOUND = ApiResult.Failure(ApiError.Http(404))
  }
}
