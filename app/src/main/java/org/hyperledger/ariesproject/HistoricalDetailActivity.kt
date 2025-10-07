package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.HistoricalDetailFragment.Companion.ARG_CONNECTION_ID
import org.hyperledger.ariesproject.HistoricalDetailFragment.Companion.ARG_CONNECTION_RECORD
import org.hyperledger.ariesproject.databinding.ActivityHistoricalDetailBinding
import org.hyperledger.ariesproject.wrapper.ConnectionRecordWrapper

class HistoricalDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoricalDetailBinding
    private lateinit var deleteConnection: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHistoricalDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.detailToolbar)
        deleteConnection = findViewById(R.id.delete_connection)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own detail action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val connectionRecordParcelable =
            intent.getParcelableExtra<ConnectionRecordWrapper>(HistoricalDetailFragment.ARG_CONNECTION_RECORD)

        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val fragment = HistoricalDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(
                        HistoricalDetailFragment.ARG_CONNECTION_ID,
                        intent.getStringExtra(HistoricalDetailFragment.ARG_CONNECTION_ID),
                    )
                    putParcelable(HistoricalDetailFragment.ARG_CONNECTION_RECORD, connectionRecordParcelable)

                }
            }

            supportFragmentManager.beginTransaction()
                .add(binding.historicalDetailContainer.id, fragment)
                .commit()
        }



        deleteConnection.setOnClickListener {
            deleteConnection()
        }

    }

    private fun deleteConnection(){
        lifecycleScope.launch {
            val app = application as WalletApp
            var connectionId: String? = intent.getStringExtra("CONNECTION_ID")
            if (connectionId != null) {
                app.agent.connectionRepository.deleteById(connectionId)
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
