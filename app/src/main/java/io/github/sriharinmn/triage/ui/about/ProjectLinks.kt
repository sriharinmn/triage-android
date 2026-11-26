package io.github.sriharinmn.triage.ui.about

/** Where Triage's source lives and how to reach its maintainer. Opened in the browser or mail app, never fetched. */
object ProjectLinks {
  const val SOURCE_URL = "https://github.com/sriharinmn/triage-android"
  const val CONTACT_EMAIL = "triagedev2026@gmail.com"
  const val CONTACT_URI = "mailto:$CONTACT_EMAIL"
}

/** Triage's own line in the licenses list. */
val TRIAGE_ITSELF = OpenSourceLibrary("Triage", "Srihari Kanakaraj", OpenSourceLicense.Apache2)
