package org.hyperledger.ariesproject

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.BesuLedgerConfig
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.cache.CacheOperations
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.v2.models.DeclineCredentialOfferOptions
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.util.Session
import org.hyperledger.ariesproject.notifications.NotificationHandler
import java.io.File

const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
const val genesisPath = "von.txn"

class WalletApp : Application() {
    lateinit var agent: Agent
    lateinit var notificationHandler: NotificationHandler
    var walletOpened: Boolean = false

    private val appJob = SupervisorJob()
    val appScope = kotlinx.coroutines.CoroutineScope(appJob + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        Session.startNewSession()
        notificationHandler = NotificationHandler.getInstance(this)

        appScope.launch {
            try {
                prepareForWalletEntry()
            } catch (e: Exception) {
                Log.e("WalletApp", "Fatal error during startup", e)
                walletOpened = false
            }
        }
    }

    suspend fun prepareForWalletEntry() {
        val prefs = getSharedPreferences("wallet_prefs", MODE_PRIVATE)
        val resetPending = prefs.getBoolean("RESET_PENDING", false)

        if (resetPending) {
            prefs.edit().putBoolean("RESET_PENDING", false).apply()
            Log.i("WalletApp", "RESET_PENDING detected → resetting agent")

            walletOpened = false

            runCatching {
                if (::agent.isInitialized) {
                    agent.reset()
                } else {
                    val tempAgent = Agent(applicationContext, createAgentConfig())
                    tempAgent.reset()
                }
            }.onFailure {
                Log.e("WalletApp", "Reset failed", it)
                throw it
            }
        }

        if (!::agent.isInitialized || !agent.isInitialized() || !walletOpened) {
            openWallet()
            subscribeAgentEvents()
            walletOpened = true
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appJob.cancel()
    }

    fun isAgentInitialized(): Boolean {
        return this::agent.isInitialized && agent.isInitialized()
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

        val agentLabel = "SimpleApp-1X_$androidId"
        // Log.e("app---------------", agentLabel)

        val besuLedgerConfig = BesuLedgerConfig(
            configFile = "besu_config.json",
            multiledger = true
        )

        val config = AgentConfig(
            walletKey = key,
            genesisPath = File(applicationContext.filesDir.absolutePath, genesisPath).absolutePath,
            mediatorConnectionsInvite = invitationUrl,
            mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
            label = agentLabel,
            autoAcceptCredential = AutoAcceptCredential.Never,
            autoAcceptProof = AutoAcceptProof.Never,
            useLedgerService = false,
            useBesuLedger = false,
            useDidWebvh = true,
            besuLedgerConfig = besuLedgerConfig,
        )

        agent = Agent(applicationContext, config)
        agent.initialize()
        walletOpened = true

        //updateCache(agent)

        Log.d("demo", "Agent initialized")
    }
    suspend fun updateCache(agent: Agent) {
        CacheOperations().updateCache(agent)
    }

    fun clearAllNotifications() {
        runCatching { notificationHandler.clearAll() }
        notifyBadgeUpdate()
    }


    private fun createAgentConfig(): AgentConfig {
        val pref = getSharedPreferences(PREFERENCE_NAME, 0)
        var key = pref.getString("walletKey", null)

        if (key == null) {
            key = Agent.generateWalletKey()
            pref.edit().putString("walletKey", key).apply()
        }

        copyResourceFile(genesisPath)

        val properties = ConfigLoader.loadProperties(this)
        val invitationUrl = properties.getProperty("invitation_url")

        val androidId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val besuLedgerConfig = BesuLedgerConfig(
            configFile = "besu_config.json",
            multiledger = true
        )

        return AgentConfig(
            walletKey = key,
            genesisPath = File(filesDir, genesisPath).absolutePath,
            mediatorConnectionsInvite = invitationUrl,
            mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
            label = "SimpleApp-1X_$androidId",
            autoAcceptCredential = AutoAcceptCredential.Never,
            autoAcceptProof = AutoAcceptProof.Never,
            useLedgerService = false,
            useBesuLedger = true,
            besuLedgerConfig = besuLedgerConfig,
        )
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
                    title = "New credential offer (2.0)",
                    message = "Credential ID: ${it.record.id}",
                    type = NotificationType.ISSUE_CREDENTIAL_V2,
                    credentialId = it.record.id
                )
                notifyBadgeUpdate()
            } else if (it.record.state == CredentialState.Done) {
                handler.addNotification(
                    title = "Credential 2.0 received",
                    message = "Credential ID: ${it.record.id}",
                    type = NotificationType.ISSUED_CREDENTIAL_DETAIL_V2,
                    credentialId = it.record.id
                )
                notifyBadgeUpdate()
            }
        }

        agent.eventBus.subscribe<AgentEvents.ProofEventV2> {
            if (it.record.state == ProofState.RequestReceived) {
                handler.addNotification(
                    title = "New proof request 2.0",
                    message = "Proof ID: ${it.record.id}",
                    type = NotificationType.ACCEPT_PROOF_REQUEST_V2,
                    proofRecordId = it.record.id
                )
                notifyBadgeUpdate()

            } else if (it.record.state == ProofState.PresentationSent) {
                handler.addNotification(
                    title = "Proof sent",
                    message = "Proof ID: ${it.record.id}",
                    type = NotificationType.PRESENTATION_PROOF_V2,
                    proofRecordId = it.record.id
                )
                notifyBadgeUpdate()

            } else if (it.record.state == ProofState.PresentationReceived) {
                receivePresentation(record = it.record)
                handler.addNotification(
                    title = "Proof received",
                    message = "Proof ID: ${it.record.id} | Verified? ${it.record.isVerified ?: false}",
                    proofRecordId = it.record.id
                )
                notifyBadgeUpdate()

            } else if (it.record.state == ProofState.Done) {
                handler.addNotification(
                    title = "Proof done",
                    message = "Proof ID: ${it.record.id}",
                    type = NotificationType.PROOF_REQUEST_V2,
                    proofRecordId = it.record.id
                )
                notifyBadgeUpdate()
            }
        }

        agent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> {
            handler.addNotification(
                title = "Revocation received",
                message = "The credential (${it.record.id}) was revoked.",
                type = NotificationType.OTHER
            )
            notifyBadgeUpdate()
        }
    }

    fun declineCredentialV2(id: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val decline = DeclineCredentialOfferOptions(
                    sendProblemReport = true
                )
                agent.credentialsV2.declineOffer(
                    credentialRecordId = id,
                    options = decline,
                )
            } catch (e: Exception) {
                // Log.d("demo", e.localizedMessage)
            }
        }
    }

    fun receivePresentation(record: ProofExchangeRecord) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                var mutableRecord = record
                val (message, updatedRecord) = agent.proofServiceV2.createAck(mutableRecord)
                val connection = agent.connectionRepository.getById(updatedRecord.connectionId)
                agent.messageSender.send(OutboundMessage(message, connection))
                // Log.d("WalletApp", "ACK sent for presentation ${updatedRecord.threadId}")
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    notificationHandler.addNotification(
                        title = "Error receiving presentation",
                        message = e.localizedMessage ?: "Unknown error"
                    )
                    // Log.e("WalletApp", "Error processing presentation: ${e.localizedMessage}", e)
                }
            }
        }
    }

    private fun declineProofV2(id: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                agent.proofCommandV2.declineRequest(id)
            } catch (e: Exception) {
                // Log.d("demo", e.localizedMessage)
            }
        }
    }

    private fun notifyBadgeUpdate() {
        val intent = Intent("org.hyperledger.ariesproject.UPDATE_BADGE").apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}