package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityW3cCredentialListBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class W3cCredentialListActivity : BaseActivity() {

    private lateinit var binding: ActivityW3cCredentialListBinding
    private lateinit var adapter: W3cAdapter
    private lateinit var records: List<W3cCredentialRecord>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityW3cCredentialListBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.toolbar.title = "W3C Credentials"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.w3cRecycler.layoutManager = LinearLayoutManager(this)

        adapter = W3cAdapter(mutableListOf()) { record ->
            val intent = Intent(this, CredentialW3cDetailActivity::class.java).apply {
                putExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C, record.credential.toJson())
                putExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C_ID, record.id)
            }
            startActivity(intent)
        }
        binding.w3cRecycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
        loadW3cCredentials()
    }

    private fun loadW3cCredentials() {
        val app = application as WalletApp
        records = if (app.isAgentInitialized()) {
            runBlocking { app.agent.w3cCredentialRepository.getAll() }
                .sortedByDescending { it.createdAt }
        } else {
            emptyList()
        }

        adapter.update(records)
        binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        binding.w3cRecycler.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
    }

    class W3cAdapter(
        private var values: MutableList<W3cCredentialRecord>,
        private val onClick: (W3cCredentialRecord) -> Unit,
    ) : RecyclerView.Adapter<W3cAdapter.ViewHolder>() {

        fun update(newValues: List<W3cCredentialRecord>) {
            values = newValues.toMutableList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_w3c_credential, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = values[position]
            val credential = record.credential

            holder.title.text = credential.type.joinToString(", ").ifEmpty { "W3C Credential" }
            holder.subtitle.text = "Issuer: ${credential.issuer}"
            holder.meta.text = "Issued: ${formatDate(credential.issuanceDate)}"
            holder.id.text = "id: ${record.id}"

            holder.itemView.setOnClickListener { onClick(record) }
            holder.copyBtn.setOnClickListener {
                val clipboard = holder.itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("W3C Credential JSON", credential.toJson())
                clipboard.setPrimaryClip(clip)
                holder.copyBtn.text = "Copied!"
                holder.itemView.postDelayed({ holder.copyBtn.text = "Copy JSON" }, 2000)
            }
        }

        private fun formatDate(raw: String): String {
            return try {
                val parsed = java.time.Instant.parse(raw)
                SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(parsed.toEpochMilli()))
            } catch (e: Exception) {
                raw
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.titleText)
            val subtitle: TextView = view.findViewById(R.id.subtitleText)
            val meta: TextView = view.findViewById(R.id.metaText)
            val id: TextView = view.findViewById(R.id.idText)
            val copyBtn: MaterialButton = view.findViewById(R.id.btnCopy)
        }
    }
}
