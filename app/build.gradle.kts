plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.guardianangel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardianangel"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.17.0"

        // ONNX Runtime ships native code; keep real phones (arm) and the emulator (x86_64).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // One fixed key for every build, so a new APK installs over the old one and keeps your data.
    // Android refuses updates signed with a different key. Never replace this keystore.
    signingConfigs {
        create("guardian") {
            storeFile = file("signing/guardian.keystore")
            storePassword = "guardian"
            keyAlias = "guardian"
            keyPassword = "guardian"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("guardian")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("guardian")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-video:$camerax")

    // On-device nudity detection for explicit photo proof (NudeNet model in assets/models).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    testImplementation("junit:junit:4.13.2")
}
