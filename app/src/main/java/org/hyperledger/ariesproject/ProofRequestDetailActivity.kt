package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.hyperledger.ariesproject.databinding.ActivityProofRequestDetailBinding

class ProofRequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProofRequestDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProofRequestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.detailToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_proof_detail)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}