plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.tomasz.tvaudiorec"
    compileSdk = 34
    defaultConfig {
        applicationId = "pl.n2s.tvaudiorecorder.v15"
        minSdk = 29
        targetSdk = 32
        versionCode = 15
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
