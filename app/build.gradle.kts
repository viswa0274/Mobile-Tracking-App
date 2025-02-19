plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.tracking"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tracking"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.okhttp)
    // Firebase dependencies
    implementation(platform(libs.firebase.bom)) // Firebase BOM
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)

    implementation(libs.play.services.location)

    // OpenStreetMap (OSMDroid)
    implementation(libs.osmdroid.android)  // OSMDroid Android map library
    implementation(libs.osmdroid.wms)     // OSMDroid WMS support
    implementation(libs.firebase.messaging.ktx)  // Firebase Messaging KTX for push notifications
    implementation(libs.androidx.work.runtime.ktx)  // WorkManager for background tasks
    implementation(libs.androidx.media3.common.ktx)  // Media3 for media playback
    implementation(libs.volley)  // Volley for network operations
    implementation(libs.firebase.messaging)  // Firebase Messaging core
    implementation(libs.androidx.lifecycle.livedata.ktx)  // LiveData KTX for lifecycle-aware data
    implementation(libs.androidx.lifecycle.viewmodel.ktx)  // ViewModel KTX for lifecycle-aware UI
    implementation(libs.androidx.navigation.fragment.ktx)  // Navigation component for fragments
    implementation(libs.androidx.navigation.ui.ktx)  // Navigation component UI handling
    implementation(libs.androidx.navigation.runtime.ktx)  // Runtime navigation component
    implementation(libs.firebase.storage.ktx)  // Firebase Storage KTX for file operations
    implementation(libs.picasso)  // Picasso for image loading
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.camera.view)
    implementation(libs.glide)

    // Testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
