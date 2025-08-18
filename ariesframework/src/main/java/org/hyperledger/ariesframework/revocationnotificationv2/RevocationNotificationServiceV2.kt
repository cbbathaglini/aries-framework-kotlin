import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.CredentialRole
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.history.models.HistoryType
import org.hyperledger.ariesframework.history.repository.HistoryRecord
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification
import org.hyperledger.ariesframework.revocationnotificationv2.RevocationNotificationConstants
import org.hyperledger.ariesframework.revocationnotificationv2.handler.RevocationNotificationHandlerV2
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.hyperledger.ariesframework.revocationnotificationv2.model.RevocationNotificationMessageV2Options
import org.hyperledger.ariesframework.util.RevocationIdentifier
import org.slf4j.LoggerFactory
import java.util.UUID

class RevocationNotificationServiceV2(val agent: Agent, val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(RevocationNotificationServiceV2::class.java)

    private val credentialRepository = agent.credentialExchangeRepository

    init {
        registerMessageHandlers(dispatcher)
        registerMessages()
    }

    fun createRevocationNotification(options: RevocationNotificationMessageV2Options): Map<String, RevocationNotificationMessageV2> {
        val (credentialId, revocationFormat, comment) = options

        val message = RevocationNotificationMessageV2(
            credentialId = credentialId,
            revocationFormat = revocationFormat,
            comment = comment,
        )

        if (!message.pleaseAckIsEmpty()) {
            message.setPleaseAck()
        }

        return mapOf("message" to message)
    }

    suspend fun processRevocationNotification(messageContext: InboundMessageContext) {
        logger.info("Processing revocation notification v2")

        val revocationMessage = messageContext.message as? RevocationNotificationMessageV2
            ?: throw CredoError("Invalid message type: Expected RevocationNotificationMessageV2")
        val credentialId = revocationMessage.credentialId

        if (revocationMessage.revocationFormat !in listOf(
                RevocationIdentifier.v2IndyRevocationFormat,
                RevocationIdentifier.v2AnonCredsRevocationFormat,
            )
        ) {
            throw CredoError(
                "Unknown revocation format: ${revocationMessage.revocationFormat}. Supported formats are indy-anoncreds and anoncreds",
            )
        }


        val credentialIdGroups =
            RevocationIdentifier.v2IndyRevocationIdentifierRegex.find(credentialId)?.groupValues
                ?: RevocationIdentifier.v2AnonCredsRevocationIdentifierRegex.find(credentialId)?.groupValues

        val anoncredsType : Boolean = RevocationIdentifier.v2AnonCredsRevocationIdentifierRegex.containsMatchIn(credentialId) ?: false;

        if (credentialIdGroups == null || credentialIdGroups.size < 2) {
            throw CredoError(
                "Incorrect revocation notification credentialId format: \n$credentialId\ndoes not match\n" +
                    "\"<revocation_registry_id>::<credential_revocation_id>\"",
            )
        }

        val anonCredsRevocationRegistryId = credentialIdGroups[1]
        val anonCredsCredentialRevocationId = credentialIdGroups[2]
        val comment = revocationMessage.comment
        val connection = messageContext.assertReadyConnection()

        val threadId = revocationMessage.getThreadId(anonCredsRevocationRegistryId, anonCredsCredentialRevocationId)

        processRevocationNotification(
            revocationRegistryId = anonCredsRevocationRegistryId,
            credentialRevocationId = anonCredsCredentialRevocationId,
            connection = connection,
            comment = comment,
            anoncredsType = anoncredsType
        )
    }

    private suspend fun processRevocationNotification(
        revocationRegistryId: String,
        credentialRevocationId: String,
        connection: ConnectionRecord,
        comment: String? = null,
        threadId: String? = null,
        anoncredsType: Boolean = false
    ) {
        lateinit var credentialRecord: CredentialExchangeRecord
        var error = false
        try {
            credentialRecord =
                    credentialRepository.getByCredentialRevocationIdAndRevocationRegistryId(
                    credentialRevocationId,
                    revocationRegistryId, anoncredsType
                )
        } catch (e: Exception) {
            logger.warn("Not found credential record by CredentialRevocationId and RevocationRegistryId")
            error = true
        }

        if (error) {
            credentialRecord = credentialRepository.getByCredentialRevocationId(credentialRevocationId, anoncredsType)
        }

        credentialRecord.revocationNotification = RevocationNotification(comment)
        agent.credentialExchangeRepository.update(credentialRecord)

        agent.historyRepository.save(
            HistoryRecord(
                historyType = HistoryType.CredentialRevoked,
                connectionId = connection.id,
                theirLabel = connection.theirLabel,
                associatedRecordId = credentialRecord.id,
            ),
        )

        agent.eventBus.publish(AgentEvents.RevocationNotificationReceivedEventV2(credentialRecord.copy()))
    }

    private fun registerMessageHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(RevocationNotificationHandlerV2(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(RevocationNotificationMessageV2.type, RevocationNotificationMessageV2::class)
    }
}
