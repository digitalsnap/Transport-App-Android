plugins {
    kotlin("jvm")
}

// Pure Kotlin module — no Android/Framework dependencies per Clean Architecture rules.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation("javax.inject:javax.inject:1")
    testImplementation(kotlin("test"))
}
