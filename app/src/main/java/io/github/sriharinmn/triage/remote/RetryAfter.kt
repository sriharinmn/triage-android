package io.github.sriharinmn.triage.remote

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Seconds to wait according to a `Retry-After` header, which holds either delta-seconds or an HTTP-date. Negative values
 * and past dates mean 0 (retry now); anything unparseable is null, so the caller picks its own default.
 */
fun parseRetryAfter(value: String?, now: Instant = Instant.now()): Long? {
  val text = value?.trim()
  if (text.isNullOrEmpty()) return null
  text.toLongOrNull()?.let { return it.coerceAtLeast(0) }
  return try {
    val retryAt = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    val millis = Duration.between(now, retryAt).toMillis()
    // Round up so the caller never retries a moment too early.
    if (millis <= 0) 0 else (millis + 999) / 1000
  } catch (_: DateTimeParseException) {
    null
  }
}
