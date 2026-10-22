plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.peyton.toddlergame"
  compileSdk = 34
  defaultConfig { applicationId = "com.peyton.toddlergame"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0" }
  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  lint { abortOnError = false }
  // Animal voice clips live in the PRIVATE submodule PeytonRingling/tap-zoo-sounds at <repo>/sounds (res/raw/animal_*.ogg).
  // If the submodule is absent/empty (public clone), this dir simply contributes nothing and AnimalSounds falls back to
  // the synthesized voices. Resources are looked up by name at runtime, so nothing references R.raw.animal_*.
  sourceSets["main"].res.srcDir(rootProject.file("sounds/res"))
  testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("roborazzi.test.record", "true"); it.maxHeapSize = "3g" } } }
}
// Robolectric/Compose tests need ui-test-manifest (debugImplementation only), so they only run on the debug variant.
// Without this, `./gradlew test` also runs testReleaseUnitTest, which fails with "Unable to resolve activity ... ComponentActivity".
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false } }
dependencies {
  implementation(platform("androidx.compose:compose-bom:2024.06.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.activity:activity-compose:1.9.0")
  implementation("androidx.core:core-ktx:1.13.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.13")
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
