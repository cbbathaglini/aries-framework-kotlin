package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.databinding.FragmentHomeBinding
import org.hyperledger.ariesproject.notifications.NotificationHandler

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateNotificationBadgeUI()

        // Ações rápidas
        binding.cardConnect.setOnClickListener {
            startActivity(Intent(requireContext(), BarcodeScannerActivity::class.java))
        }

        binding.cardCredentials.setOnClickListener {
            startActivity(Intent(requireContext(), CredentialListActivity::class.java))
        }

        binding.linkHistory.setOnClickListener {
            startActivity(Intent(requireContext(), HistoricalListActivity::class.java))
        }

        binding.linkProofs.setOnClickListener {
            startActivity(Intent(requireContext(), ProofListActivity::class.java))
        }

        binding.linkInvite.setOnClickListener {
            startActivity(Intent(requireContext(), InvitationActivity::class.java))
        }

        // 🔹 Provas Offline
        binding.btnRequestProof.setOnClickListener {
            startActivity(Intent(requireContext(), RequestProofActivity::class.java))
        }

        binding.btnScanProof.setOnClickListener {
            startActivity(Intent(requireContext(), VerifierProofActivity::class.java))
        }

        binding.btnReceivePresentation.setOnClickListener {
            startActivity(Intent(requireContext(), ReceivingPresentationActivity::class.java))
        }

        binding.btnPresentationList.setOnClickListener {
            startActivity(Intent(requireContext(), PresentationListActivity::class.java))
        }

        binding.btnShowPresentations.setOnClickListener {
            startActivity(Intent(requireContext(), PresentationsReceivedListActivity::class.java))
        }

        // 🔹 Conectar via URL
        binding.buttonConnect.setOnClickListener {
            val invitationUrl = binding.invitation.text.toString().trim()
            if (invitationUrl.isEmpty()) {
                (activity as? WalletMainActivity)?.showAlert("Por favor, insira a URL do convite.")
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val app = requireActivity().application as WalletApp
                    val (_, connection) = app.agent.oob.receiveInvitationFromUrl(invitationUrl)

                    val handler = NotificationHandler.getInstance(requireContext())
                    handler.addNotification(
                        title = "Nova Conexão",
                        message = "Conectado com ${connection?.theirLabel ?: "Desconhecido"}",
                        type = NotificationType.CONNECTION,
                        connectionId = connection?.id
                    )
                    updateNotificationBadgeUI()
                    binding.invitation.text?.clear()
                } catch (e: Exception) {
                    (activity as? WalletMainActivity)?.showAlert("Falha ao conectar: ${e.localizedMessage}")
                }
            }
        }

        binding.buttonClear.setOnClickListener {
            binding.invitation.text?.clear()
        }
    }

    private fun updateNotificationBadgeUI() {
        val handler = NotificationHandler.getInstance(requireContext())
        val unreadCount = handler.notifications.count { !it.isRead }
        (activity as? BaseActivity)?.updateNotificationBadge()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}