package org.hyperledger.ariesproject

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import java.text.SimpleDateFormat
import java.util.*

class PresentationsReceivedListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var refreshButton: Button
    private lateinit var lastUpdatedText: TextView
    private lateinit var emptyView: TextView

    private var agent: Agent? = null
    private var verifierRecords: List<VerifierRecord> = emptyList()
    private lateinit var adapter: PresentationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentations_received_list)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        listView = findViewById(R.id.presentationsListView)
        refreshButton = findViewById(R.id.refreshButton)
        lastUpdatedText = findViewById(R.id.lastUpdatedText)
        emptyView = findViewById(R.id.emptyView)

        listView.emptyView = emptyView

        agent = (application as? WalletApp)?.agent

        adapter = PresentationAdapter()
        listView.adapter = adapter

        refreshButton.setOnClickListener { refreshPresentations() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val record = verifierRecords[position]
            val intent = Intent(this, PresentationsFromRecordActivity::class.java)
            intent.putExtra("verifierRecordId", record.id)
            startActivity(intent)
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

                adapter.notifyDataSetChanged()

                val sdf = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US)
                lastUpdatedText.text = getString(R.string.last_updated_at, sdf.format(Date()))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@PresentationsReceivedListActivity,
                    "Error loading presentations: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getStatus(record: VerifierRecord): Triple<String, String, Int> {
        val presentations = record.presentation ?: return Triple("Pending", "No presentations", R.color.status_pending)

        if (presentations.isEmpty()) return Triple("Pending", "No presentations", R.color.status_pending)

        val anyUnverified = presentations.any { it.isVerified == false }
        val allVerified = presentations.all { it.isVerified == true }

        return when {
            allVerified -> Triple("Verified", "All presentations verified", R.color.status_verified)
            anyUnverified -> Triple("Failed", "Some presentations failed verification", R.color.status_failed)
            else -> Triple("Pending", "Waiting for verification", R.color.status_pending)
        }
    }

    private fun createBadgeDrawable(colorRes: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.displayMetrics.density * 12
            setColor(ContextCompat.getColor(this@PresentationsReceivedListActivity, colorRes))
        }
    }

    private inner class PresentationAdapter : BaseAdapter() {
        override fun getCount() = verifierRecords.size
        override fun getItem(position: Int) = verifierRecords[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_presentation, parent, false)
            val record = verifierRecords[position]

            view.findViewById<TextView>(R.id.threadId).text = "Thread: ${record.globalThreadId ?: "—"}"
            view.findViewById<TextView>(R.id.presentationCount).text = "Presentations: ${record.presentation?.size ?: 0}"

            val sdf = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US)
            val created = record.createdAt?.let { sdf.format(Date(it.toEpochMilliseconds())) } ?: "—"
            view.findViewById<TextView>(R.id.createdAt).text = created

            val (status, _, colorRes) = getStatus(record)
            val badge = view.findViewById<TextView>(R.id.statusBadge)
            badge.text = status
            badge.background = createBadgeDrawable(colorRes)
            badge.setTextColor(android.graphics.Color.WHITE)

            return view
        }
    }
}
