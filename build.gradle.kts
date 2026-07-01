import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Root build file. Plugins are declared here with `apply false` so each module
// can opt in without re-declaring versions — versions live in the catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // Applied (not `apply false`) so its classpath is available to `allprojects`
    // below, where every module opts in to the shared detekt configuration.
    alias(libs.plugins.detekt)
}

// Static analysis across the whole module graph. A single config lives at
// config/detekt/detekt.yml so every module is held to the same bar.
//
// ignoreFailures = true keeps detekt advisory for now: CI reports findings
// (SARIF/HTML) without turning red on a codebase that predates the tool. To
// make it blocking, generate a baseline (`./gradlew detektBaseline`) and flip
// this to false.
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        ignoreFailures = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.path
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            sarif.required.set(true)
            html.required.set(true)
            md.required.set(false)
            txt.required.set(false)
            xml.required.set(false)
        }
    }
}
