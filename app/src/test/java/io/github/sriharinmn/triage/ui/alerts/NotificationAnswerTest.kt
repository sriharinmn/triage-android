package io.github.sriharinmn.triage.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAnswerTest {
  @Test
  fun notificationsThatCanShow_areAllowed() {
    assertEquals(NotificationAnswer.Allowed, notificationAnswer(allowed = true, canAskAgain = false))
    assertEquals(NotificationAnswer.Allowed, notificationAnswer(allowed = true, canAskAgain = true))
  }

  @Test
  fun aNoThatAndroidMayAskAboutAgain_isDeclined() {
    assertEquals(NotificationAnswer.Declined, notificationAnswer(allowed = false, canAskAgain = true))
  }

  @Test
  fun aNoThatAndroidWontAskAboutAgain_isBlocked() {
    assertEquals(NotificationAnswer.Blocked, notificationAnswer(allowed = false, canAskAgain = false))
  }
}
