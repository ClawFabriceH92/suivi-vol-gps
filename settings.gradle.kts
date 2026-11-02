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

rootProject.name = "suivi-vol-gps"

include(":core")

// Le module Android n'est inclus que si le SDK Android est disponible. Cela permet de
// lancer les tests du module `core` (Kotlin pur) sur une machine sans SDK :
//   SUIVIVOL_CORE_SEUL=1 ./gradlew :core:test
if (System.getenv("SUIVIVOL_CORE_SEUL").isNullOrBlank()) {
    include(":app")
}
