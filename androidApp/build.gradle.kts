import java.util.Properties

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":riderApp"))
    implementation(project(":shared"))
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.androidx.activity.compose)
    // The Navigation SDK requires core library desugaring (the _nio flavor, as the SDK demands).
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    // FusedLocationProviderClient for the foreground tracking service (CLAUDE.md); not available in AndroidX itself.
    implementation(libs.play.services.location)
    // The turn-by-turn feed service below reads the SDK's model classes, which riderApp does not re-export.
    implementation(libs.google.navigation)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

val secrets = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.wolfeleo2.fika"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.wolfeleo2.fika"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // Secrets come from local.properties (git-ignored); empty strings keep the build working without them.
        fun secret(key: String) = "\"${secrets.getProperty(key, "")}\""
        buildConfigField("String", "SUPABASE_URL", secret("supabase.url"))
        buildConfigField("String", "SUPABASE_ANON_KEY", secret("supabase.anonKey"))
        // Google Maps/Navigation key. Empty means in-app navigation is off and Start delivery opens Google Maps.
        manifestPlaceholders["mapsApiKey"] = secrets.getProperty("maps.apiKey", "")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}