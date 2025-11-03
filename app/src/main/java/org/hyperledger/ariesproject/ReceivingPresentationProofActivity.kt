package org.hyperledger.ariesproject

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesproject.bluetooth.BluetoothServer

class ReceivingPresentationActivity : AppCompatActivity() {

    private lateinit var txtBluetoothState: TextView
    private lateinit var txtDevice: TextView
    private lateinit var txtStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutResult: LinearLayout
    private lateinit var txtJsonPreview: TextView
    private lateinit var txtLogs: TextView
    private lateinit var scrollLogs: ScrollView

    private lateinit var bluetoothServer: BluetoothServer
    private var agent: Agent? = null

    // === NOVO: launcher de permissão ===
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            appendLog("✅ Permissões concedidas, iniciando servidor BLE…")
            startBluetoothServer()
        } else {
            Toast.makeText(this, "❌ Permissões Bluetooth negadas.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        bluetoothServer.onJSONReceived = null
        bluetoothServer.disconnectClient()
        bluetoothServer.stopServer()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiving_presentation)

        txtBluetoothState = findViewById(R.id.txtBluetoothState)
        txtDevice = findViewById(R.id.txtDevice)
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)
        layoutResult = findViewById(R.id.layoutResult)
        txtJsonPreview = findViewById(R.id.txtJsonPreview)
        txtLogs = findViewById(R.id.txtLogs)
        scrollLogs = findViewById(R.id.scrollLogs)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnCopyLogs).setOnClickListener {
            val logs = txtLogs.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Logs BLE", logs)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "📋 Logs copiados para a área de transferência", Toast.LENGTH_SHORT).show()
        }

        agent = (application as? WalletApp)?.agent
        requestBluetoothPermissions()
    }

    private fun requestBluetoothPermissions() {
        val needed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val missing = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            appendLog("✅ Todas as permissões BLE já concedidas.")
            startBluetoothServer()
        } else {
            appendLog("⚠️ Solicitando permissões BLE faltantes: ${missing.joinToString()}")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // ==================================================
    // 🔹 INICIALIZA O SERVIDOR
    // ==================================================
    private fun startBluetoothServer() {
        bluetoothServer = BluetoothServer(this).apply {

            onDeviceConnected = { deviceName ->
                runOnUiThread {
                    txtDevice.text = "Dispositivo conectado: $deviceName"
                }
            }

            onLog = { log ->
                runOnUiThread {
                    txtBluetoothState.text = "Último evento: $log"
                    appendLog(log)
                }
            }

            onJSONReceived = { jsonString ->
                runOnUiThread {
                    txtStatus.text = "📥 Apresentação recebida!"
                    progressBar.visibility = ProgressBar.VISIBLE
                }

                lifecycleScope.launch {
                    try {
                        val jsonFormatter = Json {
                            prettyPrint = true
                            prettyPrintIndent = "  "
                            encodeDefaults = true
                            ignoreUnknownKeys = true
                        }

                        val formattedJson = try {
                            val parsed = jsonFormatter.parseToJsonElement(jsonString)
                            jsonFormatter.encodeToString(JsonObject.serializer(), parsed.jsonObject)
                        } catch (_: Exception) {
                            jsonString
                        }

                        runOnUiThread {
                            txtJsonPreview.text = formattedJson
                            progressBar.visibility = ProgressBar.GONE
                            txtStatus.text = "✅ Apresentação recebida e exibida!"
                            layoutResult.setBackgroundColor(getColor(android.R.color.holo_green_light))
                        }

                        // 👉 Se quiser processar a apresentação localmente:
                        // val result = agent?.proofCommandV2?.processPresentationOffline(jsonString)
                        // runOnUiThread { ... }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            progressBar.visibility = ProgressBar.GONE
                            txtStatus.text = "❌ Erro ao processar: ${e.message}"
                            layoutResult.setBackgroundColor(getColor(android.R.color.holo_red_light))
                        }
                    }
                }
            }
        }

        // ⚠️ Corrigido: não use `context` ou `onLog` fora do BluetoothServer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            appendLog("⚠️ Permissão BLUETOOTH_ADVERTISE não concedida (Android 12+).")
            return
        }

        bluetoothServer.startServer()
    }

    // ==================================================
    // 🔹 LOGGING UTIL
    // ==================================================
    private fun appendLog(msg: String) {
        txtLogs.append("\n$msg")
        scrollLogs.post { scrollLogs.fullScroll(ScrollView.FOCUS_DOWN) }
    }

}