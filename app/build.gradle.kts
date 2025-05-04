// build.gradle.kts (app level)
// Asegúrate que estos plugins estén aplicados
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt") // Necesario para Room
    id("com.google.dagger.hilt.android") 
}

android {
    namespace = "com.saiyans.gor" 
    compileSdk = 34 

    defaultConfig {
        applicationId = "com.saiyans.gor" 
        minSdk = 26 // VpnService requiere API 21+, pero API 26+ es común hoy
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Cambia a true para producción
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    // Habilita ViewBinding (opcional pero recomendado para acceder a layouts)
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1") // Revisa última versión
    implementation("androidx.appcompat:appcompat:1.6.1") // Revisa última versión
    implementation("com.google.android.material:material:1.12.0") // Revisa última versión
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Revisa última versión

    // Room Persistence Library
    val room_version = "2.6.1" // Revisa última versión estable
    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // Soporte Coroutines

    // Lifecycle Components (ViewModel, LiveData/Flow)
    val lifecycle_version = "2.7.0" // Revisa última versión
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version") // o runtime-ktx
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version")
    implementation("androidx.activity:activity-ktx:1.9.0") // para by viewModels()

    // RecyclerView (si no lo tenías ya)
    implementation("androidx.recyclerview:recyclerview:1.3.2") // Revisa última versión

    // Coroutines (si no las tenías ya)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Revisa última versión

    // Hilt (Si lo mantienes, asegúrate que sus dependencias también estén)
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1") 

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}