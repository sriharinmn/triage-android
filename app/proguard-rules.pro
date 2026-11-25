# Project-specific R8/ProGuard rules for the release build.
#
# Navigation 3 saves the back stack through a reflection-based lookup of each NavKey by its class name, which would not
# survive process death if R8 renamed Inbox, IssueDetail, Settings and the rest. Keep only their names; the classes
# themselves are still shrunk if unused, and kotlinx.serialization's own consumer rules keep the generated serializers.
-keepnames class * implements androidx.navigation3.runtime.NavKey
