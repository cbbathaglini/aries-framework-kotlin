package org.hyperledger.ariesproject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesproject.databinding.CredentialDetailBinding

class CredentialDetailFragment : Fragment() {

    private var credentialId: String? = null
    private lateinit var binding: CredentialDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialId = arguments?.getString(ARG_CREDENTIAL_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = CredentialDetailBinding.inflate(inflater, container, false)

        credentialId?.let { id ->
            val app = requireActivity().application as WalletApp

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val record = app.agent.credentialExchangeRepository.getById(id)
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            showCredential(record)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Erro ao carregar credencial ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        binding.copyButton.setOnClickListener {
            val clipboard =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Credential ID", binding.credentialId.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
        }

        return binding.root
    }

    private fun showCredential(record: CredentialExchangeRecord) {
        binding.credentialId.text = record.id
        binding.schemaId.text = "Schema ID: ${record.schemaId ?: "N/A"}"
        binding.credDefId.text =
            "Credential Definition ID: ${record.credentialDefinitionId ?: "N/A"}"
        binding.revRegId.text = "Revocation Registry: ${record.revRegId ?: "N/A"}"

        val attributes = record.credentialAttributes?.associate { it.name to it.value } ?: emptyMap()
        populateAttributes(attributes)
    }

    private fun populateAttributes(attrs: Map<String, String>) {
        binding.attributesContainer.removeAllViews()
        attrs.toSortedMap().forEach { (key, value) ->
            val attrLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val keyView = TextView(requireContext()).apply {
                text = key
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val valueView = TextView(requireContext()).apply {
                text = value
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }

            attrLayout.addView(keyView)
            attrLayout.addView(valueView)
            binding.attributesContainer.addView(attrLayout)
        }
    }

    companion object {
        const val ARG_CREDENTIAL_ID = "item_credential_id"
        const val ARG_SCHEMA_NAME = "item_schema_name"
        const val ARG_SCHEMA_ID = "item_schema_id"
        const val ARG_CREDENTIAL_DEFINITION_ID = "item_credential_definition_id"
        const val ARG_REVOCATION_ID = "item_revocation_id"
    }
}