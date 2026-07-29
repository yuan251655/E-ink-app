plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val debugDeviceApiBaseUrl = providers.gradleProperty("eink.debugDeviceApiBaseUrl")
    .getOrElse("http://192.168.4.1")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.einkphoto.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.einkphoto.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // Emulator bridge example: -Peink.debugDeviceApiBaseUrl=http://10.0.2.2:18080
            buildConfigField("String", "DEVICE_API_BASE_URL", "\"$debugDeviceApiBaseUrl\"")
        }
        release {
            // Never inherit a developer bridge endpoint into a distributable build.
            buildConfigField("String", "DEVICE_API_BASE_URL", "\"http://192.168.4.1\"")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.compose.ui:ui:1.7.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.2")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
