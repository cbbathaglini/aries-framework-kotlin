
import org.slf4j.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentialsv2.models.CredentialRole
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification

import org.hyperledger.ariesframework.revocationnotificationv2.handler.RevocationNotificationHandlerV2
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.hyperledger.ariesframework.revocationnotificationv2.model.RevocationNotificationMessageV2Options
import org.hyperledger.ariesframework.util.RevocationIdentifier
import org.slf4j.LoggerFactory

class RevocationNotificationServiceV2(val agent: Agent, val dispatcher: Dispatcher){
    private val logger = LoggerFactory.getLogger(RevocationNotificationService::class.java)
    private val credentialRepository = agent.credentialRepository

    init {
        registerMessageHandlers(dispatcher)
        registerMessages()
    }

    suspend fun createRevocationNotification(options: RevocationNotificationMessageV2Options): Map<String, RevocationNotificationMessageV2> {

        val (credentialId, revocationFormat, comment, requestAck) = options

        val message = RevocationNotificationMessageV2(
            credentialId = credentialId,
            revocationFormat = revocationFormat,
            comment = comment
        )

// TODO
//        if (message.pleaseAck.on) {
//            message.setPleaseAck()
//        }

        return mapOf("message" to message)
    }

    suspend fun processRevocationNotification(messageContext: InboundMessageContext) {
        logger.info("Processing revocation notification v2", mapOf("message" to messageContext.message))

        //como pegar o threadid

        val revocationMessage = messageContext.message as? RevocationNotificationMessageV2
            ?: throw CredoError("Invalid message type: Expected RevocationNotificationMessageV2")
        val credentialId = messageContext.message.credentialId

        if (messageContext.message.revocationFormat !in listOf(RevocationIdentifier.v2IndyRevocationFormat,
                RevocationIdentifier.v2AnonCredsRevocationFormat)) {
            throw CredoError(
                "Unknown revocation format: ${messageContext.message.revocationFormat}. Supported formats are indy-anoncreds and anoncreds"
            )
        }

        try {
            val credentialIdGroups =
                RevocationIdentifier.v2IndyRevocationIdentifierRegex.find(credentialId)?.groupValues
                    ?:  RevocationIdentifier.v2AnonCredsRevocationIdentifierRegex.find(credentialId)?.groupValues

            if (credentialIdGroups == null || credentialIdGroups.size < 2) {
                throw CredoError(
                    "Incorrect revocation notification credentialId format: \n$credentialId\ndoes not match\n" +
                            "\"<revocation_registry_id>::<credential_revocation_id>\""
                )
            }

            val anonCredsRevocationRegistryId = credentialIdGroups[1]
            val anonCredsCredentialRevocationId = credentialIdGroups[2]
            val comment = messageContext.message.comment
            val connection = messageContext.assertReadyConnection()

            processRevocationNotification(
                anonCredsRevocationRegistryId,
                anonCredsCredentialRevocationId,
                connection,
                comment,
                ""
            )
        } catch (error: Exception) {
            logger.warn("Failed to process revocation notification message", mapOf("error" to error, "credentialId" to credentialId))
        }
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

    private fun registerMessageHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(RevocationNotificationHandlerV2(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(RevocationNotificationMessageV2.type, RevocationNotificationMessageV2::class)
    }
}