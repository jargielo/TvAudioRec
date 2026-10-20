plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.tomasz.tvaudiorec"
    compileSdk = 34
    defaultConfig {
        applicationId = "pl.n2s.tvaudiorecorder.v13"
        minSdk = 29
        targetSdk = 32
        versionCode = 13
        versionName = "1.3.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
