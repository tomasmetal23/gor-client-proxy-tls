package com.saiyans.gor

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_servers") // Nombre de la tabla en la BD
data class ProxyServer(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // ID único, autogenerado

    var name: String, // Nombre descriptivo dado por el usuario
    var host: String,
    var port: Int
)