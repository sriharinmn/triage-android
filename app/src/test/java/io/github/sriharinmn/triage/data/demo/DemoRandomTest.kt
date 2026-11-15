package io.github.sriharinmn.triage.data.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRandomTest {
  @Test
  fun unit_isAlwaysInZeroToOne_andTheSameForTheSameInputs() {
    repeat(10_000) { n ->
      val u = unit(seed = 7, n = n.toLong())
      assertTrue("$u", u >= 0.0 && u < 1.0)
      assertEquals(u, unit(seed = 7, n = n.toLong()), 0.0)
    }
  }

  @Test
  fun unit_isSpreadEvenly() {
    val mean = (0 until 10_000).map { unit(seed = 99, n = it.toLong()) }.average()
    assertEquals(0.5, mean, 0.02)
  }

  @Test
  fun differentSeedsAndTexts_giveDifferentNumbers() {
    assertNotEquals(unit(seed = 1, n = 5), unit(seed = 2, n = 5), 0.0)
    assertNotEquals(seedOf("android-checkout-npe"), seedOf("web-price-undefined"))
    assertEquals(seedOf("same"), seedOf("same"))
  }
}
