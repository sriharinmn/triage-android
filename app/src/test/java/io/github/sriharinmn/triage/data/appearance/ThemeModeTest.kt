package io.github.sriharinmn.triage.data.appearance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
  @Test
  fun system_followsThePhone() {
    assertTrue(ThemeMode.System.isDark(systemDark = true))
    assertFalse(ThemeMode.System.isDark(systemDark = false))
  }

  @Test
  fun lightAndDark_ignoreThePhone() {
    assertFalse(ThemeMode.Light.isDark(systemDark = true))
    assertTrue(ThemeMode.Dark.isDark(systemDark = false))
  }
}
