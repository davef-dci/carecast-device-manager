plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.carecast.devicemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.carecast.devicemanager"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1"
    }

    flavorDimensions += "target"
    productFlavors {
        create("prod") {
            dimension = "target"
            buildConfigField("String", "FIRESTORE_PROJECT_ID", "\"carecast-v2\"")
        }
        create("sandbox") {
            dimension = "target"
            applicationIdSuffix = ".sandbox"
            versionNameSuffix = "-sandbox"
            buildConfigField("String", "FIRESTORE_PROJECT_ID", "\"carecast-sandbox\"")
        }
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
