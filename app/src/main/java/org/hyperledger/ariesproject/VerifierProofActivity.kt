package org.hyperledger.ariesproject

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.hyperledger.ariesproject.databinding.ActivityVerifierProofBinding

class VerifierProofActivity : BaseActivity() {

    private lateinit var scannerView: DecoratedBarcodeView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: Button
    private lateinit var generateButton: Button
    private lateinit var credentialSpinner: Spinner

    private var hasProcessed = false
    private var proofRequest: AnonCredsProofRequest? = null
    private var compatibleCredentials: List<CredentialExchangeRecord> = emptyList()
    private var selectedCredentialId: String? = null
    private var scannedJson: String? = null
    private var proofRecordId: String? = null
    private lateinit var binding: ActivityVerifierProofBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVerifierProofBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        scannerView = findViewById(R.id.scannerView)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        backButton = findViewById(R.id.backButton)
        credentialSpinner = findViewById(R.id.credentialSpinner)
        generateButton = findViewById(R.id.generateButton)

        backButton.setOnClickListener { finish() }
        generateButton.visibility = View.GONE
        credentialSpinner.visibility = View.GONE

        generateButton.setOnClickListener {
            if (selectedCredentialId != null && proofRecordId != null) {
                generatePresentation()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            startScanner()
        }
    }

    private fun startScanner() {
        scannerView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val text = result?.text ?: return
                if (hasProcessed) return
                hasProcessed = true
                runOnUiThread { statusText.text = "📄 QR scanned! Processing..." }
                processProof(text)
            }

            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
        scannerView.resume()
        hasProcessed = false
    }

    override fun onPause() {
        super.onPause()
        scannerView.pause()
    }

    private fun processProof(json: String) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                statusText.text = "🔄 Processing proof request..."
                statusText.setTextColor(Color.LTGRAY)

                scannedJson = json

                val app = application as? WalletApp ?: throw IllegalStateException("WalletApp not initialized!")
                val agent = app.agent ?: throw IllegalStateException("Agent not initialized!")

                val requestMsg = Json.decodeFromString(RequestPresentationMessageV2.serializer(), json)
                val record = agent.proofCommandV2.processRequest(requestMsg)
                proofRecordId = record.id

                val anonCredsString = requestMsg.anoncredsProofRequest()
                proofRequest = Json.decodeFromString(AnonCredsProofRequest.serializer(), anonCredsString)

                statusText.text = "🔍 Loading compatible credentials..."
                loadCompatibleCredentials()

            } catch (e: Exception) {
                e.printStackTrace()
                statusText.text = "❌ Error: ${e.localizedMessage ?: e.toString()}"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                progressBar.visibility = View.GONE
                hasProcessed = false
            }
        }
    }

    private suspend fun loadCompatibleCredentials() {
        try {
            val app = application as? WalletApp ?: throw IllegalStateException("WalletApp not initialized!")
            val agent = app.agent ?: throw IllegalStateException("Agent not initialized!")
            val allRecords = agent.credentialExchangeRepository.getAll()

            val requestedCredDefIds = proofRequest?.requestedAttributes?.values
                ?.flatMap { it.restrictions?.mapNotNull { r -> r.credDefId } ?: emptyList() } ?: emptyList()

            val requestedAttrNames = proofRequest?.requestedAttributes?.values
                ?.flatMap { attr ->
                    when {
                        attr.names != null -> attr.names!!
                        attr.name != null -> listOf(attr.name!!)
                        else -> emptyList()
                    }
                } ?: emptyList()

            compatibleCredentials = allRecords.filter { record ->
                val recordCredDefId = record.credentialDefinitionId ?: return@filter false
                val attrs = record.credentialAttributes?.associate { it.name to it.value } ?: emptyMap()
                val hasAllAttributes = requestedAttrNames.all { attrs.containsKey(it) }
                val matchesCredDef =
                    requestedCredDefIds.isEmpty() || requestedCredDefIds.contains(recordCredDefId)

                hasAllAttributes && matchesCredDef
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                if (compatibleCredentials.isEmpty()) {
                    statusText.text = "⚠️ No compatible credentials found."
                    credentialSpinner.visibility = View.GONE
                } else {
                    statusText.text = "✅ ${compatibleCredentials.size} compatible credentials found."
                    credentialSpinner.visibility = View.VISIBLE

                    val adapter = ArrayAdapter(
                        this@VerifierProofActivity,
                        android.R.layout.simple_spinner_item,
                        compatibleCredentials.map { "${it.comment} ?: ${it.id} (${it.id.take(6)})" }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    credentialSpinner.adapter = adapter

                    println("🎯 Total records: ${allRecords.size}")
                    println("🎯 Requested CredDefs: $requestedCredDefIds")
                    println("🎯 Requested Attributes: $requestedAttrNames")
                    println("🎯 Compatible: ${compatibleCredentials.size}")

                    credentialSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                            selectedCredentialId = compatibleCredentials[position].id
                            Log.i("credential selected: ", selectedCredentialId.toString())
                            generateButton.visibility = View.VISIBLE
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {
                            selectedCredentialId = null
                            generateButton.visibility = View.GONE
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                progressBar.visibility = View.GONE
                statusText.text = "❌ Error loading credentials: ${e.localizedMessage}"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun generatePresentation() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                statusText.text = "⚙️ Generating presentation..."

                val app = application as? WalletApp ?: throw IllegalStateException("WalletApp not initialized!")
                val agent = app.agent ?: throw IllegalStateException("Agent not initialized!")

                val record = agent.proofRepository.getById(proofRecordId!!)
                val (_, presentation) = agent.proofCommandV2.createPresentation(record, selectedCredentialId!!)

                val presentationJson = Json.encodeToString(PresentationMessageV2.serializer(), presentation)

                Snackbar.make(scannerView, "✅ Presentation successfully generated!", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(Color.parseColor("#2E7D32"))
                    .setTextColor(Color.WHITE)
                    .show()

                statusText.setTextColor(getColor(android.R.color.holo_green_dark))

            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(scannerView, "❌ Error generating presentation: ${e.localizedMessage}", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(Color.parseColor("#FF0000"))
                    .setTextColor(Color.WHITE)
                    .show()

                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}