plugins {
    kotlin("jvm")
}

// Pure Kotlin module — no Android/Framework dependencies per Clean Architecture rules.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
