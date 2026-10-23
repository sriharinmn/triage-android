package io.github.sriharinmn.triage.ui.detail

import io.github.sriharinmn.triage.data.detail.EventStack
import io.github.sriharinmn.triage.data.detail.StackFrame

private val JVM_FILE = Regex("""\.(java|kt|kts|scala|groovy)$""")

/**
 * The frame's name as a developer reads it. JVM frames show the class's simple name plus the method
 * (`ProfileFragment.bindHeader`). Other frames show the function. If there is no function, the module stands in, and
 * "?" if there's no module either.
 */
internal fun StackFrame.title(): String {
  val function = function ?: return module ?: "?"
  val className = module?.substringAfterLast('.')
  return if (className != null && file?.contains(JVM_FILE) == true) "$className.$function" else function
}

/** "shop/cart.js:42" or "ProfileFragment.kt:88"; the module stands in for a missing file. Null when neither exists. */
internal fun StackFrame.location(): String? {
  val place = file ?: module ?: return null
  return if (lineNo != null) "$place:$lineNo" else place
}

/** The frames shown for this stack: only the app's own unless [revealed], or all of them if none are the app's. */
internal fun EventStack.visibleFrames(revealed: Boolean): List<IndexedValue<StackFrame>> {
  val all = frames.withIndex().toList()
  return if (revealed || frames.none { it.inApp }) all else all.filter { it.value.inApp }
}

/** How many library frames [visibleFrames] leaves out. */
internal fun EventStack.hiddenFrameCount(revealed: Boolean): Int = frames.size - visibleFrames(revealed).size
