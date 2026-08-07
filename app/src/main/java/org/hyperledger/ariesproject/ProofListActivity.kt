package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.ActivityProofListBinding
import org.hyperledger.ariesproject.databinding.ProofListContentBinding
import java.text.SimpleDateFormat
import java.util.*

class ProofListActivity : BaseActivity() {
    private lateinit var binding: ActivityProofListBinding
    private lateinit var connectionId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProofListBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.toolbar.title = "Received Proofs"
        binding.toolbar.setNavigationOnClickListener { finish() }

        connectionId = intent.getStringExtra("CONNECTION_ID") ?: ""
        Log.d("ProofListActivity", "connectionId: $connectionId")
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
        setupRecyclerView(binding.proofList.proofListRecycler)
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        val app = application as WalletApp
        val proofs: List<ProofExchangeRecord> = if (connectionId.isNotEmpty()) {
            val proof = runBlocking { app.agent.proofRepository.getByConnectionId(connectionId) }
            if (proof != null) listOf(proof) else emptyList()
        } else {
            runBlocking { app.agent.proofRepository.getAll() }
        }

        val sortedProofs = proofs.sortedByDescending { it.createdAt  }
        recyclerView.adapter = ProofAdapter(this, sortedProofs)
    }
}