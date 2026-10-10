plugins {
    id("com.android.application")
    // AGP 9+ ma wbudowane wsparcie Kotlina - osobna wtyczka kotlin-android nie jest potrzebna
}

android {
    namespace = "pl.spacerlogger.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "pl.spacerlogger.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    // Mapa OpenStreetMap - nie wymaga klucza API
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
