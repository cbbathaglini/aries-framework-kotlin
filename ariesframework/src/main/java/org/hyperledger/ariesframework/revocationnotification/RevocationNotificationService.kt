
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v2.models.CredentialRole
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.revocationnotification.handler.RevocationNotificationHandlerV1
import org.hyperledger.ariesframework.revocationnotification.message.RevocationNotificationMessageV1
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification
import org.hyperledger.ariesframework.util.RevocationIdentifier
import org.slf4j.LoggerFactory

class RevocationNotificationService(val agent: Agent, val dispatcher: Dispatcher){
    private val logger = LoggerFactory.getLogger(RevocationNotificationService::class.java)
    private val credentialRepository = agent.credentialRepository

    init {
        registerMessageHandlers(dispatcher)
        registerMessages()
    }

    private suspend fun processRevocationNotification(
        anonCredsRevocationRegistryId: String,
        anonCredsCredentialRevocationId: String,
        connection: ConnectionRecord,
        comment: String? = null,
        threadId: String
    ) {

        logger.info("Init processRevocationNotification...")
        logger.info("-----> connection id: ${connection.id}")

        val credentials = agent.credentialRepository.getAll()

        credentials.forEach { credential ->
            logger.info("--> Credential: ${credential.toString()}")
        }

        // Query to search for revocation registry in both qualified and unqualified forms
        var query = "";

        try {
            query = Json.encodeToString(
                mapOf(
                    "\$or" to listOf(
                        mapOf(
                            "revocationRegistryId" to anonCredsRevocationRegistryId,
                            "credentialRevocationId" to anonCredsCredentialRevocationId
                        )
                    )
                )
            )
        }catch (e : Exception){
            logger.error("error in query: ${e.message.toString()}")
        }

        logger.trace("Getting record by query for revocation notification: $query")
        val credentialRecord = credentialRepository.getSingleByQuery(query)


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

    /**
     * Process a received RevocationNotificationMessageV1.
     */
    suspend fun v1ProcessRevocationNotification(messageContext: InboundMessageContext) {
        logger.info("Processing revocation notification v1 ->> ${messageContext.message.toJsonString()}")

        logger.info("MESSAGE CAMED: ${messageContext.message.toJsonString()}")

        // Explicit cast
        val revocationMessage = messageContext.message as? RevocationNotificationMessageV1
            ?: throw CredoError("Invalid message type: Expected RevocationNotificationMessageV1")

        val threadId = revocationMessage.issueThread
        if (!threadId.startsWith("indy::")) {
            throw IllegalArgumentException("Invalid threadId format: $threadId. Expected format: indy::<revocation_registry_id>::<credential_revocation_id>")
        }
        logger.info("threadId: ${threadId}")

        try {
            val threadIdGroups = RevocationIdentifier.v1ThreadRegex.find(threadId)?.groupValues
            logger.info("threadIdGroups: ${threadIdGroups.toString()}")
            if (threadIdGroups != null) {
                logger.info("threadIdGroups[2]: ${threadIdGroups.get(2).toString()}")
            }
            if (threadIdGroups == null || threadIdGroups.size < 3) {
                throw CredoError(
                    "Incorrect revocation notification threadId format: \n$threadId\ndoes not match\n" +
                            "\"indy::<revocation_registry_id>::<credential_revocation_id>\""
                )
            }

            val anonCredsRevocationRegistryId = threadIdGroups[2]
            val anonCredsCredentialRevocationId = threadIdGroups[3]

            logger.info("anoncreds getted")

            val comment = revocationMessage.comment
            val connection = messageContext.assertReadyConnection()

            processRevocationNotification(
                anonCredsRevocationRegistryId,
                anonCredsCredentialRevocationId,
                connection,
                comment,
                threadId
            )
        } catch (error: Exception) {
            logger.error("Failed to process revocation notification message: ${error.message.toString()}")
            logger.warn("Failed to process revocation notification message", mapOf("error" to error, "threadId" to threadId))
        }
    }

    private fun registerMessageHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(RevocationNotificationHandlerV1(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(RevocationNotificationMessageV1.type, RevocationNotificationMessageV1::class)
    }
}