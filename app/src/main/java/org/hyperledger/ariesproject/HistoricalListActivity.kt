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
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialListBinding
import org.hyperledger.ariesproject.databinding.ActivityHistoricalListBinding
import org.hyperledger.ariesproject.databinding.HistoricalListContentBinding
import org.hyperledger.ariesproject.wrapper.ConnectionRecordWrapper

class HistoricalListActivity : BaseActivity() {
    private lateinit var binding: ActivityHistoricalListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHistoricalListBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
        setupRecyclerView(binding.historicalList.historicalList)
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
        if (!app.isAgentInitialized()) {
            Log.e("WalletApp", "Agente não inicializado ainda!")
            return
        }


        val connections = runBlocking { app.agent.connectionRepository.getAll() }
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, connections)
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: HistoricalListActivity,
        private val values: List<ConnectionRecord>,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {
        private val onClickListener: View.OnClickListener = View.OnClickListener { v ->
            val item = v.tag as ConnectionRecord

            val intent = Intent(v.context, HistoricalDetailActivity::class.java).apply {
                putExtra(HistoricalDetailFragment.ARG_CONNECTION_ID, item.id)
                putExtra(HistoricalDetailFragment.ARG_CONNECTION_RECORD, ConnectionRecordWrapper(item))
//                putExtra(HistoricalDetailFragment.ARG_CONNECTION_THREADID, item.threadId)
//                putExtra(HistoricalDetailFragment.ARG_CONNECTION_MEDIATORID, item.mediatorId)
            }

            v.context.startActivity(intent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.historical_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.contentView.text = item.theirLabel

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var contentBinding = HistoricalListContentBinding.bind(view)
            val contentView: TextView = contentBinding.content
        }
    }
}
