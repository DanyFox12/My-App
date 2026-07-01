plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM orchestration. Use-cases depend only on repository interfaces
// (:core:capability) and domain models (:core:model) — never on Android or a
// concrete data implementation. That's what keeps them unit-testable.
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:capability"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
