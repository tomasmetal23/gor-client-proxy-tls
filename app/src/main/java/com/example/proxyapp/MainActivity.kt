package com.example.proxyapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.proxyapp.ui.screens.AppSelectScreen
import com.example.proxyapp.ui.screens.ProxyConfigScreen
import com.example.proxyapp.ui.screens.StatusScreen
import com.example.proxyapp.ui.theme.ProxyAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProxyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "config") {
                        composable("config") {
                            ProxyConfigScreen(
                                onConnectClick = {
                                    navController.navigate("status")
                                },
                                onAppSelectClick = {
                                    navController.navigate("app_select")
                                }
                            )
                        }
                        composable("status") {
                            StatusScreen(
                                onBackToConfig = { // StatusScreen SÍ parece usar 'onBackToConfig'
                                    navController.navigate("config") {
                                        popUpTo("config") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("app_select") {
                            AppSelectScreen(
                                // 👇 *** CAMBIO AQUÍ *** 👇
                                onBackToSettings = { // Cambiado de onBackToConfig a onBackToSettings
                                    navController.navigate("config")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}