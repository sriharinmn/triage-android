package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.demo.content.DemoWorld
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A moment something happens in the demo. [alwaysNew]: the first one after the start, and each alerts-on kick. */
internal data class Happening(val at: Instant, val alwaysNew: Boolean)

private enum class HappeningKind {
  New,
  Escalating,
  Regressed,
}

/**
 * The happenings in (after, until], oldest first:
 * - the first, 25 minutes after the start;
 * - daily slots at 10:00, 13:30, 17:00 and 20:00 in [zone], each moved by up to 40 minutes (fixed by the start, the date
 *   and the slot), except those before the first or within 90 minutes after it or after a kick;
 * - the kick, if one was asked for.
 */
internal fun happenings(state: DemoState, after: Instant, until: Instant, zone: ZoneId): List<Happening> {
  if (until <= after) return emptyList()
  val first = instant(state.startedAt) + FIRST_DELAY
  val kick = state.kickAt?.let(::instant)
  fun inRange(time: Instant) = time > after && time <= until

  val found = mutableListOf<Happening>()
  if (inRange(first)) found += Happening(first, alwaysNew = true)
  if (kick != null && inRange(kick)) found += Happening(kick, alwaysNew = true)
  val seed = mix(state.startedAt)
  var day = after.atZone(zone).toLocalDate().minusDays(1)
  val lastDay = until.atZone(zone).toLocalDate().plusDays(1)
  while (!day.isAfter(lastDay)) {
    SLOTS.forEachIndexed { i, slot ->
      val shift = (unit(seed, day.toEpochDay() * SLOTS.size + i) * (2 * JITTER_MINUTES + 1)).toLong() - JITTER_MINUTES
      val at = day.atTime(slot).atZone(zone).toInstant() + Duration.ofMinutes(shift)
      val crowded = at <= first + SLOT_GAP || (kick != null && at > kick && at <= kick + SLOT_GAP)
      if (!crowded && inRange(at)) found += Happening(at, alwaysNew = false)
    }
    day = day.plusDays(1)
  }
  return found.sortedBy { it.at }
}

/**
 * [state] with every happening up to [now] applied, in order. Unchanged (the same instance) when none is due, so reads
 * between happenings save nothing. A clock moved back never undoes one.
 */
internal fun advance(state: DemoState, now: Instant, zone: ZoneId): DemoState {
  val due = happenings(state, instant(state.processedUntil), now, zone)
  if (due.isEmpty()) return state
  return due.fold(state) { current, happening -> current.after(happening) }.copy(processedUntil = now.epochMs)
}

/** The state after [happening]: a new issue, an escalation or a regression, by the rotation and its fallbacks. */
internal fun DemoState.after(happening: Happening): DemoState {
  val at = happening.at
  val kind = if (happening.alwaysNew) HappeningKind.New else ROTATION[happeningCount % ROTATION.size]
  val counted = copy(happeningCount = happeningCount + 1)
  return when (kind) {
    HappeningKind.Regressed -> counted.regressOne(at) ?: counted.escalateOne(at) ?: counted.arrive(at)
    HappeningKind.Escalating -> counted.escalateOne(at) ?: counted.arrive(at)
    HappeningKind.New -> counted.arrive(at)
  }
}

/**
 * Alerts were just switched on at [now]: a new issue comes 20 minutes later, unless something already happens within 45
 * minutes. [this] must already be advanced to [now].
 */
internal fun DemoState.withAlertsKick(now: Instant, zone: ZoneId): DemoState =
  if (happenings(this, now, now + KICK_SOON, zone).isNotEmpty()) this else copy(kickAt = (now + KICK_DELAY).epochMs)

/** The issue resolved longest (at least 2 hours before [at]) comes back. */
private fun DemoState.regressOne(at: Instant): DemoState? {
  val latest = (at - MIN_RESOLVED).epochMs
  val pick =
    issues
      .filter { it.status == DemoStatus.Resolved }
      .mapNotNull { issue -> issue.quietSpans.lastOrNull { it.to == null }?.let { issue to it.from } }
      .filter { (_, since) -> since <= latest }
      .minWithOrNull(compareBy<Pair<DemoIssueState, Long>> { it.second }.thenBy { it.first.id })
      ?.first ?: return null
  val t = at.epochMs
  return replacing(
    pick.copy(
      status = DemoStatus.Unresolved,
      quietSpans = pick.quietSpans.map { if (it.to == null) it.copy(to = t) else it },
      regressedAt = t,
      inboxSince = t,
    )
  )
}

/** The issue archived longest escalates; with none archived, the busiest unresolved one not escalated this week. */
private fun DemoState.escalateOne(at: Instant): DemoState? {
  val t = at.epochMs
  val archived =
    issues
      .filter { it.status == DemoStatus.Archived && (it.archivedAt ?: 0L) <= t }
      .minWithOrNull(compareBy<DemoIssueState> { it.archivedAt ?: 0L }.thenBy { it.id })
  val pick =
    archived
      ?: issues
        .filter { issue ->
          issue.status == DemoStatus.Unresolved &&
            issue.firstSeen <= t &&
            (issue.escalatedAt == null || issue.escalatedAt <= (at - DEMO_WEEK).epochMs)
        }
        .minWithOrNull(compareByDescending<DemoIssueState> { DemoWorld.spec(it.contentKey)?.ratePerHour ?: 0.0 }.thenBy { it.id })
      ?: return null
  return replacing(pick.copy(status = DemoStatus.Unresolved, archivedAt = null, escalatedAt = t, bursts = pick.bursts + t, inboxSince = t))
}

/** The next arriving issue turns up, with a fresh id and the project's next short id. */
private fun DemoState.arrive(at: Instant): DemoState {
  val spec = DemoWorld.arrivals[arrivalCount % DemoWorld.arrivals.size]
  val project = DemoWorld.project(spec.projectSlug)
  val number = nextShortNumber[project.slug] ?: project.firstShortNumber
  val t = at.epochMs
  val issue =
    DemoIssueState(
      id = (ARRIVAL_ID_BASE + arrivalCount).toString(),
      shortId = "${project.shortIdPrefix}-$number",
      contentKey = spec.key,
      firstSeen = t,
      inboxSince = t,
    )
  return copy(issues = issues + issue, arrivalCount = arrivalCount + 1, nextShortNumber = nextShortNumber + (project.slug to number + 1))
}

private fun DemoState.replacing(issue: DemoIssueState): DemoState = copy(issues = issues.map { if (it.id == issue.id) issue else it })

private val FIRST_DELAY: Duration = Duration.ofMinutes(25)
private val KICK_DELAY: Duration = Duration.ofMinutes(20)
private val KICK_SOON: Duration = Duration.ofMinutes(45)
private val SLOT_GAP: Duration = Duration.ofMinutes(90)
private val MIN_RESOLVED: Duration = Duration.ofHours(2)
private const val JITTER_MINUTES = 40L
private val SLOTS = listOf(LocalTime.of(10, 0), LocalTime.of(13, 30), LocalTime.of(17, 0), LocalTime.of(20, 0))
private val ROTATION = listOf(HappeningKind.New, HappeningKind.Escalating, HappeningKind.Regressed)
