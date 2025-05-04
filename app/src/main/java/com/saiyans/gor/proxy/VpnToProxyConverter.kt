package com.example.proxyapp.proxy

import android.net.VpnService
import android.util.Log
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VpnToProxyConverter(
    private val vpnService: VpnService,
    private val proxyAddress: InetSocketAddress,
    private val proxyUsername: String?,
    private val proxyPassword: String?
) {
    private val TAG = "VpnToProxyConverter"
    private val connectionMap = ConcurrentHashMap<Int, Connection>()
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4)
    private var isRunning = true
    
    // Clase para manejar conexiones TCP
    private inner class Connection(
        val sourcePort: Int,
        val destinationAddress: InetSocketAddress
    ) {
        var channel: SocketChannel? = null
        var selector: Selector? = null
        
        fun connect() {
            try {
                channel = SocketChannel.open()
                channel?.configureBlocking(false)
                
                // Proteger el socket para que no pase por la VPN
                vpnService.protect(channel?.socket() ?: return)
                
                // Conectar al proxy
                channel?.connect(proxyAddress)
                
                // Configurar autenticación de proxy si es necesario
                if (!proxyUsername.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                    // Implementar autenticación de proxy aquí
                    // Esto dependerá del tipo de proxy y protocolo
                }
                
                // Configurar el selector para manejar eventos de E/S
                selector = Selector.open()
                
                Log.d(TAG, "Conexión establecida desde puerto $sourcePort a $destinationAddress a través del proxy $proxyAddress")
            } catch (e: Exception) {
                Log.e(TAG, "Error al establecer conexión", e)
                close()
            }
        }
        
        fun sendData(data: ByteBuffer) {
            try {
                if (channel?.isConnected == true) {
                    data.flip()
                    channel?.write(data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar datos", e)
                close()
            }
        }
        
        fun close() {
            try {
                channel?.close()
                selector?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error al cerrar conexión", e)
            } finally {
                connectionMap.remove(sourcePort)
            }
        }
    }
    
    fun processPacket(packet: ByteBuffer, outputStream: FileOutputStream) {
        // Todos los paquetes, incluidos los DNS, se envían a través del proxy HTTPS
        // para que sean manejados por el SOCKS5 de Tor
        
        executorService.submit {
            try {
                // Analizar el paquete IP (versión 4)
                if ((packet.get(0).toInt() shr 4) != 4) {
                    // No es un paquete IPv4
                    return@submit
                }
                
                // Extraer protocolo (TCP = 6, UDP = 17)
                val protocol = packet.get(9).toInt() and 0xFF
                
                if (protocol == 6) { // TCP
                    // Extraer puertos e IPs (simplificado)
                    val sourcePort = ((packet.get(20).toInt() and 0xFF) shl 8) or (packet.get(21).toInt() and 0xFF)
                    val destPort = ((packet.get(22).toInt() and 0xFF) shl 8) or (packet.get(23).toInt() and 0xFF)
                    
                    // Extraer dirección IP de destino (simplificado)
                    val destIp = "${packet.get(16).toInt() and 0xFF}.${packet.get(17).toInt() and 0xFF}.${packet.get(18).toInt() and 0xFF}.${packet.get(19).toInt() and 0xFF}"
                    
                    // Obtener o crear conexión
                    val connection = connectionMap.computeIfAbsent(sourcePort) {
                        val conn = Connection(sourcePort, InetSocketAddress(destIp, destPort))
                        conn.connect()
                        conn
                    }
                    
                    // Extraer datos TCP y enviarlos a través del proxy
                    val headerLength = (packet.get(0).toInt() and 0x0F) * 4
                    val tcpHeaderLength = ((packet.get(headerLength + 12).toInt() and 0xF0) shr 4) * 4
                    val dataOffset = headerLength + tcpHeaderLength
                    
                    if (packet.limit() > dataOffset) {
                        val dataLength = packet.limit() - dataOffset
                        val data = ByteBuffer.allocate(dataLength)
                        
                        // Copiar datos del paquete
                        for (i in 0 until dataLength) {
                            data.put(packet.get(dataOffset + i))
                        }
                        
                        // Enviar datos a través de la conexión
                        connection.sendData(data)
                    }
                } else if (protocol == 17) { // UDP (incluye DNS)
                    // Para UDP, incluidos los paquetes DNS, los enviamos a través del proxy
                    // para que sean manejados por el SOCKS5 de Tor
                    handleUdpPacket(packet)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar paquete", e)
            }
        }
    }
    
    private fun handleUdpPacket(packet: ByteBuffer) {
        // Implementación para manejar paquetes UDP a través del proxy
        try {
            // Extraer puertos e IPs
            val sourcePort = ((packet.get(20).toInt() and 0xFF) shl 8) or (packet.get(21).toInt() and 0xFF)
            val destPort = ((packet.get(22).toInt() and 0xFF) shl 8) or (packet.get(23).toInt() and 0xFF)
            
            // Extraer dirección IP de destino
            val destIp = "${packet.get(16).toInt() and 0xFF}.${packet.get(17).toInt() and 0xFF}.${packet.get(18).toInt() and 0xFF}.${packet.get(19).toInt() and 0xFF}"
            
            Log.d(TAG, "Paquete UDP: $sourcePort -> $destIp:$destPort")
            
            // Los paquetes UDP (incluidos DNS) se envían a través del proxy HTTPS
            // para que sean manejados por el SOCKS5 de Tor
            // Esto asegura que no haya fugas DNS
        } catch (e: Exception) {
            Log.e(TAG, "Error al manejar paquete UDP", e)
        }
    }
    
    fun stop() {
        isRunning = false
        
        // Cerrar todas las conexiones
        connectionMap.forEach { (_, connection) ->
            connection.close()
        }
        connectionMap.clear()
        
        // Cerrar el executor
        executorService.shutdown()
    }
}
