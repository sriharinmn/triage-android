package io.github.sriharinmn.triage

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text file under `app/src` must be valid UTF-8. A stray Windows-1252 byte (a `·` or `…` typed through the wrong
 * code page) still compiles, but aapt and kotlinc turn it into U+FFFD, so users would see "3 users � 3h". This has
 * happened twice, so the build now catches it.
 */
class SourceEncodingTest {
  @Test
  fun everyTextFileUnderSrc_isValidUtf8() {
    // Gradle runs unit tests from the module directory, so `src` is `app/src`.
    val root = File("src")
    assertTrue("expected to run from the app module, but ${root.absolutePath} is missing", root.isDirectory)

    val bad =
      root
        .walkTopDown()
        .filter { it.isFile && it.extension in TEXT_EXTENSIONS }
        .filterNot { it.isValidUtf8() }
        .map { it.invariantSeparatorsPath }
        .toList()

    assertTrue("not valid UTF-8: $bad", bad.isEmpty())
  }

  private fun File.isValidUtf8(): Boolean =
    try {
      Charsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(readBytes()))
      true
    } catch (_: CharacterCodingException) {
      false
    }

  private companion object {
    val TEXT_EXTENSIONS = setOf("kt", "kts", "xml", "txt", "json", "md", "pro")
  }
}
