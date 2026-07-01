plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// :core:model is a PURE Kotlin/JVM module — no Android dependency on purpose.
// That keeps domain types framework-free (no android.net.Uri, no java.io.File
// leaking through the app), makes them trivially unit-testable, and builds fast
// because there's no AAPT2 / manifest step. See docs/ARCHITECTURE.md §2.2.
dependencies {
    implementation(libs.kotlinx.serialization.json)
}
