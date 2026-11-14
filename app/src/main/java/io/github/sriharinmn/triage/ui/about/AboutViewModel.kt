package io.github.sriharinmn.triage.ui.about

import androidx.lifecycle.ViewModel

/** What About shows: the version, and the libraries with their licenses. */
data class AboutUiState(
  val versionName: String,
  val versionCode: Int,
  /** "debug" and the like; null for a release build, which shows no build type. */
  val buildType: String?,
  val libraries: List<OpenSourceLibrary>,
)

/** Holds About's content; the build's values come in from BuildConfig, so the JVM tests can pass their own. */
class AboutViewModel(
  versionName: String,
  versionCode: Int,
  buildType: String,
  libraries: List<OpenSourceLibrary> = OPEN_SOURCE_LIBRARIES,
) : ViewModel() {
  val state = AboutUiState(versionName, versionCode, buildType.takeIf { it != RELEASE }, libraries)

  private companion object {
    const val RELEASE = "release"
  }
}
