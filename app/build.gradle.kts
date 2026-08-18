import java.util.Properties

plugins {
    id("com.android.application")
}

// Single source of truth for the app version, shared with scripts/build-offline.sh.
// Bump it on every change; see CLAUDE.md.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.subtlealarm.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.subtlealarm.app"
        minSdk = 26
        targetSdk = 34
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
