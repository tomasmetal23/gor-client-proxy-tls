package com.saiyans.gor

import android.app.Application
import com.saiyans.gor.db.AppDatabase
import dagger.hilt.android.HiltAndroidApp 

// @HiltAndroidApp // Si usas Hilt
class ProxyApplication : Application() { 

    // Instancia única de la base de datos
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Inicialización adicional si es necesaria
    }
}