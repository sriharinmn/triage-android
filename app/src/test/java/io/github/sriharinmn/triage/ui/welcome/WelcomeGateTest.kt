package io.github.sriharinmn.triage.ui.welcome

import io.github.sriharinmn.triage.data.appearance.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WelcomeGateTest {
  @Test
  fun aFirstLaunch_showsTheWelcome() {
    assertEquals(SignedOutScreen.Welcome, signedOutScreen(AppearanceSettings(), getStartedTapped = false))
  }

  @Test
  fun onceGetStartedWasTapped_aMissingAccountGoesStraightToConnect() {
    assertEquals(SignedOutScreen.Connect, signedOutScreen(AppearanceSettings(welcomeSeen = true), getStartedTapped = false))
    assertEquals(SignedOutScreen.Connect, signedOutScreen(AppearanceSettings(), getStartedTapped = true))
  }

  @Test
  fun beforeTheAppearanceIsRead_nothingShows() {
    assertNull(signedOutScreen(null, getStartedTapped = false))
  }

  @Test
  fun aTapIsKept_evenIfSavingItFailed() {
    assertEquals(SignedOutScreen.Connect, signedOutScreen(null, getStartedTapped = true))
  }
}
