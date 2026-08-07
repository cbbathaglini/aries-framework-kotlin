package org.hyperledger.ariesproject

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
        savedInstanceState: Bundle?
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
                        Toast.makeText(
                            requireContext(),
                            "Error loading proof: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun showProof(record: ProofExchangeRecord, proofRequest: AnonCredsProofRequest?) {
        binding.proofId.text = "ID: ${record.id}"
        binding.connectionId.text = "Connection: ${record.connectionId ?: "connectionless"}"
        binding.state.text = "State: ${record.state ?: "Unknown"}"
        binding.threadId.text = "Thread ID: ${record.threadId ?: "N/A"}"
        binding.verified.text = "Verified: ${record.isVerified ?: "N/A"}"

        populateRequestedAttributes(proofRequest?.requestedAttributes ?: emptyMap())
        populateRequestedPredicates(proofRequest?.requestedPredicates ?: emptyMap())
        showNonRevokedInterval(proofRequest)
    }

    private fun populateRequestedAttributes(attrs: Map<String, AnonCredsRequestedAttribute>) {
        binding.attributesContainer.removeAllViews()

        if (attrs.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No requested attributes."
                setTextColor(resources.getColor(R.color.gray_500, null))
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
                setTextColor(resources.getColor(R.color.gray_900, null))
            }
            layout.addView(keyView)

            attr.names?.forEach {
                val subAttr = TextView(requireContext()).apply {
                    text = "  - $it"
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.gray_700, null))
                }
                layout.addView(subAttr)
            }

            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                ).apply { setMargins(0, 8, 0, 0) }
                setBackgroundResource(android.R.color.darker_gray)
            }
            layout.addView(divider)

            binding.attributesContainer.addView(layout)
        }
    }

    private fun populateRequestedPredicates(predicates: Map<String, AnonCredsRequestedPredicate>) {
        binding.predicatesContainer.removeAllViews()

        if (predicates.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No requested predicates."
                setTextColor(resources.getColor(R.color.gray_500, null))
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
                setTextColor(resources.getColor(R.color.gray_900, null))
            }
            layout.addView(titleView)

            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                ).apply { setMargins(0, 8, 0, 0) }
                setBackgroundResource(android.R.color.darker_gray)
            }
            layout.addView(divider)

            binding.predicatesContainer.addView(layout)
        }
    }

    private fun showNonRevokedInterval(proofRequest: AnonCredsProofRequest?) {
        val interval = proofRequest?.nonRevoked
        val toText = formatTimestamp(interval?.to)

        val text = if (interval != null) {
            "Date: $toText"
        } else {
            "Not specified"
        }

        binding.nonRevokedInterval.text = text
    }

    fun formatTimestamp(timestamp: ULong?): String {
        return if (timestamp != null) {
            val millis = timestamp.toLong() * 1000
            val date = Date(millis)
            val sdf = SimpleDateFormat("MM/dd/yyyy - HH:mm:ss", Locale.getDefault())
            sdf.format(date)
        } else "-"
    }

    companion object {
        const val ARG_PROOF_ID = "item_proof_id"
    }
}