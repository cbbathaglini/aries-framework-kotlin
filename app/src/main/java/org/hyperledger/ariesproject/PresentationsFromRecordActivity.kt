package org.hyperledger.ariesproject

import android.content.Intent
import org.hyperledger.ariesframework.proofs.repository.verifier.PresentationVerifier

import android.os.Bundle
import android.text.Html
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent

class PresentationsFromRecordActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var backButton: Button
    private lateinit var titleText: TextView
    private lateinit var threadIdText: TextView

    private var agent: Agent? = null
    private var verifierRecordId: String? = null
    private var presentations: List<PresentationVerifier> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentations_from_record)

        listView = findViewById(R.id.presentationsListView)
        emptyView = findViewById(R.id.emptyView)
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        threadIdText = findViewById(R.id.threadIdText)

        listView.emptyView = emptyView
        backButton.setOnClickListener { finish() }

        agent = (application as? WalletApp)?.agent
        verifierRecordId = intent.getStringExtra("verifierRecordId")

        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf()) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = Html.fromHtml(getItem(position) ?: "", Html.FROM_HTML_MODE_LEGACY)
                textView.textSize = 15f
                val padding = (10 * resources.displayMetrics.density).toInt()
                textView.setPadding(padding, padding, padding, padding)
                return view
            }
        }
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val pres = presentations[position]
            val proofId = pres.proofRecordId

            if (proofId != null) {
                val intent = Intent(this, ProofDetailActivity::class.java)
                intent.putExtra(ProofDetailFragment.ARG_PROOF_ID, proofId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Presentation ID not found", Toast.LENGTH_SHORT).show()
            }
        }

        loadPresentations()
    }

    private fun loadPresentations() {
        lifecycleScope.launch {
            try {
                val verifierRecord = agent?.verifierRepository?.getById(verifierRecordId!!)
                if (verifierRecord == null) {
                    Toast.makeText(this@PresentationsFromRecordActivity, "Record not found", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                threadIdText.text = "Thread ID: ${verifierRecord.globalThreadId ?: "—"}"
                presentations = verifierRecord.presentation ?: emptyList()

                if (presentations.isEmpty()) {
                    emptyView.visibility = android.view.View.VISIBLE
                    return@launch
                }

                val items = presentations.mapIndexed { index, pres ->
                    val verified = if (pres.isVerified!!) "✅ Verified" else "⚠️ Not verified"
                    val offline = if (pres.isOffline!!) "📴 Offline" else "🌐 Online"
                    val id = pres.proofRecordId ?: "N/A"

                    """
                        <b>Presentation #${index + 1}</b><br>
                        <font color="#777777" size="-1">
                        ID: $id<br>
                        Status: $verified<br>
                        Origin: $offline
                        </font>
                    """.trimIndent()
                }

                adapter.clear()
                adapter.addAll(items)
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@PresentationsFromRecordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}