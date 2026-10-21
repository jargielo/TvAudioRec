plugins { id("com.android.application") }
android {
 namespace = "pl.n2s.grabberpie"
 compileSdk = 34
 defaultConfig {
   applicationId = "pl.n2s.grabberpie.cyber198"
   minSdk = 28
   targetSdk = 28
   versionCode = 198
   versionName = "1.9.8"
 }
 compileOptions {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
 }
 packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
 implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:6.0.4")
}
