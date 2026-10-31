plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jdreserve.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jdreserve.app"
        minSdk = 26          // java.time is available natively from API 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// No third-party dependencies on purpose: the app uses only the Android platform
// (android.app.Activity, ListView, etc.), so there is nothing extra to download or break.
