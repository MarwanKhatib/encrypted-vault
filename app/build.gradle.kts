import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

// Static checks: `./gradlew ktlintCheck detekt`. Fix formatting with `./gradlew ktlintFormat`.
ktlint {
    version.set("1.3.1")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// Release signing comes from keystore.properties in the project root (git-ignored; see keystore.properties.example).
// Without it, release falls back to the debug key so local installs still work, and the build says so.
val releaseKeystore: Properties? =
    rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
if (releaseKeystore == null) logger.warn("keystore.properties not found: release APK is signed with the debug key. Do not distribute it.")

android {
    namespace = "com.example.encryptedvault"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.encryptedvault"
        minSdk = 28 // ImageDecoder (HEIC), StrongBox
        targetSdk = 34
        versionCode = 4 // bump on every release; Android refuses to install an older versionCode over a newer one
        versionName = "0.4.0" // must match the release tag (v0.4.0); the updater compares it with the latest tag
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Lets MigrationTestHelper read the exported Room schemas in instrumented tests.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures {
        compose = true
        buildConfig = true
    }
    signingConfigs {
        releaseKeystore?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug { buildConfigField("boolean", "HARDENED", "false") }
        release {
            // Shrink + obfuscate + optimise; see proguard-rules.pro. HARDENED switches on the runtime tamper checks.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            buildConfigField("boolean", "HARDENED", "true")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
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
    // Password-protected zip (AES-256) for the portable backup (ADR-014). Pure Java, so it also runs in JVM unit tests.
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // Video playback straight from an encrypted DataSource, so no plaintext copy of a video is ever written to disk.
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-transformer:1.3.1")

    testImplementation("junit:junit:4.13.2")
    // Android ships org.json; the JVM unit tests need the same API to check ReleaseFeed.parseLatest.
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
