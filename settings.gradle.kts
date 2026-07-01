pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DevExplorer"

// --- Module graph (see docs/ARCHITECTURE.md) ---
// M1 shipped the spine (app + core:model + core:designsystem).
// M2 adds the domain layer (capability/usecase) and the SAF data layer.
include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:capability")
include(":core:usecase")
include(":data:storage")
// M3 adds the sandbox — the app's only writable surface.
include(":data:workspace")
// M4 adds read-only APK/ZIP analysis.
include(":data:apk")
// M5 adds read-only PackageManager inspection.
include(":data:packages")
