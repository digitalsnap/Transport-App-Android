plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// ── Backend wiring ───────────────────────────────────────────────────────────
// Base URLs and the mock-data switch are build config, not source constants, so
// pointing the app at a local or staging CRS never needs a code edit.
// Override per-build from the command line, e.g. against a backend on your LAN:
//   ./gradlew installDebug -Pridevibe.apiBaseUrl.debug=http://192.168.1.5:8080/
// Defaults live in gradle.properties.
val apiBaseUrlDebug = (findProperty("ridevibe.apiBaseUrl.debug") as String?)
    ?: "http://10.0.2.2:8080/"
val apiBaseUrlRelease = (findProperty("ridevibe.apiBaseUrl.release") as String?)
    ?: "https://api.ridevibe.example.com/"
val useMockData = (findProperty("ridevibe.useMocks") as String?)?.toBoolean() ?: true

// Derived so the socket URL can never drift from the REST URL.
fun webSocketUrlFor(httpUrl: String): String = httpUrl
    .replaceFirst("https://", "wss://")
    .replaceFirst("http://", "ws://")
    .trimEnd('/')

android {
    namespace = "com.ridevibe.core.network"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        buildConfig = true // BuildConfig.DEBUG gates the HTTP logging interceptor
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlDebug\"")
            buildConfigField("String", "WS_BASE_URL", "\"${webSocketUrlFor(apiBaseUrlDebug)}\"")
            buildConfigField("boolean", "USE_MOCK_DATA", "$useMockData")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlRelease\"")
            buildConfigField("String", "WS_BASE_URL", "\"${webSocketUrlFor(apiBaseUrlRelease)}\"")
            buildConfigField("boolean", "USE_MOCK_DATA", "$useMockData")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-domain"))

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Staff session token at rest (auth/StaffSessionStore).
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}

// ── Seed export ──────────────────────────────────────────────────────────────
// SeedDataExporter (unit test) writes the mock corpus as JSON into build/seed;
// exportSeedData copies it into docs/seed so the backend has a seed source that
// is generated from the Kotlin, never hand-transcribed.
//
//   ./gradlew :core-network:exportSeedData
val seedStagingDir = layout.buildDirectory.dir("seed")

tasks.withType<Test>().configureEach {
    systemProperty("ridevibe.seedOutDir", seedStagingDir.get().asFile.absolutePath)
}

tasks.register<Copy>("exportSeedData") {
    group = "ridevibe"
    description = "Export terminals, routes and journeys from the mock data to docs/seed/."
    dependsOn("testDebugUnitTest")
    from(seedStagingDir)
    into(rootProject.layout.projectDirectory.dir("docs/seed"))
}
