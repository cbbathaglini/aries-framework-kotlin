package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import org.hyperledger.ariesproject.databinding.ActivityProofDetailBinding
import org.hyperledger.ariesproject.databinding.ActivityProofListBinding

class ProofDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityProofDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProofDetailBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        setSupportActionBar(binding.detailToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_proof_detail)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navigateUpTo(Intent(this, ProofListActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}