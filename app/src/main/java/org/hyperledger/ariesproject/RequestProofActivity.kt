package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.proofs.ProofService
import org.hyperledger.ariesframework.proofs.models.AttributeFilter
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofAttributeInfo
import org.hyperledger.ariesframework.proofs.models.ProofPredicateInfo
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.RevocationInterval

class RequestProofActivity : AppCompatActivity() {

    private var connectionId: String? = null
    private lateinit var addFieldButton: Button
    private lateinit var requestProofButton: Button
    private lateinit var fieldsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_proof)

        connectionId = intent.getStringExtra("CONNECTION_ID")

        addFieldButton = findViewById(R.id.addFieldButton)
        requestProofButton = findViewById(R.id.requestProofButton)
        fieldsContainer = findViewById(R.id.fieldsContainer)

        addFieldButton.setOnClickListener {
            addFieldInput()
        }

        requestProofButton.setOnClickListener {
            requestProof()
        }
    }

    private fun addFieldInput() {
        val fieldLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }

        val attrInput = EditText(this).apply {
            hint = "Atributo (ex: name)"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val predicateInput = EditText(this).apply {
            hint = "Expressão (>, >=, <=, <)"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f)
        }

        val valueInput = EditText(this).apply {
            hint = "Valor"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        fieldLayout.addView(attrInput)
        fieldLayout.addView(predicateInput)
        fieldLayout.addView(valueInput)
        fieldsContainer.addView(fieldLayout)
    }

    private fun requestProof() {
        lifecycleScope.launch {
            val app = application as WalletApp
            val requestedAttributes = mutableMapOf<String, ProofAttributeInfo>()
            val requestedPredicates = mutableMapOf<String, ProofPredicateInfo>()

            for (i in 0 until fieldsContainer.childCount) {
                val fieldLayout = fieldsContainer.getChildAt(i) as LinearLayout
                val attrInput = fieldLayout.getChildAt(0) as EditText
                val predicateInput = fieldLayout.getChildAt(1) as EditText
                val valueInput = fieldLayout.getChildAt(2) as EditText

                val attribute = attrInput.text.toString().trim()
                val predicate = predicateInput.text.toString().trim()
                val value = valueInput.text.toString().trim()

                if (attribute.isNotEmpty()) {
                    if (predicate.isNotEmpty() && value.isNotEmpty()) {
                        try {
                            val predicateType = mapPredicateType(predicate)
                            val predicateInfo = ProofPredicateInfo(
                                name = attribute,
                                nonRevoked = null,
                                predicateType = predicateType,
                                predicateValue = value.toInt(),
                                /*restrictions = listOf(
                                    AttributeFilter(credentialDefinitionId = "YOUR_CRED_DEF_ID")
                                )*/
                            )
                            requestedPredicates[attribute] = predicateInfo
                        } catch (e: Exception) {
                            Toast.makeText(this@RequestProofActivity, "Erro no predicado: ${e.message}", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    } else {
                        val attrInfo = ProofAttributeInfo(
                            name = attribute,
                            nonRevoked = null,
                            /*restrictions = listOf(
                                AttributeFilter(credentialDefinitionId = "YOUR_CRED_DEF_ID")
                            )*/
                        )
                        requestedAttributes[attribute] = attrInfo
                    }
                }
            }

            val nonce = ProofService.generateProofRequestNonce()

            val now = (System.currentTimeMillis() / 1000).toInt()

            val revocationInterval = RevocationInterval(
                from = now,  // agora
                to = now// agora
            )

            val proofRequest = ProofRequest(
                nonce = nonce,
                requestedAttributes = requestedAttributes,
                requestedPredicates = requestedPredicates,
                nonRevoked = revocationInterval,
                name = "Proof Request"
            )

            app.agent.proofs.requestProof(connectionId!!, proofRequest, comment =  "Test")
            Toast.makeText(this@RequestProofActivity, "Prova gerada:\n$proofRequest", Toast.LENGTH_LONG).show()

            // Aqui você pode enviar proofRequest ao verificador, se desejar
            val intent = Intent(this@RequestProofActivity, WalletMainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish() // finaliza a Activity atual

        }
    }

    private fun mapPredicateType(op: String): PredicateType {
        return when (op.trim()) {
            ">=", "≥" -> PredicateType.GreaterThanOrEqualTo
            "<=", "≤" -> PredicateType.LessThanOrEqualTo
            ">" -> PredicateType.GreaterThan
            "<" -> PredicateType.LessThan
            else -> throw IllegalArgumentException("Operador inválido: $op")
        }
    }

}
