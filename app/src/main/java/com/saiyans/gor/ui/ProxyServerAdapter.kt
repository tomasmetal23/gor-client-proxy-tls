package com.saiyans.gor

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gor_client_proxy_tls.databinding.ItemProxyServerBinding 

// Constante definida en MainActivity.kt, puedes moverla a un archivo común
// const val INVALID_SERVER_ID = -1

class ProxyServerAdapter(
    private val onServerClick: (ProxyServer) -> Unit,
    private val onDeleteClick: (ProxyServer) -> Unit
) : ListAdapter<ProxyServer, ProxyServerAdapter.ServerViewHolder>(ServerDiffCallback()) {

    private var selectedServerId: Int = INVALID_SERVER_ID

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedServerId(id: Int) {
        // Solo notificar si el ID realmente cambió
        if (selectedServerId != id) {
            val previousSelectedId = selectedServerId
            selectedServerId = id
            // Optimización simple: notificar solo los items afectados si los encontramos
            findPositionById(previousSelectedId)?.let { notifyItemChanged(it) }
            findPositionById(selectedServerId)?.let { notifyItemChanged(it) }
            // Si no los encontramos (lista cambió mucho?), recurrir a notifyDataSetChanged
            // (o puedes implementar una lógica más compleja)
            // Por ahora, mantenemos el notifyDataSetChanged por simplicidad si fallan los anteriores
            if (findPositionById(previousSelectedId) == null || findPositionById(selectedServerId) == null) {
                 notifyDataSetChanged()
            }
        }
    }

    // Función helper para encontrar la posición
    private fun findPositionById(id: Int): Int? {
        if (id == INVALID_SERVER_ID) return null
        return currentList.indexOfFirst { it.id == id }.takeIf { it != -1 }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemProxyServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServerViewHolder(binding, onServerClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = getItem(position)
        holder.bind(server, server.id == selectedServerId)
    }

    // ViewHolder usando ViewBinding
    class ServerViewHolder(
        private val binding: ItemProxyServerBinding, // Usa el binding generado
        private val onServerClick: (ProxyServer) -> Unit,
        private val onDeleteClick: (ProxyServer) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(server: ProxyServer, isSelected: Boolean) {
            binding.textViewServerName.text = server.name
            binding.textViewServerDetails.text = "${server.host}:${server.port}"
            binding.root.setOnClickListener { onServerClick(server) } // Clic en toda la fila
            binding.buttonDeleteServer.setOnClickListener { onDeleteClick(server) }

            // Resaltar si está seleccionado
            val backgroundColor = if (isSelected) Color.LTGRAY else Color.TRANSPARENT
            binding.root.setBackgroundColor(backgroundColor)
        }
    }

    // DiffUtil para eficiencia del ListAdapter
    class ServerDiffCallback : DiffUtil.ItemCallback<ProxyServer>() {
        override fun areItemsTheSame(oldItem: ProxyServer, newItem: ProxyServer): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProxyServer, newItem: ProxyServer): Boolean {
            return oldItem == newItem // Comparación por data class
        }
    }
}