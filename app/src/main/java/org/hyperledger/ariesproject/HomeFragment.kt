package org.hyperledger.ariesproject

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.databinding.FragmentHomeBinding
import org.hyperledger.ariesproject.databinding.MenuItemListContentBinding
import org.hyperledger.ariesproject.menu.MainMenu

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(binding.menuItemList.itemList)
        waitForAgentInitialize()

        binding.invitation.setOnEditorActionListener { _, _, _ ->
            val invitation = binding.invitation.text.toString()
            if (invitation.isNotEmpty()) {
                val app = requireActivity().application as WalletApp
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        val (_, connection) = app.agent.oob.receiveInvitationFromUrl(invitation)
                        showAlert("Connected to ${connection?.theirLabel ?: "unknown agent"}")
                    } catch (e: Exception) {
                        showAlert("Unable to connect: ${e.message}")
                    }
                }
            }
            true
        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = WalletMainActivity.SimpleItemRecyclerViewAdapter(
            requireActivity() as WalletMainActivity,
            listOf(MainMenu.GET, MainMenu.LIST, MainMenu.HISTORICAL, MainMenu.CONNECTION)
        )
    }

    private fun waitForAgentInitialize() {
        val app = requireActivity().application as WalletApp
        val timer = object : android.os.CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (app.walletOpened) {
                    Log.d("HomeFragment", "Agent initialized")
                    cancel()
                }
            }

            override fun onFinish() {
                showAlert("Failed to open wallet.")
            }
        }
        timer.start()
    }

    private fun showAlert(message: String) {
        android.app.AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}