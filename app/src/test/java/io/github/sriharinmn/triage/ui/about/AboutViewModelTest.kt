package io.github.sriharinmn.triage.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutViewModelTest {
  @Test
  fun aDebugBuild_saysSo() {
    val state = AboutViewModel(versionName = "1.0", versionCode = 1, buildType = "debug").state

    assertEquals("1.0", state.versionName)
    assertEquals(1, state.versionCode)
    assertEquals("debug", state.buildType)
  }

  @Test
  fun aReleaseBuild_showsNoBuildType() {
    assertNull(AboutViewModel(versionName = "1.0", versionCode = 1, buildType = "release").state.buildType)
  }

  @Test
  fun theLicenses_listJetBrainsMonoUnderTheOfl_andEverythingElseUnderApache() {
    val libraries = AboutViewModel("1.0", 1, "release").state.libraries

    assertEquals(OpenSourceLicense.Ofl11, libraries.single { it.name == "JetBrains Mono" }.license)
    assertTrue(libraries.filter { it.name != "JetBrains Mono" }.all { it.license == OpenSourceLicense.Apache2 })
    assertEquals(libraries.sortedBy { it.name.lowercase() }, libraries)
  }
}
