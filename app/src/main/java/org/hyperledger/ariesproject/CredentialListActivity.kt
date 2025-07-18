package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.CredentialListContentBinding

class CredentialListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCredentialListBinding
    private lateinit var connectionId : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCredentialListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        connectionId = intent.getStringExtra("CONNECTION_ID") ?: ""
        println("connectionId: ${connectionId}")
    }

    override fun onResume() {
        super.onResume()
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
        var credentialsRecords : List<CredentialRecord> = mutableListOf()
        var credentialsRecordsConn: MutableList<CredentialRecord> = mutableListOf()
        var credentialsExchange: List<CredentialExchangeRecord> = mutableListOf()
        var credentialsW3c: List<W3cCredentialRecord> = mutableListOf()

        //find all in the same connection if connection was send
        if(connectionId != ""){
            credentialsExchange = runBlocking { app.agent.credentialExchangeRepository.getByConnectionId(connectionId)};
            credentialsExchange.forEach { credential ->
                val allCredentials = credential.credentials
                allCredentials.forEach { cred ->
                    val credential = runBlocking { app.agent.credentialRepository.getByCredentialId(cred.credentialRecordId)}
                    credentialsRecordsConn.add(credential);
                }
                recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, credentialsRecordsConn)
            }
        }else {
            //find all credentials
            credentialsW3c = runBlocking { app.agent.w3cCredentialRepository.getAll()};
            credentialsRecords = runBlocking { app.agent.credentialRepository.getAll()};

            val listCredentials : List<Any> = credentialsW3c + credentialsRecords
            Log.i("W3C", "size: ${listCredentials.size}")
            recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, listCredentials)
        }

    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: CredentialListActivity,
        private val values: List<Any>,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {
        private val onClickListener: View.OnClickListener = View.OnClickListener { v ->
            val item = v.tag
            lateinit var intent: Intent
            if(item is CredentialRecord) {
                intent = Intent(v.context, CredentialDetailActivity::class.java).apply {
                    putExtra(CredentialDetailFragment.ARG_CREDENTIAL, item.credential)
                    putExtra(
                        CredentialDetailFragment.ARG_CREDENTIAL_ID,
                        item.credentialId
                    ) // Keep as Int
                }
            }else if (item is W3cCredentialRecord){

                intent = Intent(v.context, CredentialW3cDetailActivity::class.java).apply {
                    putExtra(CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C, item.credential.toString())
                    putExtra(
                        CredentialW3cDetailFragment.ARG_CREDENTIAL_W3C_ID,
                        item.id
                    )

                }
            }

            v.context.startActivity(intent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.credential_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            if(item is CredentialRecord) {
                holder.contentView.text = item.credentialId
            }else if (item is W3cCredentialRecord){
                holder.contentView.text = item.id + " (W3C)"
            }

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var contentBinding = CredentialListContentBinding.bind(view)
            val contentView: TextView = contentBinding.content
        }
    }
}
