package io.github.sriharinmn.triage.ui.detail

import io.github.sriharinmn.triage.data.detail.EventStack
import io.github.sriharinmn.triage.data.detail.StackFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameLabelsTest {
  private fun frame(function: String?, module: String?, file: String?, line: Int? = 1, inApp: Boolean = true) =
    StackFrame(function = function, module = module, file = file, lineNo = line, colNo = null, inApp = inApp, context = emptyList())

  @Test
  fun jvmFrames_areNamedClassDotMethod() {
    val frame = frame("bindHeader", "com.example.shop.ui.profile.ProfileFragment", "ProfileFragment.kt", line = 88)

    assertEquals("ProfileFragment.bindHeader", frame.title())
    assertEquals("ProfileFragment.kt:88", frame.location())
  }

  @Test
  fun otherFrames_areNamedByFunction() {
    val frame = frame("cartTotal", "shop:cart", "shop/cart.js", line = 42)

    assertEquals("cartTotal", frame.title())
    assertEquals("shop/cart.js:42", frame.location())
  }

  @Test
  fun missingParts_fallBack() {
    assertEquals("com.example.Foo", frame(null, "com.example.Foo", null).title())
    assertEquals("?", frame(null, null, null).title())
    assertEquals("com.example.Foo:1", frame("run", "com.example.Foo", null).location())
    assertEquals("a.js", frame("run", null, "a.js", line = null).location())
    assertNull(frame("run", null, null).location())
  }

  @Test
  fun libraryFrames_hideUntilRevealed() {
    val stack =
      EventStack("E", null, null, listOf(frame("a", null, "a.js"), frame("b", null, "b.js", inApp = false), frame("c", null, "c.js")))

    assertEquals(listOf(0, 2), stack.visibleFrames(revealed = false).map { it.index })
    assertEquals(1, stack.hiddenFrameCount(revealed = false))
    assertEquals(listOf(0, 1, 2), stack.visibleFrames(revealed = true).map { it.index })
    assertEquals(0, stack.hiddenFrameCount(revealed = true))
  }

  @Test
  fun aStackWithoutTheAppsFrames_showsEveryFrame() {
    val stack = EventStack("E", null, null, listOf(frame("a", null, "a.js", inApp = false), frame("b", null, "b.js", inApp = false)))

    assertEquals(2, stack.visibleFrames(revealed = false).size)
    assertEquals(0, stack.hiddenFrameCount(revealed = false))
  }
}
