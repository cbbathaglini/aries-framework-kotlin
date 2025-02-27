package org.hyperledger.ariesframework.credentials.v2

import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.credentials.v1.messages.IssueCredentialMessage
import org.hyperledger.ariesframework.credentials.v1.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.models.CreateProposalOptionsV2
import org.slf4j.LoggerFactory

class CredentialsCommandV2(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(CredentialsCommandV2::class.java)

//    init {
//        registerHandlers(dispatcher)
//        registerMessages()
//    }
//
//    private fun registerHandlers(dispatcher: Dispatcher) {
//        dispatcher.registerHandler(CredentialAckHandlerV2(agent))
//        //dispatcher.registerHandler(IssueCredentialHandlerV2(agent))
//        dispatcher.registerHandler(OfferCredentialHandlerV2(agent))
//        //dispatcher.registerHandler(RequestCredentialHandlerV2(agent))
//    }
//
//    private fun registerMessages() {
//        MessageSerializer.registerMessage(CredentialAckMessageV2.type, CredentialAckMessageV2::class)
//        MessageSerializer.registerMessage(IssueCredentialMessageV2.type, IssueCredentialMessageV2::class)
//        MessageSerializer.registerMessage(OfferCredentialMessageV2.type, OfferCredentialMessageV2::class)
//        MessageSerializer.registerMessage(ProposeCredentialMessageV2.type, ProposeCredentialMessageV2::class)
//        MessageSerializer.registerMessage(RequestCredentialMessageV2.type, RequestCredentialMessageV2::class)
//    }

    suspend fun proposeCredential(options: CreateProposalOptionsV2): CredentialExchangeRecord {
        val (message, credentialRecord) = agent.credentialServiceV2.createProposal(options)
        agent.messageSender.send(OutboundMessage(message, options.connection))
        return credentialRecord
    }

//    suspend fun offerCredential(options: CreateOfferOptionsV2): CredentialExchangeRecord {
//        val (message, credentialRecord) = agent.credentialServiceV2.createOffer(options)
//        val connection = options.connection ?: throw Exception("Connection is required for sending credential offer")
//        agent.messageSender.send(OutboundMessage(message, connection))
//        return credentialRecord
//    }
//
    suspend fun acceptOffer(options: AcceptOfferOptions): CredentialExchangeRecord {
        logger.info("[log]ACCEPTING OFFER")
        val message = agent.credentialServiceV2.createRequest(options)
        logger.info("[log]message: ${message.toJsonString()}")
        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)

        logger.info("[log]credentialRecord: ${credentialRecord.toString()}")
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        logger.info("[log]connection: ${connection.toString()}")

        agent.messageSender.send(OutboundMessage(message, connection))
        logger.info("[log]agent.messageSender.send: ${message.toJsonString()}")
        logger.info("[log]RETURN CredentialExchangeRecord: ${credentialRecord.toString()}")
        return credentialRecord
    }

    /**
     * Declines a credential offer as holder (by sending a problem report message) to the connection
     *
     * @param options options to decline the offer.
     * @return credential record associated with the declined credential.
     */
    suspend fun declineOffer(options: AcceptOfferOptions): CredentialExchangeRecord {
        val message = agent.credentialServiceV2.createOfferDeclinedProblemReport(options)
        var credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))
        return credentialRecord
    }
//
//    /**
//     * Accept a credential request as issuer (by sending a credential message) to the connection
//     * associated with the credential record.
//     *
//     * @param options options to accept the request.
//     * @return credential record associated with the sent credential message.
//     */
//    suspend fun acceptRequest(options: AcceptRequestOptions): CredentialExchangeRecord {
//        val message = agent.credentialServiceV2.createCredential(options)
//        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
//        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
//        agent.messageSender.send(OutboundMessage(message, connection))
//
//        return credentialRecord
//    }
//
//    /**
//     * Accept a credential as holder (by sending a credential acknowledgement message) to the connection
//     * associated with the credential record.
//     *
//     * @param options options to accept the credential.
//     * @return credential record associated with the sent credential acknowledgement message.
//     */
//    suspend fun acceptCredential(options: AcceptCredentialOptions): CredentialExchangeRecord {
//        val message = agent.credentialServiceV2.createAck(options)
//        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
//        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
//        agent.messageSender.send(OutboundMessage(message, connection))
//
//        return credentialRecord
//    }

    /**
     * Find a ``OfferCredentialMessage`` by credential record id.
     *
     * @param credentialRecordId: the id of the credential record.
     * @return the offer message associated with the credential record.
     */
    suspend fun findOfferMessage(credentialRecordId: String): OfferCredentialMessageV2? {
        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, OfferCredentialMessageV2.type)

        return if (messageJson != null) {
            Json.decodeFromString<OfferCredentialMessageV2>(messageJson)
        } else {
            null
        }
    }

    /**
     * Find a ``RequestCredentialMessage`` by credential record id.
     *
     * @param credentialRecordId: the id of the credential record.
     * @return the request message associated with the credential record.
     */
    suspend fun findRequestMessage(credentialRecordId: String): RequestCredentialMessageV2? {
        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, RequestCredentialMessageV2.type)

        return if (messageJson != null) {
            Json.decodeFromString<RequestCredentialMessageV2>(messageJson)
        } else {
            null
        }
    }

    /**
     * Find a ``IssueCredentialMessage`` by credential record id.
     *
     * @param credentialRecordId: the id of the credential record.
     * @return the credential message associated with the credential record.
     */
    suspend fun findCredentialMessage(credentialRecordId: String): IssueCredentialMessage? {
        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, IssueCredentialMessage.type)

        return if (messageJson != null) {
            Json.decodeFromString<IssueCredentialMessage>(messageJson)
        } else {
            null
        }
    }
}
