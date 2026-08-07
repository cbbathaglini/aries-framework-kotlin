package org.hyperledger.ariesproject

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import org.hyperledger.ariesproject.databinding.ActivityCredentialDetailBinding
import org.hyperledger.ariesproject.databinding.ActivityCredentialW3cDetailBinding

class CredentialW3cDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCredentialW3cDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialW3cDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own detail action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        binding.detailToolbar.title = "Credential Detail"
        binding.detailToolbar.setNavigationOnClickListener {
            navigateUpTo(android.content.Intent(this, CredentialListActivity::class.java))
        }

        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val fragment = CredentialW3cDetailFragment().apply {
                Log.i("W3C", "=> ${intent.getStringExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C)}")
                arguments = Bundle().apply {
                    putString(
                        CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C,
                        intent.getStringExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C),
                    )
                    putString(
                        CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C_ID,
                        intent.getStringExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C_ID),
                    )

                }
            }

            supportFragmentManager.beginTransaction()
                .add(binding.credentialW3cDetailContainer.id, fragment)
                .commit()
        }
    }

}