package org.hyperledger.ariesproject

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
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesproject.databinding.ProofDetailBinding

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

        proofId?.let { id ->
            val app = requireActivity().application as WalletApp

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val record = app.agent.proofRepository.getById(id)
                    withContext(Dispatchers.Main) {
                        if (isAdded) showProof(record)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Erro ao carregar prova: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        return binding.root
    }

    private fun showProof(record: ProofExchangeRecord) {
        binding.proofId.text = record.id
        binding.connectionId.text = "Conexão: ${record.connectionId ?: "connectionless"}"
        binding.state.text = "Estado: ${record.state}"
        binding.threadId.text = "Thread ID: ${record.threadId ?: "N/A"}"
        binding.verified.text = "Verified: ${record.isVerified ?: "N/A"}"

//
//        val attributes = record.presentationMessage?.requestedAttributes
//            ?.mapValues { it.value.name ?: it.key }
//            ?: emptyMap()

       // populateAttributes(attributes)
    }

//    private fun populateAttributes(attrs: Map<String, String>) {
//        binding.attributesContainer.removeAllViews()
//        attrs.toSortedMap().forEach { (key, value) ->
//            val attrLayout = LinearLayout(requireContext()).apply {
//                orientation = LinearLayout.VERTICAL
//                setPadding(0, 8, 0, 8)
//            }
//
//            val keyView = TextView(requireContext()).apply {
//                text = key
//                textSize = 16f
//                setTypeface(null, android.graphics.Typeface.BOLD)
//            }
//
//            val valueView = TextView(requireContext()).apply {
//                text = value
//                textSize = 14f
//                setTextColor(resources.getColor(android.R.color.darker_gray, null))
//            }
//
//            attrLayout.addView(keyView)
//            attrLayout.addView(valueView)
//            binding.attributesContainer.addView(attrLayout)
//        }
//    }

    companion object {
        const val ARG_PROOF_ID = "item_proof_id"
    }
}