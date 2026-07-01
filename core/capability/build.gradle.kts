plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. Holds the repository *interfaces* and the capability model
// that the whole app is built around. No Android, no IO — just contracts.
// See docs/ARCHITECTURE.md §2.2 and §10.
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
