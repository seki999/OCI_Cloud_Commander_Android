plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.compose"); kotlin("plugin.serialization") }
android {
 namespace = "com.example.ocicloudcommander"
 compileSdk = 35
 defaultConfig { applicationId = "com.example.ocicloudcommander"; minSdk = 29; targetSdk = 35; versionCode = 3; versionName = "0.2.1"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 sourceSets["main"].assets.srcDir(rootProject.file("content"))
}
dependencies {
 implementation(project(":core:model")); implementation(project(":core:simulator")); implementation(project(":core:command")); implementation(project(":core:content")); implementation(project(":core:persistence"))
 implementation(platform("androidx.compose:compose-bom:2024.09.00"))
 implementation("androidx.activity:activity-compose:1.9.2")
 implementation("androidx.compose.material3:material3"); implementation("androidx.compose.ui:ui-tooling-preview"); debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6"); implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
 implementation("androidx.navigation:navigation-compose:2.8.1")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00")); androidTestImplementation("androidx.compose.ui:ui-test-junit4"); androidTestImplementation("androidx.test.ext:junit:1.2.1"); debugImplementation("androidx.compose.ui:ui-test-manifest")
}
