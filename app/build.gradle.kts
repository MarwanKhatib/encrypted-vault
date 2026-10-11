plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.encryptedvault"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.encryptedvault"
        minSdk = 28 // ImageDecoder (HEIC), StrongBox
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Lets MigrationTestHelper read the exported Room schemas in instrumented tests.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug { buildConfigField("boolean", "HARDENED", "false") }
        release {
            // Shrink + obfuscate + optimise; see proguard-rules.pro. HARDENED switches on the runtime tamper checks.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            buildConfigField("boolean", "HARDENED", "true")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Local testing only: sign with the debug key so the release build can be installed. Replace with your own
            // release keystore (kept off this machine's repo) before distributing the APK anywhere.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Audited streaming AEAD (AES-GCM-HKDF in 1 MiB segments): constant memory, tamper/truncation detection, seekable reads.
    implementation("com.google.crypto.tink:tink-android:1.13.0")
    // Video playback straight from an encrypted DataSource, so no plaintext copy of a video is ever written to disk.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-transformer:1.3.1")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
