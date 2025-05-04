package com.example.proxyapp.proxy

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.* // Importar todo okhttp3
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyManager @Inject constructor() {

    // Guardar la configuración actual
    private var currentHost: String? = null
    private var currentPort: Int? = null
    private var currentUsername: String? = null
    private var currentPassword: String? = null

    @Volatile // El cliente puede ser accedido desde diferentes hilos (aunque principalmente desde el serviceScope)
    private var httpClient: OkHttpClient? = null

    /**
     * Configura el cliente OkHttp para usar el proxy especificado y realiza una prueba de conexión.
     * Esta función es 'suspend' porque realiza operaciones de red (resolución DNS, prueba HTTP).
     * Debe ser llamada desde una Coroutine en un dispatcher IO.
     *
     * @return true si la configuración y la prueba de conexión fueron exitosas, false en caso contrario.
     */
    suspend fun configureProxy(host: String, port: Int, username: String?, password: String?): Boolean {
        Log.d(TAG, "Iniciando configuración de ProxyManager para $host:$port...")

        // Paso 1: Resolver la dirección IP del host (Operación de Red)
        val resolvedAddress: InetSocketAddress? = resolveAddress(host, port)

        if (resolvedAddress == null) {
            Log.e(TAG, "Fallo al resolver la dirección del proxy: $host:$port")
            stopProxyInternal() // Limpiar estado si la resolución falla
            return false
        }
        Log.d(TAG, "Dirección resuelta a: ${resolvedAddress.address?.hostAddress}:${resolvedAddress.port}")

        // Paso 2: Guardar configuración y construir el cliente OkHttp
        this.currentHost = host // Guardar el host original o la IP resuelta? Guardemos el original.
        this.currentPort = port
        this.currentUsername = username
        this.currentPassword = password

        val proxy = Proxy(Proxy.Type.HTTP, resolvedAddress) // Usar la dirección resuelta

        val clientBuilder = OkHttpClient.Builder()
            .proxy(proxy)
            // Establecer timeouts razonables para la conexión de prueba y futuras conexiones
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)

        // Agregar autenticación si se proporcionaron credenciales
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            Log.d(TAG, "Configurando autenticación de proxy para usuario: $username")
            clientBuilder.proxyAuthenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    // Evitar bucle infinito si la autenticación falla repetidamente
                    if (response.request.header("Proxy-Authorization") != null) {
                        Log.w(TAG, "Autenticación de proxy fallida previamente, no reintentar.")
                        return null // Ya se intentó autenticar
                    }
                    Log.d(TAG, "Servidor proxy requiere autenticación (407). Enviando credenciales.")
                    val credential = Credentials.basic(username, password)
                    return response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            })
        } else {
            Log.d(TAG, "No se configuró autenticación de proxy.")
        }

        // Construir el cliente
        httpClient = clientBuilder.build()
        Log.d(TAG, "OkHttpClient construido con proxy: $proxy")

        // Paso 3: Realizar una prueba de conexión (Operación de Red)
        val connectionTestOk = testConnection()

        if (!connectionTestOk) {
            Log.e(TAG, "Falló la prueba de conexión al proxy $host:$port.")
            stopProxyInternal() // Limpiar estado si la prueba falla
            return false
        }

        Log.i(TAG, "Proxy configurado y conexión probada exitosamente para $host:$port.")
        return true
    }

    // Función suspendida separada para resolver DNS (I/O)
    private suspend fun resolveAddress(host: String, port: Int): InetSocketAddress? {
        return withContext(Dispatchers.IO) { // Asegura ejecución en hilo de I/O
            try {
                Log.d(TAG, "Resolviendo InetSocketAddress para $host:$port en Dispatchers.IO...")
                // La creación de InetSocketAddress realiza la resolución DNS si host no es IP
                InetSocketAddress(host, port)
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al resolver InetSocketAddress", e)
                null // Devolver null si hay error
            }
        }
    }

    // Función suspendida separada para la prueba de conexión (I/O)
    private suspend fun testConnection(): Boolean {
        val client = httpClient ?: run {
            Log.w(TAG, "testConnection llamado pero httpClient es nulo.")
            return false
        }

        // Usar withTimeoutOrNull para evitar que la prueba bloquee indefinidamente
        val result = withTimeoutOrNull(20000L) { // Timeout de 20 segundos para la prueba
            withContext(Dispatchers.IO) { // Asegurar ejecución en hilo de I/O
                try {
                    // Usar un sitio conocido y ligero para la prueba. generate_204 devuelve un HTTP 204 si hay conexión.
                    // O usar check.torproject.org si específicamente quieres probar Tor.
                    val testUrl = "http://connectivitycheck.gstatic.com/generate_204"
                    // val testUrl = "https://check.torproject.org" // Si necesitas probar Tor específicamente
                    Log.d(TAG, "Realizando conexión de prueba a: $testUrl")
                    val request = Request.Builder()
                        .url(testUrl)
                        .head() // Usar HEAD para no descargar contenido, solo verificar conexión/código
                        // .get() // Usar GET si necesitas verificar el contenido de la respuesta
                        .build()

                    // Ejecutar la llamada de forma síncrona dentro de la coroutine
                    val response = client.newCall(request).execute()
                    val isSuccess = response.isSuccessful // Verifica códigos 2xx
                    Log.i(TAG, "Resultado conexión de prueba - Código: ${response.code}, Éxito: $isSuccess")
                    response.close() // Muy importante cerrar la respuesta
                    isSuccess

                } catch (e: IOException) {
                    Log.e(TAG, "IOException durante la conexión de prueba", e)
                    false
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción general durante la conexión de prueba", e)
                    false
                }
            }
        }

        return if (result == null) {
            Log.e(TAG, "Timeout (>20s) durante la conexión de prueba.")
            false
        } else {
            result // Devuelve el booleano del bloque try-catch
        }
    }

    /**
     * Detiene el proxy limpiando el cliente HTTP y la configuración.
     * Es seguro llamar a esta función desde cualquier hilo.
     */
    fun stopProxy() {
        Log.d(TAG, "Llamada a stopProxy.")
        stopProxyInternal()
    }

    // Método interno para la lógica de limpieza
    private fun stopProxyInternal() {
         // Limpiar configuración
         currentHost = null
         currentPort = null
         currentUsername = null
         currentPassword = null

         // Limpiar cliente OkHttp (puede ayudar a liberar recursos)
         // OkHttp recomienda compartir instancias, pero aquí queremos limpiar.
         // Cancelar todas las llamadas pendientes podría ser útil si el cliente se reutilizara mucho,
         // pero como lo vamos a poner a null, debería ser suficiente.
         // httpClient?.dispatcher?.cancelAll() // Opcional: cancelar llamadas activas
         httpClient = null
         Log.d(TAG, "ProxyManager limpiado.")
    }


    // Podrías necesitar un método para obtener el cliente configurado si otras partes lo usan
    // fun getHttpClient(): OkHttpClient? = httpClient

    companion object {
        private const val TAG = "ProxyManager"
    }
}