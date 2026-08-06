package org.hyperledger.ariesproject

import android.os.Bundle
import android.widget.FrameLayout
import org.hyperledger.ariesproject.databinding.ActivityProofRequestDetailBinding

class ProofRequestDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityProofRequestDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProofRequestDetailBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.toolbar.title = "Request proof detail"
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            val proofId = intent.getStringExtra(ProofRequestDetailFragment.ARG_PROOF_ID)

            val fragment = ProofRequestDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ProofRequestDetailFragment.ARG_PROOF_ID, proofId)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(binding.proofDetailContainer.id, fragment)
                .commit()
        }
    }

}
