package io.github.sriharinmn.triage.remote

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryAfterTest {
  // A Thursday, so the HTTP-dates below are valid RFC 1123 dates.
  private val now = Instant.parse("2026-10-22T12:00:00Z")

  @Test
  fun deltaSeconds() {
    assertEquals(30L, parseRetryAfter("30", now))
    assertEquals(5L, parseRetryAfter(" 5 ", now))
  }

  @Test
  fun negativeOrZero_meansNow() {
    assertEquals(0L, parseRetryAfter("-3", now))
    assertEquals(0L, parseRetryAfter("0", now))
  }

  @Test
  fun httpDateInTheFuture() {
    assertEquals(120L, parseRetryAfter("Thu, 22 Oct 2026 12:02:00 GMT", now))
  }

  @Test
  fun httpDateInThePast_meansNow() {
    assertEquals(0L, parseRetryAfter("Thu, 22 Oct 2026 11:00:00 GMT", now))
  }

  @Test
  fun partialSecond_roundsUp() {
    assertEquals(1L, parseRetryAfter("Thu, 22 Oct 2026 12:00:01 GMT", now.plusMillis(500)))
  }

  @Test
  fun unparseable_isNull() {
    assertNull(parseRetryAfter(null, now))
    assertNull(parseRetryAfter("", now))
    assertNull(parseRetryAfter("soon", now))
    assertNull(parseRetryAfter("1.5", now))
  }
}
