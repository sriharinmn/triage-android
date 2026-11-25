# Project-specific R8/ProGuard rules for the release build.
#
# Navigation 3 saves the back stack through a reflection-based lookup of each NavKey by its class name, which would not
# survive process death if R8 renamed Inbox, IssueDetail, Settings and the rest. Keep only their names; the classes
# themselves are still shrunk if unused, and kotlinx.serialization's own consumer rules keep the generated serializers.
-keepnames class * implements androidx.navigation3.runtime.NavKey

# WorkManager stores each queued job's worker class name in its database, and TriageWorkerFactory matches on it. Keep
# the workers' names so a later release, with a different R8 mapping, still finds the jobs an earlier one queued.
-keepnames class * extends androidx.work.ListenableWorker
