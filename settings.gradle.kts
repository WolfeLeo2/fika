rootProject.name = "Fika"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                // The Navigation SDK depends on Cronet from Google's Maven.
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
include(":riderApp")