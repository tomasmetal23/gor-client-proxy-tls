package com.saiyans.gor 

import android.app.Application
import dagger.hilt.android.HiltAndroidApp 

// @HiltAndroidApp // Descomenta si usas Hilt
class MyApplication : Application() {

    // Instancia única de la base de datos para toda la app
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Puedes añadir inicialización adicional aquí si es necesario
    }
}