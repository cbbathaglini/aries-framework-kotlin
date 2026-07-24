package org.hyperledger.ariesproject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesproject.databinding.ActivityHistoricalDetailBinding
import org.hyperledger.ariesproject.databinding.FragmentHistoricalDetailBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoricalDetailFragment : Fragment() {

    private var connectionId: String? = null
    private lateinit var binding: FragmentHistoricalDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionId = arguments?.getString(ARG_CONNECTION_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_historical_detail, container, false)
        binding = FragmentHistoricalDetailBinding.bind(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = (requireActivity().application as WalletApp)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                connectionId?.let { id ->
                    val record = app.agent.connectionRepository.getById(id)
                    lifecycleScope.launch(Dispatchers.Main) {
                        preencherCampos(record)
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletApp", "Erro ao carregar conexão: ${e.message}")
            }
        }

        binding.copyDidButton.setOnClickListener {
            val did = binding.connectionDid.text.toString()
            if (did.isNotBlank()) {
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("DID", did))
                Toast.makeText(requireContext(), "DID copiado para a área de transferência", Toast.LENGTH_SHORT).show()
            }
        }

        binding.requestProofButton.setOnClickListener {
            connectionId?.let {
                val intent = Intent(requireContext(), RequestProofActivity::class.java)
                intent.putExtra("CONNECTION_ID", it)
                intent.putExtra(HistoricalDetailFragment.ARG_OFFLINE_PROOF, "false")
                startActivity(intent)
            }
        }

        binding.viewCredentialsButton.setOnClickListener {
            connectionId?.let {
                val intent = Intent(requireContext(), CredentialListActivity::class.java)
                intent.putExtra("CONNECTION_ID", it)
                startActivity(intent)
            }
        }
    }

    private fun preencherCampos(record: ConnectionRecord) {
        binding.connectionName.text = record.theirLabel ?: "Sem nome"
        binding.connectionState.text = "Estado: ${record.state}"
        binding.connectionId.text = record.id
        binding.connectionDid.text = record.theirDid ?: "—"
        binding.threadId.text = record.threadId ?: "—"
        binding.theirLabel.text = record.theirLabel ?: "—"

        // Safe date conversion
        val dateFormatted = try {
            when (val createdAt = record.createdAt) {
                is Date -> {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(createdAt)
                }
                is String -> {
                    // tenta interpretar uma ISO string
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    parser.timeZone = TimeZone.getTimeZone("UTC")
                    val date = parser.parse(createdAt)
                    if (date != null)
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                    else
                        createdAt
                }
                else -> "Desconhecido"
            }
        } catch (e: Exception) {
            Log.e("WalletApp", "Erro ao formatar data: ${e.message}")
            "Desconhecido"
        }

        binding.connectionDate.text = dateFormatted
    }

    companion object {
        const val ARG_CONNECTION_ID = "id"
        const val ARG_CONNECTION_RECORD = "connection_record"
        const val ARG_OFFLINE_PROOF = "offline"
    }
}