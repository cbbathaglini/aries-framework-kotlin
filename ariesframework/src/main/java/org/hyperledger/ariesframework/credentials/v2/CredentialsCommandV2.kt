package org.hyperledger.ariesframework.credentials.v2
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.models.NegotiateCredentialOfferOptions
import org.hyperledger.ariesframework.credentials.models.NegotiateCredentialProposalOptions
import org.hyperledger.ariesframework.credentials.models.OfferCredentialOptions
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.models.CreateProposalOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.DeclineCredentialOfferOptions
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.history.models.HistoryType
import org.hyperledger.ariesframework.history.repository.HistoryRecord
import org.slf4j.LoggerFactory

class CredentialsCommandV2(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(CredentialsCommandV2::class.java)

    /**
     * Initiate a new credential exchange as holder by sending a credential proposal message
     * to the connection with the specified connection id.
     *
     * @param options configuration to use for the proposal
     * @returns Credential exchange record associated with the sent proposal message
     */
    suspend fun proposeCredential(options: CreateProposalOptionsV2): CredentialExchangeRecord {

        val connectionRecord = agent.connectionService.getById(options.connection.id)
        connectionRecord.assertReady()

        val (message, credentialRecord) = agent.credentialServiceV2.createProposal(options)
        agent.messageSender.send(OutboundMessage(message, options.connection))
        return credentialRecord
    }


    /**
     * Negotiate a credential proposal as issuer (by sending a credential offer message) to the connection
     * associated with the credential record.
     *
     * @param options configuration for the offer see {@link NegotiateCredentialProposalOptions}
     * @returns Credential exchange record associated with the credential offer
     *
     */
    suspend fun negotiateProposal(options: NegotiateCredentialProposalOptions): CredentialExchangeRecord {
        val credentialExchangeRecord = getById(options.credentialExchangeRecord.id)

        if (credentialExchangeRecord.connectionId == null) {
            throw CredoError("No connection id for credential record ${credentialExchangeRecord.id} not found. Connection-less issuance does not support negotiation")
        }

        val (credentialExchange, offerCredentialMessageV2) = agent.credentialServiceV2.negotiateProposal(
            options = options
        )

        val connectionRecord = agent.connectionService.getById(credentialExchangeRecord.connectionId!!)
        agent.messageSender.send(OutboundMessage(offerCredentialMessageV2, connectionRecord))
        return credentialExchange
    }

    /**
     * Initiate a new credential exchange as issuer by sending a credential offer message
     * to the connection with the specified connection id.
     *
     * @param options config options for the credential offer
     * @returns Credential exchange record associated with the sent credential offer message
     */
    suspend fun offerCredential(options: OfferCredentialOptions): CredentialExchangeRecord {
        val connectionRecord = agent.connectionService.getById(options.connectionId)
        logger.debug("Got a credentialProtocol object for version ${options.protocolVersion}")

        val createOfferCredentialOptions =
            org.hyperledger.ariesframework.credentials.models.CreateCredentialOfferOptionsV2(
                credentialFormat = options.credentialFormat,
                autoAcceptCredential = options.autoAcceptCredential,
                comment = options.comment,
                goal = options.goal,
                goalCode = options.goalCode,
                connectionRecord = connectionRecord
            )
        val (credentialExchangeRecord, offerCredentialMessageV2) = agent.credentialServiceV2.createOffer(createOfferCredentialOptions)
        logger.debug("Offer Message successfully created; message= ${offerCredentialMessageV2}")

        agent.messageSender.send(OutboundMessage(offerCredentialMessageV2, connectionRecord))
        return credentialExchangeRecord
    }



    suspend fun negotiateOffer(options: NegotiateCredentialOfferOptions): CredentialExchangeRecord {
        val credentialExchangeRecord = getById(options.credentialExchangeRecord.id)

        if (credentialExchangeRecord.connectionId == null) {
            throw CredoError("No connection id for credential record ${credentialExchangeRecord.id} not found. Connection-less issuance does not support negotiation")
        }

        val connectionRecord = agent.connectionService.getById(credentialExchangeRecord.connectionId!!)
        connectionRecord.assertReady()

        val (credentialExchange, proposeCredentialMessageV2) = agent.credentialServiceV2.negotiateOffer(
            options = options
        )

        agent.messageSender.send(OutboundMessage(proposeCredentialMessageV2, connectionRecord))
        return credentialExchangeRecord
    }


    /**
     * Retrieve a credential record by id
     *
     * @param credentialRecordId The credential record id
     * @throws {RecordNotFoundError} If no record is found
     * @return The credential record
     *
     */
    private suspend fun getById(credentialRecordId: String): CredentialExchangeRecord {
        return agent.credentialExchangeRepository.getById(credentialRecordId)
    }

    suspend fun acceptOffer(options: AcceptCredentialOfferOptionsV2): CredentialExchangeRecord {

        logger.info("acceptOffer init")
        //val (credentialExchange, message) = agent.credentialServiceV2.createRequest(options)
        val (credentialExchange, message) = agent.credentialServiceV2.acceptOffer(options)
        logger.info("acceptOffer : $message")
        logger.info("credentialExchange : $credentialExchange")

        val connectionRecord =
            agent.connectionRepository.getById(credentialExchange.connectionId!!)
        logger.info("connectionRecord : ${connectionRecord.toString()}")
        agent.messageSender.send(OutboundMessage(message, connectionRecord))

        logger.info("after send message")
        agent.historyRepository.save(
            HistoryRecord(
                historyType = HistoryType.CredentialOfferAccepted.name,
                connectionId = connectionRecord.id,
                theirLabel = connectionRecord.theirLabel,
                associatedRecordId = credentialExchange.id,
                credentialPreviewAttr = credentialExchange.credentialAttributes,
                credentials = credentialExchange.credentials,
            ),
        )

        for (historyRecord in agent.historyRepository.getAll()) {
            logger.info(" ----> ${historyRecord.toString()}")
        }

        return credentialExchange
    }

    /**
     * Declines a credential offer as holder (by sending a problem report message) to the connection
     *
     * @param options options to decline the offer.
     * @return credential record associated with the declined credential.
     */
    suspend fun declineOffer(credentialRecordId: String, options: DeclineCredentialOfferOptions): CredentialExchangeRecord {
        val credentialRecord = agent.credentialExchangeRepository.getById(credentialRecordId)

        agent.credentialServiceV2.declineOffer(credentialRecord, options)

        val connection = agent.connectionRepository.getById(credentialRecord.connectionId ?: "")

        agent.historyRepository.save(
            HistoryRecord(
                historyType = HistoryType.CredentialOfferDeclined.name,
                connectionId = connection.id,
                theirLabel = connection.theirLabel,
                associatedRecordId = credentialRecord.id,
                credentialPreviewAttr = credentialRecord.credentialAttributes,
            ),
        )

        return credentialRecord
    }



//    suspend fun offerCredential(options: CreateCredentialOfferOptionsV2): CredentialExchangeRecord {
//        val (message, credentialRecord) = agent.credentialServiceV2.createOfferCredentialMessage(options)
//        val connection = options.connection ?: throw Exception("Connection is required for sending credential offer")
//        agent.messageSender.send(OutboundMessage(message, connection))
//        return credentialRecord
//    }
//

//
//    /*
//     * helper method to show the attributes
//     */
//    private fun printAttributesOfCredential(credentialAttributes: List<CredentialPreviewAttribute>?) {
//        if (credentialAttributes != null) {
//            credentialAttributes.forEach { attribute ->
//                logger.info("[IDD] Attribute name: ${attribute.name}, Value: ${attribute.value}")
//            }
//        }
//    }
//

//
//    /**
//     * Accept a credential request as issuer (by sending a credential message) to the connection
//     * associated with the credential record.
//     *
//     * @param options options to accept the request.
//     * @return credential record associated with the sent credential message (IssueCredentialMessageV2).
//     */
//    suspend fun acceptRequest(options: AcceptRequestOptions): CredentialExchangeRecord {
//        val message = agent.credentialServiceV2.createIssueCredentialMessage(options)
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
//        val message = agent.credentialServiceV2.createCredentialAckMessage(options)
//        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
//        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
//        agent.messageSender.send(OutboundMessage(message, connection))
//        return credentialRecord
//    }
//
//    /**
//     * Find a ``OfferCredentialMessageV2`` by credential record id.
//     *
//     * @param credentialRecordId: the id of the credential record.
//     * @return the offer message associated with the credential record.
//     */
//    suspend fun findOfferMessage(credentialRecordId: String): OfferCredentialMessageV2? {
//        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, OfferCredentialMessageV2.type)
//
//        return if (messageJson != null) {
//            Json.decodeFromString<OfferCredentialMessageV2>(messageJson)
//        } else {
//            null
//        }
//    }
//
//    /**
//     * Find a ``RequestCredentialMessageV2`` by credential record id.
//     *
//     * @param credentialRecordId: the id of the credential record.
//     * @return the request message associated with the credential record.
//     */
//    suspend fun findRequestMessage(credentialRecordId: String): RequestCredentialMessageV2? {
//        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, RequestCredentialMessageV2.type)
//
//        return if (messageJson != null) {
//            Json.decodeFromString<RequestCredentialMessageV2>(messageJson)
//        } else {
//            null
//        }
//    }
//
//    /**
//     * Find a ``IssueCredentialMessageV2`` by credential record id.
//     *
//     * @param credentialRecordId: the id of the credential record.
//     * @return the credential message associated with the credential record.
//     */
//    suspend fun findCredentialMessage(credentialRecordId: String): IssueCredentialMessageV2? {
//        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, IssueCredentialMessageV2.type)
//        return if (messageJson != null) {
//            Json.decodeFromString<IssueCredentialMessageV2>(messageJson)
//        } else {
//            null
//        }
//    }
}


//    suspend fun acceptOffer(options: AcceptOfferOptions): CredentialExchangeRecord {
//        logger.info("acceptOffer init")
//
//        val message = agent.credentialServiceV2.createRequestCredentialMessage(options)
//        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
//
//        // printAttributesOfCredential(credentialRecord.credentialAttributes);
//
//        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
//
//        agent.messageSender.send(OutboundMessage(message, connection))
//
//        agent.historyRepository.save(
//            HistoryRecord(
//                historyType = HistoryType.CredentialOfferAccepted,
//                connectionId = connection.id,
//                theirLabel = connection.theirLabel,
//                associatedRecordId = options.credentialRecordId,
//                credentialPreviewAttr = credentialRecord.credentialAttributes,
//                credentials = credentialRecord.credentials,
//            ),
//        )
//
//        return credentialRecord
//    }
