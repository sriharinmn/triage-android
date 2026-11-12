package io.github.sriharinmn.triage.testing

import io.github.sriharinmn.triage.data.alerts.AlertPost
import io.github.sriharinmn.triage.data.alerts.AlertPoster

/** Records what a check would show, instead of posting Android notifications. */
class RecordingAlertPoster : AlertPoster {
  /** False plays a phone where Triage's notifications are blocked. */
  var allowed = true

  /** (org slug, post) for every post. */
  val posts = mutableListOf<Pair<String, AlertPost>>()

  var authProblems = 0

  var cancelAlls = 0

  override fun canPost(): Boolean = allowed

  override fun post(orgSlug: String, post: AlertPost) {
    posts += orgSlug to post
  }

  override fun postAuthProblem() {
    authProblems++
  }

  override fun cancelAll() {
    cancelAlls++
  }
}
