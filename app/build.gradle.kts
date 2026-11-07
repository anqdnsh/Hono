plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aniq.glassreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aniq.glassreader"
        minSdk = 31
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/release.jks")
            storePassword = "glassreader"
            keyAlias = "glass"
            keyPassword = "glassreader"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    lint { checkReleaseBuilds = false }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("dev.chrisbanes.haze:haze:1.6.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.window:window:1.3.0")
}
