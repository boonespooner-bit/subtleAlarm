import java.util.Properties

plugins {
    id("com.android.application")
}

// Single source of truth for the app version, shared with scripts/build-offline.sh.
// Bump it on every change; see CLAUDE.md.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

// Release signing. Create keystore.properties from keystore.properties.example
// with your own upload key - it is gitignored and must never be committed.
// Without it, debug builds still work and only `bundleRelease` is unavailable.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.subtlealarm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.subtlealarm.app"
        minSdk = 26
        targetSdk = 36
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
