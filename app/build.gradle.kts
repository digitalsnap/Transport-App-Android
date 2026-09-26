import com.google.firebase.appdistribution.gradle.AppDistributionExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    // Processes app/google-services.json into resources (e.g. default_web_client_id).
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.appdistribution)
}

// Release signing credentials. Never committed: keystore.properties is gitignored;
// a CI job can instead supply the same four values as RIDEVIBE_* environment variables.
// See RELEASING.md for how to generate the keystore and fill this in.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSecret(key: String, envVar: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(envVar))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSecret("storeFile", "RIDEVIBE_STORE_FILE")
val hasReleaseKeystore = releaseStoreFile != null

android {
    namespace = "com.ridevibe.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ridevibe.app"
        minSdk = 24
        targetSdk = 34
        // Every App Distribution upload needs a fresh versionCode — the console
        // rejects duplicates. Bump ridevibe.versionCode in gradle.properties, or
        // override per-build with -Pridevibe.versionCode=<n> from CI.
        versionCode = (findProperty("ridevibe.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("ridevibe.versionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingSecret("storePassword", "RIDEVIBE_STORE_PASSWORD")
                keyAlias = signingSecret("keyAlias", "RIDEVIBE_KEY_ALIAS")
                keyPassword = signingSecret("keyPassword", "RIDEVIBE_KEY_PASSWORD")
            }
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "RideVibe: no release keystore configured — assembleRelease will produce an " +
                        "UNSIGNED APK that testers cannot install. See RELEASING.md."
                )
            }

            // Upload the R8 mapping file so beta crash reports deobfuscate.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }

            configure<AppDistributionExtension> {
                artifactType = "APK"
                // Tester groups are managed in the Firebase console under App Distribution.
                groups = "beta"
                releaseNotesFile = "${rootDir}/release-notes.txt"
            }
        }
        debug {
            // Local debug crashes are noise in the Crashlytics dashboard.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-network"))
    implementation(project(":feature-search"))
    implementation(project(":feature-seatmap"))
    implementation(project(":feature-checkout"))
    implementation(project(":feature-ticket"))
    implementation(project(":feature-admin"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Social sign-in
    implementation(libs.play.services.auth)
    implementation(libs.facebook.login)

    // Firebase — the BoM pins every Firebase artifact's version
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}