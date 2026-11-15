package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.data.account.Account
import io.github.sriharinmn.triage.data.demo.content.DemoWorld

/**
 * Where the demo's pretend Sentry "lives". `.invalid` can never resolve (RFC 2606), so even a bug that let a demo call
 * reach the network would fail there and send nothing anywhere.
 */
const val DEMO_BASE_URL = "https://sample-data.invalid"

/** The demo's stored account: an ordinary [Account] whose server is [DEMO_BASE_URL]. Its token isn't a secret. */
val DemoAccount: Account =
  Account(
    apiBaseUrl = DEMO_BASE_URL,
    webBaseUrl = DEMO_BASE_URL,
    token = "sample-data",
    orgSlug = DemoWorld.ORG_SLUG,
    orgName = DemoWorld.ORG_NAME,
  )

/** True for the demo's account. */
val Account.isDemo: Boolean
  get() = apiBaseUrl == DEMO_BASE_URL
