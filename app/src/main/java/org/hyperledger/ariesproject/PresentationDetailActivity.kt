package org.hyperledger.ariesproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import org.hyperledger.ariesframework.error.CredoError

@SuppressLint("MissingPermission")
class PresentationDetailActivity : AppCompatActivity() {

    private lateinit var txtRecordId: TextView
    private lateinit var txtCreatedAt: TextView
    private lateinit var txtJson: TextView
    private lateinit var btnCopyJson: Button
    private lateinit var btnScanDevices: Button
    private lateinit var btnSendBluetooth: Button
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

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            appendLog("✅ Permissões concedidas. Iniciando scan…")
            startBleScan()
        } else {
            appendLog("❌ Permissões negadas.")
            Toast.makeText(this, "Permissões necessárias para Bluetooth", Toast.LENGTH_LONG).show()
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
        txtBluetoothStatus = findViewById(R.id.txtBluetoothStatus)
        devicesList        = findViewById(R.id.bluetoothDevicesList)
        logsRecycler       = findViewById(R.id.logsRecycler)

        logsAdapter = LogsAdapter(mutableListOf())
        logsRecycler.layoutManager = LinearLayoutManager(this)
        logsRecycler.adapter = logsAdapter

        devicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices)
        devicesList.adapter = devicesAdapter

        agent = (application as? WalletApp)?.agent
        val recordId = intent.getStringExtra("recordId")
        lifecycleScope.launch { loadRecord(recordId) }

        setupBluetoothClient()

        btnCopyJson.setOnClickListener {
            val json = txtJson.text.toString()
            if (json.isNotEmpty()) {
                copyToClipboard(json)
                Toast.makeText(this, "📋 Conteúdo copiado", Toast.LENGTH_SHORT).show()
            }
        }

        btnScanDevices.setOnClickListener { checkAndRequestPermsThenScan() }

        btnSendBluetooth.setOnClickListener {
            record?.let {
                pendingJson = loadJSONPreview(it)
                checkAndRequestPermsThenScan()
            }
        }

        devicesList.setOnItemClickListener { _, _, pos, _ ->
            val device = devices[pos]
            appendLog("🔗 Conectando a $device…")
            bluetoothClient.connectToNamedDevice(device)
        }
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

    private suspend fun loadRecord(recordId: String?) {
        if (recordId == null) return
        val rec = agent?.proofRepository?.getById(recordId)
        record = rec
        runOnUiThread {
            txtRecordId.text  = rec?.id ?: "—"
            txtCreatedAt.text = rec?.createdAt?.toString() ?: "—"
            txtJson.text      = loadJSONPreview(rec)
        }
    }

    private fun loadJSONPreview(record: ProofExchangeRecord?): String {
        val presentation = record?.presentationMessage ?: return "Nenhum conteúdo disponível"
        return try {
            val json = Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                encodeDefaults = true
                explicitNulls = false
            }.encodeToString(presentation)
            json
        } catch (e: Exception) {
            appendLog("❌ Erro ao gerar JSON: ${e.localizedMessage}")
            "{}"
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Presentation JSON", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun checkAndRequestPermsThenScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val need = perms.any {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) requestPerms.launch(perms)
        else startBleScan()
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

    private fun appendLog(line: String) {
        logsAdapter.add(line)
        logsRecycler.scrollToPosition(logsAdapter.itemCount - 1)
    }
}