plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.phpserverapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.phpserverapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // No native compilation happens here (no NDK toolchain needed in CI) —
        // AGP just packages whatever ABI folders it finds under jniLibs/.
        // Only commit binaries for the ABIs you actually have a php build for.
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Lets us ship the php executable inside jniLibs so it lands in
    // nativeLibraryDir, which is one of the few app-writable/executable
    // locations Android still permits code execution from.
    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
