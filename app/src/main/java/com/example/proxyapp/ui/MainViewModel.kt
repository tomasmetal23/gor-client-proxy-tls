package com.saiyans.gor

import androidx.lifecycle.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ViewModel para MainActivity
class MainViewModel(private val dao: ProxyServerDao) : ViewModel() {

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

// Factory para poder pasar el DAO al crear el ViewModel
class MainViewModelFactory(private val dao: ProxyServerDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}