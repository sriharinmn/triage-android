package io.github.sriharinmn.triage

import android.app.Application

class TriageApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
