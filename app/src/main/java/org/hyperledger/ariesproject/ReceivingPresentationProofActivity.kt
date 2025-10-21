package org.hyperledger.ariesproject

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
    private lateinit var txtCreatedAt: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutResult: LinearLayout

    private lateinit var bluetoothServer: BluetoothServer
    private var agent: Agent? = null

    private lateinit var txtJsonPreview: TextView

    private lateinit var txtLogs: TextView
    private lateinit var scrollLogs: ScrollView
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) startBluetoothServer()
        else Toast.makeText(this, "Permissões Bluetooth negadas.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiving_presentation)

        txtBluetoothState = findViewById(R.id.txtBluetoothState)
        txtDevice = findViewById(R.id.txtDevice)
        txtStatus = findViewById(R.id.txtStatus)
        txtCreatedAt = findViewById(R.id.txtCreatedAt)
        progressBar = findViewById(R.id.progressBar)
        layoutResult = findViewById(R.id.layoutResult)
        txtJsonPreview = findViewById(R.id.txtJsonPreview)
        txtLogs = findViewById(R.id.txtLogs)
        scrollLogs = findViewById(R.id.scrollLogs)

        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        val copyButton: Button = findViewById(R.id.btnCopyLogs)
        copyButton.setOnClickListener {
            val logs = txtLogs.text.toString()
            if (logs.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Logs BLE", logs)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Logs copiados para a área de transferência ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nenhum log disponível para copiar.", Toast.LENGTH_SHORT).show()
            }
        }

        agent = (application as? WalletApp)?.agent
        requestBluetoothPermissions()
    }

    private fun requestBluetoothPermissions() {
        val needed = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN
        )

        val missing = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) startBluetoothServer()
        else permissionLauncher.launch(needed)
    }

    private fun startBluetoothServer() {
        bluetoothServer = BluetoothServer(this)

        bluetoothServer.onDeviceConnected = { deviceName ->
            runOnUiThread {
                txtDevice.text = "Dispositivo conectado: $deviceName"
            }
        }

        bluetoothServer.onLog = { log ->
            runOnUiThread {
                // Mostra o log principal
                txtBluetoothState.text = "Último evento: $log"

                // Acumula logs no terminal
                txtLogs.append("\n$log")

                // Auto-scroll para o final
                scrollLogs.post { scrollLogs.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        bluetoothServer.onJSONReceived = { jsonString ->
            runOnUiThread {
                txtStatus.text = "📥 Apresentação recebida!"
                progressBar.visibility = ProgressBar.VISIBLE
            }

            lifecycleScope.launch {
                try {
                    // Tenta formatar JSON com kotlinx.serialization
                    val jsonFormatter = Json {
                        prettyPrint = true
                        prettyPrintIndent = "  "
                        encodeDefaults = true
                        ignoreUnknownKeys = true
                    }

                    val formattedJson = try {
                        val parsed = jsonFormatter.parseToJsonElement(jsonString)
                        jsonFormatter.encodeToString(JsonObject.serializer(), parsed.jsonObject)
                    } catch (e: Exception) {
                        // Se falhar no parse, mostra cru
                        jsonString
                    }

                    runOnUiThread {
                        txtJsonPreview.text = formattedJson
                        progressBar.visibility = ProgressBar.GONE
                        txtStatus.text = "✅ Apresentação recebida e exibida!"
                    }

//                    val result = agent?.proofCommandV2?.processPresentationOffline(jsonString)
//                    runOnUiThread {
//                        progressBar.visibility = ProgressBar.GONE
//                        if (result != null) {
//                            txtStatus.text = "✅ Apresentação verificada com sucesso!"
//                            layoutResult.setBackgroundColor(getColor(android.R.color.holo_green_light))
//                        } else {
//                            txtStatus.text = "❌ Falha na verificação."
//                            layoutResult.setBackgroundColor(getColor(android.R.color.holo_red_light))
//                        }
//                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE
                        txtStatus.text = "❌ Erro ao processar: ${e.message}"
                    }
                }
            }
        }

        bluetoothServer.startServer()
    }
}