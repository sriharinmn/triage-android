package io.github.sriharinmn.triage.ui.alerts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import java.util.Locale

/** One short tip per phone brand whose battery saver is known to stop background work, and one for the rest. */
enum class BatteryTip(@StringRes val text: Int) {
  OnePlus(R.string.battery_tip_oneplus),
  Samsung(R.string.battery_tip_samsung),
  Xiaomi(R.string.battery_tip_xiaomi),
  Oppo(R.string.battery_tip_oppo),
  Vivo(R.string.battery_tip_vivo),
  Huawei(R.string.battery_tip_huawei),
  Generic(R.string.battery_tip_generic),
}

/**
 * The tip for a phone, from `Build.MANUFACTURER` and `Build.BRAND`: Redmi and POCO phones say Xiaomi in one and their own
 * name in the other. OnePlus comes first because OnePlus phones are made by Oppo's group but have their own settings.
 */
fun batteryTipFor(manufacturer: String, brand: String): BatteryTip {
  val names = setOf(manufacturer.lowercase(Locale.ROOT), brand.lowercase(Locale.ROOT))
  fun madeBy(vararg brands: String) = brands.any { it in names }
  return when {
    madeBy("oneplus") -> BatteryTip.OnePlus
    madeBy("samsung") -> BatteryTip.Samsung
    madeBy("xiaomi", "redmi", "poco") -> BatteryTip.Xiaomi
    madeBy("oppo", "realme") -> BatteryTip.Oppo
    madeBy("vivo") -> BatteryTip.Vivo
    madeBy("huawei", "honor") -> BatteryTip.Huawei
    else -> BatteryTip.Generic
  }
}

/** Why checks can be late, the phone's tip, and a button to Android's battery-optimization list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatteryHelpSheet(tip: BatteryTip, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleLarge)
      Text(stringResource(R.string.battery_why), style = MaterialTheme.typography.bodyMedium)
      Text(stringResource(tip.text), style = MaterialTheme.typography.bodyMedium)
      Button(onClick = onOpenSettings, modifier = Modifier.testTag(AlertsTags.BATTERY_SETTINGS)) {
        Text(stringResource(R.string.battery_open_settings))
      }
    }
  }
}
