package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.ActivityHistoricalDetailBinding
import org.hyperledger.ariesproject.databinding.CredentialListContentBinding

class CredentialListActivity : BaseActivity() {
    private lateinit var binding: ActivityCredentialListBinding
    private lateinit var connectionId : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialListBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        connectionId = intent.getStringExtra("CONNECTION_ID") ?: ""
        println("connectionId: ${connectionId}")
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
        setupRecyclerView(binding.credentialList.credentialList)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        val app = application as WalletApp
        var credentialsRecords : List<CredentialExchangeRecord> = mutableListOf()
        var credentialsRecordsConn: MutableList<CredentialExchangeRecord> = mutableListOf()
        var credentialsExchange: List<CredentialExchangeRecord> = mutableListOf()
        //var credentialsW3c: List<W3cCredentialRecord> = mutableListOf()

        //find all in the same connection if connection was send
        if(connectionId != ""){
            credentialsExchange = runBlocking { app.agent.credentialExchangeRepository.getByConnectionId(connectionId)};
            credentialsExchange.forEach { credential ->
                val allCredentials = credential.credentials
                allCredentials.forEach { cred ->
                    val credential = runBlocking { app.agent.credentialExchangeRepository.getByCredentialId(cred.credentialRecordId)}
                    credentialsRecordsConn.add(credential);
                }
                recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, credentialsRecordsConn)
            }
        }else {
            credentialsRecords = runBlocking { app.agent.credentialExchangeRepository.getAll()};

            recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, credentialsRecords)
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
                    //putExtra(CredentialDetailFragment.ARG_CREDENTIAL, item.credential)
                    putExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID, item.id)

                    val attributesDict = item.credentialAttributes?.associate { attr ->
                        attr.name to attr.value
                    } ?: emptyMap()

                    //putExtra(CredentialDetailFragment.ARG_ATTR, attributesDict)
                    putExtra(CredentialDetailFragment.ARG_SCHEMA_NAME, item.schemaName)
                    putExtra(CredentialDetailFragment.ARG_SCHEMA_ID, item.schemaId)
                    putExtra(CredentialDetailFragment.ARG_CREDENTIAL_DEFINITION_ID, item.credentialDefinitionId)
                    putExtra(CredentialDetailFragment.ARG_REVOCATION_ID, item.revRegId)

                }
//                is W3cCredentialRecord -> Intent(v.context, CredentialW3cDetailActivity::class.java).apply {
//                    putExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C, item.credential.toString())
//                    putExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C_ID, item.id)
//                }
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
            val dateFormatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())

            when (item) {
                is CredentialExchangeRecord -> {
                    holder.contentView.text = item.id
                    val date = item.createdAt ?: java.util.Date()
                    holder.dateView.text = "Criado em: ${date}"
                    val type = ""//item.credentials.first().credentialRecordType
                    holder.typeView.text = "Is revoked?: ${item.state == CredentialState.Revoked}"
                }

//                is W3cCredentialRecord -> {
//                    holder.contentView.text = "${item.id}"
//                    val date = item.createdAt ?: java.util.Date()
//                    holder.dateView.text = "Criado em: ${date}"
//                    holder.typeView.text = "Tipo: W3C"
//                }
            }

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
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