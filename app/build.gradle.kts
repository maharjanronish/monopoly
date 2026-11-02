plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.local.monopoly"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.local.monopoly"
        minSdk = 24        // Android 7.0+ (~98% of devices)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 shrinking keeps the release APK tiny (~2 MB).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the debug key so the CI-built release APK is
            // directly installable. Replace with a real keystore before
            // publishing to a store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM keeps all Compose versions consistent.
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // Intentionally NOT included (to keep the app tiny):
    // - no material-icons (emoji + code-drawn shapes are used instead)
    // - no image / font / serialization / navigation libraries
    // - no coroutines-service, no networking, no analytics
}
