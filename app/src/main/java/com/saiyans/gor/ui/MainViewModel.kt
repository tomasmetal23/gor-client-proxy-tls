package com.saiyans.gor.ui 

import androidx.lifecycle.*
import com.saiyans.gor.db.ProxyServer 
import com.saiyans.gor.db.ProxyServerDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(private val dao: ProxyServerDao) : ViewModel() {
    // ... (contenido idéntico al anterior) ...
    val allServers: Flow<List<ProxyServer>> = dao.getAllServers()

    fun addServer(server: ProxyServer) {
        viewModelScope.launch {
            dao.insertOrUpdate(server)
        }
    }

    fun deleteServer(server: ProxyServer) {
        viewModelScope.launch {
            dao.delete(server)
        }
    }
}

// Factory (puede estar aquí o en archivo separado)
class MainViewModelFactory(private val dao: ProxyServerDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}