package com.example.proxyapp.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxyapp.viewmodel.ProxyViewModel

@Composable
fun ProxyConfigScreen(
    viewModel: ProxyViewModel = hiltViewModel(),
    onConnectClick: () -> Unit,
    onAppSelectClick: () -> Unit
) {
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("443") } // Puerto por defecto para HTTPS
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val vpnPrepareIntent by viewModel.vpnPrepareIntent.collectAsState()
    
    // Launcher para manejar el resultado de la solicitud de permiso VPN
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
            onConnectClick()
        } else {
            isLoading = false
            errorMessage = "Permiso de VPN denegado"
        }
    }
    
    // Observar el intent de preparación de VPN
    LaunchedEffect(vpnPrepareIntent) {
        vpnPrepareIntent?.let {
            vpnPermissionLauncher.launch(it)
        }
    }

    // Cargar configuraciones guardadas si existen
    LaunchedEffect(Unit) {
        viewModel.proxySettings.collect { settings ->
            proxyHost = settings.host ?: ""
            proxyPort = settings.port?.toString() ?: "443"
            username = settings.username ?: ""
            password = settings.password ?: ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Proxy HTTPS Client",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = proxyHost,
            onValueChange = { proxyHost = it },
            label = { Text("Host del Proxy") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = proxyPort,
            onValueChange = { proxyPort = it },
            label = { Text("Puerto del Proxy") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario (opcional)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña (opcional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        // Botón para seleccionar aplicaciones
        Button(
            onClick = onAppSelectClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Seleccionar Aplicaciones")
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                
                try {
                    val port = proxyPort.toIntOrNull() ?: 443
                    
                    if (proxyHost.isBlank()) {
                        errorMessage = "Por favor ingresa el host del proxy"
                        isLoading = false
                        return@Button
                    }
                    
                    if (port <= 0 || port > 65535) {
                        errorMessage = "Puerto inválido. Debe ser un número entre 1 y 65535"
                        isLoading = false
                        return@Button
                    }
                    
                    // Guardar configuración en el ViewModel
                    viewModel.setProxySettings(proxyHost, port)
                    viewModel.setProxyCredentials(
                        username.takeIf { it.isNotBlank() },
                        password.takeIf { it.isNotBlank() }
                    )
                    
                    // Preparar y solicitar permiso para la VPN
                    viewModel.prepareVpn()
                    
                    // Si no se necesita permiso, onConnectClick se llamará después de iniciar el servicio
                    if (viewModel.vpnPrepareIntent.value == null) {
                        onConnectClick()
                    }
                } catch (e: Exception) {
                    errorMessage = "Error: ${e.message}"
                    isLoading = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Conectar")
            }
        }
    }
}
