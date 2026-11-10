package io.github.sriharinmn.triage.data.actions

import io.github.sriharinmn.triage.data.RateLimitGate
import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.detail.DetailResult
import io.github.sriharinmn.triage.data.detail.call
import io.github.sriharinmn.triage.remote.SentryApi

/** Sends issue actions to Sentry through the app-wide rate-limit gate. Never retries by itself. */
class IssueActionRepository(private val api: SentryApi, private val gate: RateLimitGate) {
  suspend fun apply(account: Account, issueId: String, action: IssueAction): DetailResult<Unit> =
    gate.call { api.updateIssue(account.apiBaseUrl, account.token, account.orgSlug, issueId, action.toChanges()) }
}
