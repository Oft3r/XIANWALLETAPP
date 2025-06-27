plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt") // Add kotlin-kapt plugin
    // KSP plugin temporarily disabled due to compatibility issues with Kotlin 2.0.21
}

android {
    namespace = "net.xian.xianwalletapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.xian.xianwalletapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 26 // Cambia este valor al nuevo código de versión
        versionName = "1.6.1" // Cambia este valor a la nueva versión

        // Aquí configuras el nombre del APK
        setProperty("archivesBaseName", "Xian Wallet-$versionName")
        
        // Configuración para compatibilidad con páginas de 16 KB
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        
        // Configuración adicional para 16 KB page size
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
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
    lint {
        abortOnError = false
    }
    
    // Configuración para compatibilidad con páginas de 16 KB
    packaging {
        jniLibs {
            useLegacyPackaging = false
            // Fuerza la alineación de 16 KB para todas las bibliotecas nativas
            pickFirsts += listOf("**/libbarhopper_v3.so", "**/libimage_processing_util_jni.so")
        }
        resources {
            // Excluye bibliotecas problemáticas si es necesario
            excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt")
        }
    }

}

dependencies {
    // Core Android dependencies
    // WorkManager para tareas periódicas en background
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.appcompat:appcompat:1.7.0") // Added for AppCompatActivity
    implementation("com.google.android.material:material:1.11.0") // Add Material Design dependency
    
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
    implementation("org.bouncycastle:bcprov-jdk18on:1.79") // Re-enabled explicit Bouncy Castle
    
    implementation("org.bitcoinj:bitcoinj-core:0.16.2") { // Re-added bitcoinj
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18") // Exclude transitive BC dependency
    }
    // implementation("io.github.novacrypto:bip39:0.1.10") // Removed again


    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")    // QR Code Generation (ZXing)
    implementation("com.google.zxing:core:3.5.3")
    // QR Code Scanning (ZXing Embedded) - Keep for fallback
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // CameraX for integrated QR scanning - Versiones actualizadas para 16 KB
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    // ML Kit for barcode scanning - Versión actualizada para 16 KB
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Forzar versión compatible de Google Play Services
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("io.coil-kt:coil-compose:2.5.0")
    // HTML Parsing
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.slf4j:slf4j-nop:1.7.32") // Add NOP SLF4J implementation for R8
    
    // Jetpack DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1") // Use the latest stable version    // Room Persistence Library
    val room_version = "2.6.1" // Use the latest stable version
    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version") // Change this line    // Optional - Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:$room_version")

    // Vico Chart Library for price charts
    implementation("com.patrykandpatrick.vico:compose:1.15.0")
    implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")
    implementation("com.patrykandpatrick.vico:core:1.15.0")

    // Testing dependencies removed for Play Store release
    // testImplementation(libs.junit)
    // androidTestImplementation(libs.androidx.junit)
    // androidTestImplementation(libs.androidx.espresso.core)
    // androidTestImplementation(platform(libs.androidx.compose.bom))
    // androidTestImplementation(libs.androidx.ui.test.junit4)
    // debugImplementation(libs.androidx.ui.tooling)
    // debugImplementation(libs.androidx.ui.test.manifest)
}