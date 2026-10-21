plugins { id("com.android.application") }
android {
 namespace = "pl.n2s.grabberpie"
 compileSdk = 34
 defaultConfig {
   applicationId = "pl.n2s.grabberpie"
   minSdk = 28
   targetSdk = 28
   versionCode = 192
   versionName = "1.9.2"
 }
 compileOptions {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
 }
}
