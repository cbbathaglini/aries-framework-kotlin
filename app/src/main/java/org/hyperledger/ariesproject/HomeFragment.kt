package org.hyperledger.ariesproject

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

        binding.menuList.layoutManager = LinearLayoutManager(requireContext())
        (activity as? WalletMainActivity)?.setupRecyclerView(binding.menuList)

        // 🔹 Atualiza badge ao abrir a Home
        updateNotificationBadgeUI()

        binding.buttonConnect.setOnClickListener {
            val invitationUrl = binding.invitation.text.toString().trim()
            if (invitationUrl.isEmpty()) {
                (activity as? WalletMainActivity)?.showAlert("Por favor, insira um link de convite.")
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val app = requireActivity().application as WalletApp
                    val (_, connection) = app.agent.oob.receiveInvitationFromUrl(invitationUrl)
                    val notificationHandler = NotificationHandler.getInstance(requireContext())
                    notificationHandler.addNotification(
                        title = "Nova Conexão",
                        message = "Conectado com ${connection?.theirLabel ?: "Desconhecido"}",
                        type = NotificationType.CONNECTION,
                        connectionId= connection?.id
                    )
                    updateNotificationBadgeUI()
//                    (activity as? WalletMainActivity)?.showAlert(
//                        "Conectado com ${connection?.theirLabel ?: "Agente desconhecido"} - ${connection?.id ?: ""}"
//                    )
                    binding.invitation.text.clear()
                } catch (e: Exception) {
                    (activity as? WalletMainActivity)?.showAlert("Falha ao conectar: ${e.localizedMessage}")
                }
            }
        }
    }

    fun updateNotificationBadgeUI() {

        val handler = NotificationHandler.getInstance(requireContext())
        val unreadCount = handler.notifications.count { !it.isRead }

        (activity as? BaseActivity)?.updateNotificationBadge()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}