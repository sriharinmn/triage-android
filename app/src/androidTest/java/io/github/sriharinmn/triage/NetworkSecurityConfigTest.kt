package io.github.sriharinmn.triage

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The app's network security config is in place: plain HTTP is refused everywhere, sentry.io included. */
@RunWith(AndroidJUnit4::class)
class NetworkSecurityConfigTest {
  @Test
  fun cleartextIsRefused_forSentryAndForSelfHostedServers() {
    val policy = NetworkSecurityPolicy.getInstance()
    assertFalse(policy.isCleartextTrafficPermitted("sentry.io"))
    assertFalse(policy.isCleartextTrafficPermitted("de.sentry.io"))
    assertFalse(policy.isCleartextTrafficPermitted("sentry.example.com"))
  }
}
