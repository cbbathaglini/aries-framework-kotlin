package org.hyperledger.ariesproject

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.BesuLedgerConfig
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesproject.notifications.NotificationHandler
import java.io.File

const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
const val genesisPath = "von.txn"

class WalletApp : Application() {
    lateinit var agent: Agent
    lateinit var notificationHandler: NotificationHandler
    var walletOpened: Boolean = false

    fun isAgentInitialized(): Boolean {
        return this::agent.isInitialized
    }

    private fun copyResourceFile(resource: String) {
        val inputStream = applicationContext.assets.open(resource)
        val file = File(applicationContext.filesDir.absolutePath, resource)
        file.outputStream().use { inputStream.copyTo(it) }
    }

    private suspend fun openWallet() {
        val pref = applicationContext.getSharedPreferences(PREFERENCE_NAME, 0)
        var key = pref.getString("walletKey", null)

        if (key == null) {
            key = Agent.generateWalletKey()
            pref.edit().putString("walletKey", key).apply()
        }
        copyResourceFile(genesisPath)
        val properties = ConfigLoader.loadProperties(this)
        val invitationUrl = properties.getProperty("invitation_url")

        val androidId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // 2) Monte o label, por ex. "SimpleApp-<ANDROID_ID>"
        val agentLabel = "SimpleApp-1X$androidId"

        val chainId= properties.getProperty("besu_chainId").toULong()
        val nodeAddress= properties.getProperty("besu_nodeAddress")

        val besuLedgerContig = BesuLedgerConfig(
            chainId= chainId,
            nodeAddress = nodeAddress
        )


        val config = AgentConfig(
            walletKey = key,
            genesisPath = File(applicationContext.filesDir.absolutePath, genesisPath).absolutePath,
            mediatorConnectionsInvite = invitationUrl,
            mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
            label = agentLabel,
            autoAcceptCredential = AutoAcceptCredential.Never,
            autoAcceptProof = AutoAcceptProof.Never,
            useLedgerService = false, // indy
            useBesuLedger =  true, //besu
            besuLedgerConfig = besuLedgerContig,
        )
        agent = Agent(applicationContext, config)
        agent.initialize()

//        kotlinx.coroutines.delay(2000)
//        subscribeAgentEvents()


        walletOpened = true

        Log.d("demo", "Agent initialized")
    }

//    override fun onCreate() {
//        super.onCreate()
//        notificationHandler = NotificationHandler.getInstance(this)
//        GlobalScope.launch(Dispatchers.IO) {
//            openWallet()
//            agent.eventBus.subscribe<AgentEvents.AgentReadyEvent> {
//                subscribeAgentEvents()
//                walletOpened = true
//                Log.d("WalletApp", "✅ AgentReadyEvent recebido — assinaturas ativas")
//            }
//            agent.eventBus.publish(AgentEvents.AgentReadyEvent())
//            Log.d("WalletApp", "🚀 AgentReadyEvent publicado")
//        }
//
//    }

    override fun onCreate() {
        super.onCreate()
        notificationHandler = NotificationHandler.getInstance(this)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // 1️⃣ Inicializa o agente
                openWallet()

                // 2️⃣ Depois que o agente estiver pronto, registre os listeners
                subscribeAgentEvents()
                walletOpened = true
                Log.d("WalletApp", "✅ Agent inicializado e listeners registrados")

            } catch (e: Exception) {
                Log.e("WalletApp", "Erro ao inicializar o agente: ${e.message}", e)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    fun subscribeAgentEvents() {
        val agent = this.agent
        val handler = this.notificationHandler

        agent.eventBus.subscribe<AgentEvents.CredentialEventV2> {
            if (it.record.state == CredentialState.OfferReceived) {
                handler.addNotification(
                    title = "Nova oferta de credencial (2.0)",
                    message = "ConnectionID: ${it.record.id}",
                    type = NotificationType.ISSUE_CREDENTIAL_V2,
                    credentialId = it.record.id
                )
                notifyBadgeUpdate()
            } else if (it.record.state == CredentialState.Done) {
                handler.addNotification(
                    title = "Credencial 2.0 recebida",
                    message = "A credencial ${it.record.id} foi emitida com sucesso.",
                    type = NotificationType.ISSUED_CREDENTIAL_DETAIL_V2,
                    credentialId = it.record.id
                )
                notifyBadgeUpdate()
            }
        }

        agent.eventBus.subscribe<AgentEvents.ProofEventV2> {
            if (it.record.state == ProofState.RequestReceived) {
                handler.addNotification(
                    title = "Nova solicitação de prova (2.0)",
                    message = "ConnectionID: ${it.record.id}",
                    type = NotificationType.PROOF_REQUEST_V2,
                    proofRecordId = it.record.id
                )
                notifyBadgeUpdate()
            } else if (it.record.state == ProofState.PresentationSent) {
                handler.addNotification(
                    title = "Proof sent",
                    message = "Proof ID ${it.record.id} was sent",
                    type = NotificationType.OTHER
                )
                notifyBadgeUpdate()
            }else if (it.record.state == ProofState.Done) {
                handler.addNotification(
                    title = "Prova concluída",
                    message = "Prova ${it.record.id} foi apresentada com sucesso.",
                    type = NotificationType.PRESENTATION_PROOF_V2
                )
                notifyBadgeUpdate()
            }
        }

        agent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> {
            handler.addNotification(
                title = "Revogação recebida",
                message = "Uma credencial foi revogada.",
                type = NotificationType.OTHER
            )
            notifyBadgeUpdate()
        }

        //updateNotificationBadge()
    }

    private fun notifyBadgeUpdate() {
        val intent = Intent("org.hyperledger.ariesproject.UPDATE_BADGE")
        sendBroadcast(intent)
    }
}
