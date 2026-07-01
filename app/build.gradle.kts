import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is configured out-of-band so no secret ever lives in the repo.
// Provide either a `keystore.properties` at the repo root (git-ignored) or the
// matching environment variables (handy for CI). If neither is present, a
// release build still assembles — just unsigned — so `assembleRelease` never
// fails for contributors who only want to exercise R8.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}

fun secret(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("storeFile", "DEVEXPLORER_KEYSTORE")
val hasReleaseSigning = releaseStoreFile != null

android {
    namespace = "com.devexplorer.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.devexplorer.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        // Only ship the densities/locales we actually use later; keeps the APK
        // small for low-storage devices.
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = secret("storePassword", "DEVEXPLORER_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "DEVEXPLORER_KEY_ALIAS")
                keyPassword = secret("keyPassword", "DEVEXPLORER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            // R8 shrinking + resource shrinking → smaller, faster on low-end devices.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign with the release key when one is configured; otherwise leave
            // the APK unsigned (still builds) so CI/local can validate shrinking.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        // checkDependencies makes `:app:lint` also analyze every library module,
        // so one lint run covers the whole graph. Advisory for now (like detekt):
        // it writes HTML/XML reports without failing the build on a pre-existing
        // codebase. Flip abortOnError to true once the reports are clean.
        checkDependencies = true
        abortOnError = false
        warningsAsErrors = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:capability"))
    implementation(project(":core:usecase"))
    implementation(project(":data:storage"))
    implementation(project(":data:workspace"))
    implementation(project(":data:apk"))
    implementation(project(":data:packages"))
    implementation(project(":data:db"))
    implementation(project(":data:work"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
