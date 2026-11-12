package io.github.sriharinmn.triage.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkCauseTest {
  @Test
  fun aFailedSecureConnection_isTls() {
    assertEquals(NetworkCause.Tls, SSLHandshakeException("PKIX path building failed").networkCause())
    assertEquals(NetworkCause.Tls, SSLPeerUnverifiedException("Hostname sentry.example.com not verified").networkCause())
  }

  @Test
  fun aNameThatDoesNotResolve_isAnUnknownHost() {
    assertEquals(NetworkCause.UnknownHost, UnknownHostException("sentry.example.invalid").networkCause())
  }

  @Test
  fun everythingElse_isOther() {
    for (e in listOf(SocketTimeoutException("timeout"), ConnectException("refused"), IOException("closed"))) {
      assertEquals(NetworkCause.Other, e.networkCause())
    }
  }
}
