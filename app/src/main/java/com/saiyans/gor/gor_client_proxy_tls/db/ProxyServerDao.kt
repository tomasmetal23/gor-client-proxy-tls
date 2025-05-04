package com.saiyans.gor

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyServerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(server: ProxyServer)

    @Query("SELECT * FROM proxy_servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<ProxyServer>>

    @Query("SELECT * FROM proxy_servers WHERE id = :serverId LIMIT 1")
    suspend fun getServerById(serverId: Int): ProxyServer?

    @Delete
    suspend fun delete(server: ProxyServer)

    @Query("DELETE FROM proxy_servers WHERE id = :serverId")
    suspend fun deleteById(serverId: Int)

    @Query("SELECT * FROM proxy_servers WHERE name = :serverName LIMIT 1")
    suspend fun getServerByName(serverName: String): ProxyServer?
}