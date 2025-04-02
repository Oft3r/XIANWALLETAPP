plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.xianwalletapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.xianwalletapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Aquí configuras el nombre del APK
        setProperty("archivesBaseName", "Xian Wallet-$versionName")

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
        compose = true
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Material Icons
    implementation("androidx.compose.material:material-icons-core:1.7.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.compose.material:material:1.7.0") // For ExperimentalMaterialApi
    
    // Accompanist
    implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3") // Add ViewModel for Compose
    
    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // Add logging interceptor
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Biometric authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    // Using a different crypto library since libsodium is not accessible
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    // QR Code Generation (ZXing)
    implementation("com.google.zxing:core:3.5.3")
    // QR Code Scanning (ZXing Embedded)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}