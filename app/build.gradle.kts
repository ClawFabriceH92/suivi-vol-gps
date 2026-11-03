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

val storePassword = secretEnv("STORE_PASSWORD")
val keyAliasValue = secretEnv("KEY_ALIAS") ?: "suivivol"
val keyPasswordValue = secretEnv("KEY_PASSWORD") ?: storePassword
val peutSigner = keystoreFile.exists() && !storePassword.isNullOrBlank()

android {
    namespace = "com.trucdecomptable.suivivol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trucdecomptable.suivivol"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("fr")
    }

    signingConfigs {
        if (peutSigner) {
            create("release") {
                storeFile = keystoreFile
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

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
