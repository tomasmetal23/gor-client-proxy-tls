// build.gradle.kts (app level)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt") // Necesario para Room y Hilt
    id("com.google.dagger.hilt.android") // Mantener Hilt
}

android {
    namespace = "com.saiyans.gor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.saiyans.gor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Necesario para algunas librerías de Compose (como ui-tooling-preview)
        vectorDrawables {
            useSupportLibrary = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        // Mantener ViewBinding si aún usas layouts XML en alguna parte
        viewBinding = true
        // *** HABILITAR COMPOSE ***
        compose = true
    }
    // *** AÑADIR CONFIGURACIÓN DE COMPOSE ***
    composeOptions {
        // Asegúrate que esta versión sea compatible con tu versión de Kotlin Plugin
        // Consulta: https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.13" // Ajusta si usas otra versión de Kotlin
        // Por ejemplo, Kotlin 1.9.23 -> Compose Compiler 1.5.11
        // Kotlin 2.0.0 -> Compose Compiler 1.5.13
    }
    // Necesario para previews de Compose
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // Core y AppCompat (mantener)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1") // Necesario si usas AppCompatActivity
    implementation("com.google.android.material:material:1.12.0") // Material Components (útil para diálogos, etc.)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Si usas ConstraintLayout en XML

    // Room (mantener)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // Lifecycle (mantener, Compose los usa extensivamente)
    val lifecycle_version = "2.7.0"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version") // Muy importante para Compose
    implementation("androidx.activity:activity-ktx:1.9.0")

    // RecyclerView (mantener si lo usas en MainActivity por ahora)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines (mantener)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // O una versión más nueva si está disponible

    // Hilt (mantener)
    implementation("com.google.dagger:hilt-android:2.51.1") // Revisa última versión
    kapt("com.google.dagger:hilt-compiler:2.51.1") // Revisa última versión

    // *** AÑADIR DEPENDENCIAS DE JETPACK COMPOSE ***
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00") // Revisa la última BOM estable
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Implementaciones fundamentales de Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3") // Usa Material 3 (recomendado)
    // O si prefieres Material 2: implementation("androidx.compose.material:material")

    // Integración con Activity (necesaria si usas ComponentActivity/AppCompatActivity)
    implementation("androidx.activity:activity-compose:1.9.0") // Revisa última versión

    // Dependencias para Debug/Tooling (opcionales pero recomendadas)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Tests (mantener)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // Tests de UI para Compose (opcional)
    // androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}