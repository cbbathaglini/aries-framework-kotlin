package org.hyperledger.ariesproject

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2

class VerifierProofActivity : AppCompatActivity() {

    private lateinit var scannerView: DecoratedBarcodeView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: Button
    private var hasProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verifier_proof)

        scannerView = findViewById(R.id.scannerView)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener { finish() }

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

                runOnUiThread {
                    statusText.text = "📄 QR lido! Processando..."
                }

                processProof(text)
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })
    }

    override fun onResume() {
        super.onResume()
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
                statusText.text = "🔄 Processando solicitação..."
                statusText.setTextColor(Color.LTGRAY)

                println("📦 JSON recebido: ${json.take(200)}")

                val app = application as? WalletApp
                    ?: throw IllegalStateException("WalletApp não inicializado!")

                val agent = app.agent
                    ?: throw IllegalStateException("Agent não inicializado no WalletApp!")

                println("✅ Agent encontrado: ${agent.javaClass.simpleName}")

                val requestMsg = try {
                    Json.decodeFromString(RequestPresentationMessageV2.serializer(), json)
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw IllegalArgumentException("Falha ao decodificar RequestPresentationMessageV2: ${e.localizedMessage}")
                }

                println("✅ RequestPresentationMessageV2 decodificado: ${requestMsg}")

                val record = try {
                    agent.proofCommandV2.processRequest(requestMsg)
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw IllegalStateException("Falha ao processar request: ${e.localizedMessage}")
                }

                println("✅ ProofRecord criado: ${record.id}")

                val (proofRecord, presentation) = agent.proofCommandV2.createPresentation(record)

                println("✅ PresentationMessageV2 criado!")

                val presentationJson = Json.encodeToString(
                    PresentationMessageV2.serializer(),
                    presentation
                )

                println("📄 Presentation JSON: ${presentationJson.take(300)}...")

                statusText.text = "✅ Apresentação gerada com sucesso!"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))

            } catch (e: Exception) {
                e.printStackTrace()
                statusText.text = "❌ Erro: ${e.localizedMessage ?: e.toString()}"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            } finally {
                progressBar.visibility = View.GONE
                hasProcessed = false
                scannerView.resume()
            }
        }
    }
}