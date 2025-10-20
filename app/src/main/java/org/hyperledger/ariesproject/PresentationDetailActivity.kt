package org.hyperledger.ariesproject

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.bluetooth.BluetoothClient
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class PresentationDetailActivity : AppCompatActivity() {

    private lateinit var txtRecordId: TextView
    private lateinit var txtCreatedAt: TextView
    private lateinit var txtJson: TextView
    private lateinit var btnSendBluetooth: Button
    private lateinit var txtBluetoothStatus: TextView
    private lateinit var logView: TextView
    private lateinit var backButton: Button

    private var agent: Agent? = null
    private var record: ProofExchangeRecord? = null
    private lateinit var bluetoothClient: BluetoothClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation_detail)

        txtRecordId = findViewById(R.id.txtRecordId)
        txtCreatedAt = findViewById(R.id.txtCreatedAt)
        txtJson = findViewById(R.id.txtJson)
        btnSendBluetooth = findViewById(R.id.btnSendBluetooth)
        txtBluetoothStatus = findViewById(R.id.txtBluetoothStatus)
        logView = findViewById(R.id.logView)
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        agent = (application as? WalletApp)?.agent

        val recordId = intent.getStringExtra("recordId")
        lifecycleScope.launch { loadRecord(recordId) }

        bluetoothClient = BluetoothClient(this)
        bluetoothClient.onLog = { msg -> runOnUiThread { logView.append("$msg\n") } }

        btnSendBluetooth.setOnClickListener {
            record?.let { rec -> sendViaBluetooth(rec) }
        }
    }

    private suspend fun loadRecord(recordId: String?) {
        if (recordId == null) return
        val rec = agent?.proofRepository?.getById(recordId)
        record = rec
        runOnUiThread {
            txtRecordId.text = rec?.id ?: "—"
            txtCreatedAt.text = rec?.createdAt?.toString() ?: "—"
            txtJson.text = rec?.presentationMessage?.toString() ?: "Nenhum conteúdo disponível"
        }
    }


    @SuppressLint("MissingPermission")
    private fun sendViaBluetooth(record: ProofExchangeRecord) {
        val json = record.presentationMessage?.toString() ?: "{}"

        // 🔹 Verifica a permissão antes de escanear
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                1001
            )
            Toast.makeText(this, "Solicitando permissão Bluetooth...", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothClient.startScan()
        bluetoothClient.sendJSON(json)
        Toast.makeText(this, "📤 Apresentação enviada via Bluetooth", Toast.LENGTH_LONG).show()
    }
}