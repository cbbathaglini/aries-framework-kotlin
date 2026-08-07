package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

        val app = application as WalletApp
        val prefs = getSharedPreferences("wallet_prefs", MODE_PRIVATE)

        binding.startAgainButton.setOnClickListener {
            startWalletAgain()
        }
    }

    private fun startWalletAgain() {
        binding.startAgainButton.isEnabled = false

        lifecycleScope.launch {
            val app = application as WalletApp

            val success = withContext(Dispatchers.IO) {
                runCatching {
                    app.prepareForWalletEntry()
                    true
                }.getOrElse {
                    Log.e("LoggedOutActivity", "Failed to reopen wallet", it)
                    false
                }
            }

            if (success) {
                val intent = Intent(this@LoggedOutActivity, WalletMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(EXTRA_REOPEN_WALLET, true)
                }
                startActivity(intent)
                finish()
            } else {
                binding.startAgainButton.isEnabled = true
            }
        }
    }


    companion object {
        const val EXTRA_REOPEN_WALLET = "REOPEN_WALLET"
    }
}