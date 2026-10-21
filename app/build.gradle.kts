plugins { id("com.android.application") }
android {
 namespace = "pl.n2s.grabberpie"
 compileSdk = 34
 defaultConfig {
   applicationId = "pl.n2s.grabberpie.cyber199"
   minSdk = 28
   targetSdk = 28
   versionCode = 199
   versionName = "1.9.9"
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
