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
        // jcenter() // Commented out as it's deprecated and might cause issues
        maven { url = uri("https://jitpack.io") } // Alternative source for libsodium
    }
}

rootProject.name = "XIANWALLETAPP"
include(":app")
 