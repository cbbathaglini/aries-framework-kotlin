package org.hyperledger.ariesproject

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.models.DeclineCredentialOfferOptions
import org.hyperledger.ariesframework.problemreports.messages.CredentialProblemReportMessage
import org.hyperledger.ariesframework.problemreports.messages.MediationProblemReportMessage
import org.hyperledger.ariesframework.problemreports.messages.PresentationProblemReportMessage
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.ProofConstants
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.models.RequestedCredentials
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesproject.databinding.ActivityWalletMainBinding
import org.hyperledger.ariesproject.databinding.MenuItemListContentBinding
import org.hyperledger.ariesproject.menu.MainMenu
import org.json.JSONObject

class WalletMainActivity : BaseActivity() {

    private lateinit var binding: ActivityWalletMainBinding
    private val TAG = "WalletMainActivity"

    private var credentialProgress: ProgressDialog? = null
    private var proofProgress: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setChildContent(R.layout.activity_wallet_main)
        binding = ActivityWalletMainBinding.bind(findViewById(R.id.baseContainer))
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = title

        openFragment(HomeFragment())
        updateToolbarAndBackground(R.color.teal_700, R.color.white)
        waitForAgentInitialize()
        updateNotificationBadge()
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun updateToolbarAndBackground(toolbarColor: Int, backgroundColor: Int) {
        binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, toolbarColor))
    }

    private suspend fun receivePresentationProof(
        app: WalletApp,
        it: AgentEvents.ProofEventV2
    ) {
        val (message, proofRecord) = app.agent.proofServiceV2.createAck(it.record)
        val connection = app.agent.connectionRepository.getById(it.record.connectionId)
        app.agent.messageSender.send(OutboundMessage(message, connection))

        val presentationMessageJson = app.agent.didCommMessageRepository.getAgentMessage(
            proofRecord.id,
            PresentationMessageV2.type
        )

        val json = Json { ignoreUnknownKeys = true }
        Json.decodeFromString<JsonElement>(presentationMessageJson)

        val presentationMessage =
            MessageSerializer.decodeFromString(presentationMessageJson) as PresentationMessageV2

        showProofInfo(presentationMessage.anoncredsProof())
    }

    private suspend fun receivePresentationProof(
        app: WalletApp,
        it: AgentEvents.ProofEvent
    ) {
        val (message, proofRecord) = app.agent.proofService.createAck(it.record)
        val connection = app.agent.connectionRepository.getById(it.record.connectionId)
        app.agent.messageSender.send(OutboundMessage(message, connection))

        val presentationMessageJson = app.agent.didCommMessageRepository.getAgentMessage(
            proofRecord.id,
            PresentationMessageV2.type
        )

        val json = Json { ignoreUnknownKeys = true }
        Json.decodeFromString<JsonElement>(presentationMessageJson)
    }

    private fun extractRevealedAttributes(json: JSONObject): List<String> {
        val list = mutableListOf<String>()
        val requestedProof = json.optJSONObject("requested_proof") ?: return list
        val revealed = requestedProof.optJSONObject("revealed_attrs") ?: return list

        val keys = revealed.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val attr = revealed.optJSONObject(key) ?: continue
            list.add("$key: ${attr.optString("raw", "N/A")}")
        }
        return list
    }

    private fun extractCredentialIdentifiers(json: JSONObject): List<String> {
        val list = mutableListOf<String>()
        val identifiers = json.optJSONArray("identifiers") ?: return list

        for (i in 0 until identifiers.length()) {
            val obj = identifiers.optJSONObject(i) ?: continue

            list.add(
                """
                Schema ID: ${obj.optString("schema_id", "N/A")}
                CredDef ID: ${obj.optString("cred_def_id", "N/A")}
                RevReg ID: ${obj.optString("rev_reg_id", "N/A")}
                Timestamp: ${obj.optLong("timestamp", -1)}
                """.trimIndent()
            )
        }
        return list
    }

    private fun showProofInfo(presentationMessage: String) {
        val json = JSONObject(presentationMessage)
        val attributes = extractRevealedAttributes(json)
        val identifiers = extractCredentialIdentifiers(json)

        val msg = buildString {
            append("Presented attributes:\n")
            append(attributes.joinToString("\n"))
            append("\n\nCredentials used:\n")
            append(identifiers.joinToString("\n\n"))
        }

        showAlert(msg)
    }

    fun showAlert(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private val badgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "org.hyperledger.ariesproject.UPDATE_BADGE") {
                Log.d(TAG, "Updating badge via broadcast")
                updateNotificationBadge()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(badgeReceiver, IntentFilter("org.hyperledger.ariesproject.UPDATE_BADGE"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(badgeReceiver)
    }

    private fun runOnConfirm(message: String, action: () -> Unit) {
        runOnConfirm(message, action) {}
    }

    private fun waitForAgentInitialize() {
        val app = application as WalletApp
        val progress = ProgressDialog(this)
        progress.setTitle("Initializing agent...")
        progress.setCancelable(false)
        progress.show()

        val timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(ms: Long) {
                if (app.isAgentInitialized() && app.walletOpened) {
                    try {
                        Log.d(TAG, "Agent initialized")
                        progress.dismiss()
                        updateNotificationBadge()
                    } catch (e: Exception) {
                        Log.e(TAG, "Initialization error: ${e.message}", e)
                    }
                    cancel()
                }
            }

            override fun onFinish() {
                progress.dismiss()
                showAlert("Failed to initialize agent.")
            }
        }

        timer.start()
    }

    private fun declineCredential(id: String) {
        val app = application as WalletApp

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.credentials.declineOffer(
                    AcceptOfferOptions(
                        credentialRecordId = id,
                        autoAcceptCredential = AutoAcceptCredential.Never
                    )
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    showAlert("Failed to decline credential.")
                }
            }
        }
    }

    private fun declineProof(id: String) {
        val app = application as WalletApp

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.proofs.declineRequest(id)
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    showAlert("Failed to decline proof.")
                }
            }
        }
    }

    private fun getCredential(id: String) {
        val app = application as WalletApp
        val progress = ProgressDialog(this)
        progress.setTitle("Loading")
        progress.setCancelable(true)

        val job = lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.credentials.acceptOffer(
                    AcceptOfferOptions(
                        credentialRecordId = id,
                        autoAcceptCredential = AutoAcceptCredential.Always
                    )
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    progress.dismiss()
                    showAlert("Failed to receive credential.")
                }
            }
        }

        progress.setOnCancelListener { job.cancel() }
        progress.show()
        credentialProgress = progress
    }

    fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(
            this,
            listOf(
                MainMenu.GET,
                MainMenu.LIST,
                MainMenu.PROOF_LIST,
                MainMenu.HISTORICAL,
                MainMenu.CONNECTION,
                MainMenu.REQUESTPROOF,
                MainMenu.SCANREQUESTPROOF,
                MainMenu.RECEIVING_PRESENTATION_PROOF,
                MainMenu.PRESENTATION_LIST
            )
        )
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: WalletMainActivity,
        private val values: List<MainMenu>
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.MenuItemHolder>() {

        private val onClickListener = View.OnClickListener { v ->
            val menu = v.tag as MainMenu
            val context = v.context

            when (menu) {
                MainMenu.GET ->
                    (context as Activity).startActivityForResult(
                        Intent(context, BarcodeScannerActivity::class.java), 0
                    )

                MainMenu.LIST ->
                    context.startActivity(Intent(context, CredentialListActivity::class.java))

                MainMenu.PROOF_LIST ->
                    context.startActivity(Intent(context, ProofListActivity::class.java))

                MainMenu.HISTORICAL ->
                    context.startActivity(Intent(context, HistoricalListActivity::class.java))

                MainMenu.REQUESTPROOF ->
                    context.startActivity(Intent(context, RequestProofActivity::class.java))

                MainMenu.SCANREQUESTPROOF ->
                    context.startActivity(Intent(context, VerifierProofActivity::class.java))

                MainMenu.RECEIVING_PRESENTATION_PROOF ->
                    context.startActivity(Intent(context, ReceivingPresentationActivity::class.java))

                MainMenu.PRESENTATION_LIST ->
                    context.startActivity(Intent(context, PresentationListActivity::class.java))

                MainMenu.CONNECTION ->
                    context.startActivity(Intent(context, InvitationActivity::class.java))
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuItemHolder {
            val binding = MenuItemListContentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return MenuItemHolder(binding)
        }

        override fun onBindViewHolder(holder: MenuItemHolder, position: Int) {
            val item = values[position]
            holder.contentView.text = item.text

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class MenuItemHolder(val binding: MenuItemListContentBinding) :
            RecyclerView.ViewHolder(binding.root) {
            val contentView: TextView = binding.content
        }

        suspend fun getProofRecord(agent: Agent, threadId: String): ProofExchangeRecord {
            return agent.proofRepository.getByThreadAndConnectionId(threadId, null)
        }
    }
}