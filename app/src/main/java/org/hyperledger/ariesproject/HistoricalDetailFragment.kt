package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesproject.databinding.ActivityHistoricalDetailBinding
import org.hyperledger.ariesproject.databinding.HistoricalDetailBinding
import org.hyperledger.ariesproject.wrapper.ConnectionRecordWrapper

class HistoricalDetailFragment : Fragment() {

    private var item: ConnectionRecordWrapper? = null
    private var connectionId: String? = null
    private lateinit var detailBinding: ActivityHistoricalDetailBinding
    private lateinit var binding: HistoricalDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            if (it.containsKey(ARG_CONNECTION_ID)) {
                item = it.getParcelable(ARG_CONNECTION_RECORD)
                connectionId = it.getString(ARG_CONNECTION_ID)
                detailBinding = ActivityHistoricalDetailBinding.inflate(layoutInflater)
                detailBinding.toolbarLayout.title = getString(R.string.title_historical_detail)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = HistoricalDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root
        binding.historicalDetail.text = item?.printFields()
        binding.connectionName.text = item?.theirLabel;

        val activity = activity as HistoricalDetailActivity
        val app = activity.application as WalletApp

       // Bind the delete button to the delete action
        binding.getAllCredentialsButton.setOnClickListener {
            // To prevent multiple clicks
            binding.getAllCredentialsButton.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) {
                activity.runOnUiThread {
                    // Send credentialId to another activity
                    val intent = Intent(activity, CredentialListActivity::class.java).apply {
                        putExtra("CONNECTION_ID", item?.id)
                    }
                    activity.startActivity(intent)

                    // Close current activity if needed
                    activity.finish()
                }
            }

        }

        binding.sendMessage.setOnClickListener {
            // To prevent multiple clicks
            binding.sendMessage.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) {
                activity.runOnUiThread {
                    // Send credentialId to another activity
                    val intent = Intent(activity, SendMessageActivity::class.java).apply {
                        putExtra("CONNECTION_ID", item?.id)
                    }
                    activity.startActivity(intent)

                    // Close current activity if needed
                    activity.finish()
                }
            }
        }

        return rootView
    }

    companion object {
        const val ARG_CONNECTION_RECORD = "connection_record"
        const val ARG_CONNECTION_ID = "id"
        const val ARG_CONNECTION_THREADID = "threadId"
        const val ARG_CONNECTION_MEDIATORID = "mediatorId"
    }
}
