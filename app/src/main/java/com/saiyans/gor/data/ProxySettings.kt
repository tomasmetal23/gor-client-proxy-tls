package com.example.proxyapp.data

data class ProxySettings(
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val allowedApps: Set<String> = emptySet(),
    val isAppSelectiveMode: Boolean = false // false = todas las apps, true = solo apps seleccionadas
)
