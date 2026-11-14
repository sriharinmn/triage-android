package io.github.sriharinmn.triage.ui.detail

/**
 * A release name short enough for a narrow spot: "shop-api@1.4.2" shows as "1.4.2". Sentry names releases
 * `package@version`, so this is the part after the last `@`, or the whole [name] when nothing follows it (a bare version
 * or a commit SHA stays as it is).
 */
internal fun shortRelease(name: String): String {
  val version = name.substringAfterLast('@', missingDelimiterValue = name)
  return version.ifBlank { name }
}
