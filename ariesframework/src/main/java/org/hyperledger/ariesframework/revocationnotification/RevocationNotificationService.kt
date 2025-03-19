
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v2.models.CredentialRole
import org.hyperledger.ariesframework.decorators.AckDecorator
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.revocationnotification.handler.RevocationNotificationHandlerV1
import org.hyperledger.ariesframework.revocationnotification.message.RevocationNotificationMessageV1
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotificationMessageV1Options
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.hyperledger.ariesframework.revocationnotificationv2.model.RevocationNotificationMessageV2Options
import org.hyperledger.ariesframework.util.RevocationIdentifier
import org.slf4j.LoggerFactory

class RevocationNotificationService(val agent: Agent, val dispatcher: Dispatcher){
    private val logger = LoggerFactory.getLogger(RevocationNotificationService::class.java)
    private val credentialRepository = agent.credentialRepository

    init {
        registerMessageHandlers(dispatcher)
        registerMessages()
    }

    fun createRevocationNotification(options: RevocationNotificationMessageV1Options): Map<String, RevocationNotificationMessageV1> {

        val (issueThread, comment, pleaseAck) = options

        val message = RevocationNotificationMessageV1(
            issueThread= issueThread,
            comment= comment,
            pleaseAck= pleaseAck
        )

        return mapOf("message" to message)
    }

    suspend fun processRevocationNotification(messageContext: InboundMessageContext) {
        logger.debug("processRevocationNotification init")

        val revocationMessage = messageContext.message as? RevocationNotificationMessageV1
            ?: throw CredoError("Invalid message type: Expected RevocationNotificationMessageV1")

        val threadId = revocationMessage.issueThread
        if (!threadId.startsWith("indy::")) {
            throw IllegalArgumentException("Invalid threadId format: $threadId. Expected format: indy::<revocation_registry_id>::<credential_revocation_id>")
        }

        val threadIdGroups = RevocationIdentifier.v1ThreadRegex.find(threadId)?.groupValues
        if (threadIdGroups == null || threadIdGroups.size < 3) {
            throw CredoError(
                "Incorrect revocation notification threadId format: \n$threadId\ndoes not match\n" +
                        "\"indy::<revocation_registry_id>::<credential_revocation_id>\""
            )
        }

        val anonCredsRevocationRegistryId = threadIdGroups[2]
        val anonCredsCredentialRevocationId = threadIdGroups[3]

        val comment = revocationMessage.comment
        val connection = messageContext.assertReadyConnection()

        processRevocationNotification(
            revocationRegistryId = anonCredsRevocationRegistryId,
            credentialRevocationId = anonCredsCredentialRevocationId,
            connection = connection,
            comment = comment,
            threadId = threadId
        )

    }

    private suspend fun processRevocationNotification(
        revocationRegistryId: String,
        credentialRevocationId: String,
        connection: ConnectionRecord,
        comment: String? = null,
        threadId: String
    ) {

        lateinit var credentialRecord: CredentialRecord
        var error = false
        try {
            credentialRecord =
                credentialRepository.getByCredentialRevocationIdAndRevocationRegistryId(
                    credentialRevocationId,
                    revocationRegistryId
                )
        }catch (e:Exception){
            logger.warn("Not found credential record by CredentialRevocationId and RevocationRegistryId")
            error = true;
        }

        if (error){
            credentialRecord = credentialRepository.getByCredentialRevocationId(credentialRevocationId)
        }

        credentialRecord.revocationNotification = RevocationNotification(comment)
        agent.credentialRepository.update(credentialRecord)

        logger.trace("Emitting RevocationNotificationReceivedEvent")

        val credentialExchangeRecord = credentialRecord.toCredentialExchangeRecord(
            connection.id,
            threadId,
            CredentialState.Revoked,
            "v1",
            CredentialRole.Holder
        )

        agent.eventBus.publish(AgentEvents.RevocationNotificationReceivedEvent(credentialExchangeRecord.copy()))
    }




    private fun registerMessageHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(RevocationNotificationHandlerV1(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(RevocationNotificationMessageV1.type, RevocationNotificationMessageV1::class)
    }
}