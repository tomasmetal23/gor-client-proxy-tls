package com.example.proxyapp.di

import com.example.proxyapp.proxy.ProxyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideProxyManager(): ProxyManager {
        return ProxyManager()
    }
}
