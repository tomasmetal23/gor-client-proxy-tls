package com.example.gor_client_proxy_tls // <<< AJUSTA TU PACKAGE

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gor_client_proxy_tls.databinding.ActivityMainBinding // <<< USA VIEW BINDING

// Constantes (puedes moverlas a un archivo común)
const val PREFS_NAME = "ProxySettings"
const val KEY_SELECTED_SERVER_ID = "selected_server_id"
const val INVALID_SERVER_ID = -1

class MainActivity : AppCompatActivity() {

    // ViewBinding
    private lateinit var binding: ActivityMainBinding

    private lateinit var serverAdapter: ProxyServerAdapter
    private lateinit var prefs: SharedPreferences
    private var selectedServerId: Int = INVALID_SERVER_ID

    // ViewModel
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as MyApplication).database.proxyServerDao())
    }

    // VPN Permission Launcher
    private val prepareVpn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
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
        selectedServerId = prefs.getInt(KEY_SELECTED_SERVER_ID, INVALID_SERVER_ID)
        Log.d("MainActivity", "ID de servidor cargado de Prefs: $selectedServerId")


        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        serverAdapter = ProxyServerAdapter(
            onServerClick = { server -> selectServer(server) },
            onDeleteClick = { server -> showDeleteConfirmationDialog(server) }
        )
        binding.recyclerViewServers.apply { // Acceder a través de binding
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = serverAdapter
        }
        // Aplicar la selección inicial después de que el adapter esté listo
         serverAdapter.setSelectedServerId(selectedServerId)
    }

    private fun setupObservers() {
        viewModel.allServers.asLiveData().observe(this) { servers ->
            servers?.let {
                Log.d("MainActivity", "Actualizando lista de servidores en UI: ${it.size} items")
                serverAdapter.submitList(it) {
                    // Opcional: Volver a aplicar la selección después de que la lista se actualice
                    // para asegurar que la selección visual persista si la lista cambia rápido.
                     serverAdapter.setSelectedServerId(selectedServerId)
                     Log.d("MainActivity", "Selección $selectedServerId aplicada al adapter después de submitList")

                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonStartVpn.setOnClickListener {
            if (selectedServerId == INVALID_SERVER_ID) {
                Toast.makeText(this, "Por favor, selecciona un servidor primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prepareAndStartVpn()
        }

        binding.buttonStopVpn.setOnClickListener {
            stopVpnService()
        }

        binding.fabAddServer.setOnClickListener {
            showAddEditServerDialog(null) // Pasar null para añadir nuevo
        }
    }

    // Muestra un diálogo simple para añadir/editar servidor
    private fun showAddEditServerDialog(serverToEdit: ProxyServer?) {
        val dialogTitle = if (serverToEdit == null) "Añadir Servidor" else "Editar Servidor"
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle(dialogTitle)

        // Layout para el diálogo (podrías hacerlo en un XML separado)
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt() // 16dp padding
        layout.setPadding(padding, padding/2, padding, padding/2)

        val inputName = EditText(context)
        inputName.hint = "Nombre (ej: Proxy Casa)"
        inputName.setText(serverToEdit?.name ?: "")
        layout.addView(inputName)

        val inputHost = EditText(context)
        inputHost.hint = "Host (ej: tor.saiyans.com.ve)"
        inputHost.setText(serverToEdit?.host ?: "")
        layout.addView(inputHost)

        val inputPort = EditText(context)
        inputPort.hint = "Puerto (ej: 8443)"
        inputPort.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputPort.setText(serverToEdit?.port?.toString() ?: "")
        layout.addView(inputPort)

        builder.setView(layout)

        // Botones
        builder.setPositiveButton(if (serverToEdit == null) "Añadir" else "Guardar") { dialog, _ ->
            val name = inputName.text.toString().trim()
            val host = inputHost.text.toString().trim()
            val portStr = inputPort.text.toString().trim()

            if (name.isEmpty() || host.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(context, "Todos los campos son requeridos", Toast.LENGTH_SHORT).show()
                return@setPositiveButton // No cierra el diálogo implícitamente
            }

            try {
                val port = portStr.toInt()
                if (port <= 0 || port > 65535) {
                    Toast.makeText(context, "Puerto inválido (1-65535)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Crear o actualizar el objeto ProxyServer
                val server = serverToEdit?.copy(name = name, host = host, port = port)
                    ?: ProxyServer(name = name, host = host, port = port)

                viewModel.addServer(server) // Llama al ViewModel para guardar/actualizar
                dialog.dismiss()

            } catch (e: NumberFormatException) {
                Toast.makeText(context, "Puerto inválido (debe ser número)", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    // Diálogo de confirmación para borrar
    private fun showDeleteConfirmationDialog(server: ProxyServer) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Borrado")
            .setMessage("¿Estás seguro de que quieres borrar el servidor '${server.name}'?")
            .setIcon(android.R.drawable.ic_dialog_alert) // Icono de advertencia
            .setPositiveButton("Borrar") { _, _ ->
                viewModel.deleteServer(server)
                // Si borramos el seleccionado, deseleccionamos
                if (server.id == selectedServerId) {
                    selectServer(null)
                }
                 Toast.makeText(this, "Servidor '${server.name}' borrado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null) // null simplemente cierra el diálogo
            .show()
    }


    private fun selectServer(server: ProxyServer?) {
        val newId = server?.id ?: INVALID_SERVER_ID
        if (newId != selectedServerId) { // Solo guardar y actualizar UI si cambia
            selectedServerId = newId
            prefs.edit().putInt(KEY_SELECTED_SERVER_ID, selectedServerId).apply()
            serverAdapter.setSelectedServerId(selectedServerId) // Actualiza el resaltado
            Log.i("MainActivity", "Servidor seleccionado ID: $selectedServerId")
        }
    }

    private fun prepareAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.i("MainActivity", "Solicitando permiso VPN...")
            prepareVpn.launch(intent)
        } else {
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
        Log.d("MainActivity", "Enviando intent para iniciar VpnService...")
        val intent = Intent(this, MyVpnService::class.java).apply {
             action = MyVpnService.ACTION_CONNECT // Añadir acciones para claridad
        }
        // Iniciar como servicio en primer plano para evitar que Android lo mate
        startForegroundService(intent)
         Toast.makeText(this, "Iniciando conexión VPN...", Toast.LENGTH_SHORT).show()
    }

    private fun stopVpnService() {
        Log.d("MainActivity", "Enviando intent para detener VpnService...")
         val intent = Intent(this, MyVpnService::class.java).apply {
             action = MyVpnService.ACTION_DISCONNECT
        }
        startService(intent) // startService también puede usarse para enviar comandos
        // Opcionalmente, podrías llamar a stopService(intent) si solo quieres detenerlo.
        Toast.makeText(this, "Deteniendo VPN...", Toast.LENGTH_SHORT).show()
    }
}