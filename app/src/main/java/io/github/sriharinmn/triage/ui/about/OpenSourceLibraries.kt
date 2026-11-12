package io.github.sriharinmn.triage.ui.about

import androidx.annotation.RawRes
import io.github.sriharinmn.triage.R

/** A license Triage's libraries come under, with its full text in res/raw. */
enum class OpenSourceLicense(val title: String, @param:RawRes val textRes: Int) {
  Apache2("Apache License 2.0", R.raw.license_apache_2_0),
  Ofl11("SIL Open Font License 1.1", R.raw.license_ofl),
}

/** One library the app ships (test-only libraries aren't in the app, so they aren't listed). */
data class OpenSourceLibrary(val name: String, val author: String, val license: OpenSourceLicense)

/**
 * Hand-kept (a generator plugin didn't fit this build; see the M7 plan). Add a line when a dependency is added to
 * `gradle/libs.versions.toml`. AndroidX libraries pulled in by these are Apache 2.0 too, as the screen says.
 */
val OPEN_SOURCE_LIBRARIES: List<OpenSourceLibrary> =
  listOf(
      OpenSourceLibrary("AndroidX Activity", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("AndroidX Core", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("AndroidX DataStore", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("AndroidX Lifecycle", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("AndroidX Navigation 3", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("AndroidX Room", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("AndroidX WorkManager", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("Jetpack Compose (UI, Foundation, Material 3)", "The Android Open Source Project", OpenSourceLicense.Apache2),
      OpenSourceLibrary("JetBrains Mono", "The JetBrains Mono Project Authors", OpenSourceLicense.Ofl11),
      OpenSourceLibrary("Kotlin standard library", "JetBrains", OpenSourceLicense.Apache2),
      OpenSourceLibrary("kotlinx.coroutines", "JetBrains", OpenSourceLicense.Apache2),
      OpenSourceLibrary("kotlinx.serialization", "JetBrains", OpenSourceLicense.Apache2),
      OpenSourceLibrary("Material Design icons", "Google", OpenSourceLicense.Apache2),
      OpenSourceLibrary("OkHttp", "Square", OpenSourceLicense.Apache2),
      OpenSourceLibrary("Okio", "Square", OpenSourceLicense.Apache2),
    )
    .sortedBy { it.name.lowercase() }
