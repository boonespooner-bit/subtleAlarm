plugins {
    id("com.android.application")
}

android {
    namespace = "com.subtlealarm.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.subtlealarm.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
