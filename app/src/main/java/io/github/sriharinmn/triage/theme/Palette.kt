package io.github.sriharinmn.triage.theme

/**
 * Every Material 3 color role of one scheme, as 0xAARRGGBB numbers. Plain numbers rather than Compose colors, so the JVM
 * tests can check contrast and hue without Android.
 */
internal data class SchemeHex(
  val primary: Long,
  val onPrimary: Long,
  val primaryContainer: Long,
  val onPrimaryContainer: Long,
  val inversePrimary: Long,
  val secondary: Long,
  val onSecondary: Long,
  val secondaryContainer: Long,
  val onSecondaryContainer: Long,
  val tertiary: Long,
  val onTertiary: Long,
  val tertiaryContainer: Long,
  val onTertiaryContainer: Long,
  val background: Long,
  val onBackground: Long,
  val surface: Long,
  val onSurface: Long,
  val surfaceVariant: Long,
  val onSurfaceVariant: Long,
  val surfaceTint: Long,
  val inverseSurface: Long,
  val inverseOnSurface: Long,
  val error: Long,
  val onError: Long,
  val errorContainer: Long,
  val onErrorContainer: Long,
  val outline: Long,
  val outlineVariant: Long,
  val scrim: Long,
  val surfaceBright: Long,
  val surfaceContainer: Long,
  val surfaceContainerHigh: Long,
  val surfaceContainerHighest: Long,
  val surfaceContainerLow: Long,
  val surfaceContainerLowest: Long,
  val surfaceDim: Long,
  val primaryFixed: Long,
  val primaryFixedDim: Long,
  val onPrimaryFixed: Long,
  val onPrimaryFixedVariant: Long,
  val secondaryFixed: Long,
  val secondaryFixedDim: Long,
  val onSecondaryFixed: Long,
  val onSecondaryFixedVariant: Long,
  val tertiaryFixed: Long,
  val tertiaryFixedDim: Long,
  val onTertiaryFixed: Long,
  val onTertiaryFixedVariant: Long,
)

/**
 * Triage's own colors: a Material 3 TonalSpot scheme generated from cobalt #3D5BA9 with Google's material-color-utilities
 * (npm 0.3.0), primary chroma 36 and secondary 16. Two palettes are moved so no role leans violet (Sentry's brand is
 * purple): the neutrals use HCT hue 255 instead of the seed's 271, which tints the light surfaces lavender, and the
 * tertiary sits 60 degrees below the seed (teal) instead of above it (mauve).
 */
internal object CobaltPalette {
  val light =
    SchemeHex(
      primary = 0xFF4A5C92,
      onPrimary = 0xFFFFFFFF,
      primaryContainer = 0xFFDBE1FF,
      onPrimaryContainer = 0xFF324578,
      inversePrimary = 0xFFB3C5FF,
      secondary = 0xFF585E71,
      onSecondary = 0xFFFFFFFF,
      secondaryContainer = 0xFFDDE1F9,
      onSecondaryContainer = 0xFF414659,
      tertiary = 0xFF39656D,
      onTertiary = 0xFFFFFFFF,
      tertiaryContainer = 0xFFBDEAF4,
      onTertiaryContainer = 0xFF1F4D55,
      background = 0xFFF8F9FF,
      onBackground = 0xFF191C20,
      surface = 0xFFF8F9FF,
      onSurface = 0xFF191C20,
      surfaceVariant = 0xFFDFE2EB,
      onSurfaceVariant = 0xFF43474E,
      surfaceTint = 0xFF4A5C92,
      inverseSurface = 0xFF2E3135,
      inverseOnSurface = 0xFFEFF0F7,
      error = 0xFFBA1A1A,
      onError = 0xFFFFFFFF,
      errorContainer = 0xFFFFDAD6,
      onErrorContainer = 0xFF93000A,
      outline = 0xFF73777F,
      outlineVariant = 0xFFC3C6CF,
      scrim = 0xFF000000,
      surfaceBright = 0xFFF8F9FF,
      surfaceContainer = 0xFFECEEF4,
      surfaceContainerHigh = 0xFFE7E8EE,
      surfaceContainerHighest = 0xFFE1E2E8,
      surfaceContainerLow = 0xFFF2F3FA,
      surfaceContainerLowest = 0xFFFFFFFF,
      surfaceDim = 0xFFD8DAE0,
      primaryFixed = 0xFFDBE1FF,
      primaryFixedDim = 0xFFB3C5FF,
      onPrimaryFixed = 0xFF001849,
      onPrimaryFixedVariant = 0xFF324578,
      secondaryFixed = 0xFFDDE1F9,
      secondaryFixedDim = 0xFFC1C6DD,
      onSecondaryFixed = 0xFF151B2C,
      onSecondaryFixedVariant = 0xFF414659,
      tertiaryFixed = 0xFFBDEAF4,
      tertiaryFixedDim = 0xFFA1CED7,
      onTertiaryFixed = 0xFF001F24,
      onTertiaryFixedVariant = 0xFF1F4D55,
    )

  val dark =
    SchemeHex(
      primary = 0xFFB3C5FF,
      onPrimary = 0xFF192E60,
      primaryContainer = 0xFF324578,
      onPrimaryContainer = 0xFFDBE1FF,
      inversePrimary = 0xFF4A5C92,
      secondary = 0xFFC1C6DD,
      onSecondary = 0xFF2A3042,
      secondaryContainer = 0xFF414659,
      onSecondaryContainer = 0xFFDDE1F9,
      tertiary = 0xFFA1CED7,
      onTertiary = 0xFF00363E,
      tertiaryContainer = 0xFF1F4D55,
      onTertiaryContainer = 0xFFBDEAF4,
      background = 0xFF111418,
      onBackground = 0xFFE1E2E8,
      surface = 0xFF111418,
      onSurface = 0xFFE1E2E8,
      surfaceVariant = 0xFF43474E,
      onSurfaceVariant = 0xFFC3C6CF,
      surfaceTint = 0xFFB3C5FF,
      inverseSurface = 0xFFE1E2E8,
      inverseOnSurface = 0xFF2E3135,
      error = 0xFFFFB4AB,
      onError = 0xFF690005,
      errorContainer = 0xFF93000A,
      onErrorContainer = 0xFFFFDAD6,
      outline = 0xFF8D9199,
      outlineVariant = 0xFF43474E,
      scrim = 0xFF000000,
      surfaceBright = 0xFF36393E,
      surfaceContainer = 0xFF1D2024,
      surfaceContainerHigh = 0xFF272A2F,
      surfaceContainerHighest = 0xFF32353A,
      surfaceContainerLow = 0xFF191C20,
      surfaceContainerLowest = 0xFF0B0E13,
      surfaceDim = 0xFF111418,
      primaryFixed = 0xFFDBE1FF,
      primaryFixedDim = 0xFFB3C5FF,
      onPrimaryFixed = 0xFF001849,
      onPrimaryFixedVariant = 0xFF324578,
      secondaryFixed = 0xFFDDE1F9,
      secondaryFixedDim = 0xFFC1C6DD,
      onSecondaryFixed = 0xFF151B2C,
      onSecondaryFixedVariant = 0xFF414659,
      tertiaryFixed = 0xFFBDEAF4,
      tertiaryFixedDim = 0xFFA1CED7,
      onTertiaryFixed = 0xFF001F24,
      onTertiaryFixedVariant = 0xFF1F4D55,
    )
}

/** Colors with one meaning whichever scheme is on, tuned per light and dark for contrast on the surfaces they sit on. */
internal data class SemanticHex(
  /** The square glyph of error and fatal issues. */
  val levelError: Long,
  /** The triangle glyph of warnings. */
  val levelWarning: Long,
  /** The dot glyph of info issues; cyan, so it never reads as the brand blue. */
  val levelInfo: Long,
  val statusEscalating: Long,
  val statusRegressed: Long,
  val statusNew: Long,
  /** Behind a row swiped right (Resolve), and the label on it. */
  val resolveSwipe: Long,
  val onResolveSwipe: Long,
  /** Behind a row swiped left (Archive), and the label on it. */
  val archiveSwipe: Long,
  val onArchiveSwipe: Long,
)

internal object SemanticPalette {
  val light =
    SemanticHex(
      levelError = 0xFFBA1A1A,
      levelWarning = 0xFF8F5000,
      levelInfo = 0xFF00687A,
      statusEscalating = 0xFFB3261E,
      statusRegressed = 0xFF7A4F00,
      statusNew = 0xFF255EA6,
      resolveSwipe = 0xFF00796B,
      onResolveSwipe = 0xFFFFFFFF,
      archiveSwipe = 0xFF546E7A,
      onArchiveSwipe = 0xFFFFFFFF,
    )

  val dark =
    SemanticHex(
      levelError = 0xFFFF7A70,
      levelWarning = 0xFFF6A642,
      levelInfo = 0xFF7FD3E6,
      statusEscalating = 0xFFFF9E95,
      statusRegressed = 0xFFF8C27C,
      statusNew = 0xFF9FCBFF,
      resolveSwipe = 0xFF4DB6AC,
      onResolveSwipe = 0xFF000000,
      archiveSwipe = 0xFF90A4AE,
      onArchiveSwipe = 0xFF000000,
    )
}

/** The trend chart draws its older bars in primary at this alpha over the surface; the latest bars are full primary. */
internal const val CHART_BAR_ALPHA = 0.7f
