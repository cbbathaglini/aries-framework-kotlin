package org.hyperledger.ariesproject

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
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

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_historical_detail)

        // Deletar conexão
        binding.deleteConnection.setOnClickListener {
            deleteConnection()
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalhes da Conexão"

        toolbar.setNavigationOnClickListener {
            finish()
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
                Log.e("WalletApp", "Erro ao deletar conexão: ${e.message}")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }
}