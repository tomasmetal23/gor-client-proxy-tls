package com.example.proxyapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxyapp.viewmodel.ProxyViewModel
import kotlinx.coroutines.delay

@Composable
fun StatusScreen(
    viewModel: ProxyViewModel = hiltViewModel(),
    onBackToConfig: () -> Unit
) {
    var elapsedTime by remember { mutableStateOf(0L) }
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    var connectionStatus by remember { mutableStateOf("Conectando...") }
    
    // Actualizar estado de conexión
    LaunchedEffect(isVpnActive) {
        connectionStatus = if (isVpnActive) "Conectado" else "Desconectado"
    }
    
    // Actualizar tiempo transcurrido cada segundo si está conectado
    LaunchedEffect(isVpnActive) {
        if (isVpnActive) {
            while (true) {
                delay(1000)
                elapsedTime += 1
            }
        }
    }
    
    // Formatear tiempo transcurrido
    val formattedTime = remember(elapsedTime) {
        val hours = elapsedTime / 3600
        val minutes = (elapsedTime % 3600) / 60
        val seconds = elapsedTime % 60
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Estado de la VPN Proxy",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isVpnActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isVpnActive) {
                    Text(
                        text = "Tiempo conectado: $formattedTime",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Mostrar configuración actual
                    viewModel.proxySettings.collectAsState().value.let { settings ->
                        Text(
                            text = "Host: ${settings.host}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Puerto: ${settings.port}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        if (!settings.username.isNullOrBlank()) {
                            Text(
                                text = "Usuario: ${settings.username}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    viewModel.stopVpnService()
                    onBackToConfig()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Desconectar")
            }
            
            Button(
                onClick = onBackToConfig
            ) {
                Text("Configuración")
            }
        }
    }
}
