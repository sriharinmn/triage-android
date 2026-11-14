package io.github.sriharinmn.triage.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkCauseTest {
  @Test
  fun aCertificateThePhoneDoesNotTrust_isTls() {
    val untrusted =
      SSLHandshakeException("PKIX path building failed").apply {
        initCause(CertificateException("Trust anchor for certification path not found."))
      }

    assertEquals(NetworkCause.Tls, untrusted.networkCause())
  }

  @Test
  fun aCertificateProblemDeeperInTheCauseChain_isTls() {
    val expired =
      SSLHandshakeException("Handshake failed").apply {
        initCause(IOException("wrapped").apply { initCause(CertificateExpiredException("NotAfter: 2026-01-01")) })
      }

    assertEquals(NetworkCause.Tls, expired.networkCause())
  }

  @Test
  fun aCertificateForAnotherName_isTls() {
    assertEquals(NetworkCause.Tls, SSLPeerUnverifiedException("Hostname sentry.example.com not verified").networkCause())
  }

  @Test
  fun aSecureConnectionThatDropped_isOther() {
    val dropped =
      listOf(
        SSLException("Read error: ssl=0x0: I/O error during system call, Connection reset by peer"),
        SSLException("Write error: ssl=0x0: I/O error during system call, Software caused connection abort"),
        SSLHandshakeException("Connection closed by peer"),
      )
    for (e in dropped) {
      assertEquals(e.message, NetworkCause.Other, e.networkCause())
    }
  }

  @Test(timeout = 1_000)
  fun aCauseChainThatLoops_isOther() {
    val handshake = SSLHandshakeException("Handshake failed")
    val wrapper = IOException("wrapped")
    handshake.initCause(wrapper)
    wrapper.initCause(handshake)

    assertEquals(NetworkCause.Other, handshake.networkCause())
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
