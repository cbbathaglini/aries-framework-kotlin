package org.hyperledger.ariesproject

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsEncoder
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
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

    private fun showSnack(message: String, ok: Boolean) {
        val root = binding.root
        val snack = Snackbar.make(root, message, Snackbar.LENGTH_LONG)
            .setTextColor(Color.WHITE)
            .setBackgroundTint(if (ok) Color.parseColor("#2E7D32") else Color.parseColor("#FF0000"))

        val bottomBar = findViewById<View?>(R.id.bottomNavigation)
        if (bottomBar != null) snack.setAnchorView(bottomBar)

        snack.show()
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
                statusText.setTextColor(Color.LTGRAY)
                statusText.text = "⚙️ Generating presentation..."

                val app = application as? WalletApp ?: throw IllegalStateException("WalletApp not initialized!")
                val agent = app.agent ?: throw IllegalStateException("Agent not initialized!")

                statusText.text = "📦 Loading proof record..."
                val record = agent.proofRepository.getById(proofRecordId!!)

                statusText.text = "🧠 Creating presentation..."
                val (_, presentation) = agent.proofCommandV2.createPresentation(record, selectedCredentialId!!)

                // ======== TESTE: encoded check ========
//                val json = Json { ignoreUnknownKeys = true }
//
//                val proofObj = decodeAnoncredsProofJson(presentation, json)
//                val before = pegarattr(proofObj)
//                logando("VERIFIER", "antss", before)
//
//                val nome = "Monica Geller Geller"
//                val tamperedProofObj = tamperRevealedRawByReferent(
//                    proofObj = proofObj,
//                    referent = before.referent,
//                    newRaw = nome
//                )
//
//                //val after = pegarattr(tamperedProofObj) // cuidado: isso pega o "first" de novo
//                val afterSame = run {
//                    val requestedProof = tamperedProofObj["requested_proof"]!!.jsonObject
//                    val revealedAttrs = requestedProof["revealed_attrs"]!!.jsonObject
//                    val attr = revealedAttrs[before.referent]!!.jsonObject
//                    val raw = attr["raw"]?.jsonPrimitive?.contentOrNull
//                    val encoded = attr["encoded"]?.jsonPrimitive?.contentOrNull
//                    EncSample(
//                        referent = before.referent,
//                        raw = raw,
//                        encoded = encoded,
//                        expected = AnonCredsEncoder.encodeCredentialValue(raw)
//                    )
//                }
//                logando("VERIFIER", "AFTER tamper", afterSame)
//
//                try {
//                    encodeanoncredsproof(tamperedProofObj)
//                    Log.e("VERIFIER", "🚨 Encoding check depois: PASSED (RUIM!)")
//                } catch (e: Throwable) {
//                    Log.i("VERIFIER", "✅ Encoding check depois: FAILED como esperado -> ${e.message}")
//                }
//
//                try {
//                    encodeanoncredsproof(tamperedProofObj)
//                    Log.e("VERIFIER", "🚨 Encoding check AFTER tamper: PASSED (isso seria ruim!)")
//                    showSnack("🚨 Encoding check AFTER tamper: PASSED (RUIM!)", ok = false)
//                } catch (e: Throwable) {
//                    Log.i("VERIFIER", "✅ Encoding check AFTER tamper: FAILED como esperado -> ${e.message}")
//                    showSnack("✅ Encoding check AFTER tamper: FAILED (ok)", ok = true)
//                }
//
//                val tamperedPresentationJson = alterarjsonpresentation(
//                    pres = presentation,
//                    json = json,
//                    tamperedProofObj = tamperedProofObj
//                )
//                Log.i("VERIFIER", "presentation json size=${tamperedPresentationJson.length}")
                // =====================================

                statusText.text = "✅ Done!"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))

            } catch (t: Throwable) {
                Log.e("VERIFIER", "generatePresentation failed", t)
                showSnack("❌ Error generating presentation: ${t.localizedMessage ?: t}", ok = false)
                statusText.text = "❌ Error: ${t.localizedMessage ?: t}"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    // ================== encoded-check helpers ==================
//    private val ANONCREDS_ATTACH_ID = "anoncreds"
//
//    private fun getAnoncredsAttachmentIndex(pres: PresentationMessageV2): Int {
//        val idx = pres.presentationAttachments.indexOfFirst { it.id == ANONCREDS_ATTACH_ID }
//        if (idx < 0) error("anoncreds attachment not found")
//        return idx
//    }
//
//    /**
//     * Reads the anoncreds proof JSON from the attachment (base64) of PresentationMessageV2
//     * (API 24 ok, usando android.util.Base64)
//     */
//    private fun decodeAnoncredsProofJson(pres: PresentationMessageV2, json: Json): JsonObject {
//        val idx = getAnoncredsAttachmentIndex(pres)
//        val b64 = pres.presentationAttachments[idx].data.base64
//            ?: error("anoncreds attachment has no base64 data")
//
//        val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
//        return json.parseToJsonElement(decoded).jsonObject
//    }
//
//    /**
//     * Changes ONLY 1 raw (keeps encoded) in requested_proof.revealed_attrs OR revealed_attr_groups.
//     */
//    private fun tamperRevealedRawByReferent(
//        proofObj: JsonObject,
//        referent: String,
//        newRaw: String
//    ): JsonObject {
//        val requestedProof = proofObj["requested_proof"]?.jsonObject
//            ?: error("requested_proof not found in anoncreds proof")
//
//        val revealedAttrs = requestedProof["revealed_attrs"]?.jsonObject
//            ?: error("revealed_attrs not found")
//
//        val attrObj = revealedAttrs[referent]?.jsonObject
//            ?: error("revealed_attrs[$referent] not object")
//
//        val newAttrObj = JsonObject(attrObj.toMutableMap().apply {
//            put("raw", JsonPrimitive(newRaw))
//            // NÃO mexe no encoded
//        })
//
//        val newRevealedAttrs = JsonObject(revealedAttrs.toMutableMap().apply {
//            put(referent, newAttrObj)
//        })
//
//        val newRequestedProof = JsonObject(requestedProof.toMutableMap().apply {
//            put("revealed_attrs", newRevealedAttrs)
//        })
//
//        return JsonObject(proofObj.toMutableMap().apply {
//            put("requested_proof", newRequestedProof)
//        })
//    }
//
//    /**
//     * Executa o check raw->encoded em todos os revealed attrs e groups.
//     * Throws exception on the first mismatch.
//     */
//    private fun encodeanoncredsproof(proofObj: JsonObject) {
//        val requestedProof = proofObj["requested_proof"]?.jsonObject
//            ?: error("requested_proof not found")
//
//        requestedProof["revealed_attrs"]?.jsonObject?.forEach { (referent, v) ->
//            val attr = v.jsonObject
//            val raw = attr["raw"]?.jsonPrimitive?.contentOrNull
//            val encoded = attr["encoded"]?.jsonPrimitive?.contentOrNull
//                ?: error("encoded missing for $referent")
//
//            val expected = AnonCredsEncoder.encodeCredentialValue(raw)
//            if (expected != encoded) {
//                error("mismatch for '$referent' expected=$expected actual=$encoded raw=$raw")
//            }
//        }
//
//        // revealed_attr_groups
//        requestedProof["revealed_attr_groups"]?.jsonObject?.forEach { (_, groupEl) ->
//            val groupObj = groupEl.jsonObject
//            val values = groupObj["values"]?.jsonObject ?: return@forEach
//            values.forEach { (attrName, attrEl) ->
//                val attr = attrEl.jsonObject
//                val raw = attr["raw"]?.jsonPrimitive?.contentOrNull
//                val encoded = attr["encoded"]?.jsonPrimitive?.contentOrNull
//                    ?: error("encoded missing for group attr $attrName")
//
//                val expected = AnonCredsEncoder.encodeCredentialValue(raw)
//                if (expected != encoded) {
//                    error("mismatch for '$attrName' expected=$expected actual=$encoded raw=$raw")
//                }
//            }
//        }
//    }
//
//
//    //pegar campo e alterar o base64
//    private fun alterarjsonpresentation(
//        pres: PresentationMessageV2,
//        json: Json,
//        tamperedProofObj: JsonObject
//    ): String {
//        val presJsonString = Json.encodeToString(PresentationMessageV2.serializer(), pres)
//        val presObj = json.parseToJsonElement(presJsonString).jsonObject
//
//        val attaches = presObj["presentations~attach"]?.jsonArray
//            ?: error("presentations~attach not found")
//
//        val newProofStr = json.encodeToString(JsonObject.serializer(), tamperedProofObj)
//        val newB64 = Base64.encodeToString(newProofStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
//
//        val newAttaches: List<JsonElement> = attaches.map { attEl ->
//            val attObj = attEl.jsonObject
//
//            val attId =
//                attObj["@id"]?.jsonPrimitive?.contentOrNull
//                    ?: attObj["id"]?.jsonPrimitive?.contentOrNull
//
//            if (attId == ANONCREDS_ATTACH_ID) {
//                val dataObj = attObj["data"]?.jsonObject ?: error("attachment.data missing")
//                val newDataObj = JsonObject(dataObj.toMutableMap().apply {
//                    put("base64", JsonPrimitive(newB64))
//                })
//
//                JsonObject(attObj.toMutableMap().apply {
//                    put("data", newDataObj)
//                })
//            } else {
//                attEl
//            }
//        }
//
//        val newPresObj = JsonObject(presObj.toMutableMap().apply {
//            put("presentations~attach", JsonArray(newAttaches))
//        })
//
//        return json.encodeToString(JsonObject.serializer(), newPresObj)
//    }
//
//    private data class EncSample(
//        val referent: String,
//        val raw: String?,
//        val encoded: String?,
//        val expected: String
//    )
//
//    //facilitar a leitura
//    private fun pegarattr(proofObj: JsonObject): EncSample {
//        val requestedProof = proofObj["requested_proof"]?.jsonObject
//            ?: error("requested_proof not found")
//
//        val revealedAttrs = requestedProof["revealed_attrs"]?.jsonObject
//            ?: error("revealed_attrs not found")
//
//        val referent = revealedAttrs.keys.firstOrNull()
//            ?: error("revealed_attrs is empty")
//
//        val attr = revealedAttrs[referent]?.jsonObject
//            ?: error("revealed_attrs[$referent] not object")
//
//        val raw = attr["raw"]?.jsonPrimitive?.contentOrNull
//        val encoded = attr["encoded"]?.jsonPrimitive?.contentOrNull
//        val expected = AnonCredsEncoder.encodeCredentialValue(raw)
//
//        return EncSample(
//            referent = referent,
//            raw = raw,
//            encoded = encoded,
//            expected = expected
//        )
//    }
//
//    private fun logando(tag: String, whenLabel: String, sample: EncSample) {
//        Log.i(tag, "==== $whenLabel ($tag) ====")
//        Log.i(tag, "atributo : ${sample.referent}")
//        Log.i(tag, "valor do atributo      : ${sample.raw}")
//        Log.i(tag, "encoded original  : ${sample.encoded}")
//        Log.i(tag, "o que veio no encoded : ${sample.expected}")
//        Log.i(tag, "match?   : ${sample.expected == sample.encoded}")
//    }
}