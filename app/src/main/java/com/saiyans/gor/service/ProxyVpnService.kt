package com.saiyans.gor.service 

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.saiyans.gor.MainActivity // <<< Import CORREGIDO
import com.saiyans.gor.ProxyApplication // <<< Import CORREGIDO
import com.saiyans.gor.R // <<< Import R para recursos (icono)
import com.saiyans.gor.db.ProxyServer // <<< Import CORREGIDO
import com.saiyans.gor.db.ProxyServerDao // <<< Import CORREGIDO
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

// Constantes (definidas aquí también para uso interno y externo)
const val PREFS_NAME = "ProxyAppSettings" // Debe coincidir con MainActivity
const val KEY_SELECTED_SERVER_ID = "selected_server_id"
const val INVALID_SERVER_ID = -1

class ProxyVpnService : VpnService() { // <<< NOMBRE DE CLASE CORREGIDO

    companion object {
        const val ACTION_CONNECT = "com.saiyans.gor.service.ACTION_CONNECT" // <<< Action CORREGIDO
        const val ACTION_DISCONNECT = "com.saiyans.gor.service.ACTION_DISCONNECT" // <<< Action CORREGIDO
        private const val NOTIFICATION_CHANNEL_ID = "ProxyAppVpnChannel"
        private const val NOTIFICATION_ID = 123
        private const val TAG = "ProxyVpnService" // <<< TAG CORREGIDO
    }

    // ... (Resto de las variables: vpnInterface, prefs, selectedServerId, activeProxyServer, proxyDao, serviceJob, serviceScope, vpnProcessingJob) ...
    // Son idénticas a la versión anterior de MyVpnService.kt
    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var prefs: SharedPreferences
    private var selectedServerId: Int = INVALID_SERVER_ID
    private var activeProxyServer: ProxyServer? = null

    private val proxyDao: ProxyServerDao by lazy {
        (application as ProxyApplication).database.proxyServerDao() // <<< USA TU NOMBRE DE APP
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var vpnProcessingJob: Job? = null


    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.i(TAG, "Servicio Creado.")
        createNotificationChannel()
    }

     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ... (Lógica de onStartCommand idéntica a MyVpnService.kt anterior) ...
        val action = intent?.action ?: ACTION_CONNECT // Acción por defecto
        Log.i(TAG, "onStartCommand: Acción=$action, startId=$startId")
        when (action) {
            ACTION_CONNECT -> {
                 if (vpnProcessingJob == null || !vpnProcessingJob!!.isActive) {
                     stopVpnInternal(false)
                     startVpnConnection()
                 } else { Log.w(TAG, "Acción CONNECT recibida, pero la VPN ya está activa.") }
            }
            ACTION_DISCONNECT -> {
                Log.i(TAG, "Acción DISCONNECT recibida.")
                stopVpnInternal(true)
                stopSelf()
            }
        }
         return START_REDELIVER_INTENT
     }

    // ... startVpnConnection, configureAndEstablishVpnInterface, protectSocketIfNeeded ...
    // (Lógica idéntica a MyVpnService.kt anterior)
    private fun startVpnConnection() {
        Log.i(TAG, "Iniciando conexión VPN...")
        selectedServerId = prefs.getInt(KEY_SELECTED_SERVER_ID, INVALID_SERVER_ID)
        if (selectedServerId == INVALID_SERVER_ID) {
            Log.e(TAG, "Fallo al iniciar: No hay servidor seleccionado en SharedPreferences.")
            stopSelf()
            return
        }
         vpnProcessingJob = serviceScope.launch {
             activeProxyServer = proxyDao.getServerById(selectedServerId)
             if (activeProxyServer == null) {
                 Log.e(TAG, "Fallo al iniciar: Servidor ID $selectedServerId no encontrado en BD.")
                 withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Error: Servidor seleccionado no encontrado.", Toast.LENGTH_LONG).show() }
                 stopSelf()
                 return@launch
             }
             val server = activeProxyServer!!
             Log.i(TAG, "Servidor activo cargado: ${server.name} (${server.host}:${server.port})")
             startForeground(NOTIFICATION_ID, createNotification("Conectando a ${server.name}..."))
             Log.d(TAG, "Configurando interfaz VPN...")
             val pfd = configureAndEstablishVpnInterface(server)
             if (pfd == null || !isActive) {
                 Log.e(TAG, "Fallo al establecer la interfaz VPN.")
                 if(isActive) {
                      withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Error al iniciar VPN.", Toast.LENGTH_LONG).show() }
                     stopVpnInternal(true)
                     stopSelf()
                 }
                 return@launch
             }
             vpnInterface = pfd
             Log.i(TAG, "Interfaz VPN establecida con éxito.")
             startForeground(NOTIFICATION_ID, createNotification("Conectado a ${server.name}"))
             Log.i(TAG, "Iniciando bucle de procesamiento de paquetes...")
             try {
                 val inputStream = ParcelFileDescriptor.AutoCloseInputStream(vpnInterface)
                 // val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(vpnInterface)
                 val buffer = ByteArray(32767)
                 while (isActive) {
                     val length = inputStream.read(buffer)
                     if (length > 0) {
                         val packetData = buffer.copyOfRange(0, length)
                         // !!! <<< IMPLEMENTACIÓN CRÍTICA AQUÍ >>> !!!
                         Log.v(TAG, "Paquete leído: ${length} bytes.") // Log temporal
                     } else if (length < 0) {
                         Log.w(TAG, "Fin de stream (EOF) leyendo de TUN.")
                         break
                     }
                     yield()
                 }
             } catch (e: IOException) {
                 if (isActive) Log.e(TAG, "Error de I/O en bucle de procesamiento", e)
             } catch (e: Exception) {
                 if (isActive) Log.e(TAG, "Error inesperado en bucle de procesamiento", e)
             } finally {
                 Log.i(TAG, "Bucle de procesamiento finalizado.")
                 if (isActive) { stopVpnInternal(true); stopSelf() }
             }
         }
         vpnProcessingJob?.invokeOnCompletion { throwable ->
             val cause = throwable?.message ?: "Normal"
             Log.i(TAG, "Job de procesamiento de VPN completado (Causa: $cause). Limpiando...")
              if (throwable != null && throwable !is CancellationException) {
                   Log.e(TAG, "Job de procesamiento falló con error no cancelado", throwable)
              }
              stopVpnInternal(true); stopSelf()
         }
     }
    private suspend fun configureAndEstablishVpnInterface(server: ProxyServer): ParcelFileDescriptor? {
        return withContext(Dispatchers.Main.immediate) {
            try {
                val builder = Builder()
                builder.setSession(server.name)
                    .addAddress("10.8.0.1", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDisallowedApplication(packageName) // Evitar bucles
                Log.d(TAG, "Llamando a builder.establish()...")
                val pfd = builder.establish()
                if (pfd == null) Log.e(TAG, "builder.establish() devolvió null!")
                else Log.i(TAG, "builder.establish() exitoso.")
                pfd
            } catch (e: SecurityException) { Log.e(TAG, "Excepción de Seguridad al establecer interfaz VPN", e); null }
              catch (e: Exception) { Log.e(TAG, "Excepción genérica al establecer interfaz VPN", e); null }
        }
    }
     private fun protectSocketIfNeeded(socket: Socket): Boolean {
         val protected = protect(socket)
         if (!protected) { Log.w(TAG, "¡Fallo al proteger socket a ${socket.inetAddress}:${socket.port}!") }
         return protected
     }


    // ... stopVpnInternal, onDestroy ...
    // (Lógica idéntica a MyVpnService.kt anterior)
     private fun stopVpnInternal(stopForegroundService: Boolean) {
        Log.i(TAG, "Llamada a stopVpnInternal (stopForeground: $stopForegroundService)")
        vpnProcessingJob?.cancel()
        vpnProcessingJob = null
        serviceJob.cancelChildren()
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.i(TAG, "Interfaz VPN cerrada.")
        } catch (e: IOException) { Log.w(TAG, "Error cerrando interfaz VPN al detener: ${e.message}") }
        activeProxyServer = null
        selectedServerId = INVALID_SERVER_ID
        if (stopForegroundService) {
            Log.d(TAG, "Deteniendo servicio en primer plano...")
            stopForeground(STOP_FOREGROUND_REMOVE) // API 24+ (true antes)
        }
    }
    override fun onDestroy() {
        Log.i(TAG, "Servicio Destruyendo...")
        stopVpnInternal(true)
        serviceJob.cancel()
        super.onDestroy()
        Log.i(TAG, "Servicio Destruido.")
    }


    // --- Notificaciones ---
    // (Lógica idéntica a MyVpnService.kt anterior, asegúrate que los R.drawable existan)
     private fun createNotificationChannel() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             val channel = NotificationChannel(
                 NOTIFICATION_CHANNEL_ID,
                 "Estado VPN",
                 NotificationManager.IMPORTANCE_LOW
             ).apply { description = "Muestra el estado de la conexión VPN" }
             val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.createNotificationChannel(channel)
             Log.d(TAG, "Canal de notificación creado: $NOTIFICATION_CHANNEL_ID")
         }
     }
     private fun createNotification(contentText: String): Notification {
         val notificationIntent = Intent(this, MainActivity::class.java) // Ir a MainActivity al tocar
         val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
             PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
         } else { PendingIntent.FLAG_UPDATE_CURRENT }
         val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

         val disconnectIntent = Intent(this, ProxyVpnService::class.java).apply { action = ACTION_DISCONNECT } // <<< NOMBRE SERVICIO CORREGIDO
         val disconnectPendingIntent = PendingIntent.getService(this, 1, disconnectIntent, pendingIntentFlags)

         return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
             .setContentTitle("ProxyApp VPN") // Cambia el título si quieres
             .setContentText(contentText)
             .setSmallIcon(R.drawable.ic_notification_icon) // <<< USA TU ICONO
             .setContentIntent(pendingIntent)
             .addAction(R.drawable.ic_disconnect_icon, "Desconectar", disconnectPendingIntent) // <<< USA TU ICONO
             .setOngoing(true)
             .setOnlyAlertOnce(true)
             .build()
     }

}