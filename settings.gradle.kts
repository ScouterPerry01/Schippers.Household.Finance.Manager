rootProject.name = "household-finance-manager"

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

include(
    ":core:money",
    ":core:calc",
    ":core:domain",
    ":core:security",
    ":core:data",
    ":core:data-jdbc",
    ":core:books",
    ":core:i18n",
    ":core:importers",
    ":core:sync",
    ":core:ocr",
    ":core:ocr-desktop",
    ":app:desktop",
    ":app:android",
)
