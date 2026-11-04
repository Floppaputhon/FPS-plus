plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.redmagic.fps"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { applicationId = "com.redmagic.fps"; minSdk = 26; targetSdk = 36; versionCode = 7; versionName = "1.0" }
    buildFeatures { compose = true; aidl = true; buildConfig = true }
    val releaseStore = providers.environmentVariable("FPSPLUS_KEYSTORE").orNull
    val releasePassword = providers.environmentVariable("FPSPLUS_KEY_PASSWORD").orNull
    val releaseAlias = providers.environmentVariable("FPSPLUS_KEY_ALIAS").orNull
    if (releaseStore != null && releasePassword != null && releaseAlias != null) {
        signingConfigs.create("releaseKey") {
            storeFile = file(releaseStore)
            storePassword = releasePassword
            keyAlias = releaseAlias
            keyPassword = releasePassword
        }
    }
    buildTypes {
        release {
            // UserService is loaded by class name; keep its class unobfuscated.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("releaseKey")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
dependencies { implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0") }

dependencies { testImplementation("junit:junit:4.13.2") }
