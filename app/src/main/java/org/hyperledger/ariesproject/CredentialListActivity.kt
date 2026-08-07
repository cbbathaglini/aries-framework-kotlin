package org.hyperledger.ariesproject

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.CredentialListContentBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CredentialListActivity : BaseActivity() {
    private lateinit var binding: ActivityCredentialListBinding
    private lateinit var connectionId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialListBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }
        connectionId = intent.getStringExtra("CONNECTION_ID") ?: ""
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
        setupRecyclerView(binding.credentialList.credentialList)
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        val app = application as WalletApp
        var credentialRecords: List<CredentialExchangeRecord> = mutableListOf()
        var credentialRecordsByConnection: MutableList<CredentialExchangeRecord> = mutableListOf()
        var credentialExchange: List<CredentialExchangeRecord> = mutableListOf()

        if (connectionId != "") {
            credentialExchange = runBlocking { app.agent.credentialExchangeRepository.getByConnectionId(connectionId) }
            credentialExchange.forEach { credential ->
                val allCredentials = credential.credentials
                allCredentials.forEach { cred ->
                    val record = runBlocking {
                        app.agent.credentialExchangeRepository.getByCredentialId(cred.credentialRecordId)
                    }
                    credentialRecordsByConnection.add(record)
                }
                recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, credentialRecordsByConnection)
            }
        } else {
            credentialRecords = runBlocking { app.agent.credentialExchangeRepository.getAll() }
            recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, credentialRecords)
        }
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: CredentialListActivity,
        private val values: List<Any>,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        private val onClickListener: View.OnClickListener = View.OnClickListener { v ->
            val item = v.tag
            val intent = when (item) {
                is CredentialExchangeRecord -> Intent(v.context, CredentialDetailActivity::class.java).apply {
                    putExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID, item.id)

                    val attributesDict = item.credentialAttributes?.associate { attr ->
                        attr.name to attr.value
                    } ?: emptyMap()

                    putExtra(CredentialDetailFragment.ARG_SCHEMA_NAME, item.schemaName)
                    putExtra(CredentialDetailFragment.ARG_SCHEMA_ID, item.schemaId)
                    putExtra(CredentialDetailFragment.ARG_CREDENTIAL_DEFINITION_ID, item.credentialDefinitionId)
                    putExtra(CredentialDetailFragment.ARG_REVOCATION_ID, item.revRegId)
                }

                else -> null
            }

            intent?.let { v.context.startActivity(it) }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.credential_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]

            when (item) {
                is CredentialExchangeRecord -> {
                    holder.contentView.text = item.id
                    holder.dateView.text = formatDate(item.createdAt)
                    bindStatusBadge(holder.typeView, item.state == CredentialState.Revoked)
                }
            }

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        private fun formatDate(date: kotlinx.datetime.Instant?): String {
            val value = date?.toEpochMilliseconds() ?: System.currentTimeMillis()
            return SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US).format(java.util.Date(value))
        }

        private fun bindStatusBadge(view: TextView, isRevoked: Boolean) {
            val color = if (isRevoked) {
                parentActivity.getColor(R.color.status_failed)
            } else {
                parentActivity.getColor(R.color.status_verified)
            }
            view.text = if (isRevoked) "Revoked" else "Active"
            view.setTextColor(Color.WHITE)
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = parentActivity.resources.displayMetrics.density * 12
                setColor(color)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val binding = CredentialListContentBinding.bind(view)
            val contentView: TextView = binding.content
            val dateView: TextView = binding.date
            val typeView: TextView = binding.type
        }
    }
}
