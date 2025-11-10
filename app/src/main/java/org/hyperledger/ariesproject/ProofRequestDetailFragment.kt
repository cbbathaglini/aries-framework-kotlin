package org.hyperledger.ariesproject

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesproject.databinding.ProofRequestDetailBinding

class ProofRequestDetailFragment : Fragment() {

    private var proofId: String? = null
    private var type: NotificationType = NotificationType.OTHER
    private lateinit var binding: ProofRequestDetailBinding
    private var compatibleCredentials: List<Map<String, Any?>> = emptyList()
    var chosenCredentialId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proofId = arguments?.getString(ARG_PROOF_ID)
        type = NotificationType.fromString(arguments?.getString(ARG_TYPE))
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

        // Atributos solicitados
        val attrs = proofRequest?.requestedAttributes ?: emptyMap()
        populateRequestedAttributes(attrs)

        // Predicados solicitados
        val predicates = proofRequest?.requestedPredicates ?: emptyMap()
        populateRequestedPredicates(predicates)

        // Intervalo de não revogação
        showNonRevokedInterval(proofRequest)

        // Carrega credenciais compatíveis
        if (record.state != ProofState.Declined && record.state != ProofState.Abandoned) {
            loadAvailableCredentials(proofRequest, record)
            binding.btnSendProof.apply {
                visibility = if (record.state != ProofState.Done) View.VISIBLE else View.GONE
                setOnClickListener { sendProof(record.id) }
            }
        } else {
            // Oculta o seletor e botão se a prova foi recusada ou abandonada
            binding.selectCredentialHeader.text = "❌ Prova recusada ou abandonada — não é possível selecionar credenciais."
            binding.credentialSpinner.visibility = View.GONE
            binding.btnSendProof.visibility = View.GONE
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

    private fun populateRequestedPredicates(predicates: Map<String, AnonCredsRequestedPredicate>) {
        binding.requestedPredicatesContainer.removeAllViews()

        if (predicates.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "Nenhum predicado solicitado."
                setTextColor(Color.GRAY)
            }
            binding.requestedPredicatesContainer.addView(empty)
            return
        }

        predicates.toSortedMap().forEach { (key, pred) ->
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val symbol = when (pred.pType) {
                PredicateType.GreaterThanOrEqualTo -> ">="
                PredicateType.LessThanOrEqualTo -> "<="
                PredicateType.GreaterThan -> ">"
                PredicateType.LessThan -> "<"
                else -> "?"
            }


            val titleView = TextView(requireContext()).apply {

                text = "• ${pred.name} ${symbol} ${pred.pValue}"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }
            layout.addView(titleView)

            pred.restrictions?.forEach { r ->
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

            binding.requestedPredicatesContainer.addView(layout)
        }
    }


    private fun showNonRevokedInterval(proofRequest: AnonCredsProofRequest?) {
        val interval = proofRequest?.nonRevoked

        // Função auxiliar para converter timestamp em formato legível
        fun formatTimestamp(timestamp: Long?): String {
            return if (timestamp != null) {
                val date = Date(timestamp * 1000) // segundos → milissegundos
                val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", Locale.getDefault())
                sdf.format(date)
            } else "-"
        }

        val fromText = formatTimestamp(interval?.from)
        val toText = formatTimestamp(interval?.to)

        val text = if (interval != null) {
            "Início: $fromText | Fim: $toText"
        } else {
            "Não especificado"
        }

        binding.nonRevokedInterval.text = text
    }

    private fun updateStatusCard(state: ProofState?) {
        val (bgColor, message) = when (state) {
            ProofState.Done -> Pair("#C8E6C9", "✅ Prova concluída.")
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

    private fun loadAvailableCredentials(proofRequest: AnonCredsProofRequest?, record: ProofExchangeRecord) {
        if (proofRequest == null) return
        val app = requireActivity().application as WalletApp

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allRecords = app.agent.credentialExchangeRepository.getAll()
                val requestedCredDefIds = proofRequest.requestedAttributes.values
                    .flatMap { it.restrictions?.mapNotNull { r -> r.credDefId } ?: emptyList() }

                val requestedCredDefIdsPredicates = proofRequest.requestedPredicates.values
                    .flatMap { it.restrictions?.mapNotNull { r -> r.credDefId } ?: emptyList() }

                val requestedAttrNames = proofRequest.requestedAttributes.values
                    .flatMap { attr ->
                        when {
                            attr.names != null -> attr.names!!
                            attr.name != null -> listOf(attr.name!!)
                            else -> emptyList()
                        }
                    }

                val compatible = allRecords.mapNotNull { cred ->
                    if (cred.state == CredentialState.Revoked) return@mapNotNull null

                    val recordCredDefId = cred.credentialDefinitionId ?: return@mapNotNull null
                    val recordAttrs = cred.credentialAttributes?.associate { it.name to it.value } ?: emptyMap()

                    val hasAllAttributes = requestedAttrNames.all { recordAttrs.containsKey(it) }
                    val matchesCredDefAttr = requestedCredDefIds.isEmpty() || requestedCredDefIds.contains(recordCredDefId)
                    val matchesCredDefPredicates = requestedCredDefIdsPredicates.isEmpty() || requestedCredDefIdsPredicates.contains(recordCredDefId)

                    val allPredicatesSatisfied = proofRequest.requestedPredicates.values.all { predicate ->
                        val attrName = predicate.name
                        val rawValue = recordAttrs[attrName]
                        val attrValue = rawValue?.toIntOrNull() ?: return@all false

                        when (predicate.pType) {
                            PredicateType.GreaterThanOrEqualTo -> attrValue >= predicate.pValue
                            PredicateType.GreaterThan -> attrValue > predicate.pValue
                            PredicateType.LessThanOrEqualTo -> attrValue <= predicate.pValue
                            PredicateType.LessThan -> attrValue < predicate.pValue
                            else -> false
                        }
                    }

                    if (hasAllAttributes && matchesCredDefAttr && matchesCredDefPredicates && allPredicatesSatisfied) {
                        mapOf(
                            "id" to cred.id,
                            "attrs" to recordAttrs,
                            "schemaId" to cred.schemaId,
                            "credDefId" to cred.credentialDefinitionId,
                            "createdAt" to cred.createdAt
                        )
                    } else null
                }

                compatibleCredentials = compatible

                withContext(Dispatchers.Main) {
                    if (compatible.isEmpty()) {
                        binding.btnSendProof.visibility = View.GONE
                        binding.btnSendProof.isEnabled = false
                        binding.selectCredentialHeader.text = "Nenhuma credencial compatível encontrada."
                        binding.credentialSpinner.visibility = View.GONE
                    } else {
                        val spinnerItems = compatible.map {
                            val schema = it["schemaId"] ?: "sem schema"
                            val id = (it["id"] as? String)?.takeLast(10)
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
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Erro ao carregar credenciais: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
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
                val chosenCredentialId = compatibleCredentials[selectedIndex]["id"] as String
                Log.i("ProofRequestDetail", "Chosen credential ID: $chosenCredentialId")

                val presentationResult = app.agent.proofCommandV2.acceptRequest(record.id, chosenCredentialId)

                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    binding.proofStatusMessage.text = "✅ Apresentação criada e enviada com sucesso!"
                    binding.btnSendProof.visibility = View.GONE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    binding.proofStatusMessage.text = "❌ Erro ao criar/apresentar prova: ${e.localizedMessage}"
                    binding.btnSendProof.isEnabled = true
                }
            }
        }
    }

    companion object {
        const val ARG_PROOF_ID = "PROOF_ID"
        const val ARG_TYPE = "TYPE"
    }
}