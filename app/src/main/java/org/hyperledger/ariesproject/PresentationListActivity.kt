package org.hyperledger.ariesproject

import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.proofs.models.ProofState

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

        adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)

                textView.text = android.text.Html.fromHtml(
                    getItem(position) ?: "",
                    android.text.Html.FROM_HTML_MODE_LEGACY
                )

                val padding = (8 * resources.displayMetrics.density).toInt()
                textView.setPadding(padding, padding, padding, padding)
                textView.textSize = 16f

                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = (12 * resources.displayMetrics.density).toInt()
                view.layoutParams = params

                return view
            }
        }

        listView.adapter = adapter

        refreshButton.setOnClickListener { refreshRecords() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val record = records[position]
            val intent = Intent(this, ProofDetailActivity::class.java)
            intent.putExtra(ProofDetailFragment.ARG_PROOF_ID, record.id)
            startActivity(intent)
        }

        refreshRecords()
    }

    private fun refreshRecords() {
        lifecycleScope.launch {
            try {
                val all = agent?.proofRepository?.getAll() ?: emptyList()
                val filtered = all
                    .filter { it.presentationMessage != null }
                    .filter { it.state == ProofState.PresentationSent }
                    .sortedByDescending { it.createdAt }

                records = filtered

                val items = filtered.map { record ->
                    val dateFormatted = record.createdAt?.toString() ?: "N/A"

                    """
                        <b>ID:</b> ${record.id}<br>
                        <font color="#777777" size="-1">
                        State: ${record.state}<br>
                        Created at: $dateFormatted
                        </font>
                    """.trimIndent()
                }

                adapter.clear()
                adapter.addAll(items)
                adapter.notifyDataSetChanged()

                lastUpdatedText.text = "Last updated: ${java.util.Date()}"
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@PresentationListActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}