package org.hyperledger.ariesproject


import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord

class PresentationsReceivedListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var refreshButton: Button
    private lateinit var lastUpdatedText: TextView
    private lateinit var emptyView: TextView
    private lateinit var backButton: Button

    private var agent: Agent? = null
    private var verifierRecords: List<VerifierRecord> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentations_received_list)

        listView = findViewById(R.id.presentationsListView)
        refreshButton = findViewById(R.id.refreshButton)
        lastUpdatedText = findViewById(R.id.lastUpdatedText)
        emptyView = findViewById(R.id.emptyView)
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        listView.emptyView = emptyView

        agent = (application as? WalletApp)?.agent

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

        refreshButton.setOnClickListener { refreshPresentations() }

        // Clique em uma apresentação abre detalhes
        listView.setOnItemClickListener { _, _, position, _ ->

            listView.setOnItemClickListener { _, _, position, _ ->
                val record = verifierRecords[position]
                val intent = Intent(this, PresentationsFromRecordActivity::class.java)
                intent.putExtra("verifierRecordId", record.id)
                startActivity(intent)
            }

//            val record = verifierRecords[position]
//            val lastPresentation = record.presentation?.lastOrNull()
//            if (lastPresentation != null) {
//                val intent = Intent(this, PresentationDetailActivity::class.java)
//                intent.putExtra("recordId", lastPresentation.proofRecordId)
//                startActivity(intent)
//            } else {
//                Toast.makeText(this, "Nenhum detalhe disponível", Toast.LENGTH_SHORT).show()
//            }
        }

        refreshPresentations()
    }

    private fun refreshPresentations() {
        lifecycleScope.launch {
            try {
                val allVerifierRecords = agent?.verifierRepository?.getAll() ?: emptyList()
                verifierRecords = allVerifierRecords
                    .filter { !it.presentation.isNullOrEmpty() }
                    .sortedByDescending { it.createdAt }

                val items = verifierRecords.map { vr ->
                    val latest = vr.presentation?.lastOrNull()
                    val verifiedStatus = if (latest?.isVerified == true) "✅ Verificado" else "⚠️ Não verificado"
                    val created = vr.createdAt.toString()

                    """
                        <b>Thread:</b> ${vr.globalThreadId ?: "—"}<br>
                        <b>Apresentações:</b> ${vr.presentation?.size ?: 0}<br>
                        <font color="#777777" size="-1">
                        Criado em: $created
                        </font>
                    """.trimIndent()
                }

                adapter.clear()
                adapter.addAll(items)
                adapter.notifyDataSetChanged()

                lastUpdatedText.text = "Última atualização: ${java.util.Date()}"
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@PresentationsReceivedListActivity, "Erro ao carregar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}