package io.github.sriharinmn.triage.data.demo

import io.github.sriharinmn.triage.testing.TEST_ACCOUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAccountTest {
  @Test
  fun theDemoAccount_isTheDemo_andARealOneIsNot() {
    assertTrue(DemoAccount.isDemo)
    assertFalse(TEST_ACCOUNT.isDemo)
  }

  @Test
  fun theDemoAccount_pointsNowhereReal() {
    assertEquals("https://sample-data.invalid", DemoAccount.apiBaseUrl)
    assertEquals("https://sample-data.invalid", DemoAccount.webBaseUrl)
    assertEquals("example-shop", DemoAccount.orgSlug)
    assertEquals("Example Shop", DemoAccount.orgName)
  }
}
