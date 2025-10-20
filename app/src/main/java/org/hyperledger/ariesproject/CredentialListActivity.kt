package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.CredentialListContentBinding

class CredentialListActivity : BaseActivity() {
    private lateinit var binding: ActivityCredentialListBinding
    private lateinit var connectionId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCredentialListBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        connectionId = intent.getStringExtra("CONNECTION_ID") ?: ""
        Log.d("CredentialList", "connectionId: $connectionId")

        // ✅ Garante que nenhuma aba fique verde
        clearBottomNavigationSelection()
    }

    override fun onResume() {
        super.onResume()
        clearBottomNavigationSelection()
        setupRecyclerView(binding.credentialList.credentialList)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        val app = application as WalletApp
        val credentialsRecordsConn = mutableListOf<CredentialRecord>()

        if (connectionId.isNotEmpty()) {
            val credentialsExchange = runBlocking {
                app.agent.credentialExchangeRepository.getByConnectionId(connectionId)
            }
            credentialsExchange.forEach { exchange ->
                exchange.credentials.forEach { cred ->
                    val credential = runBlocking {
                        app.agent.credentialRepository.getByCredentialId(cred.credentialRecordId)
                    }
                    credentialsRecordsConn.add(credential)
                }
            }
            recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, credentialsRecordsConn)
        } else {
            val credentialsW3c = runBlocking { app.agent.w3cCredentialRepository.getAll() }
            val credentialsRecords = runBlocking { app.agent.credentialRepository.getAll() }
            recyclerView.adapter =
                SimpleItemRecyclerViewAdapter(this, credentialsW3c + credentialsRecords)
        }
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: CredentialListActivity,
        private val values: List<Any>,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        private val onClickListener = View.OnClickListener { v ->
            val item = v.tag
            val intent = when (item) {
                is CredentialRecord -> Intent(v.context, CredentialDetailActivity::class.java).apply {
                    putExtra(CredentialDetailFragment.ARG_CREDENTIAL, item.credential)
                    putExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID, item.credentialId)
                }
                is W3cCredentialRecord -> Intent(v.context, CredentialW3cDetailActivity::class.java).apply {
                    putExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C, item.credential.toString())
                    putExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C_ID, item.id)
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
            holder.contentView.text = when (item) {
                is CredentialRecord -> item.credentialId
                is W3cCredentialRecord -> "${item.id} (W3C)"
                else -> ""
            }
            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val contentBinding = CredentialListContentBinding.bind(view)
            val contentView: TextView = contentBinding.content
        }
    }
}