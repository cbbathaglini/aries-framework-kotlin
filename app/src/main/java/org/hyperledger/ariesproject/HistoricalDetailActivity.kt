package org.hyperledger.ariesproject

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.databinding.ActivityHistoricalDetailBinding
import org.hyperledger.ariesproject.wrapper.ConnectionRecordWrapper

class HistoricalDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityHistoricalDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHistoricalDetailBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.toolbar.title = "Connection Details"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.deleteConnection.setOnClickListener {
            deleteConnection()
        }

        val connectionRecordParcelable =
            intent.getParcelableExtra<ConnectionRecordWrapper>(
                HistoricalDetailFragment.ARG_CONNECTION_RECORD
            )

        if (savedInstanceState == null) {
            val fragment = HistoricalDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(
                        HistoricalDetailFragment.ARG_CONNECTION_ID,
                        intent.getStringExtra(HistoricalDetailFragment.ARG_CONNECTION_ID)
                    )

                    intent.putExtra(HistoricalDetailFragment.ARG_OFFLINE_PROOF, "false")

                    putParcelable(
                        HistoricalDetailFragment.ARG_CONNECTION_RECORD,
                        connectionRecordParcelable
                    )
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(binding.historicalDetailContainer.id, fragment)
                .commit()
        }
    }

    private fun deleteConnection() {
        lifecycleScope.launch {
            try {
                val app = application as WalletApp
                val connectionId =
                    intent.getStringExtra(HistoricalDetailFragment.ARG_CONNECTION_ID)

                connectionId?.let {
                    app.agent.connectionRepository.deleteById(it)
                    finish()
                }
            } catch (e: Exception) {

                // Log.e: Indicates an error occurred while trying to delete a connection.
                Log.e("WalletApp", "Error deleting connection: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }
}