pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MinogaTVBox"

// Core modules
include(":app")
include(":core:core-model")
include(":core:core-database")
include(":core:core-network")
include(":core:core-parser")
include(":core:core-data")

// Feature modules
include(":feature:feature-channels")
