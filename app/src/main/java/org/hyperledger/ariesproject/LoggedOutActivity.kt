package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hyperledger.ariesproject.databinding.ActivityLoggedOutBinding

class LoggedOutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoggedOutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoggedOutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startAgainButton.setOnClickListener {
            startWalletAgain()
        }
    }

    private fun startWalletAgain() {
        val intent = Intent(this, WalletMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_REOPEN_WALLET, true)
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    companion object {
        const val EXTRA_REOPEN_WALLET = "REOPEN_WALLET"
    }
}