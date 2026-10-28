import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.nourtime.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nourtime.app"
        minSdk = 26
        targetSdk = 37
        // 1 locally; a build server or a release sets it with -Pnourtime.versionCode=<n> (Play needs it
        // to grow with each upload).
        versionCode = (project.findProperty("nourtime.versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        // Instrumented tests (app/src/androidTest) run with Hilt's test application; see HiltTestRunner.
        testInstrumentationRunner = "com.nourtime.app.HiltTestRunner"
    }

    // Release signing: keystore.properties in the project root (git-ignored; see README "Release"), or
    // the NOURTIME_KEYSTORE* environment variables on a build server. Without either, release builds
    // are unsigned.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
    val storeFilePath = signingValue("storeFile", "NOURTIME_KEYSTORE")
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingValue("storePassword", "NOURTIME_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NOURTIME_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NOURTIME_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds use the local Firebase Emulator Suite, reached through `adb reverse`, unless
            // built with -Pnourtime.firebaseEmulator=false. For that, also remove the demo
            // app/src/debug/google-services.json so the real app/google-services.json is used.
            val emulator = (project.findProperty("nourtime.firebaseEmulator") as String?)?.toBoolean() ?: true
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", if (emulator) "\"127.0.0.1\"" else "\"\"")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // CI's minified build uses the demo Firebase project, which has no Crashlytics to send the
            // R8 mapping to: -Pnourtime.crashlyticsMappingUpload=false.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = (project.findProperty("nourtime.crashlyticsMappingUpload") as String?)?.toBoolean() ?: true
            }
        }
    }
    lint {
        // Today's warnings are in the baseline (`./gradlew :app:updateLintBaseline` after fixing some);
        // any new warning fails lint, so it can't slip in unnoticed (2026-10-28 audit).
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        // Newer library or tool versions appear on their own and are upgraded on purpose (§28), not
        // when a build happens to see them: listed, never failing.
        informational += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
        // The app speaks Arabic and English only: leave the libraries' other ~80 translations out.
        localeFilters += listOf("en", "ar")
    }
    packaging {
        // Debug and build metadata that a phone never reads.
        resources.excludes += listOf("DebugProbesKt.bin", "kotlin-tooling-metadata.json", "META-INF/*.version", "META-INF/{AL2.0,LGPL2.1}")
    }
    bundle {
        // The in-app language switcher can pick Arabic on an English phone (and the reverse), so a
        // Play download must carry both languages' strings, not only the phone's.
        language { enableSplit = false }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    // The empty activity Compose UI tests render into (debug builds only).
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    // App Check: Play Integrity in release builds, the debug provider in debug builds (AppCheckSetup).
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    kspAndroidTest(libs.hilt.compiler)
}

// AGP 9 gives unit tests the Android build of multiplatform libraries. DataStore's Android file storage
// can't replace an existing file on Windows (Android can), so every second write failed in the unit
// tests on the development PC. Unit tests use DataStore's JVM build, as they did with AGP 8; the app is
// unchanged.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.datastore:datastore-core"))
            .using(module("androidx.datastore:datastore-core-jvm:${libs.versions.datastore.get()}"))
    }
}

// SpeechCatalogTest reads the recorded clips from disk: rerun it when they change.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets/audio").withPropertyName("recordedAudio")
}

// Exported database schemas, committed so every migration can be checked (DatabaseMigrationsTest).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
