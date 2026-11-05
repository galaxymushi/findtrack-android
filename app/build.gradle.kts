import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Google Maps API key is read from local.properties (never committed to source).
// Add a line like:  maps.api.key=AIza...   to android/local.properties
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsApiKey: String = localProps.getProperty("maps.api.key", "")

android {
    namespace = "com.findtrack.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.findtrack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.3.2"
        vectorDrawables.useSupportLibrary = true

        // Injected into AndroidManifest as ${MAPS_API_KEY}. Empty is allowed —
        // the app falls back to its offline stylized map when no key is present.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    signingConfigs {
        // A development release keystore generated for this project (see README "Signing").
        // For production distribution, replace with your own credentials / CI secrets.
        val releaseKeystore = file("findtrack.keystore")
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = "findtrack123"
                keyAlias = "findtrack"
                keyPassword = "findtrack123"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Lifecycle (ViewModel, runtime-compose collection, foreground service base)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")

    // Compose UI + Material 3 + icon set used by the approved design
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation between the design's screens
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Persistence: saved devices + location history (offline-first, no cloud)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Google Maps (real map tiles when an API key is configured) + Compose map
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")
    // High-accuracy fused location provider (Google Play services)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Preferences (demo mode, sound feedback)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
