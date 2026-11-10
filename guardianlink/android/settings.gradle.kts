pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "guardianlink-android"

include(":core:common")
include(":core:storage")
include(":core:network")
include(":core:designsystem")
include(":feature:auth")
include(":feature:pairing")
include(":feature:dashboard")
include(":feature:childstatus")
include(":feature:usage")
include(":feature:location")
include(":app-parent")
include(":app-child")
