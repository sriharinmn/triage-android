package io.github.sriharinmn.triage.data.alerts

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
  /** The default window, switched on: 22:00 to 07:00. */
  private val night = QuietHours(enabled = true)

  @Test
  fun switchedOff_isNeverQuiet() {
    assertFalse(QuietHours().isQuietAt(LocalTime.of(23, 0)))
  }

  @Test
  fun aWindowAcrossMidnight_coversTheLateEveningAndTheEarlyMorning() {
    assertTrue(night.isQuietAt(LocalTime.of(22, 0)))
    assertTrue(night.isQuietAt(LocalTime.of(23, 59)))
    assertTrue(night.isQuietAt(LocalTime.MIDNIGHT))
    assertTrue(night.isQuietAt(LocalTime.of(6, 59)))
    assertFalse(night.isQuietAt(LocalTime.of(7, 0)))
    assertFalse(night.isQuietAt(LocalTime.NOON))
    assertFalse(night.isQuietAt(LocalTime.of(21, 59)))
  }

  @Test
  fun aWindowWithinOneDay_isQuietOnlyBetweenItsEnds() {
    val lunch = QuietHours(enabled = true, startMinute = 12 * 60, endMinute = 13 * 60 + 30)

    assertFalse(lunch.isQuietAt(LocalTime.of(11, 59)))
    assertTrue(lunch.isQuietAt(LocalTime.NOON))
    assertTrue(lunch.isQuietAt(LocalTime.of(13, 29)))
    assertFalse(lunch.isQuietAt(LocalTime.of(13, 30)))
  }

  @Test
  fun equalEnds_meanNoWindow() {
    assertFalse(QuietHours(enabled = true, startMinute = 600, endMinute = 600).isQuietAt(LocalTime.of(10, 0)))
  }

  @Test
  fun theWindowIsInThePhonesTimeZone() {
    val instant = Instant.parse("2026-11-11T17:00:00Z")

    // 22:30 in India, 18:00 in Germany.
    assertTrue(night.isQuietAt(instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalTime()))
    assertFalse(night.isQuietAt(instant.atZone(ZoneId.of("Europe/Berlin")).toLocalTime()))
  }
}
