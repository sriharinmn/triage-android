package io.github.sriharinmn.triage.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortReleaseTest {
  @Test
  fun aPackageAndVersion_showsTheVersion() {
    assertEquals("1.4.2", shortRelease("shop-api@1.4.2"))
    assertEquals("2.4.0+240", shortRelease("com.example.shop@2.4.0+240"))
  }

  @Test
  fun aScopedPackage_showsTheVersionAfterTheLastAt() {
    assertEquals("1.0.0", shortRelease("@scope/pkg@1.0.0"))
  }

  @Test
  fun aBareVersion_staysAsItIs() {
    assertEquals("1.4.2", shortRelease("1.4.2"))
  }

  @Test
  fun nothingAfterTheAt_keepsTheWholeName() {
    assertEquals("abc@", shortRelease("abc@"))
    assertEquals("abc@ ", shortRelease("abc@ "))
  }

  @Test
  fun aCommitSha_staysAsItIs() {
    val sha = "3f2a9c41d0b7e85f6a1c2d3e4f5a6b7c8d9e0f1a"
    assertEquals(sha, shortRelease(sha))
  }
}
