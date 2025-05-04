package com.example.proxyapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service // Importar Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.proxyapp.MainActivity // Asegúrate que esta es tu Activity principal
import com.example.proxyapp.R // Asegúrate que R se importa correctamente
import com.example.proxyapp.proxy.ProxyManager
import com.example.proxyapp.proxy.VpnToProxyConverter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.concurrent.Volatile // Correct import for Volatile

@AndroidEntryPoint
class ProxyVpnService : VpnService() {

    @Inject
    lateinit var proxyManager: ProxyManager

    // Usar SupervisorJob para que el fallo de una coroutine hija no cancele el scope entero
    private val serviceJob = SupervisorJob()
    // Usar Dispatchers.IO para operaciones de red y disco
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val notificationChannelId = "proxy_vpn_service_channel"
    private val notificationId = 1 // ID único para la notificación

    @Volatile // Asegurar visibilidad entre hilos
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnToProxyConverter: VpnToProxyConverter? = null

    @Volatile // Asegurar visibilidad entre hilos
    private var isRunning = false

    private var allowedApps: Set<String> = emptySet()
    private var isAppSelectiveMode: Boolean = false

    // Guardar los últimos parámetros por si necesitamos reiniciar la conexión
    private var lastHost: String? = null
    private var lastPort: Int = -1
    private var lastUsername: String? = null
    private var lastPassword: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Servicio VPN creado.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand recibido con acción: ${intent?.action}, flags: $flags")

        if (intent == null) {
            Log.w(TAG, "Intent nulo en onStartCommand (probablemente reiniciado por el sistema).")
            // Podríamos intentar reiniciar con los últimos parámetros si los tenemos
            if (lastHost != null && lastPort != -1 && !isRunning) {
                Log.i(TAG, "Intentando reiniciar VPN con los últimos parámetros.")
                startVpnService(lastHost!!, lastPort, lastUsername, lastPassword, allowedApps, isAppSelectiveMode)
            } else {
                Log.w(TAG, "No hay parámetros previos o ya está corriendo. Deteniendo.")
                stopSelf() // Detener si no podemos reiniciar
            }
            return START_NOT_STICKY // No seguir intentando si no hay intent
        }


        when (intent.action) {
            ACTION_START -> {
                if (isRunning) {
                    Log.w(TAG, "VPN ya está corriendo, ignorando ACTION_START adicional.")
                    // Considerar si se deben actualizar los parámetros aquí si es necesario
                    return START_REDELIVER_INTENT // Reentregar el último intent si el servicio muere
                }

                val host = intent.getStringExtra(EXTRA_HOST)
                val port = intent.getIntExtra(EXTRA_PORT, -1)
                val username = intent.getStringExtra(EXTRA_USERNAME)
                val password = intent.getStringExtra(EXTRA_PASSWORD)

                // Guardar parámetros para posible reinicio
                lastHost = host
                lastPort = port
                lastUsername = username
                lastPassword = password

                // Obtener configuración de filtrado de apps
                isAppSelectiveMode = intent.getBooleanExtra(EXTRA_APP_SELECTIVE_MODE, false)
                allowedApps = intent.getStringArrayListExtra(EXTRA_ALLOWED_APPS)?.toSet() ?: emptySet()

                if (host == null || port == -1) {
                    Log.e(TAG, "Host o puerto inválidos recibidos. Host: $host, Port: $port. Deteniendo servicio.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startVpnService(host, port, username, password, allowedApps, isAppSelectiveMode)

            }
            ACTION_STOP -> {
                Log.d(TAG, "Recibido ACTION_STOP. Deteniendo VPN y servicio.")
                stopVpn() // Limpia recursos de VPN
                stopSelf() // Detiene el servicio
            }
            else -> {
                Log.w(TAG, "Acción desconocida o nula recibida: ${intent.action}")
            }
        }

        // Indica que si el sistema mata el servicio, debe re-lanzarlo y
        // re-entregar el último Intent que se envió a onStartCommand.
        return START_REDELIVER_INTENT
    }

    private fun startVpnService(
        host: String,
        port: Int,
        username: String?,
        password: String?,
        appsToAllow: Set<String>,
        isSelectiveMode: Boolean
    ) {
        Log.d(TAG, "Iniciando VPN para $host:$port. Modo selectivo: $isSelectiveMode, Apps: ${appsToAllow.joinToString()}")
        // Mostrar notificación de "Conectando..." inmediatamente
        startForeground(notificationId, createNotification("Conectando a $host:$port...", isConnecting = true))

        // Lanzar la configuración y arranque en un hilo de fondo (IO)
        serviceScope.launch {
            startVpnInBackground(host, port, username, password, appsToAllow, isSelectiveMode)
        }
    }


    // Función que realiza el trabajo pesado en segundo plano
    private suspend fun startVpnInBackground(
        host: String,
        port: Int,
        username: String?,
        password: String?,
        appsToAllow: Set<String>,
        isSelectiveMode: Boolean
    ) {
        // Prevenir inicio múltiple concurrente
        synchronized(this) {
            if (isRunning) {
                Log.w(TAG, "startVpnInBackground llamado pero VPN ya está corriendo o iniciando.")
                return@startVpnInBackground
            }
             // Marcar como intentando iniciar para evitar llamadas concurrentes a esta lógica
             isRunning = true
        }

        var localVpnInterface: ParcelFileDescriptor? = null

        try {
            Log.d(TAG, "Configurando y probando proxy en background...")

             // 1. Configurar y probar el Proxy (esta función ahora es suspend y se ejecuta en IO)
             // Esta función ahora resuelve internamente la dirección y prueba la conexión.
             val proxyConfigured = proxyManager.configureProxy(host, port, username, password)

             if (!proxyConfigured) {
                 throw IllegalStateException("Falló la configuración o prueba de conexión del proxy.")
             }
             Log.i(TAG, "Proxy configurado y probado exitosamente.")

            // 2. Configurar la interfaz VPN
             Log.d(TAG, "Configurando interfaz VpnService...")
             val builder = Builder()
                 .addAddress("10.8.0.1", 24) // Dirección IP virtual para el cliente VPN
                 .addRoute("0.0.0.0", 0) // Enrutar todo el tráfico IPv4
                 // .addRoute("::", 0) // Descomentar si necesitas enrutar IPv6 también
                 .setSession(getString(R.string.app_name)) // Nombre mostrado en los ajustes de Android
                 .setMtu(1500) // MTU estándar, ajustar si es necesario
                 .setBlocking(true) // establish() bloqueará hasta que esté lista o falle

             // IMPORTANTE: No establecer DNS aquí para forzar que las consultas DNS
             // pasen por el túnel y sean manejadas por el proxy (si el proxy lo soporta, como SOCKS5)
             // builder.addDnsServer("1.1.1.1") // ¡NO HACER ESTO si quieres evitar fugas DNS!

             // 3. Aplicar filtrado de aplicaciones si está activado
             if (isSelectiveMode) {
                 if (appsToAllow.isNotEmpty()) {
                     Log.d(TAG, "Aplicando modo selectivo para ${appsToAllow.size} apps: $appsToAllow")
                     // Es VITAL permitir nuestra propia app para que la UI pueda comunicarse
                     // y para que el servicio VPN funcione correctamente.
                      try {
                           builder.addAllowedApplication(packageName)
                           Log.d(TAG,"Permitida aplicación propia: $packageName")
                      } catch (e: Exception) {
                           Log.e(TAG,"Error crítico al permitir la aplicación propia: $packageName", e)
                           // Esto probablemente debería detener el inicio
                           throw IllegalStateException("No se pudo permitir la propia app VPN.", e)
                      }

                     appsToAllow.forEach { pkgName ->
                         if (pkgName != packageName) { // Evitar añadirla dos veces
                             try {
                                 builder.addAllowedApplication(pkgName)
                                 Log.d(TAG, "App permitida: $pkgName")
                             } catch (e: Exception) {
                                 Log.e(TAG, "No se pudo permitir la app '$pkgName'. ¿Está instalada?", e)
                                 // Considera notificar al usuario sobre apps no encontradas
                             }
                         }
                     }
                 } else {
                      Log.w(TAG,"Modo selectivo activado pero sin apps seleccionadas. Permitida solo la propia app.")
                       // Solo permitir la propia app si la lista está vacía en modo selectivo
                        try {
                           builder.addAllowedApplication(packageName)
                       } catch (e: Exception) {
                           Log.e(TAG,"Error crítico al permitir la aplicación propia (modo selectivo vacío): $packageName", e)
                            throw IllegalStateException("No se pudo permitir la propia app VPN.", e)
                       }
                 }
             } else {
                 Log.d(TAG, "Permitiendo todas las aplicaciones (modo global).")
                 // En modo global, no necesitamos añadir/quitar nada explícitamente aquí.
                 // *NO* llames a addDisallowedApplication(packageName) para tu app.
             }


            // 4. Establecer la interfaz VPN (esta llamada bloquea hasta que esté lista o el usuario cancele)
             Log.d(TAG, "Llamando a builder.establish()...")
             localVpnInterface = builder.establish()

             if (localVpnInterface == null) {
                 // El usuario probablemente presionó "Cancelar" en el diálogo de permiso VPN
                 Log.w(TAG, "builder.establish() devolvió null. Permiso denegado por el usuario o error.")
                 throw SecurityException("Permiso VPN denegado por el usuario o fallo al establecer.")
             }
             this.vpnInterface = localVpnInterface // Asignar a la variable de instancia de forma segura

             Log.i(TAG, "Interfaz VPN establecida exitosamente. Iniciando procesamiento de paquetes.")

             // 5. Actualizar notificación a "Conectado" (desde el hilo principal)
             withContext(Dispatchers.Main) {
                 val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                 notificationManager.notify(notificationId, createNotification("Conectado a $host:$port", isConnecting = false))
             }

             // 6. Iniciar el procesamiento de paquetes (ya estamos en el contexto IO)
             // Necesitamos la dirección resuelta que debería estar implícita en el ProxyManager o resolvemos de nuevo si es necesario
              val resolvedProxyAddress = withContext(Dispatchers.IO) {
                 InetSocketAddress(host, port) // Resolver de nuevo aquí por simplicidad
              }
             processPackets(localVpnInterface, resolvedProxyAddress, username, password)

        } catch (e: CancellationException) {
             Log.w(TAG, "Coroutine de inicio VPN cancelada.", e)
             isRunning = false // Asegurar que el estado es falso si se cancela
             // La limpieza la hará onDestroy o la llamada a stopVpn
        } catch (e: Exception) {
             Log.e(TAG, "Error crítico durante el inicio de la VPN en background", e)
             withContext(Dispatchers.Main.immediate) { // Usar Main.immediate para actualización rápida
                 val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                 notificationManager.notify(notificationId, createNotification("Error: ${e.message ?: "desconocido"}", isError = true))
             }
             // Limpiar recursos inmediatamente tras un error de inicio
             stopVpn()
             stopSelf()
        } finally {
             // Este bloque se ejecuta si processPackets termina (normal o por error dentro de él)
             // O si hubo una excepción durante la configuración antes de llamar a processPackets
             if (isRunning) { // Si salimos pero aún estábamos marcados como corriendo (ej. error en processPackets)
                 Log.w(TAG, "Saliendo de startVpnInBackground pero isRunning aún era true. Forzando parada.")
                 // No debería pasar si processPackets maneja bien su salida, pero por seguridad:
                 isRunning = false // Asegurar estado
                  // No necesitamos llamar a stopSelf aquí normalmente,
                  // el servicio sigue vivo hasta que se llame a stopService o stopSelf
             }
             Log.d(TAG, "Fin de la coroutine startVpnInBackground.")
        }
    }

    // Función que maneja la lectura/escritura de paquetes
    private fun processPackets(
        vpnInterfaceFd: ParcelFileDescriptor,
        resolvedProxyAddress: InetSocketAddress, // Usar la dirección ya resuelta
        username: String?,
        password: String?
    ) {
        Log.d(TAG, "Iniciando bucle processPackets...")
        try {
            // Usar .use para asegurar el cierre automático de los streams
            FileInputStream(vpnInterfaceFd.fileDescriptor).use { inputStream ->
                FileOutputStream(vpnInterfaceFd.fileDescriptor).use { outputStream ->

                    val buffer = ByteBuffer.allocate(32767) // Tamaño generoso para paquetes
                    // Crear el convertidor DENTRO del contexto donde se usa
                    vpnToProxyConverter = VpnToProxyConverter(this, resolvedProxyAddress, username, password)
                    Log.d(TAG, "VpnToProxyConverter listo.")

                    // Bucle principal de procesamiento
                    while (isRunning) { // Controlado por la variable de estado Volatile
                        var readBytes = 0
                        try {
                            readBytes = inputStream.read(buffer.array())
                            if (readBytes > 0) {
                                buffer.limit(readBytes)
                                // Log.v(TAG, "Paquete VPN leído: $readBytes bytes") // Descomentar para debug intenso
                                vpnToProxyConverter?.processPacket(buffer, outputStream) // Pasar al convertidor
                                buffer.clear() // Preparar buffer para la siguiente lectura
                            } else if (readBytes == 0) {
                                // No es un error, pero significa que no hay datos ahora. Evitar busy-wait.
                                try { Thread.sleep(10) } catch (_: InterruptedException) {}
                            } else {
                                // readBytes < 0 indica fin de stream o error
                                if (isRunning) {
                                    Log.w(TAG, "inputStream.read() devolvió $readBytes. Interfaz VPN cerrada o error.")
                                }
                                break // Salir del bucle si la interfaz se cierra
                            }
                        } catch (e: Exception) {
                            if (isRunning) { // Solo loguear error si se supone que estamos activos
                                Log.e(TAG, "Error durante lectura/procesamiento de paquete", e)
                                // Podríamos querer implementar lógica para reintentar o detener aquí
                                Thread.sleep(100) // Pausa antes de reintentar o salir
                            } else {
                                Log.d(TAG, "Excepción esperada en I/O durante la detención.")
                                break // Salir si nos estamos deteniendo
                            }
                        }
                    } // Fin del while(isRunning)
                } // outputStream.use
            } // inputStream.use
        } catch (e: Exception) {
             // Error al crear/cerrar los streams principales
             if (isRunning) { // Solo si ocurre inesperadamente
                 Log.e(TAG, "Error fatal con los streams de la interfaz VPN", e)
             }
        } finally {
            Log.d(TAG, "Saliendo de processPackets (isRunning=$isRunning).")
            // Asegurar que nos detenemos si salimos de aquí inesperadamente mientras 'isRunning' era true
             if (isRunning) {
                  Log.w(TAG, "processPackets finalizó pero isRunning seguía true. Forzando parada.")
                   // Lanzar en Main thread para detener el servicio de forma segura
                   CoroutineScope(Dispatchers.Main).launch {
                        stopVpn()
                        stopSelf()
                   }
             }
            vpnToProxyConverter?.stop() // Asegurar que el convertidor se detiene
            vpnToProxyConverter = null
        }
    }

    // Detiene la lógica de la VPN y limpia recursos
    private fun stopVpn() {
        Log.d(TAG, "Llamada a stopVpn (isRunning=$isRunning)")
        if (!isRunning && vpnInterface == null) {
            Log.w(TAG, "stopVpn llamada pero ya estaba detenida.")
             // Asegurarse de quitar la notificación si quedó alguna por error
             stopForeground(Service.STOP_FOREGROUND_REMOVE)
            return
        }

        isRunning = false // Señalizar a los bucles que paren

        // Detener el convertidor primero
        vpnToProxyConverter?.stop()
        vpnToProxyConverter = null

        // Cerrar la interfaz VPN de forma segura
        val interfaceToClose = vpnInterface
        vpnInterface = null // Poner a null ANTES de cerrar para evitar race conditions

        if (interfaceToClose != null) {
            Log.d(TAG, "Cerrando ParcelFileDescriptor de la VPN...")
            try {
                interfaceToClose.close()
                Log.d(TAG, "ParcelFileDescriptor cerrado.")
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al cerrar la interfaz VPN", e)
            }
        } else {
            Log.w(TAG, "stopVpn: vpnInterface ya era null al intentar cerrar.")
        }

        // Detener el proxy manager si tiene estado interno que limpiar
        proxyManager.stopProxy()

        // Quitar la notificación de primer plano
        stopForeground(Service.STOP_FOREGROUND_REMOVE)

        Log.i(TAG, "VPN detenida y recursos limpiados.")

         // Limpiar últimos parámetros conocidos al detener explícitamente
         lastHost = null
         lastPort = -1
         lastUsername = null
         lastPassword = null
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy del servicio VPN.")
        stopVpn() // Asegurarse de que todo esté limpio
        serviceJob.cancel() // Cancelar todas las coroutines activas en este scope
        super.onDestroy()
        Log.d(TAG, "Servicio VPN destruido.")
    }

    // Crea el canal de notificación (necesario para Android 8+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Servicio Proxy VPN", // Nombre visible para el usuario
                NotificationManager.IMPORTANCE_LOW // Baja importancia para que no sea molesta
            ).apply {
                description = "Notificación persistente del estado del servicio VPN"
                // Deshabilitar vibración, sonido, etc. para notificaciones de baja importancia
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificación creado o ya existente.")
        }
    }

    // Crea o actualiza la notificación de primer plano
    private fun createNotification(
        contentText: String,
        isConnecting: Boolean = false,
        isError: Boolean = false
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Flags para abrir la UI principal al tocar la notificación
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // PendingIntent para abrir la UI
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, // requestCode 0
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent para la acción de detener
        val stopIntent = Intent(this, ProxyVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1, // requestCode 1 (diferente al anterior)
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            isConnecting -> "Conectando VPN..."
            isError -> "Error de VPN"
            else -> "Proxy VPN Activo"
        }

        // Usar un icono diferente para el error si lo tienes
        val smallIconRes = if (isError) R.drawable.ic_error else R.drawable.ic_proxy // Necesitas ic_error y ic_proxy

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(smallIconRes)
            .setContentIntent(pendingIntent) // Acción al tocar la notificación principal
            .setOngoing(true) // Hacerla no descartable por el usuario
            .setSilent(true) // Evitar sonido/vibración en actualizaciones
            .setPriority(NotificationCompat.PRIORITY_LOW) // Prioridad baja
            .addAction(R.drawable.ic_stop, "Detener", stopPendingIntent) // Botón "Detener", necesitas ic_stop
            .build()
    }

    // Definición de constantes estáticas
    companion object {
        private const val TAG = "ProxyVpnService" // Tag para Logs

        const val ACTION_START = "com.example.proxyapp.service.START_VPN"
        const val ACTION_STOP = "com.example.proxyapp.service.STOP_VPN"

        const val EXTRA_HOST = "com.example.proxyapp.service.extra_host"
        const val EXTRA_PORT = "com.example.proxyapp.service.extra_port"
        const val EXTRA_USERNAME = "com.example.proxyapp.service.extra_username"
        const val EXTRA_PASSWORD = "com.example.proxyapp.service.extra_password"
        const val EXTRA_ALLOWED_APPS = "com.example.proxyapp.service.extra_allowed_apps"
        const val EXTRA_APP_SELECTIVE_MODE = "com.example.proxyapp.service.extra_app_selective_mode"
    }
}