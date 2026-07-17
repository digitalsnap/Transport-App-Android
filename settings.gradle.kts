pluginManagement {
    repositories {
        google()
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

rootProject.name = "RideVibe"

include(
    ":app",
    ":core-network",
    ":core-domain",
    ":feature-search",
    ":feature-seatmap",
    ":feature-checkout",
    ":feature-ticket",
)
