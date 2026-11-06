import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Signature release : même keystore à chaque build, sinon Android refuse
// l'installation par-dessus l'ancienne version. Le mot de passe vit dans
// /root/.secrets (jamais dans le dépôt) ; la CI le recevra par secret.
// ---------------------------------------------------------------------------
val keystoreFile = File("/root/.secrets/keystores-android/suivivol-release.keystore")
val keystoreEnv = File("/root/.secrets/keystores-android/suivivol-keystore.env")

fun secretEnv(cle: String): String? {
    System.getenv(cle)?.takeIf { it.isNotBlank() }?.let { return it }
    if (!keystoreEnv.exists()) return null
    keystoreEnv.readLines().forEach { ligne ->
        val i = ligne.indexOf('=')
        if (i > 0 && ligne.substring(0, i).trim() == cle) return ligne.substring(i + 1).trim()
    }
    return null
}

// En CI, le keystore n'existe pas sur le disque : il arrive en base64 dans un
// secret, on le matérialise dans un fichier temporaire. Le même keystore qu'en
// local, sinon Android refuse l'installation par-dessus la version précédente.
val keystoreCi: File? = System.getenv("SUIVIVOL_KEYSTORE_B64")
    ?.takeIf { it.isNotBlank() }
    ?.let { b64 ->
        val f = File(System.getenv("RUNNER_TEMP") ?: System.getProperty("java.io.tmpdir"), "suivivol-release.keystore")
        f.parentFile?.mkdirs()
        f.writeBytes(Base64.getDecoder().decode(b64.trim()))
        f
    }

val storePassword = secretEnv("SUIVIVOL_KEYSTORE_PASSWORD") ?: secretEnv("STORE_PASSWORD")
val keyAliasValue = secretEnv("SUIVIVOL_KEY_ALIAS") ?: secretEnv("KEY_ALIAS") ?: "suivivol"
val keyPasswordValue = secretEnv("SUIVIVOL_KEY_PASSWORD") ?: secretEnv("KEY_PASSWORD") ?: storePassword
val keystoreRetenu = keystoreCi ?: keystoreFile.takeIf { it.exists() }
val peutSigner = keystoreRetenu != null && !storePassword.isNullOrBlank()

android {
    namespace = "com.trucdecomptable.suivivol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fabrice.suivivol"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.5.2"
        resourceConfigurations += listOf("fr")
    }

    signingConfigs {
        if (peutSigner) {
            create("release") {
                storeFile = keystoreRetenu
                storePassword = this@Build_gradle.storePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (peutSigner) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME sert au contrôle de mise à jour.
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Carte hors ligne : tuiles téléchargées avant le décollage, puis lues du cache.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Mise à jour : téléchargement de l'APK depuis GitHub Releases.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
