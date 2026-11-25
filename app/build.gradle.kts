plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.sriharinmn.triage"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.sriharinmn.triage"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Installs next to the release build (the owner's daily app), so development never touches its connection.
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The phone check of demo mode: installs next to the debug app (its own applicationId), so the owner's real
        // connection in the debug app is never touched.
        create("sample") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".sample"
            versionNameSuffix = "-sample"
            matchingFallbacks += listOf("debug")
        }
    }
    sourceSets {
        getByName("sample") {
            // Built-in Kotlin only compiles .kt files from the kotlin dirs, so the debug receiver needs this, not java.srcDir.
            kotlin.directories += "src/debug/java"
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
  // Commit the schema JSON so future migrations can be reviewed and tested.
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)

  // Coroutines runtime: declared so the app and the unit tests run the same version
  implementation(libs.kotlinx.coroutines.android)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Networking
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)

  // Persistence
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)

  // Background alert checks and notification actions. work-testing needs a real Context, so it's for device tests only.
  implementation(libs.androidx.work.runtime.ktx)
  androidTestImplementation(libs.androidx.work.testing)
}
