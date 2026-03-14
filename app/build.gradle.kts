plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services")
}

android {
    namespace = "com.abhinav.upcycleengine"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.abhinav.upcycleengine"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)


    // CameraX core & UI
    val cameraxVersion = "1.6.0-rc01"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
        // The CameraX to ML Kit Bridge
    implementation("androidx.camera:camera-mlkit-vision:$cameraxVersion")

    // The ML kit
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mlkit:object-detection-custom:17.0.2")

    // Generative AI
        // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
        // Add the dependency for the Firebase AI Logic library
        // (No version needed here because the BoM handles it!)
    implementation("com.google.firebase:firebase-ai:17.10.0")

    // Lifecycle
    val lifecycleVersion = "2.10.0"
        // 1. Gives you 'viewModelScope' so you can run Gemini API calls in the background
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
        // 2. Allows you to easily create and bind ViewModels inside your Compose screens
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
        // 3. Gives you 'collectAsStateWithLifecycle()', the safest way to read StateFlow in UI
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("com.google.accompanist:accompanist-permissions:0.37.3")




}