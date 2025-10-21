package org.hyperledger.ariesproject

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRepository
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

        // 🏠 Abre fragmento inicial
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

    private fun subscribeEvents() {
        val app = application as WalletApp
        app.agent.eventBus.subscribe<AgentEvents.CredentialEvent> {
            lifecycleScope.launch(Dispatchers.Main) {

                if (it.record.state == CredentialState.OfferReceived) {
                    runOnConfirm("Accept credential?", action = {
                        getCredential(it.record.id)

                    }, negAction = {
                        declineCredential(it.record.id)
                    })
                } else if (it.record.state == CredentialState.Done) {
                    credentialProgress?.dismiss()
                    showAlert("Credential received")

                }
            }
        }

        /* CredentialEvent for version 2.0 */
        app.agent.eventBus.subscribe<AgentEvents.CredentialEventV2> {
            lifecycleScope.launch(Dispatchers.Main) {
                val handler = (application as WalletApp).notificationHandler

                if (it.record.state == CredentialState.OfferReceived) {
                    // 🟢 Adiciona notificação antes de atualizar o badge
                    handler.addNotification(
                        title = "Nova oferta de credencial (2.0)",
                        message = "ConnectionID: ${it.record.id}",
                        type = NotificationType.ISSUE_CREDENTIAL_V2,
                        credentialId = it.record.id
                    )

                    updateNotificationBadge()

                } else if (it.record.state == CredentialState.Done) {
                    showAlert("credential done")
                    handler.addNotification(
                        title = "Credencial 2.0 recebida",
                        message = "A credencial ${it.record.id} foi emitida com sucesso.",
                        type = NotificationType.ISSUED_CREDENTIAL_DETAIL_V2
                    )

                    updateNotificationBadge()
                }
            }
        }

        app.agent.eventBus.subscribe<AgentEvents.ProofEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it.record.state == ProofState.RequestReceived) {
                    runOnConfirm("Accept proof request?", action = {
                        sendProof( it.record.id, ProofConstants.PROTOCOL_VERSION_V1)
                    }, negAction = {
                        declineProof(it.record.id)
                    })
                } else if (it.record.state == ProofState.Done) {
                    proofProgress?.dismiss()
                    showAlert("Proof done")
                } else if (it.record.state == ProofState.PresentationReceived) {
                    receivePresentationProof(app, it)
                }
            }
        }

        app.agent.eventBus.subscribe<AgentEvents.ProofEventV2> {
            lifecycleScope.launch(Dispatchers.Main) {

                Log.i("proofrecord>>>>>>:", "itrecordid: " + it.record)
//                if (it.record.state == ProofState.RequestReceived) { //1
//                    runOnConfirm("Accept proof request?", action = {
//
//                        Log.i("proofrecord:", "itrecordid: " + it.record)
//
//                        sendProof(it.record.id, ProofConstants.PROTOCOL_VERSION_V2)
//                    }, negAction = {
//                        declineProofV2(it.record.id)
//                    })
//                } else if (it.record.state == ProofState.Done) {//3
//                    proofProgress?.dismiss()
//                    showAlert("Proof done")
//                } else if (it.record.state == ProofState.PresentationReceived) {//2
//                    receivePresentationProof(app, it)
//                }else{
//                    showAlert("message: ${it.record.state}")
//                }
            }
        }

        //show an alert on revocation of credential - revocation notification - 1.0
        app.agent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                showAlert("Credential revoked (1.0): ${it.record.id}")
            }
        }

        //show an alert on revocation of credential - revocation notification - 2.0
        app.agent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> {
            lifecycleScope.launch(Dispatchers.Main) {
                showAlert("Credential revoked (2.0): ${it.record.id}")
            }
        }

        // Show an alert on basic message, this is useful for debugging.
        app.agent.eventBus.subscribe<AgentEvents.BasicMessageEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                showAlert("Basic Message: ${it.record}")
            }
        }

        // Show an alert on credential problem report
        app.agent.eventBus.subscribe<AgentEvents.ProblemReportEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                // Check if message type is a CredentialProblemReport
                if (it.message is CredentialProblemReportMessage) {
                    showAlert("Issuer reported a problem while issuing the credential - ${it.message.description.en}")
                }
                if (it.message is PresentationProblemReportMessage) {
                    showAlert("Verifier reported a problem while verifying the presentation - ${it.message.description.en}")
                }
                if (it.message is MediationProblemReportMessage) {
                    showAlert("Mediator reported a problem - ${it.message.description.en}")
                }
            }
        }
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
        val json = Json { ignoreUnknownKeys = true } // Permite ignorar campos extras
        // Primeiro, parseia como JsonElement
        val element = json.decodeFromString<JsonElement>(presentationMessageJson)
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        val presentationMessage = MessageSerializer.decodeFromString(presentationMessageJson) as PresentationMessageV2
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
        val json = Json { ignoreUnknownKeys = true } // Permite ignorar campos extras

        // Primeiro, parseia como JsonElement
        val element = json.decodeFromString<JsonElement>(presentationMessageJson)

        // Pega o campo "type"
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        //app.agent.didCommMessageRepository.getAgentMessage
        /*val presentationMessageJson = app.agent.didCommMessageRepository.getAgentMessage(
            proofRecord.id,
            PresentationMessage.type
        )*/

//        if (type == "https://didcomm.org/present-proof/1.0/presentation") {
//            val presentationMessage = MessageSerializer.decodeFromString(presentationMessageJson) as PresentationMessage
//            showProofInfo(presentationMessage.indyProof())
//        } else {
//            val presentationMessage = MessageSerializer.decodeFromString(presentationMessageJson) as PresentationMessageV2
//            showProofInfo(presentationMessage.indyProof())
//        }

    }


    private fun extractRevealedAttributes(json: JSONObject): List<String> {
        val attrList = mutableListOf<String>()
        val requestedProof = json.optJSONObject("requested_proof") ?: return attrList
        val revealedAttrs = requestedProof.optJSONObject("revealed_attrs") ?: return attrList

        val keys = revealedAttrs.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val attrObj = revealedAttrs.optJSONObject(key) ?: continue
            val rawValue = attrObj.optString("raw", "N/A")
            attrList.add("$key: $rawValue")
        }
        return attrList
    }

    private fun extractCredentialIdentifiers(json: JSONObject): List<String> {
        val idList = mutableListOf<String>()
        val identifiers = json.optJSONArray("identifiers") ?: return idList

        for (i in 0 until identifiers.length()) {
            val identifierObj = identifiers.optJSONObject(i) ?: continue
            val schemaId = identifierObj.optString("schema_id", "N/A")
            val credDefId = identifierObj.optString("cred_def_id", "N/A")
            val revRegId = identifierObj.optString("rev_reg_id", "N/A")
            val timestamp = identifierObj.optLong("timestamp", -1)

            idList.add(
                """
            Schema ID: $schemaId
            CredDef ID: $credDefId
            RevReg ID: $revRegId
            Timestamp: $timestamp
            """.trimIndent()
            )
        }
        return idList
    }

    private fun showProofInfo(presentationMessage: String) {
        val json = JSONObject(presentationMessage)

        val attrList = extractRevealedAttributes(json)
        val idList = extractCredentialIdentifiers(json)

        val messageToShow = buildString {
            append("Atributos apresentados:\n")
            append(attrList.joinToString("\n"))
            append("\n\nCredenciais usadas:\n")
            append(idList.joinToString("\n\n"))
        }

        showAlert(messageToShow)
    }

    fun showAlert(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

//    private fun runOnConfirm(message: String, action: () -> Unit, negAction: () -> Unit) {
//        val builder = AlertDialog.Builder(this@WalletMainActivity)
//        builder.setMessage(message)
//            .setPositiveButton(R.string.ok) { _, _ ->
//                action()
//            }
//            .setNegativeButton(R.string.cancel) { _, _ ->
//                negAction()
//            }
//        builder.create().show()
//    }

    private fun runOnConfirm(message: String, action: () -> Unit) {
        runOnConfirm(message, action) {}
    }

    private fun waitForAgentInitialize() {
        val app = application as WalletApp
        val progress = ProgressDialog(this)
        progress.setTitle("Inicializando agente...")
        progress.setCancelable(false)
        progress.show()

        val timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (app.walletOpened) {
                    subscribeEvents()
                    try {
                        progress.dismiss()
                    } catch (e: Exception) {
                        Log.d(TAG, e.message ?: "Erro desconhecido")
                    }
                    cancel()
                }
            }

            override fun onFinish() {
                progress.dismiss()
                showAlert("Falha ao inicializar o agente.")
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
                        autoAcceptCredential = AutoAcceptCredential.Never,
                    ),
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    showAlert("Failed to decaline a credential.")
                }
            }
        }
    }


    /* v2.0 */
    fun declineCredentialV2(id: String) {
        val app = application as WalletApp

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val decline = DeclineCredentialOfferOptions(
                    sendProblemReport = true
                )
                app.agent.credentialsV2.declineOffer(
                    credentialRecordId = id,
                    options = decline,
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    showAlert("Failed to decline a credential.")
                }
            }
        }
    }

    private fun declineProofV2(id: String) {
        val app = application as WalletApp

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.proofCommandV2.declineRequest(id)
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    showAlert("Failed to decline a proof (v2).")
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
                    Log.d("demo", e.localizedMessage)
                    showAlert("Failed to decline a proof.")
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
                    ),
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("demo", e.localizedMessage)
                    progress.dismiss()
                    showAlert("Failed to receive a credential.")
                }
            }
        }

        progress.setOnCancelListener {
            job.cancel()
        }
        progress.show()
        credentialProgress = progress
    }

//    public fun getCredentialV2(credentialExchangeRecord: CredentialExchangeRecord) {
//        Log.i("CV2", "HERE")
//        val app = application as WalletApp
//        val progress = ProgressDialog(this)
//        progress.setTitle("Loading")
//        progress.setCancelable(true)
//
//        val job = lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                val connectionRecordList = app.agent.connectionRepository.getAll()
//                Log.i("connectionRecordList", connectionRecordList.toString())
//
//                val connectionRecord =
//                    app.agent.connectionRepository.getById(credentialExchangeRecord.connectionId!!)
//                Log.i("IDD", connectionRecord.toString())
//                app.agent.credentialsV2.acceptOffer(
//                    AcceptCredentialOfferOptionsV2(
//                        credentialExchangeRecord = credentialExchangeRecord,
//                        credentialFormats = credentialExchangeRecord.formats,
//                        autoAcceptCredential = AutoAcceptCredential.Always,
//                    )
//                )
//            } catch (e: Exception) {
//                lifecycleScope.launch(Dispatchers.Main) {
//                    Log.d("demo", e.localizedMessage)
//                    progress.dismiss()
//                    showAlert("Failed to receive a credential.")
//                }
//            }
//        }
//
//        progress.setOnCancelListener {
//            job.cancel()
//        }
//        progress.show()
//
//
//        credentialProgress = progress
//    }

    private fun sendProof( id: String, version: String) {

        try {
            val app = application as WalletApp
            val progress = ProgressDialog(this)
            progress.setTitle("Sending proof")
            progress.setCancelable(true)

            val job = lifecycleScope.launch(Dispatchers.IO) {
                try {

                    if (ProofConstants.PROTOCOL_VERSION_V1.equals(version)) {
                        val retrievedCredentials =
                            app.agent.proofs.getRequestedCredentialsForProofRequest(id)
                        val requestedCredentials: RequestedCredentials =
                            app.agent.proofService.autoSelectCredentialsForProofRequest(
                                retrievedCredentials
                            )
                        app.agent.proofs.acceptRequest(id, requestedCredentials)
                    } else {
                        app.agent.proofCommandV2.acceptRequest(id)
                    }

                } catch (e: Exception) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Log.i("demo proof", e.localizedMessage)
                        progress.dismiss()
                        showAlert("Failed to present proof.")
                    }
                }
            }

            progress.setOnCancelListener {
                job.cancel()
            }
            progress.show()
            proofProgress = progress
        }catch (e: Exception){
            showAlert("Error in proof: ${e.message} | ${e.localizedMessage}")
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val app = application as WalletApp
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    val qrcodeData = data!!.getStringExtra("qrcode")
                    Log.d("demo", "Scanned code: $qrcodeData")

                    val (_, connection) = app.agent.oob.receiveInvitationFromUrl(qrcodeData!!)

                    val handler = (application as WalletApp).notificationHandler
                    handler.addNotification(
                        title = "Nova Conexão",
                        message = "Conectado com ${connection?.theirLabel ?: "Emissor desconhecido"}",
                        type = NotificationType.CONNECTION
                    )

                    //showAlert("Conectado com ${connection?.theirLabel ?: "Agente desconhecido"}")

                } catch (e: Exception) {
                    Log.e("demo", "Erro ao processar QRCode: ${e.localizedMessage}")
                    showAlert("QRCode inválido ou falha na conexão.")
                }
            }
        }
    }

    fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(
            this,
            listOf(MainMenu.GET, MainMenu.LIST, MainMenu.HISTORICAL, MainMenu.CONNECTION, MainMenu.REQUESTPROOF,
                MainMenu.SCANREQUESTPROOF, MainMenu.RECEIVING_PRESENTATION_PROOF, MainMenu.PRESENTATION_LIST)
        )
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: WalletMainActivity,
        private val values: List<MainMenu>,
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.MenuItemHolder>() {

        private val onClickListener: View.OnClickListener = View.OnClickListener { v ->
            val menu = v.tag as MainMenu
            when (menu) {
                MainMenu.GET -> {
                    val intent = Intent(v.context, BarcodeScannerActivity::class.java)
                    (v.context as Activity).startActivityForResult(intent, 0)
                }

                MainMenu.LIST -> {
                    val intent = Intent(v.context, CredentialListActivity::class.java)
                    v.context.startActivity(intent)
                }

                MainMenu.HISTORICAL -> {
                    val intent = Intent(v.context, HistoricalListActivity::class.java)
                    v.context.startActivity(intent)
                }

                MainMenu.REQUESTPROOF -> {
                    val intent = Intent(v.context, RequestProofActivity::class.java)
                    v.context.startActivity(intent)
                }

                MainMenu.SCANREQUESTPROOF -> {
                    val intent = Intent(v.context, VerifierProofActivity::class.java)
                    v.context.startActivity(intent)
                }

                MainMenu.RECEIVING_PRESENTATION_PROOF -> {
                    val intent = Intent(v.context, ReceivingPresentationActivity::class.java)
                    v.context.startActivity(intent)
                }

                MainMenu.PRESENTATION_LIST -> {
                    val intent = Intent(v.context, PresentationListActivity::class.java)
                    v.context.startActivity(intent)
                }

                MainMenu.CONNECTION -> {
                    val intent = Intent(v.context, InvitationActivity::class.java)
                    v.context.startActivity(intent)
                }
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
