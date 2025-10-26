package org.hyperledger.ariesproject

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesproject.databinding.ProofRequestDetailBinding

class ProofRequestDetailFragment : Fragment() {

    private var proofId: String? = null
    private lateinit var binding: ProofRequestDetailBinding
    private var compatibleCredentials: List<Map<String, Any?>> = emptyList()
    var chosenCredentialId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proofId = arguments?.getString(ARG_PROOF_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ProofRequestDetailBinding.inflate(inflater, container, false)
        loadProof()
        return binding.root
    }

    private fun loadProof() {
        proofId?.let { id ->
            val app = requireActivity().application as WalletApp

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        binding.loadingProgress.visibility = View.VISIBLE
                        binding.proofStatusMessage.text = "Carregando detalhes da prova..."
                    }

                    val record: ProofExchangeRecord = app.agent.proofRepository.getById(id)
                    val verifierRecord: VerifierRecord? =
                        app.agent.verifierRepository.getByGlobalThreadId(record.threadId)
                    val proofRequest: AnonCredsProofRequest? = verifierRecord?.proofRequest

                    withContext(Dispatchers.Main) {
                        binding.loadingProgress.visibility = View.GONE
                        binding.proofStatusMessage.text = ""
                        showProof(record, proofRequest)
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.loadingProgress.visibility = View.GONE
                        binding.proofStatusMessage.text =
                            "Erro ao carregar prova: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    private fun showProof(record: ProofExchangeRecord, proofRequest: AnonCredsProofRequest?) {
        binding.proofId.text = "ID: ${record.id}"
        binding.proofState.text = "Estado: ${record.state ?: "Desconhecido"}"
        binding.proofConnection.text = "Conexão: ${record.connectionId ?: "N/A"}"

        updateStatusCard(record.state)

        val attrs = proofRequest?.requestedAttributes ?: emptyMap()
        populateRequestedAttributes(attrs)

        loadAvailableCredentials(proofRequest, record)

        binding.btnSendProof.apply {
            visibility = if (record.state != ProofState.Done) View.VISIBLE else View.GONE
            setOnClickListener { sendProof(record.id) }
        }
    }

    private fun loadAvailableCredentials(proofRequest: AnonCredsProofRequest?,record: ProofExchangeRecord) {
        if (proofRequest == null) return
        val app = requireActivity().application as WalletApp

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allRecords = app.agent.credentialExchangeRepository.getAll()

                val requestedCredDefIds = proofRequest.requestedAttributes.values
                    .flatMap { it.restrictions?.mapNotNull { r -> r.credDefId } ?: emptyList() }

                val requestedCredDefIdsPredicates = proofRequest.requestedPredicates.values
                    .flatMap { it.restrictions?.mapNotNull { r -> r.credDefId } ?: emptyList() }

                // Extrai nomes dos atributos solicitados
                val requestedAttrNames = proofRequest.requestedAttributes.values
                    .flatMap { attr ->
                        when {
                            attr.names != null -> attr.names!!
                            attr.name != null -> listOf(attr.name!!)
                            else -> emptyList()
                        }
                    }

                val compatible = allRecords.mapNotNull { record ->
                    val recordCredDefId = record.credentialDefinitionId ?: return@mapNotNull null
                    val recordAttrs = record.credentialAttributes
                        ?.associate { it.name to it.value } ?: emptyMap()

                    // 1️⃣ Verifica se possui todos os atributos requeridos
                    val hasAllAttributes = requestedAttrNames.all { recordAttrs.containsKey(it) }

                    // 2️⃣ Verifica credDefId
                    val matchesCredDef =
                        requestedCredDefIds.isEmpty() || requestedCredDefIds.contains(recordCredDefId)

                    // 3️⃣ Verifica predicados (>, <, >=, <=)
                    val allPredicatesSatisfied = proofRequest.requestedPredicates.values.all { predicate ->
                        val attrName = predicate.name
                        val rawValue = recordAttrs[attrName] ?: return@all false
                        val attrValue = rawValue.toIntOrNull() ?: return@all false

                        when (predicate.pType) {
                            PredicateType.GreaterThanOrEqualTo -> attrValue >= predicate.pValue
                            PredicateType.LessThanOrEqualTo -> attrValue <= predicate.pValue
                            PredicateType.GreaterThan -> attrValue > predicate.pValue
                            PredicateType.LessThan -> attrValue < predicate.pValue
                            else -> false
                        }
                    }

                    if (hasAllAttributes && matchesCredDef && allPredicatesSatisfied) {
                        mapOf(
                            "id" to record.id,
                            "attrs" to recordAttrs,
                            "schemaId" to record.schemaId,
                            "credDefId" to record.credentialDefinitionId,
                            "createdAt" to record.createdAt
                        )
                    } else null
                }

                compatibleCredentials = compatible

                withContext(Dispatchers.Main) {
                    if (compatible.isEmpty()) {
                        binding.selectCredentialHeader.text = "Nenhuma credencial compatível encontrada."
                        binding.credentialSpinner.visibility = View.GONE
                    } else {
                        val spinnerItems = compatible.map {
                            val schema = it["schemaId"] ?: "sem schema"
                            val id = (it["id"] as? String)?.takeLast(6)
                            "Credencial ${id ?: ""} ($schema)"
                        }

                        val adapter = android.widget.ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_dropdown_item,
                            spinnerItems
                        )

                        binding.credentialSpinner.apply {
                            visibility = View.VISIBLE
                            this.adapter = adapter
                        }

                        record.chosenCredentialId?.let { chosenId ->
                            val indexToSelect = compatible.indexOfFirst {
                                val id = it["id"] as? String
                                id == chosenId
                            }

                            if (indexToSelect >= 0) {
                                Log.i("ProofRequestDetail", "Selecionando credencial pré-escolhida: $chosenId")
                                binding.credentialSpinner.isEnabled = false
                                binding.credentialSpinner.setSelection(indexToSelect)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Erro ao carregar credenciais: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun updateStatusCard(state: ProofState?) {
        val (bgColor, message) = when (state) {
            ProofState.Done -> Pair("#C8E6C9", "✅ Prova concluída e verificada.")
            ProofState.PresentationSent, ProofState.PresentationReceived ->
                Pair("#BBDEFB", "📤 Prova em apresentação.")
            ProofState.RequestReceived ->
                Pair("#E1BEE7", "📨 Solicitação de prova recebida.")
            ProofState.Abandoned, ProofState.Declined ->
                Pair("#FFCDD2", "❌ Prova recusada ou abandonada.")
            else -> Pair("#E0E0E0", "Estado: ${state ?: "Desconhecido"}")
        }

        binding.statusCard.apply {
            setBackgroundColor(Color.parseColor(bgColor))
            visibility = View.VISIBLE
        }

        binding.statusText.apply {
            text = message
            setTextColor(Color.BLACK)
        }
    }

    private fun populateRequestedAttributes(attrs: Map<String, AnonCredsRequestedAttribute>) {
        binding.requestedAttributesContainer.removeAllViews()

        if (attrs.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "Nenhum atributo solicitado."
                setTextColor(Color.GRAY)
            }
            binding.requestedAttributesContainer.addView(empty)
            return
        }

        attrs.toSortedMap().forEach { (key, attr) ->
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val title = attr.name ?: key
            val keyView = TextView(requireContext()).apply {
                text = "• $title"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }
            layout.addView(keyView)

            attr.names?.forEach {
                val subAttr = TextView(requireContext()).apply {
                    text = "  - $it"
                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                }
                layout.addView(subAttr)
            }

            attr.restrictions?.forEach { r ->
                r.schemaName?.let { schema ->
                    val schemaView = TextView(requireContext()).apply {
                        text = "Schema: $schema"
                        setTextColor(Color.GRAY)
                        textSize = 12f
                    }
                    layout.addView(schemaView)
                }
                r.credDefId?.let { cred ->
                    val credView = TextView(requireContext()).apply {
                        text = "CredDefId: $cred"
                        setTextColor(Color.GRAY)
                        textSize = 12f
                    }
                    layout.addView(credView)
                }
            }

            binding.requestedAttributesContainer.addView(layout)
        }
    }

    private fun sendProof(proofRecordId: String) {
        val app = requireActivity().application as WalletApp
        val selectedIndex = binding.credentialSpinner.selectedItemPosition

        if (selectedIndex == android.widget.AdapterView.INVALID_POSITION) {
            Toast.makeText(requireContext(), "Selecione uma credencial compatível primeiro.", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnSendProof.isEnabled = false
        binding.proofStatusMessage.text = "🔄 Criando apresentação..."
        binding.loadingProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val record = app.agent.proofRepository.getById(proofRecordId)

                val selectedIndex = binding.credentialSpinner.selectedItemPosition
                val chosenCredentialId = compatibleCredentials[selectedIndex]["id"] as String
                Log.i("chosen credential id: ", chosenCredentialId)
                val presentationResult = app.agent.proofCommandV2.acceptRequest(record.id, chosenCredentialId)

                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    binding.proofStatusMessage.text = "✅ Apresentação criada e enviada com sucesso!"
                    binding.btnSendProof.visibility = View.GONE

                    val presentationJson = presentationResult.toString()
                    val resultView = TextView(requireContext()).apply {
                        text = presentationJson
                        setTextColor(Color.DKGRAY)
                        textSize = 12f
                        setPadding(8, 8, 8, 8)
                    }
                    binding.requestedAttributesContainer.addView(resultView)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    binding.proofStatusMessage.text = "❌ Erro ao criar/apresentar prova: ${e.localizedMessage}"
                    binding.btnSendProof.isEnabled = true
                    Toast.makeText(requireContext(), "Falha: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val ARG_PROOF_ID = "PROOF_ID"
    }
}