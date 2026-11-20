plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mitrasis.tarot"
    compileSdk = 35

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        applicationId = "com.mitrasis.tarot"
        minSdk = 24
        targetSdk = 35
        versionCode = 76
        versionName = "9.8"
    }

    signingConfigs {
        create("stable") {
            storeFile = file("../keystore/tarot.jks")
            storePassword = "tarotdebug"
            keyAlias = "tarot"
            keyPassword = "tarotdebug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
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

    packaging {
        jniLibs {
            // сжимаем нативные библиотеки: ONNX Runtime иначе лежит в APK
            // несжатым на 36 МБ и не влезает в лимит Telegram
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events("failed", "standardOut")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStandardStreams = true
            }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
}
