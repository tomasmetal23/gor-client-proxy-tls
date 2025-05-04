package com.example.proxyapp.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxyapp.data.AppInfo
import com.example.proxyapp.data.ProxySettings
import com.example.proxyapp.service.ProxyVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProxyViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    
    private val _proxySettings = MutableStateFlow(ProxySettings())
    val proxySettings: StateFlow<ProxySettings> = _proxySettings.asStateFlow()
    
    private val _vpnPrepareIntent = MutableStateFlow<Intent?>(null)
    val vpnPrepareIntent: StateFlow<Intent?> = _vpnPrepareIntent.asStateFlow()
    
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()
    
    private val _appList = MutableStateFlow<List<AppInfo>>(emptyList())
    val appList: StateFlow<List<AppInfo>> = _appList.asStateFlow()
    
    fun setProxyCredentials(username: String?, password: String?) {
        viewModelScope.launch {
            _proxySettings.value = _proxySettings.value.copy(
                username = username,
                password = password
            )
        }
    }
    
    fun setProxySettings(host: String, port: Int) {
        viewModelScope.launch {
            _proxySettings.value = _proxySettings.value.copy(
                host = host,
                port = port
            )
        }
    }
    
    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                val currentAllowedApps = _proxySettings.value.allowedApps
                
                installedApps
                    .filter { app ->
                        // Filtrar solo aplicaciones de usuario que usan internet
                        (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                        pm.checkPermission(android.Manifest.permission.INTERNET, app.packageName) == PackageManager.PERMISSION_GRANTED
                    }
                    .map { app ->
                        val appName = pm.getApplicationLabel(app).toString()
                        val icon = pm.getApplicationIcon(app.packageName)
                        val isSelected = app.packageName in currentAllowedApps || currentAllowedApps.isEmpty()
                        
                        AppInfo(
                            packageName = app.packageName,
                            appName = appName,
                            icon = icon,
                            isSelected = isSelected
                        )
                    }
                    .sortedBy { it.appName }
            }
            
            _appList.value = apps
        }
    }
    
    fun updateAppSelection(packageName: String, isSelected: Boolean) {
        val updatedList = _appList.value.map { app ->
            if (app.packageName == packageName) {
                app.copy(isSelected = isSelected)
            } else {
                app
            }
        }
        _appList.value = updatedList
    }
    
    fun saveSelectedApps(isAppSelectiveMode: Boolean) {
        val selectedApps = _appList.value
            .filter { it.isSelected }
            .map { it.packageName }
            .toSet()
        
        _proxySettings.value = _proxySettings.value.copy(
            allowedApps = selectedApps,
            isAppSelectiveMode = isAppSelectiveMode
        )
    }
    
    fun prepareVpn() {
        val intent = VpnService.prepare(application.applicationContext)
        if (intent != null) {
            // El usuario necesita aprobar la VPN
            _vpnPrepareIntent.value = intent
        } else {
            // VPN ya está autorizada, podemos iniciarla directamente
            startVpnService()
        }
    }
    
    fun onVpnPermissionGranted() {
        _vpnPrepareIntent.value = null
        startVpnService()
    }
    
    private fun startVpnService() {
        val intent = Intent(application, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
            putExtra(ProxyVpnService.EXTRA_HOST, _proxySettings.value.host)
            putExtra(ProxyVpnService.EXTRA_PORT, _proxySettings.value.port)
            putExtra(ProxyVpnService.EXTRA_USERNAME, _proxySettings.value.username)
            putExtra(ProxyVpnService.EXTRA_PASSWORD, _proxySettings.value.password)
            putExtra(ProxyVpnService.EXTRA_APP_SELECTIVE_MODE, _proxySettings.value.isAppSelectiveMode)
            putStringArrayListExtra(
                ProxyVpnService.EXTRA_ALLOWED_APPS, 
                ArrayList(_proxySettings.value.allowedApps)
            )
        }
        application.startForegroundService(intent)
        _isVpnActive.value = true
    }
    
    fun stopVpnService() {
        val intent = Intent(application, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        application.startService(intent)
        _isVpnActive.value = false
    }
}
