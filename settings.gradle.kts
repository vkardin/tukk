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

rootProject.name = "tukk"
include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:ui",
    ":detection",
    ":actions",
    ":service",
    ":feature:home",
    ":feature:editor",
    ":feature:settings",
)
