plugins { id("com.android.application") }
android {
 namespace = "pl.n2s.grabberpie"
 compileSdk = 34
 defaultConfig {
   applicationId = "pl.n2s.grabberpie.cyber196"
   minSdk = 28
   targetSdk = 28
   versionCode = 196
   versionName = "1.9.6"
 }
 compileOptions {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
 }
}
