// Fichier racine volontairement minimal : chaque module déclare ses plugins (versions dans
// gradle/libs.versions.toml). Ne pas déclarer ici le plugin Android, sinon Gradle le résout même
// pour les builds « core seul » (voir settings.gradle.kts).
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
