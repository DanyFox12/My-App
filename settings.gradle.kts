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
// Milestone 1 ships the spine: the app + the two foundational core modules.
// Feature/data modules are extracted in their respective milestones.
include(":app")
include(":core:model")
include(":core:designsystem")
