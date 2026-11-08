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

rootProject.name = "MPX Player"
include(":app")
// Conector desktop-sync: modulo Kotlin/JVM ISOLADO, irmao do :app (o :app NAO depende dele).
include(":desktop-agent")
