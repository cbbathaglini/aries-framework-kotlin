package org.hyperledger.ariesframework.agent

import ILedgerService
import RevocationNotificationService
import RevocationNotificationServiceV2
import android.content.Context
import askar_uniffi.AskarStoreManager
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.anoncreds.AnonCredsModuleConfig
import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.EthrAnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsModuleConfigOptions
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialDefinitionPrivateRepository
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialDefinitionRepository
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialRepository
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsKeyCorrectnessProofRepository
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsLinkSecretRepository
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsRevocationRegistryDefinitionPrivateRepository
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsRevocationRegistryDefinitionRepository
import org.hyperledger.ariesframework.anoncreds.service.AnonCredsRegistryService
import org.hyperledger.ariesframework.anoncreds.service.AnonCredsRsHolderService
import org.hyperledger.ariesframework.anoncreds.service.AnonCredsRsIssuerService
import org.hyperledger.ariesframework.anoncreds.service.AnoncredsService
import org.hyperledger.ariesframework.anoncreds.storage.CredentialDefinitionRepository
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRepository
import org.hyperledger.ariesframework.anoncreds.storage.RevocationRegistryRepository
import org.hyperledger.ariesframework.basicmessage.BasicMessageCommand
import org.hyperledger.ariesframework.connection.ConnectionCommand
import org.hyperledger.ariesframework.connection.ConnectionService
import org.hyperledger.ariesframework.connection.DidExchangeService
import org.hyperledger.ariesframework.connection.JwsService
import org.hyperledger.ariesframework.connection.PeerDIDService
import org.hyperledger.ariesframework.connection.repository.ConnectionRepository
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRepository
import org.hyperledger.ariesframework.credentials.v1.CredentialService
import org.hyperledger.ariesframework.credentials.v1.CredentialsCommand
import org.hyperledger.ariesframework.credentials.v2.CredentialServiceV2
import org.hyperledger.ariesframework.credentials.v2.CredentialsCommandV2
import org.hyperledger.ariesframework.history.repository.HistoryRepository
import org.hyperledger.ariesframework.ledger.ledgerBesu.LedgerBesuService
import org.hyperledger.ariesframework.ledger.ledgerIndy.LedgerIndyService
import org.hyperledger.ariesframework.oob.OutOfBandCommand
import org.hyperledger.ariesframework.oob.OutOfBandService
import org.hyperledger.ariesframework.oob.repository.OutOfBandRepository
import org.hyperledger.ariesframework.problemreports.ProblemReportsCommand
import org.hyperledger.ariesframework.proofs.RevocationService
import org.hyperledger.ariesframework.proofs.repository.ProofRepository
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRepository
import org.hyperledger.ariesframework.proofs.v1.ProofCommand
import org.hyperledger.ariesframework.proofs.v1.ProofService
import org.hyperledger.ariesframework.proofs.v2.ProofCommandV2
import org.hyperledger.ariesframework.proofs.v2.ProofServiceV2
import org.hyperledger.ariesframework.proofs.v2.verifier.AnonCredsRsVerifierService
import org.hyperledger.ariesframework.routing.MediationRecipient
import org.hyperledger.ariesframework.storage.DidCommMessageRepository
import org.hyperledger.ariesframework.vc.dataintegrity.W3cJsonLdCredentialService
import org.hyperledger.ariesframework.vc.modules.W3cCredentialsModuleConfig
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRepository
import org.hyperledger.ariesframework.vc.service.W3cCredentialService
import org.hyperledger.ariesframework.vc.service.W3cJwtCredentialService
import org.hyperledger.ariesframework.wallet.Wallet
import org.slf4j.LoggerFactory

class Agent(val context: Context, val agentConfig: AgentConfig) {
    private val logger = LoggerFactory.getLogger(LedgerBesuService::class.java)
    val wallet: Wallet = Wallet(this)
    val eventBus = EventBus()
    val dispatcher = Dispatcher(this)
    val messageReceiver = MessageReceiver(this)
    val messageSender = MessageSender(this)
    val connectionRepository = ConnectionRepository(this)
    val connectionService = ConnectionService(this)
    val didExchangeService = DidExchangeService(this)
    val peerDIDService = PeerDIDService(this)
    val jwsService = JwsService(this)
    val connections = ConnectionCommand(this, dispatcher)
    val mediationRecipient = MediationRecipient(this, dispatcher)
    val outOfBandRepository = OutOfBandRepository(this)
    val outOfBandService = OutOfBandService(this)
    val oob = OutOfBandCommand(this, dispatcher)
    val didCommMessageRepository = DidCommMessageRepository(this)
    val credentialExchangeRepository = CredentialExchangeRepository(this)
    val ledgerService: ILedgerService
        get() = _ledgerService ?: error("LedgerService was not initialized")
    private val _ledgerService: ILedgerService? = initializeLedgerService()
    val credentialDefinitionRepository = CredentialDefinitionRepository(this)
    val revocationRegistryRepository = RevocationRegistryRepository(this)
    val anoncredsService = AnoncredsService(this)
    val credentialService = CredentialService(this)
    val credentialServiceV2 = CredentialServiceV2(this)
    val credentials = CredentialsCommand(this, dispatcher)
    val credentialsV2 = CredentialsCommandV2(this, dispatcher)
    val credentialRepository = CredentialRepository(this)
    val historyRepository = HistoryRepository(this)
    val revocationService = RevocationService(this)
    val revocationNotificationService = RevocationNotificationService(this, dispatcher)
    val revocationNotificationServicev2 = RevocationNotificationServiceV2(this, dispatcher)
    val proofRepository = ProofRepository(this)
    val proofService = ProofService(this)
    val proofServiceV2 = ProofServiceV2(this)
    val anoncredsVerifierService = AnonCredsRsVerifierService(this)

    val anoncredsCredentialDefinitionRepository = AnonCredsCredentialDefinitionRepository(this)
    val anonCredsHolderService = AnonCredsRsHolderService(this)
    val anonCredsIssuerService = AnonCredsRsIssuerService(this)
    val anonCredsRegistryService = AnonCredsRegistryService(this)
    val anonCredsRevocationRegistryDefinitionPrivateRepository = AnonCredsRevocationRegistryDefinitionPrivateRepository(this)
    val anonCredsKeyCorrectnessProofRepository = AnonCredsKeyCorrectnessProofRepository(this)
    val anonCredsCredentialDefinitionPrivateRepository = AnonCredsCredentialDefinitionPrivateRepository(this)
    val anonCredsRevocationRegistryDefinitionRepository = AnonCredsRevocationRegistryDefinitionRepository(this)
    val anonCredsLinkSecretRepository = AnonCredsLinkSecretRepository(this)
    val anonCredsCredentialRepository = AnonCredsCredentialRepository(this)
    val anoncredsmodulesconfig = AnonCredsModuleConfig(
        agent = this,
        options = AnonCredsModuleConfigOptions(
            registries = listOf<AnonCredsRegistry>(EthrAnonCredsRegistry()),
        ),
    )
    val w3cCredentialsModuleConfig = W3cCredentialsModuleConfig()
    val w3cJsonLdCredentialService = W3cJsonLdCredentialService(this, w3cCredentialsModuleConfig, context)
    val w3cJwtCredentialService = W3cJwtCredentialService(this)
    val w3cCredentialRepository = W3cCredentialRepository(this)
    val w3cCredentialService = W3cCredentialService(w3cCredentialRepository, w3cJsonLdCredentialService, w3cJwtCredentialService)

    val proofs = ProofCommand(this, dispatcher)
    val proofCommandV2 = ProofCommandV2(this, dispatcher)
    val basicMessages = BasicMessageCommand(this, dispatcher)

    val problemReports = ProblemReportsCommand(this, dispatcher)
    val verifierRepository = VerifierRepository(this)

    private var _isInitialized = false

    private fun initializeLedgerService(): ILedgerService {
        return if (agentConfig.useBesuLedger && agentConfig.besuLedgerConfig != null) {
            LedgerBesuService(this, context)
        } else {
            LedgerIndyService(this)
        }
    }

    /**
     * Initialize the agent. This will create a wallet if necessary and open it.
     * It will also connect to the mediator if configured and connect to the ledger.
     */
    suspend fun initialize() {
        logger.info("Initializing o LedgerService")
        wallet.initialize()

        agentConfig.publicDidSeed?.let {
            wallet.initPublicDid(it)
        }

        if (agentConfig.useLedgerService) {
            ledgerService.initialize()
        } else if (agentConfig.useBesuLedger) {
            ledgerService.initialize()
        }

        if (agentConfig.mediatorConnectionsInvite != null) {
            mediationRecipient.initialize(agentConfig.mediatorConnectionsInvite!!)
        } else {
            setInitialized()
        }
    }

    fun setInitialized() {
        _isInitialized = true
    }

    /**
     * Whether the agent is initialized. Agent should make new connections after it is initialized.
     */
    fun isInitialized(): Boolean {
        return _isInitialized
    }

    /**
     * Shutdown the agent. This will close the wallet, disconnect from the ledger, disconnect from the mediator and close open websockets.
     */
    suspend fun shutdown() {
        mediationRecipient.close()
        messageSender.close()
        wallet.close()
        _isInitialized = false
    }

    /**
     * Remove the wallet and ledger data. This makes the agent as if it was never initialized.
     */
//    suspend fun reset() {
//        if (isInitialized()) {
//            shutdown()
//        }
//        wallet.delete()
//    }

    /**
     * Remove the wallet and ledger data. This makes the agent as if it was never initialized.
     */
    suspend fun reset() {
        runCatching { mediationRecipient.close() }
        runCatching { messageSender.close() }
        runCatching { wallet.close() }

        //double check
        runCatching { credentialExchangeRepository.deleteAll() }
        runCatching { w3cCredentialRepository.deleteAll() }
        runCatching { proofRepository.deleteAll() }
        runCatching { connectionRepository.deleteAll() }
        runCatching { connectionRepository.deleteAll() }
        runCatching { credentialDefinitionRepository.deleteAll() }
        runCatching { revocationRegistryRepository.deleteAll() }
        runCatching { credentialRepository.deleteAll() }
        runCatching { historyRepository.deleteAll() }
        runCatching { anonCredsCredentialDefinitionPrivateRepository.deleteAll() }
        runCatching { anonCredsLinkSecretRepository.deleteAll() }
        runCatching { anonCredsKeyCorrectnessProofRepository.deleteAll() }
        runCatching { anonCredsRevocationRegistryDefinitionPrivateRepository.deleteAll() }
        runCatching { anoncredsCredentialDefinitionRepository.deleteAll() }
        runCatching { verifierRepository.deleteAll() }
        runCatching { didCommMessageRepository.deleteAll() }
        runCatching { outOfBandRepository.deleteAll() }

        _isInitialized = false
        wallet.delete()
    }

    suspend fun receiveMessage(encryptedMessage: EncryptedMessage) {
        messageReceiver.receiveMessage(encryptedMessage)
    }

    /**
     * Set the outbound transport for the agent. This will override the default http/websocket transport.
     * It is useful for testing.
     */
    fun setOutboundTransport(outboundTransport: OutboundTransport) {
        messageSender.setOutboundTransport(outboundTransport)
    }

    override fun toString(): String {
        return "Agent(context=$context, agentConfig=$agentConfig, wallet=$wallet, eventBus=$eventBus, dispatcher=$dispatcher, messageReceiver=$messageReceiver, messageSender=$messageSender, connectionRepository=$connectionRepository, connectionService=$connectionService, didExchangeService=$didExchangeService, peerDIDService=$peerDIDService, jwsService=$jwsService, connections=$connections, mediationRecipient=$mediationRecipient, outOfBandRepository=$outOfBandRepository, outOfBandService=$outOfBandService, oob=$oob, didCommMessageRepository=$didCommMessageRepository, credentialExchangeRepository=$credentialExchangeRepository, ledgerService=$ledgerService, credentialDefinitionRepository=$credentialDefinitionRepository, revocationRegistryRepository=$revocationRegistryRepository, anoncredsService=$anoncredsService, credentialService=$credentialService, credentialServiceV2=$credentialServiceV2, credentials=$credentials, credentialsV2=$credentialsV2, credentialRepository=$credentialRepository, revocationService=$revocationService, revocationNotificationService=$revocationNotificationService, revocationNotificationServicev2=$revocationNotificationServicev2, proofRepository=$proofRepository, proofService=$proofService, proofs=$proofs, basicMessages=$basicMessages, problemReports=$problemReports, _isInitialized=$_isInitialized)"
    }

    companion object {
        /**
         * Generate a key to encrypt the wallet.
         */
        fun generateWalletKey(): String {
            return AskarStoreManager().generateRawStoreKey(null)
        }
    }
}
