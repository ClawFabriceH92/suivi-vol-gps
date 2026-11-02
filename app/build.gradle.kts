import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version de l'application : à incrémenter à chaque release (versionCode strictement croissant).
val versionCodeApp = 2
val versionNameApp = "1.0.1"
val dateBuild: String = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
    .format(Date())
val depotGitHub = "ClawFabriceH92/suivi-vol-gps"

android {
    namespace = "fr.suivivol.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.suivivol.app"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeApp
        versionName = versionNameApp
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DATE_BUILD", "\"$dateBuild\"")
        buildConfigField("String", "DEPOT_GITHUB", "\"$depotGitHub\"")
        buildConfigField("String", "URL_VERSION_JSON", "\"https://github.com/$depotGitHub/releases/latest/download/version.json\"")
        buildConfigField("String", "URL_RELEASES", "\"https://github.com/$depotGitHub/releases/latest\"")
    }

    signingConfigs {
        create("release") {
            // Clé de signature fournie par l'environnement (secrets GitHub Actions).
            // Sans ces variables, l'APK « release » est signé avec la clé de debug locale.
            val fichier = System.getenv("SUIVIVOL_KEYSTORE")
            if (!fichier.isNullOrBlank() && file(fichier).exists()) {
                storeFile = file(fichier)
                storePassword = System.getenv("SUIVIVOL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SUIVIVOL_KEY_ALIAS")
                keyPassword = System.getenv("SUIVIVOL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val cle = signingConfigs.getByName("release")
            signingConfig = if (cle.storeFile != null) cle else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.osmdroid.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
