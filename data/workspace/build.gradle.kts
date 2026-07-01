plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.devexplorer.data.workspace"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// The ONLY module that can write. It holds the sole WriteCapability
// implementation and writes exclusively into app-private filesDir/workspace.
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:capability"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
