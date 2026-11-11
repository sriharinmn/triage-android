package io.github.sriharinmn.triage.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryTipsTest {
  @Test
  fun eachKnownBrand_getsItsOwnTip() {
    assertEquals(BatteryTip.OnePlus, batteryTipFor("OnePlus", "OnePlus"))
    assertEquals(BatteryTip.Samsung, batteryTipFor("samsung", "samsung"))
    assertEquals(BatteryTip.Xiaomi, batteryTipFor("Xiaomi", "Redmi"))
    assertEquals(BatteryTip.Xiaomi, batteryTipFor("Xiaomi", "POCO"))
    assertEquals(BatteryTip.Oppo, batteryTipFor("OPPO", "OPPO"))
    assertEquals(BatteryTip.Oppo, batteryTipFor("realme", "realme"))
    assertEquals(BatteryTip.Vivo, batteryTipFor("vivo", "vivo"))
    assertEquals(BatteryTip.Huawei, batteryTipFor("HUAWEI", "HUAWEI"))
    assertEquals(BatteryTip.Huawei, batteryTipFor("HONOR", "HONOR"))
  }

  @Test
  fun anyOtherPhone_getsTheGenericTip() {
    assertEquals(BatteryTip.Generic, batteryTipFor("Google", "google"))
  }
}
