plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.secretmelody"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.secretmelody"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "0.7"
        // shortcut: arm64 only keeps the APK small; add "armeabi-v7a" if someone has an old 32-bit phone
        ndk { abiFilters += "arm64-v8a" }
    }

    // yt-dlp's bundled python/ffmpeg must be extracted to disk to run
    packaging { jniLibs { useLegacyPackaging = true } }

    signingConfigs {
        // Fixed debug key so each CI-built APK installs over the previous one. Never use for the Play Store.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // The APK people install: R8 strips unused code and icons (about 60 MB down to a few MB).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // same key as earlier builds, so it installs over them
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.palette:palette:1.0.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // Android's org.json is a stub in local unit tests
}
