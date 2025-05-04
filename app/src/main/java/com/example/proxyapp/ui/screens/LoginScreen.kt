package com.example.proxyapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxyapp.viewmodel.ProxyViewModel

@Composable
fun LoginScreen(
    viewModel: ProxyViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

        // Campo para Host del Proxy
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

        // Campo para Puerto del Proxy
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

        // Campo para Usuario del Proxy
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nombre de Usuario Proxy") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        // Campo para Contraseña del Proxy
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña Proxy") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

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
                    val port = proxyPort.toIntOrNull()
                    
                    if (proxyHost.isBlank()) {
                        errorMessage = "Por favor ingresa el host del proxy"
                        isLoading = false
                        return@Button
                    }
                    
                    if (port == null || port <= 0 || port > 65535) {
                        errorMessage = "Puerto inválido. Debe ser un número entre 1 y 65535"
                        isLoading = false
                        return@Button
                    }
                    
                    // Guardar credenciales y configuración en el ViewModel
                    viewModel.setProxyCredentials(username, password)
                    viewModel.setProxySettings(proxyHost, port)
                    
                    onLoginSuccess()
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
