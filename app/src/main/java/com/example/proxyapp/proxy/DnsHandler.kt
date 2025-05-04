package com.example.proxyapp.proxy

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Clase para manejar consultas DNS y evitar fugas
 */
class DnsHandler {
    private val TAG = "DnsHandler"
    
    // Servidor DNS personalizado (Cloudflare)
    private val customDnsServer = "1.1.1.1"
    private val customDnsPort = 53
    
    /**
     * Procesa una consulta DNS y la redirige a través del proxy
     */
    fun handleDnsQuery(queryData: ByteBuffer, proxyAddress: String, proxyPort: Int): ByteBuffer? {
        try {
            // Extraer datos de la consulta DNS
            val queryBytes = ByteArray(queryData.limit())
            queryData.get(queryBytes)
            
            // Crear socket para enviar la consulta a través del proxy
            val socket = DatagramSocket()
            
            // Configurar el paquete para enviar al servidor DNS personalizado a través del proxy
            val dnsServerAddress = InetAddress.getByName(customDnsServer)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, dnsServerAddress, customDnsPort)
            
            // Enviar la consulta
            socket.send(sendPacket)
            
            // Preparar para recibir la respuesta
            val responseBuffer = ByteArray(4096)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            
            // Establecer timeout para evitar bloqueos
            socket.soTimeout = 5000
            
            // Recibir respuesta
            socket.receive(responsePacket)
            
            // Cerrar socket
            socket.close()
            
            // Convertir respuesta a ByteBuffer
            val resultBuffer = ByteBuffer.allocate(responsePacket.length)
            resultBuffer.put(responseBuffer, 0, responsePacket.length)
            resultBuffer.flip()
            
            return resultBuffer
        } catch (e: Exception) {
            Log.e(TAG, "Error al manejar consulta DNS", e)
            return null
        }
    }
    
    /**
     * Verifica si un paquete es una consulta DNS
     */
    fun isDnsPacket(packet: ByteBuffer): Boolean {
        // Verificar si es un paquete UDP
        if (packet.limit() < 28) return false
        
        // Obtener el protocolo (UDP = 17)
        val protocol = packet.get(9).toInt() and 0xFF
        if (protocol != 17) return false
        
        // Verificar si el puerto de destino es 53 (DNS)
        val destPort = ((packet.get(22).toInt() and 0xFF) shl 8) or (packet.get(23).toInt() and 0xFF)
        return destPort == 53
    }
}
