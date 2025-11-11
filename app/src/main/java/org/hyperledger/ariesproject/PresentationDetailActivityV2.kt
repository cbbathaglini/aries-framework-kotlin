package org.hyperledger.ariesproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.bluetooth.BluetoothClient
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord


@SuppressLint("MissingPermission")
class PresentationDetailActivityV2 : AppCompatActivity() {

    private lateinit var txtRecordId: TextView
    private lateinit var txtCreatedAt: TextView
    private lateinit var txtJson: TextView
    private lateinit var btnCopyJson: Button
    private lateinit var btnCopyLogs: Button
    private lateinit var btnScanDevices: Button
    private lateinit var btnSendBluetooth: Button
    private lateinit var btnSendBluetoothAndroid: Button
    private lateinit var txtBluetoothStatus: TextView
    private lateinit var logsRecycler: RecyclerView
    private lateinit var logsAdapter: LogsAdapter
    private lateinit var devicesList: ListView

    private lateinit var bluetoothClient: BluetoothClient
    private var agent: Agent? = null
    private var record: ProofExchangeRecord? = null
    private var pendingJson: String? = null
    private val devices = mutableListOf<String>()
    private lateinit var devicesAdapter: ArrayAdapter<String>

    // === Novo: lista de permissões BLE (ajustada por versão) ===
    private val blePermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

//    private val requestPerms = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { grants ->
//        val allGranted = grants.values.all { it }
//        if (allGranted) {
//            appendLog("✅ Permissões concedidas. Iniciando scan…")
//            startBleScan()
//        } else {
//            appendLog("❌ Permissões negadas.")
//            Toast.makeText(this, "Permissões necessárias para Bluetooth", Toast.LENGTH_LONG).show()
//        }
//    }

    private val requestPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                appendLog("BLE = ✅ Permissões concedidas. Aguardando inicialização do Bluetooth...")

                // 🔄 Executa o scan depois de 1.5 s usando coroutine
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500)
                    appendLog("BLE = 🚀 Iniciando scan automático após permissões.")
                    startBleScan()

                    // 🔁 Reexecuta mais uma vez depois de 2 s para garantir descoberta
                    kotlinx.coroutines.delay(2000)
                    appendLog("BLE = 🔁 Repetindo scan após estabilização do BLE.")
                    startBleScan()
                }
            } else {
                appendLog("BLE = ❌ Permissões não concedidas.")
                Toast.makeText(this, "Permissões necessárias não concedidas.", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkAndRequestPermsThenScan() {
        val needRequest = blePermissions.any {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest) {
            appendLog("BLE = 🔐 Solicitando permissões...")
            requestPerms.launch(blePermissions)
        } else {
            appendLog("BLE = 🔎 Iniciando scan…")
            startBleScan()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation_detail)

        // === Inicializa views ===
        txtRecordId        = findViewById(R.id.txtRecordId)
        txtCreatedAt       = findViewById(R.id.txtCreatedAt)
        txtJson            = findViewById(R.id.txtJson)
        btnCopyJson        = findViewById(R.id.btnCopyJson)
        btnScanDevices     = findViewById(R.id.btnScanDevices)
        btnSendBluetooth   = findViewById(R.id.btnSendBluetooth)
        btnCopyLogs = findViewById(R.id.btnCopyLogs)
        //btnSendBluetoothAndroid   = findViewById(R.id.btnSendBluetoothAndroid)
        txtBluetoothStatus = findViewById(R.id.txtBluetoothStatus)
        devicesList        = findViewById(R.id.bluetoothDevicesList)
        logsRecycler       = findViewById(R.id.logsRecycler)

        logsAdapter = LogsAdapter(mutableListOf())
        logsRecycler.layoutManager = LinearLayoutManager(this)
        logsRecycler.adapter = logsAdapter

        devicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices)
        devicesList.adapter = devicesAdapter

        setupBluetoothClient()

        btnCopyJson.setOnClickListener {
            val json = txtJson.text.toString()
            if (json.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Presentation JSON", json)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Conteúdo copiado", Toast.LENGTH_SHORT).show()
            }
        }

        btnCopyLogs.setOnClickListener {
            // Recupera todas as mensagens do adapter
            val allLogs = logsAdapter.getAllLogs().joinToString("\n")

            if (allLogs.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Logs BLE", allLogs)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Logs copiados para a área de transferência", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nenhum log para copiar", Toast.LENGTH_SHORT).show()
            }
        }

        btnScanDevices.setOnClickListener {
            checkAndRequestPermsThenScan()
        }
//        btnScanDevices.setOnClickListener {
//            checkAndRequestPermsThenScan()
//            Handler(Looper.getMainLooper()).postDelayed({
//                btnScanDevices.performClick()
//            }, 1000)
//        }

        btnSendBluetooth.setOnClickListener {
            pendingJson = loadJSONPreview()
            if (bluetoothClient.isConnected()) {
                appendLog("📡 Conexão já ativa — enviando JSON diretamente…")
                sendJSONSafely(pendingJson!!)
            } else {
                appendLog("🔍 Ainda não conectado — iniciando scan…")
                checkAndRequestPermsThenScan()
            }

        }

        devicesList.setOnItemClickListener { _, _, pos, _ ->
            val device = devices[pos]
            appendLog("🔗 Conectando a $device…")
            bluetoothClient.connectToNamedDevice(device)
        }
    }

    override fun onStop() {
        super.onStop()
        bluetoothClient.disconnect()
        //bluetoothClientAV.disconnect()
    }

    private fun setupBluetoothClient() {
        bluetoothClient = BluetoothClient(this).apply {
            onLog = { msg ->
                runOnUiThread {
                    appendLog(msg)
                    txtBluetoothStatus.text = msg
                }
            }
            onDeviceFound = { name ->
                runOnUiThread {
                    if (!name.isNullOrBlank() && !devices.contains(name)) {
                        devices.add(name)
                        devicesAdapter.notifyDataSetChanged()
                    }
                }
            }
            onConnected = { name ->
                runOnUiThread {
                    appendLog("🤝 Conectado a $name")
                    txtBluetoothStatus.text = "Conectado a $name"
                    pendingJson?.let { sendJSONSafely(it) }
                }
            }
        }
    }

    // === CARREGA REGISTRO ===
    private suspend fun loadRecord(recordId: String?) {
        if (recordId == null) return
        val rec = agent?.proofRepository?.getById(recordId)
        record = rec
        runOnUiThread {
            txtRecordId.text  = rec?.id ?: "—"
            txtCreatedAt.text = rec?.createdAt?.toString() ?: "—"
            txtJson.text      = loadJSONPreview()
        }
    }

    private fun loadJSONPreview(): String {
        return "{\"msg\":\"123456789\"}"
    }


    private fun startBleScan() {
        devices.clear()
        devicesAdapter.notifyDataSetChanged()
        txtBluetoothStatus.text = "🔍 Procurando dispositivos BLE…"

        appendLog("🔎 Iniciando scan…")
        bluetoothClient.startScan()
    }


    private fun sendJSONSafely(json: String) {
        try {
            bluetoothClient.sendJSON(json)
            appendLog("✅ JSON enviado com sucesso.")
            Toast.makeText(this, "📤 Apresentação enviada via Bluetooth", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            appendLog("❌ Falha ao enviar JSON: ${e.localizedMessage}")
            Toast.makeText(this, "Erro ao enviar JSON", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendJSONSafelyAndroid(json: String) {
        try {
//            bluetoothClientAV = BluetoothClientAV(this)
//            bluetoothClientAV.start(json)

        } catch (e: Exception) {
            appendLog("❌ Falha ao enviar JSON: ${e.localizedMessage}")
            Toast.makeText(this, "Erro ao enviar JSON", Toast.LENGTH_LONG).show()
        }
    }

    private fun appendLog(line: String) {
        logsAdapter.add(line)
        logsRecycler.scrollToPosition(logsAdapter.itemCount - 1)
    }
}