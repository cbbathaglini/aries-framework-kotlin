package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import com.google.android.material.snackbar.Snackbar
import org.hyperledger.ariesproject.databinding.ActivityCredentialDetailBinding

class CredentialDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityCredentialDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialDetailBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        setSupportActionBar(binding.detailToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_credential_detail)

//        binding.fab.setOnClickListener { view ->
//            Snackbar.make(view, "Ação ainda não implementada", Snackbar.LENGTH_SHORT).show()
//        }

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navigateUpTo(Intent(this, CredentialListActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}