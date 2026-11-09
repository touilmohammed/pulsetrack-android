import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Les rappels FCM demandent le google-services.json du projet Firebase. Il reste hors du dépôt public :
// sans lui, l'app se compile quand même, et les réglages expliquent que les rappels sont indisponibles.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// La clé de signature release : hors du dépôt (keystore.properties et le .jks sont ignorés par Git).
// Sans ce fichier (un autre poste, la CI), le build release reste possible mais non signé.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "corsica.universita.pulsetrack"
    compileSdk = 37

    defaultConfig {
        applicationId = "corsica.universita.pulsetrack"
        minSdk = 26 // Android 8 : les canaux de notification existent partout
        targetSdk = 37
        versionCode = 10
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // L'API de la PWA, sur la même origine que le site. Pour tester une branche du serveur avant de la
        // mettre en production : ./gradlew assembleDebug -Ppulsetrack.apiBaseUrl=https://<branche>--pulsetrackisco.netlify.app/
        val apiBaseUrl = providers.gradleProperty("pulsetrack.apiBaseUrl").orElse("https://pulsetrackisco.netlify.app/").get()
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        if (keystoreProperties != null) create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 : retire le code inutilisé, raccourcit les noms, supprime les ressources orphelines.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Le schéma de chaque version de la base, commité : la mémoire de ce qui vit chez les utilisateurs.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.play.services.location)
    implementation(libs.exifinterface)
    implementation(libs.lifecycle.process)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}
