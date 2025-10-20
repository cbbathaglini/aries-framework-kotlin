package org.hyperledger.ariesproject

import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent

class PresentationListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var refreshButton: Button
    private lateinit var lastUpdatedText: TextView
    private lateinit var emptyView: TextView
    private lateinit var backButton: Button

    private var agent: Agent? = null
    private var records: List<ProofExchangeRecord> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation_list)

        listView = findViewById(R.id.presentationList)
        refreshButton = findViewById(R.id.refreshButton)
        lastUpdatedText = findViewById(R.id.lastUpdatedText)
        emptyView = findViewById(R.id.emptyView)
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        listView.emptyView = emptyView

        agent = (application as? WalletApp)?.agent

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        refreshButton.setOnClickListener { refreshRecords() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val record = records[position]
            val intent = Intent(this, PresentationDetailActivity::class.java)
            intent.putExtra("recordId", record.id)
            startActivity(intent)
        }

        refreshRecords()
    }

    private fun refreshRecords() {
        lifecycleScope.launch {
            try {
                val all = agent?.proofRepository?.getAll() ?: emptyList()
                val filtered = all.filter { it.presentationMessage != null }
                records = filtered

                val items = filtered.map { "ID: ${it.id} - ${it.state}" }
                adapter.clear()
                adapter.addAll(items)
                adapter.notifyDataSetChanged()

                lastUpdatedText.text = "Última atualização: ${java.util.Date()}"
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@PresentationListActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}