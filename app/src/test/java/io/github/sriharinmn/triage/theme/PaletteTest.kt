package io.github.sriharinmn.triage.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG AA for every color pair the screens use, in both schemes (4.5:1 for text, 3:1 for glyphs and chart bars), and no
 * role leaning violet, since Sentry's brand is purple.
 */
class PaletteTest {
  private class Scheme(val name: String, val roles: SchemeHex, val semantic: SemanticHex)

  private val schemes =
    listOf(
      Scheme("light", CobaltPalette.light, SemanticPalette.light),
      Scheme("dark", CobaltPalette.dark, SemanticPalette.dark),
    )

  /** Text and the background it sits on. */
  private fun textPairs(s: SchemeHex, m: SemanticHex): Map<String, Pair<Long, Long>> =
    mapOf(
      "onSurface on surface (titles, body)" to (s.onSurface to s.surface),
      "onSurfaceVariant on surface (culprits, meta lines, labels)" to (s.onSurfaceVariant to s.surface),
      "primary on surface (short ids, text buttons)" to (s.primary to s.surface),
      "error on surface (Connect's form errors)" to (s.error to s.surface),
      "onSurface on surfaceContainer (stat strip values)" to (s.onSurface to s.surfaceContainer),
      "onSurfaceVariant on surfaceContainer (stat strip labels)" to (s.onSurfaceVariant to s.surfaceContainer),
      "onSurface on surfaceContainerLow (alerts card title)" to (s.onSurface to s.surfaceContainerLow),
      "onSurfaceVariant on surfaceContainerLow (alerts card text)" to (s.onSurfaceVariant to s.surfaceContainerLow),
      "primary on surfaceContainerLow (alerts card buttons)" to (s.primary to s.surfaceContainerLow),
      "onSurfaceVariant on surfaceContainerHigh (offline banner)" to (s.onSurfaceVariant to s.surfaceContainerHigh),
      "primary on surfaceContainerHigh (offline banner Retry)" to (s.primary to s.surfaceContainerHigh),
      "onPrimary on primary (hot tile, Get started)" to (s.onPrimary to s.primary),
      "onPrimaryContainer on primaryContainer (warm tile, org tile, step numbers)" to
        (s.onPrimaryContainer to s.primaryContainer),
      "onSurfaceVariant on surfaceContainerHighest (quiet tile)" to (s.onSurfaceVariant to s.surfaceContainerHighest),
      "onSecondaryContainer on secondaryContainer (selected chips and segments)" to
        (s.onSecondaryContainer to s.secondaryContainer),
      "onErrorContainer on errorContainer (reconnect banner)" to (s.onErrorContainer to s.errorContainer),
      "onSurface on errorContainer (the error line in code)" to (s.onSurface to s.errorContainer),
      "inverseOnSurface on inverseSurface (snackbars)" to (s.inverseOnSurface to s.inverseSurface),
      "status Escalating on surface" to (m.statusEscalating to s.surface),
      "status Regressed on surface" to (m.statusRegressed to s.surface),
      "status New on surface" to (m.statusNew to s.surface),
      "the Resolve label on its swipe color" to (m.onResolveSwipe to m.resolveSwipe),
      "the Archive label on its swipe color" to (m.onArchiveSwipe to m.archiveSwipe),
    )

  /** Glyphs, marks and bars (WCAG 1.4.11) against the surface they're drawn on. */
  private fun graphicPairs(s: SchemeHex, m: SemanticHex): Map<String, Pair<Long, Long>> =
    mapOf(
      "the error and fatal glyph" to (m.levelError to s.surface),
      "the warning glyph" to (m.levelWarning to s.surface),
      "the info glyph" to (m.levelInfo to s.surface),
      "the debug and unknown glyph (onSurfaceVariant)" to (s.onSurfaceVariant to s.surface),
      "the chart's latest bars (primary)" to (s.primary to s.surface),
      "the chart's older bars (primary at CHART_BAR_ALPHA)" to (blend(s.primary, s.surface, CHART_BAR_ALPHA) to s.surface),
      "outline (segmented buttons, text fields)" to (s.outline to s.surface),
    )

  @Test
  fun everyTextPair_meetsWcagAA() {
    val failures =
      schemes.flatMap { scheme ->
        textPairs(scheme.roles, scheme.semantic).mapNotNull { (name, pair) -> below(4.5, scheme.name, name, pair) }
      }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  @Test
  fun everyGlyphAndBar_meetsThreeToOne() {
    val failures =
      schemes.flatMap { scheme ->
        graphicPairs(scheme.roles, scheme.semantic).mapNotNull { (name, pair) -> below(3.0, scheme.name, name, pair) }
      }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  @Test
  fun theChartsOlderBars_stayDistinctFromTheLatestOnes() {
    for (scheme in schemes) {
      val older = blend(scheme.roles.primary, scheme.roles.surface, CHART_BAR_ALPHA)
      val ratio = contrast(older, scheme.roles.primary)
      assertTrue("${scheme.name}: older vs latest bars are only ${"%.2f".format(ratio)}:1", ratio >= 1.5)
    }
  }

  @Test
  fun noRole_leansViolet() {
    // Sentry's purples sit at HSL 247°–252°; every role of ours stays blue (or teal for the tertiary). Errors are red.
    for (scheme in schemes) {
      for ((role, argb) in scheme.roles.roles()) {
        if ("error" in role.lowercase()) continue
        val hue = hue(argb) ?: continue
        assertTrue("${scheme.name} $role ${"#%06X".format(argb and 0xFFFFFF)} has hue $hue°", hue in 180.0..240.0)
      }
    }
  }

  private fun below(minimum: Double, scheme: String, name: String, pair: Pair<Long, Long>): String? {
    val ratio = contrast(pair.first, pair.second)
    return if (ratio >= minimum) null else "$scheme: $name is ${"%.2f".format(ratio)}:1, needs $minimum:1"
  }

  /** Every role by name, read from the data class's fields (no kotlin-reflect needed). */
  private fun SchemeHex.roles(): Map<String, Long> =
    SchemeHex::class.java.declaredFields
      .filter { it.type == java.lang.Long.TYPE }
      .associate { field ->
        field.isAccessible = true
        field.name to field.getLong(this)
      }

  private fun channel(argb: Long, shift: Int): Double = ((argb shr shift) and 0xFF).toDouble() / 255.0

  /** WCAG 2.x relative luminance. */
  private fun luminance(argb: Long): Double {
    fun linear(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * linear(channel(argb, 16)) + 0.7152 * linear(channel(argb, 8)) + 0.0722 * linear(channel(argb, 0))
  }

  private fun contrast(a: Long, b: Long): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
  }

  /** [fg] at [alpha] over [bg], channel by channel in sRGB, as Compose's alpha drawing and `compositeOver` do. */
  private fun blend(fg: Long, bg: Long, alpha: Float): Long {
    var out = 0xFF000000L
    for (shift in listOf(16, 8, 0)) {
      val mixed = alpha * ((fg shr shift) and 0xFF) + (1 - alpha) * ((bg shr shift) and 0xFF)
      out = out or (mixed.roundToLong().coerceIn(0L, 255L) shl shift)
    }
    return out
  }

  /** HSL hue in degrees, or null for a gray (no hue). */
  private fun hue(argb: Long): Double? {
    val r = channel(argb, 16)
    val g = channel(argb, 8)
    val b = channel(argb, 0)
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    if (delta == 0.0) return null
    val sector =
      when (max) {
        r -> ((g - b) / delta).mod(6.0)
        g -> (b - r) / delta + 2
        else -> (r - g) / delta + 4
      }
    return sector * 60
  }
}
