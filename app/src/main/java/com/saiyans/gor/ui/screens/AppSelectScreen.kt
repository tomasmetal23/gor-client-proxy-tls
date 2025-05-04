package com.example.proxyapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxyapp.data.AppInfo
import com.example.proxyapp.viewmodel.ProxyViewModel

@Composable
fun AppSelectScreen(
    viewModel: ProxyViewModel = hiltViewModel(),
    onBackToSettings: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectiveMode by remember { mutableStateOf(false) }
    
    // Cargar la lista de aplicaciones
    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context)
        viewModel.appList.collect { apps ->
            appList = apps
            isLoading = false
        }
        
        viewModel.proxySettings.collect { settings ->
            selectiveMode = settings.isAppSelectiveMode
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Barra superior con título y botón de guardar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Seleccionar Aplicaciones",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Button(
                onClick = {
                    viewModel.saveSelectedApps(selectiveMode)
                    onBackToSettings()
                }
            ) {
                Text("Guardar")
            }
        }
        
        // Modo de selección
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modo de filtrado:",
                modifier = Modifier.padding(end = 8.dp)
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    selectiveMode = false
                }
            ) {
                RadioButton(
                    selected = !selectiveMode,
                    onClick = { selectiveMode = false }
                )
                Text("Todas las apps")
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    selectiveMode = true
                }
            ) {
                RadioButton(
                    selected = selectiveMode,
                    onClick = { selectiveMode = true }
                )
                Text("Solo seleccionadas")
            }
        }
        
        // Barra de búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar aplicaciones") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        
        // Lista de aplicaciones
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                val filteredApps = if (searchQuery.isEmpty()) {
                    appList
                } else {
                    appList.filter { 
                        it.appName.contains(searchQuery, ignoreCase = true) 
                    }
                }
                
                items(filteredApps) { app ->
                    AppItem(
                        app = app,
                        onAppSelected = { selected ->
                            viewModel.updateAppSelection(app.packageName, selected)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppItem(
    app: AppInfo,
    onAppSelected: (Boolean) -> Unit
) {
    var isSelected by remember { mutableStateOf(app.isSelected) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                isSelected = !isSelected
                app.isSelected = isSelected
                onAppSelected(isSelected)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de la app
        Image(
            bitmap = app.icon.toBitmap().asImageBitmap(),
            contentDescription = "App icon",
            modifier = Modifier
                .size(40.dp)
                .padding(end = 16.dp)
        )
        
        // Nombre de la app
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { checked ->
                isSelected = checked
                app.isSelected = checked
                onAppSelected(checked)
            }
        )
    }
}
