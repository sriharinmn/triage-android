package io.github.sriharinmn.triage.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAnswerTest {
  @Test
  fun notificationsThatCanShow_areAllowed() {
    for (askedJustNow in listOf(false, true)) {
      for (canAskAgain in listOf(false, true)) {
        assertEquals(
          NotificationAnswer.Allowed,
          notificationAnswer(allowed = true, askedJustNow = askedJustNow, canAskAgain = canAskAgain),
        )
      }
    }
  }

  @Test
  fun aNoThatAndroidMayAskAboutAgain_isDeclined() {
    assertEquals(NotificationAnswer.Declined, notificationAnswer(allowed = false, askedJustNow = false, canAskAgain = true))
  }

  @Test
  fun aNoGivenInAPopupJustShown_isDeclinedEvenWhenAndroidWontAskAgain() {
    assertEquals(NotificationAnswer.Declined, notificationAnswer(allowed = false, askedJustNow = true, canAskAgain = false))
    assertEquals(NotificationAnswer.Declined, notificationAnswer(allowed = false, askedJustNow = true, canAskAgain = true))
  }

  @Test
  fun noPopupShownAndAndroidWontAsk_isBlocked() {
    assertEquals(NotificationAnswer.Blocked, notificationAnswer(allowed = false, askedJustNow = false, canAskAgain = false))
  }
}
