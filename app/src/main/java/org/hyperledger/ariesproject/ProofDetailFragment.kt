package org.hyperledger.ariesproject

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesproject.databinding.ProofDetailBinding
import java.text.SimpleDateFormat
import java.util.*

class ProofDetailFragment : Fragment() {

    private var proofId: String? = null
    private lateinit var binding: ProofDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proofId = arguments?.getString(ARG_PROOF_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ProofDetailBinding.inflate(inflater, container, false)
        loadProof()
        return binding.root
    }

    private fun loadProof() {
        proofId?.let { id ->
            val app = requireActivity().application as WalletApp

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val record: ProofExchangeRecord = app.agent.proofRepository.getById(id)
                    val verifierRecord: VerifierRecord? =
                        app.agent.verifierRepository.getByGlobalThreadId(record.threadId)
                    val proofRequest: AnonCredsProofRequest? = verifierRecord?.proofRequest

                    withContext(Dispatchers.Main) {
                        if (isAdded) showProof(record, proofRequest)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Erro ao carregar prova: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showProof(record: ProofExchangeRecord, proofRequest: AnonCredsProofRequest?) {
        binding.proofId.text = "ID: ${record.id}"
        binding.connectionId.text = "Conexão: ${record.connectionId ?: "connectionless"}"
        binding.state.text = "Estado: ${record.state ?: "Desconhecido"}"
        binding.threadId.text = "Thread ID: ${record.threadId ?: "N/A"}"
        binding.verified.text = "Verificado: ${record.isVerified ?: "N/A"}"

        // Mostra atributos e predicados solicitados
        populateRequestedAttributes(proofRequest?.requestedAttributes ?: emptyMap())
        populateRequestedPredicates(proofRequest?.requestedPredicates ?: emptyMap())
        showNonRevokedInterval(proofRequest)
    }

    private fun populateRequestedAttributes(attrs: Map<String, AnonCredsRequestedAttribute>) {
        binding.attributesContainer.removeAllViews()

        if (attrs.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "Nenhum atributo solicitado."
                setTextColor(Color.GRAY)
            }
            binding.attributesContainer.addView(empty)
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

            binding.attributesContainer.addView(layout)
        }
    }

    private fun populateRequestedPredicates(predicates: Map<String, AnonCredsRequestedPredicate>) {
        binding.predicatesContainer.removeAllViews()

        if (predicates.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "Nenhum predicado solicitado."
                setTextColor(Color.GRAY)
            }
            binding.predicatesContainer.addView(empty)
            return
        }

        predicates.toSortedMap().forEach { (_, pred) ->
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
                text = "• ${pred.name} $symbol ${pred.pValue}"
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

            binding.predicatesContainer.addView(layout)
        }
    }

    private fun showNonRevokedInterval(proofRequest: AnonCredsProofRequest?) {
        val interval = proofRequest?.nonRevoked
        val fromText = formatTimestamp(interval?.from)
        val toText = formatTimestamp(interval?.to)

        val text = if (interval != null) {
            "Data: $toText"
        } else {
            "Não especificado"
        }

        binding.nonRevokedInterval.text = text
    }

    private fun formatTimestamp(timestamp: Long?): String {
        return if (timestamp != null) {
            val date = Date(timestamp * 1000)
            val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", Locale.getDefault())
            sdf.format(date)
        } else "-"
    }

    companion object {
        const val ARG_PROOF_ID = "item_proof_id"
    }
}