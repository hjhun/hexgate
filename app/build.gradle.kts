plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "dev.hexgate"
 compileSdk = 36
 defaultConfig { applicationId = "dev.hexgate"; minSdk = 26; targetSdk = 36; versionCode = 16; versionName = "0.1.15"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 packaging { resources.merges += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md"); resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
}
dependencies {
 implementation(project(":terminal-view"))
 implementation(platform("androidx.compose:compose-bom:2025.06.01"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
 implementation("com.hierynomus:sshj:0.41.2")
 implementation("org.bouncycastle:bcprov-jdk18on:1.86")
 implementation("org.bouncycastle:bcutil-jdk18on:1.86")
 implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:rules:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
