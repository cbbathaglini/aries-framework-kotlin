package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.databinding.ActivityHistoricalDetailBinding
import org.hyperledger.ariesproject.wrapper.ConnectionRecordWrapper

class HistoricalDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoricalDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoricalDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.detailToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ✅ usa direto o botão do binding
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
                    putParcelable(
                        HistoricalDetailFragment.ARG_CONNECTION_RECORD,
                        connectionRecordParcelable
                    )
                }
            }

            supportFragmentManager.beginTransaction()
                .add(binding.historicalDetailContainer.id, fragment)
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
                    finish() // fecha a tela após deletar
                }
            } catch (e: Exception) {
                Log.e("WalletApp", "Erro ao deletar conexão: ${e.message}")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                navigateUpTo(Intent(this, HistoricalListActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}