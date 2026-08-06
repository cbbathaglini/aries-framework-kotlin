package org.hyperledger.ariesproject

import android.os.Bundle
import android.widget.FrameLayout
import org.hyperledger.ariesproject.databinding.ActivityProofDetailBinding

class ProofDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityProofDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProofDetailBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.detailToolbar.title = getString(R.string.title_proof_detail)
        binding.detailToolbar.setNavigationOnClickListener {
            navigateUpTo(android.content.Intent(this, ProofListActivity::class.java))
        }

        if (savedInstanceState == null) {
            val proofId = intent.getStringExtra(ProofDetailFragment.ARG_PROOF_ID)
            val fragment = ProofDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ProofDetailFragment.ARG_PROOF_ID, proofId)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(binding.proofDetailContainer.id, fragment)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

}