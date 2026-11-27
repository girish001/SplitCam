import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gireesh.camviewsplit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gireesh.camviewsplit"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { stream ->
                    localProperties.load(stream)
                }
            }

            val keystoreFile = localProperties.getProperty("KEYSTORE_FILE")
                ?: System.getenv("KEYSTORE_FILE")
            val keystorePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = localProperties.getProperty("KEY_ALIAS")
                ?: System.getenv("KEY_ALIAS")
            val keyPassword = localProperties.getProperty("KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")

            val missingConfigs = mutableListOf<String>()
            if (keystoreFile.isNullOrBlank() || !File(keystoreFile).exists()) {
                missingConfigs.add("KEYSTORE_FILE (file missing or unreadable)")
            }
            if (keystorePassword.isNullOrBlank()) {
                missingConfigs.add("KEYSTORE_PASSWORD")
            }
            if (keyAlias.isNullOrBlank()) {
                missingConfigs.add("KEY_ALIAS")
            }
            if (keyPassword.isNullOrBlank()) {
                missingConfigs.add("KEY_PASSWORD")
            }

            if (missingConfigs.isEmpty()) {
                storeFile = File(keystoreFile!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                val gradleTasks = gradle.startParameter.taskNames.joinToString(" ")
                val isReleaseRequested = gradleTasks.contains("release", ignoreCase = true) ||
                        gradleTasks.contains("bundle", ignoreCase = true) ||
                        gradleTasks.contains("publish", ignoreCase = true)

                if (isReleaseRequested) {
                    throw GradleException(
                        "RELEASE BUILD FAILED: Missing required release signing credentials: ${missingConfigs.joinToString(", ")}. " +
                        "Please define KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD in local.properties or environment variables."
                    )
                }
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperty("net.bytebuddy.experimental", "true")
            }
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // Media3 ExoPlayer & Transformer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)

    // Coil for Compose and video frame decoding
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}