package org.hyperledger.ariesproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

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

        bluetoothServer.onLog = { log ->
            runOnUiThread {
                txtBluetoothState.text = log
            }
        }

        bluetoothServer.onJSONReceived = { jsonString ->
            runOnUiThread {
                txtStatus.text = "📥 Apresentação recebida!"
                progressBar.visibility = ProgressBar.VISIBLE
            }

            lifecycleScope.launch {
                try {
                    print("AQUIIIIIII")
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
                        txtStatus.text = "❌ Erro na verificação: ${e.message}"
                    }
                }
            }
        }

        bluetoothServer.startServer()
    }
}