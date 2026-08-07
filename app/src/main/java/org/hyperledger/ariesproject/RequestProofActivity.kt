package org.hyperledger.ariesproject

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsProofFormatService
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesframework.proofs.v1.ProofService
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.hyperledger.ariesproject.databinding.ActivityRequestProofBinding
import java.util.Calendar

class RequestProofActivity : BaseActivity() {

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
    private var toTimestamp: Int? = null
    private lateinit var statusText: TextView
    private lateinit var qrImageView: ImageView
    private lateinit var binding: ActivityRequestProofBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRequestProofBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        // toolbar handles back via navigation icon
        binding.toolbar.setNavigationOnClickListener { finish() }

        connectionId = intent.getStringExtra("CONNECTION_ID")

        if (intent.getStringExtra(HistoricalDetailFragment.ARG_OFFLINE_PROOF) != null) {
            offline = intent.getStringExtra(HistoricalDetailFragment.ARG_OFFLINE_PROOF).toBoolean()
        }

        credentialDefInput = findViewById(R.id.credentialDefInput)
        toDateButton = findViewById(R.id.toDateButton)
        toDateButton.setOnClickListener { pickDateTime() }

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

        addAttributeField("name")
        addAttributeField("email")
        addPredicateField("birthdate", ">=", "19910612")
    }

    private fun pickDateTime() {
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
                        toDateButton.text = "Until: ${calendar.time}"
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
            hint = "Attribute name"
            setText(defaultName)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val removeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(0)
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
            hint = "Predicate name"
            setText(name)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val operatorOptions = listOf(
            PredicateType.LessThanOrEqualTo,
            PredicateType.LessThan,
            PredicateType.GreaterThan,
            PredicateType.GreaterThanOrEqualTo
        )

        val opInput = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f)
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            operatorOptions.map { it.toSymbol() }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        opInput.adapter = adapter

        val initialType = when (op.trim()) {
            ">=" -> PredicateType.GreaterThanOrEqualTo
            ">" -> PredicateType.GreaterThan
            "<" -> PredicateType.LessThan
            "<=" -> PredicateType.LessThanOrEqualTo
            else -> null
        }
        initialType?.let {
            val index = operatorOptions.indexOf(it)
            if (index >= 0) opInput.setSelection(index)
        }

        val valueInput = EditText(this).apply {
            hint = "Value"
            setText(value)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val removeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(0)
            setOnClickListener { predicatesContainer.removeView(layout) }
        }

        layout.addView(nameInput)
        layout.addView(opInput)
        layout.addView(valueInput)
        layout.addView(removeButton)
        predicatesContainer.addView(layout)
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

    private fun requestProof() {
        lifecycleScope.launch {
            try {
                val app = application as WalletApp
                val credDefId = credentialDefInput.text.toString().trim()
                val restrictions = buildRestrictions(credDefId)

                progressBar.visibility = View.VISIBLE
                statusText.text = "Sending request..."
                requestProofButton.isEnabled = false

                var revocationInterval: AnonCredsNonRevokedInterval? = null
                if (toTimestamp != null) {
                    revocationInterval = AnonCredsNonRevokedInterval(
                        from = 0.toULong(),
                        to = toTimestamp!!.toULong()
                    )
                }

                val attributes = mutableMapOf<String, AnonCredsRequestedAttribute>()
                val predicates = mutableMapOf<String, AnonCredsRequestedPredicate>()

                for (i in 0 until attributesContainer.childCount) {
                    val layout = attributesContainer.getChildAt(i) as LinearLayout
                    val attrInput = layout.getChildAt(0) as EditText
                    val name = attrInput.text.toString().trim()
                    if (name.isNotEmpty()) {
                        attributes[name] = AnonCredsRequestedAttribute(
                            name = name,
                            restrictions = restrictions,
                            nonRevoked = null
                        )
                    }
                }

                for (i in 0 until predicatesContainer.childCount) {
                    val layout = predicatesContainer.getChildAt(i) as LinearLayout

                    val nameInput = layout.getChildAt(0) as EditText
                    val opInput = layout.getChildAt(1) as Spinner
                    val valueInput = layout.getChildAt(2) as EditText

                    val name = nameInput.text.toString().trim()
                    val value = valueInput.text.toString().trim()

                    if (name.isNotEmpty() && value.isNotEmpty()) {

                        val symbol = opInput.selectedItem as String
                        val type = symbolToPredicate(symbol)

                        predicates[name] = AnonCredsRequestedPredicate(
                            name = name,
                            pType = type,
                            pValue = value.toLong(),
                            restrictions = restrictions,
                            nonRevoked = null
                        )
                    }
                }

                val proofRequest = AnonCredsProofRequest(
                    name = "Dynamic Proof Request",
                    nonce = ProofService.generateProofRequestNonce(),
                    requestedAttributes = attributes,
                    requestedPredicates = predicates,
                    nonRevoked = revocationInterval,
                    version = "1.0"
                )

                val jsonDebug = Json.encodeToString(AnonCredsProofRequest.serializer(), proofRequest)
                Log.e("DEBUG_PROOF_JSON", jsonDebug)

                val proofFormats: List<ProofFormatSpec> = listOf(
                    ProofFormatSpec(
                        attachmentId = RequestPresentationMessageV2.ANONCREDS_PROOF_REQUEST_ATTACHMENT_ID,
                        format = AnoncredsProofFormatService.ANONCREDS_PRESENTATION_REQUEST
                    )
                )

                val result = if (offline) {
                    app.agent.proofCommandV2.requestProofOffline(
                        proofRequest = proofRequest,
                        formats = proofFormats
                    )
                } else {
                    app.agent.proofCommandV2.requestProof(
                        connectionId = connectionId!!,
                        proofRequest = proofRequest,
                        formats = proofFormats
                    )
                }

                val record: ProofExchangeRecord = result.first
                val verifierRecord: VerifierRecord = result.second

                verifierRecord.requestMessage?.let { message ->
                    val jsonString = Json.encodeToString(
                        RequestPresentationMessageV2.serializer(),
                        message
                    )
                    val qrBitmap = generateQRCode(jsonString)
                    qrImageView.setImageBitmap(qrBitmap)
                } ?: throw Exception("No requestMessage found in VerifierRecord")

                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                statusText.text = "Proof generated and QR Code ready!"

            } catch (e: Exception) {
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                statusText.text = "Request error: ${e.message}"
                Log.e("error req", e.stackTraceToString())
            } finally {
                progressBar.visibility = View.GONE
                requestProofButton.isEnabled = true
            }
        }
    }

    private fun buildRestrictions(credDefId: String): List<AnonCredsProofRequestRestriction>? {
        val clean = credDefId.trim()
        return if (clean.isEmpty()) null
        else listOf(AnonCredsProofRequestRestriction(credDefId = clean))
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
            null
        }
    }

    private fun symbolToPredicate(symbol: String): PredicateType {
        return when (symbol) {
            "<" -> PredicateType.LessThan
            "<=" -> PredicateType.LessThanOrEqualTo
            ">" -> PredicateType.GreaterThan
            ">=" -> PredicateType.GreaterThanOrEqualTo
            else -> throw IllegalArgumentException("Invalid predicate operator: $symbol")
        }
    }

    private fun PredicateType.toSymbol(): String {
        return when (this) {
            PredicateType.LessThan -> "<"
            PredicateType.LessThanOrEqualTo -> "<="
            PredicateType.GreaterThan -> ">"
            PredicateType.GreaterThanOrEqualTo -> ">="
        }
    }
}