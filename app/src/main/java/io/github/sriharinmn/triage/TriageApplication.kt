package io.github.sriharinmn.triage

import android.app.Application
import androidx.work.Configuration
import io.github.sriharinmn.triage.data.alerts.TriageWorkerFactory

class TriageApplication : Application(), Configuration.Provider {
  lateinit var container: AppContainer
    private set

  /**
   * WorkManager starts on demand with this configuration (the manifest removes its startup initializer), so its workers
   * run against the app's container. Its first use comes after [onCreate]: from [AppContainer.startAlerts], or from a
   * system job, which Android only starts once the application exists.
   */
  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setWorkerFactory(
          TriageWorkerFactory(
            check = { container.alertCheckRunner.runCheck() },
            send = { container.notificationActions.send(it) },
          )
        )
        .build()

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    container.startAlerts()
    container.startWelcomeTracking()
  }
}
