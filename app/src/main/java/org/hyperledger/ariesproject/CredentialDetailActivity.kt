package org.hyperledger.ariesproject

import android.os.Bundle
import android.widget.FrameLayout
import org.hyperledger.ariesproject.databinding.ActivityCredentialDetailBinding

class CredentialDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityCredentialDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialDetailBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.detailToolbar.title = getString(R.string.title_credential_detail)
        binding.detailToolbar.setNavigationOnClickListener {
            navigateUpTo(android.content.Intent(this, CredentialListActivity::class.java))
        }

        if (savedInstanceState == null) {
            val credentialId = intent.getStringExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID)

            val fragment = CredentialDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(CredentialDetailFragment.ARG_CREDENTIAL_ID, credentialId)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(binding.credentialDetailContainer.id, fragment)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

}