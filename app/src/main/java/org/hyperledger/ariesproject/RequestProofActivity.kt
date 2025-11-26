package org.hyperledger.ariesproject

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsProofFormatService
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.proofs.models.*
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesframework.proofs.v1.ProofService
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import java.util.Calendar
import kotlin.String

class RequestProofActivity : AppCompatActivity() {

    private var connectionId: String? = null
    private var offline: Boolean = true
    private lateinit var addAttributeButton: Button
    private lateinit var addPredicateButton: Button
    private lateinit var requestProofButton: Button
    private lateinit var attributesContainer: LinearLayout
    private lateinit var predicatesContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var credentialDefInput: EditText
    private lateinit var toDateButton: Button
    private var fromTimestamp: Int? = null
    private var toTimestamp: Int? = null
    private lateinit var statusText: TextView
    private lateinit var qrImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_proof)

        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        // ConnectionId agora é opcional
        connectionId = intent.getStringExtra("CONNECTION_ID")

        if(intent.getStringExtra(HistoricalDetailFragment.ARG_OFFLINE_PROOF) != null) {
            offline = intent.getStringExtra(HistoricalDetailFragment.ARG_OFFLINE_PROOF).toBoolean()
        }

        credentialDefInput = findViewById(R.id.credentialDefInput)
        toDateButton = findViewById(R.id.toDateButton)

        toDateButton.setOnClickListener { pickDateTime(false) }
        addAttributeButton = findViewById(R.id.addAttributeButton)
        addPredicateButton = findViewById(R.id.addPredicateButton)
        requestProofButton = findViewById(R.id.requestProofButton)
        attributesContainer = findViewById(R.id.attributesContainer)
        predicatesContainer = findViewById(R.id.predicatesContainer)
        qrImageView = findViewById(R.id.qrImageView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        addAttributeButton.setOnClickListener { addAttributeField("") }
        addPredicateButton.setOnClickListener { addPredicateField("", ">", "") }
        requestProofButton.setOnClickListener { requestProof() }

        addAttributeField("nome")
        addAttributeField("email")
        addPredicateField("data_nascimento", ">", "19970612")
    }

    private fun pickDateTime(isFrom: Boolean) {
        val calendar = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute, 0)
                        val timestamp = (calendar.timeInMillis / 1000).toInt()

                        toTimestamp = timestamp
                        toDateButton.text = "Até: ${calendar.time}"

                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun addAttributeField(defaultName: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }

        val attrInput = EditText(this).apply {
            hint = "Nome do atributo"
            setText(defaultName)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val removeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(0x00000000)
            setOnClickListener { attributesContainer.removeView(layout) }
        }

        layout.addView(attrInput)
        layout.addView(removeButton)
        attributesContainer.addView(layout)
    }

    private fun addPredicateField(name: String, op: String, value: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }

        val nameInput = EditText(this).apply {
            hint = "Nome"
            setText(name)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val opInput = EditText(this).apply {
            hint = "Operador (>, >=, <, <=)"
            setText(op)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f)
        }

        val valueInput = EditText(this).apply {
            hint = "Valor"
            setText(value)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val removeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(0x00000000)
            setOnClickListener { predicatesContainer.removeView(layout) }
        }

        layout.addView(nameInput)
        layout.addView(opInput)
        layout.addView(valueInput)
        layout.addView(removeButton)
        predicatesContainer.addView(layout)
    }

    private fun requestProof() {
        lifecycleScope.launch {
            try {
                val app = application as WalletApp
                val credDefId = credentialDefInput.text.toString().trim()

//                if (credDefId.isEmpty()) {
//                    Toast.makeText(this@RequestProofActivity, "Informe o Credential Definition ID", Toast.LENGTH_SHORT).show()
//                    return@launch
//                }

                progressBar.visibility = View.VISIBLE
                statusText.text = "Enviando solicitação..."
                requestProofButton.isEnabled = false

                val attributes = mutableMapOf<String, AnonCredsRequestedAttribute>()
                val predicates = mutableMapOf<String, AnonCredsRequestedPredicate>()

                // Coleta atributos
                for (i in 0 until attributesContainer.childCount) {
                    val layout = attributesContainer.getChildAt(i) as LinearLayout
                    val attrInput = layout.getChildAt(0) as EditText
                    val name = attrInput.text.toString().trim()
                    if (name.isNotEmpty()) {
                        attributes[name] = AnonCredsRequestedAttribute(
                            name = name,
                            restrictions = listOf(AnonCredsProofRequestRestriction(credDefId = credDefId))
                        )
                    }
                }

                // Coleta predicados
                for (i in 0 until predicatesContainer.childCount) {
                    val layout = predicatesContainer.getChildAt(i) as LinearLayout
                    val nameInput = layout.getChildAt(0) as EditText
                    val opInput = layout.getChildAt(1) as EditText
                    val valueInput = layout.getChildAt(2) as EditText

                    val name = nameInput.text.toString().trim()
                    val op = opInput.text.toString().trim()
                    val value = valueInput.text.toString().trim()

                    if (name.isNotEmpty() && op.isNotEmpty() && value.isNotEmpty()) {
                        val type = mapPredicateType(op)
                        predicates[name] = AnonCredsRequestedPredicate(
                            name = name,
                            pType = type,
                            pValue = value.toLong(),
                            restrictions = listOf(AnonCredsProofRequestRestriction(credDefId = credDefId))
                        )
                    }
                }


                val revocationInterval = AnonCredsNonRevokedInterval(from = 0L, to = toTimestamp!!.toLong())

                val proofRequest = AnonCredsProofRequest(
                    name = "Dynamic Proof Request",
                    nonce = ProofService.generateProofRequestNonce(),
                    requestedAttributes = attributes,
                    requestedPredicates = predicates,
                    nonRevoked = revocationInterval,
                    version = "1.0"
                )

                val proofFormats: List<ProofFormatSpec> = listOf(
                    ProofFormatSpec(
                        attachmentId = RequestPresentationMessageV2.ANONCREDS_PROOF_REQUEST_ATTACHMENT_ID,
                        format = AnoncredsProofFormatService.ANONCREDS_PRESENTATION_REQUEST

                    )
                )

                var text :String = "Prova offline gerada!"
                var result : Pair<ProofExchangeRecord, VerifierRecord>? = null
                if (offline) {
                    result = app.agent.proofCommandV2.requestProofOffline(
                        proofRequest = proofRequest,
                        formats = proofFormats
                    )
                }else{
                    text = "Prova gerada!"
                    result = app.agent.proofCommandV2.requestProof(
                        connectionId = connectionId!!,
                        proofRequest = proofRequest,
                        formats = proofFormats
                    )
                }

                val record: ProofExchangeRecord = result.first
                val verifierRecord: VerifierRecord = result.second

                Log.d("ProofDebug", "Record.id = ${record.id}")
                Log.d("ProofDebug", "VerifierRecord.requestMessage?.id = ${verifierRecord.requestMessage?.id}")

                verifierRecord.requestMessage?.let { message ->
                    val jsonString = Json.encodeToString(RequestPresentationMessageV2.serializer(), message)
                    val qrBitmap = generateQRCode(jsonString)
                    qrImageView.setImageBitmap(qrBitmap)

                }?: run {
                    throw Exception("VerifierRecord não contém requestMessage")
                }

                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                statusText.text = "✅ Prova gerada e QR Code disponível!"
                Toast.makeText(this@RequestProofActivity, text, Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                statusText.text = "❌ Erro req: ${e.message}"
                e.printStackTrace()
            } finally {
                progressBar.visibility = View.GONE
                requestProofButton.isEnabled = true
            }
        }
    }

    private fun generateQRCode(data: String): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, 800, 800)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun mapPredicateType(op: String): PredicateType {
        return when (op.trim()) {
            ">", "maior que" -> PredicateType.GreaterThan
            ">=", "maior ou igual" -> PredicateType.GreaterThanOrEqualTo
            "<", "menor que" -> PredicateType.LessThan
            "<=", "menor ou igual" -> PredicateType.LessThanOrEqualTo
            else -> throw IllegalArgumentException("Operador inválido: $op")
        }
    }
}