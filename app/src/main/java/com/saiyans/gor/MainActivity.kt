package com.saiyans.gor

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.util.Log
// Quitar imports no usados como: import android.widget.Button
// Quitar imports no usados como: import android.widget.EditText
import android.widget.EditText // Todavía lo usamos para el diálogo
import android.widget.LinearLayout // Para el diálogo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
// Quitar import no usado: import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
// Quitar import no usado: import androidx.recyclerview.widget.RecyclerView
// Quitar import no usado: import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.proxyapp.databinding.ActivityMainBinding // <<< USA VIEW BINDING
import com.example.proxyapp.db.ProxyServer // Importar desde tu paquete db
import com.example.proxyapp.db.ProxyServerDao // Importar desde tu paquete db
import com.example.proxyapp.ui.MainViewModel // Importar desde tu paquete ui
import com.example.proxyapp.ui.ProxyServerAdapter // Importar desde tu paquete ui
// import kotlinx.coroutines.launch // No se usa directamente aquí ahora


// Constantes (pueden ir en un archivo común)
const val PREFS_NAME = "ProxySettings" // Mantenemos el nombre original de tu código
const val KEY_SELECTED_SERVER_ID = "selected_server_id"
const val INVALID_SERVER_ID = -1

// Define la Factory aquí o en un archivo separado
class MainViewModelFactory(private val dao: ProxyServerDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


class MainActivity : AppCompatActivity() {

    // ViewBinding
    private lateinit var binding: ActivityMainBinding

    private lateinit var serverAdapter: ProxyServerAdapter
    private lateinit var prefs: SharedPreferences
    private var selectedServerId: Int = INVALID_SERVER_ID

    // ViewModel inicializado con la Factory
    private val viewModel: MainViewModel by viewModels {
        // Obtener el DAO de la instancia de Application
        MainViewModelFactory((application as MyApplication).database.proxyServerDao())
    }

    // VPN Permission Launcher
    private val prepareVpn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i("MainActivity", "Permiso VPN concedido por el usuario.")
            startVpnService()
        } else {
            Toast.makeText(this, "Permiso VPN denegado", Toast.LENGTH_SHORT).show()
            Log.w("MainActivity", "Permiso VPN denegado por el usuario.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar layout usando ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Cargar el ID seleccionado ANTES de configurar el adapter
        selectedServerId = prefs.getInt(KEY_SELECTED_SERVER_ID, INVALID_SERVER_ID)
        Log.d("MainActivity", "onCreate - ID de servidor cargado de Prefs: $selectedServerId")


        setupRecyclerView()
        setupObservers()
        setupClickListeners()

         // Asegúrate de aplicar la selección inicial al adapter aquí también,
         // por si los datos se cargan antes de que el observer se active la primera vez.
         serverAdapter.setSelectedServerId(selectedServerId)
         Log.d("MainActivity", "onCreate - Selección inicial $selectedServerId aplicada al adapter")
    }

    private fun setupRecyclerView() {
        Log.d("MainActivity", "Configurando RecyclerView")
        serverAdapter = ProxyServerAdapter(
            onServerClick = { server ->
                Log.d("MainActivity", "Clic en servidor: ${server.name}")
                selectServer(server)
                          },
            onDeleteClick = { server ->
                Log.d("MainActivity", "Clic en borrar servidor: ${server.name}")
                showDeleteConfirmationDialog(server)
                          }
        )
        binding.recyclerViewServers.apply { // Acceder a través de binding
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = serverAdapter
            // Opcional: añadir item decoration para líneas separadoras
            // addItemDecoration(DividerItemDecoration(this@MainActivity, LinearLayoutManager.VERTICAL))
        }
    }

    private fun setupObservers() {
        Log.d("MainActivity", "Configurando Observers")
        // Observa la lista de servidores desde el ViewModel
        viewModel.allServers.asLiveData().observe(this) { servers ->
            servers?.let {
                Log.d("MainActivity", "Observer recibió lista actualizada: ${it.size} items. SelectedID actual: $selectedServerId")
                serverAdapter.submitList(it) {
                    // Este bloque se ejecuta después de que DiffUtil calcula las diferencias
                    // y actualiza el RecyclerView. Es un buen lugar para asegurar
                    // que la selección visual esté correcta después de cambios en la lista.
                    Log.d("MainActivity", "submitList completado. Reaplicando selección $selectedServerId al adapter.")
                    serverAdapter.setSelectedServerId(selectedServerId)
                }
            } ?: Log.d("MainActivity", "Observer recibió lista null")
        }
    }

    private fun setupClickListeners() {
        Log.d("MainActivity", "Configurando Click Listeners")
        binding.buttonStartVpn.setOnClickListener {
            Log.d("MainActivity", "Botón Iniciar VPN presionado")
            if (selectedServerId == INVALID_SERVER_ID) {
                Toast.makeText(this, "Por favor, selecciona un servidor primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prepareAndStartVpn()
        }

        binding.buttonStopVpn.setOnClickListener {
            Log.d("MainActivity", "Botón Detener VPN presionado")
            stopVpnService()
        }

        binding.fabAddServer.setOnClickListener {
            Log.d("MainActivity", "Botón Añadir Servidor presionado")
            showAddEditServerDialog(null) // Pasar null para añadir nuevo
        }
    }

    // Muestra un diálogo simple para añadir/editar servidor
    private fun showAddEditServerDialog(serverToEdit: ProxyServer?) {
        val isEditing = serverToEdit != null
        val dialogTitle = if (isEditing) "Editar Servidor" else "Añadir Servidor"
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle(dialogTitle)

        Log.d("MainActivity", "Mostrando diálogo para ${if (isEditing) "editar" else "añadir"} servidor.")

        // Layout para el diálogo
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        layout.setPadding(padding, padding/2, padding, padding/2)

        val inputName = EditText(context).apply {
            hint = "Nombre (ej: Proxy Casa)"
            setText(serverToEdit?.name ?: "")
        }
        layout.addView(inputName)

        val inputHost = EditText(context).apply {
            hint = "Host (ej: tor.saiyans.com.ve o IP)"
            setText(serverToEdit?.host ?: "")
        }
        layout.addView(inputHost)

        val inputPort = EditText(context).apply {
            hint = "Puerto (ej: 8443)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(serverToEdit?.port?.toString() ?: "")
        }
        layout.addView(inputPort)

        builder.setView(layout)

        // Botones
        builder.setPositiveButton(if (isEditing) "Guardar" else "Añadir") { dialog, _ ->
            val name = inputName.text.toString().trim()
            val host = inputHost.text.toString().trim()
            val portStr = inputPort.text.toString().trim()

            if (name.isEmpty() || host.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(context, "Todos los campos son requeridos", Toast.LENGTH_SHORT).show()
                // No cerrar el diálogo si la validación falla (necesitaría override del listener)
                // Por ahora, simplemente no hacemos nada y el diálogo se cierra.
                // Para mantenerlo abierto: https://stackoverflow.com/questions/2620444/how-to-prevent-a-dialog-from-closing-when-a-button-is-clicked
                Log.w("MainActivity", "Validación fallida al guardar/añadir servidor.")
                return@setPositiveButton
            }

            try {
                val port = portStr.toInt()
                if (port <= 0 || port > 65535) {
                    Toast.makeText(context, "Puerto inválido (1-65535)", Toast.LENGTH_SHORT).show()
                     Log.w("MainActivity", "Puerto inválido: $port")
                    return@setPositiveButton
                }

                val server = if (isEditing) {
                    // Actualizar el objeto existente con el mismo ID
                    serverToEdit!!.copy(name = name, host = host, port = port)
                } else {
                    // Crear nuevo objeto (ID será 0, Room lo autogenerará)
                    ProxyServer(name = name, host = host, port = port)
                }

                Log.d("MainActivity", "Guardando/Añadiendo servidor: $server")
                viewModel.addServer(server) // Guardar en BD vía ViewModel
                // El diálogo se cierra automáticamente aquí

            } catch (e: NumberFormatException) {
                Toast.makeText(context, "Puerto inválido (debe ser número)", Toast.LENGTH_SHORT).show()
                 Log.w("MainActivity", "Error de formato de número en puerto: '$portStr'")
            } catch (e: Exception) {
                 Toast.makeText(context, "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                  Log.e("MainActivity", "Error inesperado al guardar servidor", e)
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
             Log.d("MainActivity", "Diálogo añadir/editar cancelado.")
             dialog.cancel()
        }

        // Mostrar el diálogo
        builder.create().show() // Usar create().show() para poder interceptar botones si fuera necesario
    }

    // Diálogo de confirmación para borrar
    private fun showDeleteConfirmationDialog(server: ProxyServer) {
        Log.d("MainActivity", "Mostrando confirmación para borrar: ${server.name}")
        AlertDialog.Builder(this)
            .setTitle("Confirmar Borrado")
            .setMessage("¿Estás seguro de que quieres borrar el servidor '${server.name}'?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Borrar") { _, _ ->
                Log.i("MainActivity", "Confirmado borrado para: ${server.name} (ID: ${server.id})")
                viewModel.deleteServer(server)
                // Si borramos el actualmente seleccionado, limpiar la selección
                if (server.id == selectedServerId) {
                    Log.i("MainActivity", "El servidor borrado era el seleccionado. Deseleccionando.")
                    selectServer(null)
                }
                Toast.makeText(this, "Servidor '${server.name}' borrado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                Log.d("MainActivity", "Borrado cancelado para: ${server.name}")
            }
            .show()
    }


    private fun selectServer(server: ProxyServer?) {
        val newId = server?.id ?: INVALID_SERVER_ID
        // Solo hacer algo si la selección realmente cambia
        if (newId != selectedServerId) {
            Log.i("MainActivity", "Seleccionando servidor. Nuevo ID: $newId (Anterior: $selectedServerId)")
            selectedServerId = newId
            // Guardar el nuevo ID seleccionado en SharedPreferences
            prefs.edit().putInt(KEY_SELECTED_SERVER_ID, selectedServerId).apply()
            // Actualizar la UI del adapter para mostrar el resaltado
            serverAdapter.setSelectedServerId(selectedServerId)
            Log.d("MainActivity", "Nuevo ID $selectedServerId guardado en Prefs y aplicado al adapter.")
        } else {
             Log.d("MainActivity", "Clic en servidor ya seleccionado (ID: $selectedServerId). Sin cambios.")
        }
    }

    private fun prepareAndStartVpn() {
        Log.d("MainActivity", "Preparando para iniciar VPN...")
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Necesitamos permiso del usuario
            Log.i("MainActivity", "Permiso VPN requerido. Lanzando intent de preparación...")
            try {
                prepareVpn.launch(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error lanzando VpnService.prepare intent", e)
                 Toast.makeText(this, "No se pudo solicitar permiso VPN", Toast.LENGTH_LONG).show()
            }
        } else {
            // Permiso ya concedido
            Log.i("MainActivity", "Permiso VPN ya concedido.")
            startVpnService()
        }
    }

    private fun startVpnService() {
        if (selectedServerId == INVALID_SERVER_ID) {
            Log.e("MainActivity", "Intento de iniciar VPN sin servidor seleccionado.")
            Toast.makeText(this, "Error: Ningún servidor seleccionado.", Toast.LENGTH_LONG).show()
            return
        }
        Log.i("MainActivity", "Enviando intent para iniciar VpnService (ACTION_CONNECT)...")
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_CONNECT // Usar acción definida
        }
        // Iniciar como servicio en primer plano es crucial para VPNs
        try {
            // Necesario desde Android O (API 26)
             startForegroundService(intent)
            Toast.makeText(this, "Iniciando conexión VPN...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error iniciando Foreground Service", e)
             Toast.makeText(this, "Error al iniciar servicio VPN: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVpnService() {
        Log.i("MainActivity", "Enviando intent para detener VpnService (ACTION_DISCONNECT)...")
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_DISCONNECT
        }
        // Usar startService para enviar el comando de parada
        try {
             startService(intent)
             Toast.makeText(this, "Deteniendo VPN...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
             Log.e("MainActivity", "Error enviando comando de parada al servicio", e)
             Toast.makeText(this, "Error al detener servicio VPN: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}