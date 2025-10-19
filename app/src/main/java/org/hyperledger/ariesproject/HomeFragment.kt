package org.hyperledger.ariesproject

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import org.hyperledger.ariesproject.databinding.FragmentHomeBinding

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

        // 🔹 Configura o RecyclerView do menu principal
        binding.menuList.layoutManager = LinearLayoutManager(requireContext())
        (activity as? WalletMainActivity)?.let {
            it.setupRecyclerView(binding.menuList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}