package io.github.sriharinmn.triage.data.issues

import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import org.junit.Assert.assertEquals
import org.junit.Test

class IssueLinksTest {
  private val built = "https://example-org.sentry.io/organizations/example-org/issues/6/"

  @Test
  fun anHttpsPermalink_isUsed() {
    assertEquals(
      "https://example-org.sentry.io/issues/6/",
      issueBrowserUrl(TEST_ACCOUNT, "6", "https://example-org.sentry.io/issues/6/"),
    )
  }

  @Test
  fun anythingElse_isReplacedByALinkBuiltFromTheAccount() {
    assertEquals(built, issueBrowserUrl(TEST_ACCOUNT, "6", "http://example-org.sentry.io/issues/6/"))
    assertEquals(built, issueBrowserUrl(TEST_ACCOUNT, "6", "javascript:alert(1)"))
    assertEquals(built, issueBrowserUrl(TEST_ACCOUNT, "6", null))
  }
}
