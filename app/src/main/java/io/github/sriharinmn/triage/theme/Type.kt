package io.github.sriharinmn.triage.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.sriharinmn.triage.R

/** Material 3's type scale in the system font (Roboto). */
val TriageTypography = Typography()

/** JetBrains Mono 2.304 (SIL Open Font License 1.1), for ids, culprits, code and releases. */
val JetBrainsMono =
  FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    // SemiBold marks the app's own stack frames; a Medium request falls back to Regular.
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
  )

/** This style in JetBrains Mono. Mono glyphs are wide already, so the extra letter spacing goes. */
fun TextStyle.mono(): TextStyle = copy(fontFamily = JetBrainsMono, letterSpacing = 0.sp)

/** This style with tabular figures, so counts line up and don't jump as they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
