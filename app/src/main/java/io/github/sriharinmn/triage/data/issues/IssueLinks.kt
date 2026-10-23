package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.data.account.Account
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where "open in browser" goes: Sentry's permalink if it is https, otherwise a link built from the account. */
fun issueBrowserUrl(account: Account, issueId: String, permalink: String?): String =
  permalink?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
    ?: "${account.webBaseUrl}/organizations/${account.orgSlug}/issues/$issueId/"
