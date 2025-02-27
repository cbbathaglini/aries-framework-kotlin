package org.hyperledger.ariesframework.credentials.v2

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.Prover
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.credentials.v1.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.ProposeCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.v2.formats.Format
import org.hyperledger.ariesframework.credentials.v2.handlers.CredentialAckHandlerV2
import org.hyperledger.ariesframework.credentials.v2.handlers.OfferCredentialHandlerV2
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialAckMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialProblemReportMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateProposalOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.CredentialRole
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.slf4j.LoggerFactory


class CredentialServiceV2(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(CredentialServiceV2::class.java)

    private val credentialExchangeRepository = agent.credentialExchangeRepository
    private val credentialRepository = agent.credentialRepository
    private val didCommMessageRepository = agent.didCommMessageRepository
    private val connectionService = agent.connectionService
    private val ledgerService = agent.ledgerService



    init {
        logger.info("INIT HERE")
        registerHandlers(agent.dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(CredentialAckHandlerV2(agent))
        //dispatcher.registerHandler(IssueCredentialHandlerV2(agent))
        dispatcher.registerHandler(OfferCredentialHandlerV2(agent))
        //dispatcher.registerHandler(RequestCredentialHandlerV2(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(CredentialAckMessageV2.type, CredentialAckMessageV2::class)
        MessageSerializer.registerMessage(IssueCredentialMessageV2.type, IssueCredentialMessageV2::class)
        MessageSerializer.registerMessage(OfferCredentialMessageV2.type, OfferCredentialMessageV2::class)
        MessageSerializer.registerMessage(ProposeCredentialMessageV2.type, ProposeCredentialMessageV2::class)
        MessageSerializer.registerMessage(RequestCredentialMessageV2.type, RequestCredentialMessageV2::class)
    }

    /**
     * Create a ``ProposeCredentialMessageV2`` not bound to an existing credential record.
     *
     * @param options options for the proposal.
     * @return proposal message and associated credential record.
     */
    suspend fun createProposal(options: CreateProposalOptionsV2): Pair<ProposeCredentialMessageV2, CredentialExchangeRecord> {
        logger.debug("Get the Format Service and Create Proposal Message")

        val credentialRecord = CredentialExchangeRecord(
            connectionId = options.connection.id,
            threadId = BaseRecord.generateId(),
            state = CredentialState.ProposalSent,
            role = CredentialRole.Holder,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = CredentialsV2Constants.PROTOCOL_VERSION
        )

        var proposalCredentialMessageV2 = ProposeCredentialMessageV2.Builder()
            .comment(options.comment)
            .proposalAttachments(options.proposalAttachments)
            .goal(options.goal)
            .goalCode(options.goalCode)
            .credentialPreview(options.credentialPreview)
            .formats(options.formats)
            .build()
        proposalCredentialMessageV2.id  = credentialRecord.threadId

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, proposalCredentialMessageV2, credentialRecord.id)
        credentialExchangeRepository.save(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialRecord.copy()))

        logger.info("[CredentialServiceV2][createProposal] ProposalCredentialMessageV2 created ${proposalCredentialMessageV2.toJsonString()}")

        return Pair(proposalCredentialMessageV2, credentialRecord);

    }


//    suspend fun processProposal(messageContext: InboundMessageContext): CredentialExchangeRecord {
//        val (proposalMessage, connection, agentContext) = messageContext
//        logger.debug("Processing credential proposal with id ${proposalMessage.id}")
//
//
//        var credentialRecord = findByProperties(agentContext, CredentialRecordProperties(
//            threadId = proposalMessage.threadId,
//            role = CredentialRole.Issuer
//        ))
//
//        val formatServices = getFormatServicesFromMessage(proposalMessage.formats)
//        if (formatServices.isEmpty()) {
//            throw CredoError("Unable to process proposal. No supported formats")
//        }
//
//        if (credentialRecord != null) {
//            val proposalCredentialMessage = didCommMessageRepository.findAgentMessage(agentContext, credentialRecord.id, V2ProposeCredentialMessage::class, DidCommMessageRole.Receiver)
//            val offerCredentialMessage = didCommMessageRepository.findAgentMessage(agentContext, credentialRecord.id, V2OfferCredentialMessage::class, DidCommMessageRole.Sender)
//
//            credentialRecord.assertProtocolVersion("v2")
//            credentialRecord.assertState(CredentialState.OfferSent)
//            connectionService.assertConnectionOrOutOfBandExchange(messageContext, proposalCredentialMessage, offerCredentialMessage, credentialRecord.connectionId)
//
//            credentialRecord.connectionId = credentialRecord.connectionId ?: connection?.id
//
//            credentialFormatCoordinator.processProposal(agentContext, credentialRecord, formatServices, proposalMessage)
//
//            updateState(agentContext, credentialRecord, CredentialState.ProposalReceived)
//            return credentialRecord
//        } else {
//            connectionService.assertConnectionOrOutOfBandExchange(messageContext)
//
//            credentialRecord = CredentialExchangeRecord(
//                connectionId = connection?.id,
//                threadId = proposalMessage.threadId,
//                parentThreadId = proposalMessage.thread?.parentThreadId,
//                state = CredentialState.ProposalReceived,
//                role = CredentialRole.Issuer,
//                protocolVersion = "v2"
//            )
//
//            credentialFormatCoordinator.processProposal(agentContext, credentialRecord, formatServices, proposalMessage)
//
//            credentialRepository.save(agentContext, credentialRecord)
//            emitStateChangedEvent(agentContext, credentialRecord, null)
//
//            agent.eventBus.publish(CredentialStateChangedEvent)
//
//            return credentialRecord
//        }
//    }


//    suspend fun processCredential(messageContext: InboundMessageContext
//    ): CredentialExchangeRecord {
//
//        val credentialMessage = messageContext.message
//        val connection = messageContext.connection
//
//        logger.debug("Processing credential with id ${credentialMessage.id}")
//
//        val credentialRecord = getByProperties(
//            agentContext,
//            mapOf(
//                "threadId" to credentialMessage.threadId,
//                "role" to CredentialRole.Holder,
//                "connectionId" to connection?.id
//            )
//        )
//
//        val requestMessage = didCommMessageRepository.getAgentMessage(
//            agentContext,
//            GetAgentMessageParams(
//                associatedRecordId = credentialRecord.id,
//                messageClass = V2RequestCredentialMessage::class.java,
//                role = DidCommMessageRole.Sender
//            )
//        )
//
//        val offerMessage = didCommMessageRepository.findAgentMessage(
//            agentContext,
//            FindAgentMessageParams(
//                associatedRecordId = credentialRecord.id,
//                messageClass = V2OfferCredentialMessage::class.java,
//                role = DidCommMessageRole.Receiver
//            )
//        )
//
//        // Assertions
//        credentialRecord.assertProtocolVersion("v2")
//        credentialRecord.assertState(CredentialState.RequestSent)
//
//        connectionService.assertConnectionOrOutOfBandExchange(
//            messageContext,
//            ConnectionAssertionParams(
//                lastReceivedMessage = offerMessage,
//                lastSentMessage = requestMessage,
//                expectedConnectionId = credentialRecord.connectionId
//            )
//        )
//
//        val formatServices = getFormatServicesFromMessage(credentialMessage.formats)
//        if (formatServices.isEmpty()) {
//            throw CredoError("Unable to process credential. No supported formats")
//        }
//
//        credentialFormatCoordinator.processCredential(
//            agentContext,
//            CredentialProcessingParams(
//                credentialRecord = credentialRecord,
//                formatServices = formatServices,
//                requestMessage = requestMessage,
//                message = credentialMessage
//            )
//        )
//
//        updateState(agentContext, credentialRecord, CredentialState.CredentialReceived)
//
//        return credentialRecord
//    }

    /**
     * Method called by {@link OfferCredentialHandlerV2} on reception of a offer credential message
     * We do the necessary processing here to accept the offer and do the state change, emit event etc.
     * @param messageContext the inbound offer credential message
     * @returns credential record appropriate for this incoming message (once accepted)
     */
    suspend fun processOffer(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val offerMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as OfferCredentialMessageV2
        logger.debug("Processing credential offer with id ${offerMessage.id}")

//        checkNotNull(offerMessage.findIndyFormatByAttachId()) {
//            "Indy attachment with id ${OfferCredentialMessageV2.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID} not found in offer message"
//        }

//        val formatsOfMessage = offerMessage.getFormatServicesFromMessage()
//        if (formatServices.length === 0) {
//            throw new CredoError(`Unable to process offer. No supported formats`)
//        }

        var credentialRecord = credentialExchangeRepository.findByThreadAndConnectionId(offerMessage.threadId, messageContext.connection?.id)
        if (credentialRecord != null) {
            agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, offerMessage, credentialRecord.id)
            updateState(credentialRecord, CredentialState.OfferReceived)
        } else {
            val connection = messageContext.assertReadyConnection()
            credentialRecord = CredentialExchangeRecord(
                connectionId = connection.id,
                threadId = offerMessage.id,
                parentThreadId = "", //todo
                state = CredentialState.OfferReceived,
                protocolVersion = CredentialsV2Constants.PROTOCOL_VERSION,
            )

            checkNotNull(credentialRecord){
                throw IllegalStateException("CredentialExchangeRecord should not be null before returning from processOffer")
            }

            logger.info("[log] --> credentialRecord: ${credentialRecord.toString()}")


            agent.didCommMessageRepository.saveAgentMessage(
                DidCommMessageRole.Receiver,
                offerMessage,
                credentialRecord.id
            )
            credentialExchangeRepository.save(credentialRecord)
            agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialRecord.copy())) // accept credential?

        }

        logger.info("[log] return of cred: ${credentialRecord.toString()}")
        return credentialRecord
    }

    /**
     * Create a ``RequestCredentialMessageV2`` as response to a received credential offer.
     *
     * @param options options for the request.
     * @return request message.
     */
    suspend fun createRequest(options: AcceptOfferOptions): RequestCredentialMessageV2 {
        logger.info("[log] CREATING_REQUEST -----------------------")
        logger.info("[log] credentialRecordid: ${options.credentialRecordId}")
        val credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion(CredentialsV2Constants.PROTOCOL_VERSION)
        credentialRecord.assertState(CredentialState.OfferReceived)

        logger.info("[log] credentialRecord: ${credentialRecord.toString()}")

        val offerMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessageV2.type)
        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessageV2
        logger.info("[log] offerMessage: ${offerMessage.toJsonString()}")

        checkNotNull(offerMessage.findIndyFormatByAttachId()) {
            "Indy attachment with id ${OfferCredentialMessageV2.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID} not found in offer message"
        }

        val holderDid = options.holderDid ?: getHolderDid(credentialRecord)

        val formats = offerMessage.formats
        val goal = offerMessage.goal
        val goalCode = offerMessage.goalCode
        val comment = offerMessage.comment
        val credentialOfferJson = offerMessage.getCredentialOffer()
        val credentialOffer = CredentialOffer(credentialOfferJson)
        val credentialDefinition = ledgerService.getCredentialDefinition(credentialOffer.credDefId())

        logger.info("[log] 100000000000")
        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
        val credReqTuple = Prover().createCredentialRequest(
            null,
            holderDid,
            CredentialDefinition(credentialDefinition),
            linkSecret,
            agent.wallet.linkSecretId!!,
            credentialOffer,
        )

        logger.info("[log] 200000000000")
        credentialRecord.indyRequestMetadata = credReqTuple.metadata.toJson()
        credentialRecord.credentialDefinitionId = credentialOffer.credDefId()

        val attachment = Attachment.fromData(
            credReqTuple.request.toJson().toByteArray(),
            RequestCredentialMessageV2.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID,
        )

        logger.info("[log] 300000000000")
        val requestMessage = RequestCredentialMessageV2(
            formats,
            listOf(attachment),
            goalCode,
            goal,
            comment
        )
        requestMessage.thread = ThreadDecorator(credentialRecord.threadId)

        credentialRecord.credentialAttributes = offerMessage.credentialPreview?.attributes
        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential

        logger.info("[log] 400000000000")
        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, requestMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.RequestSent)

        logger.info("[log] requestMessage: ${requestMessage.toJsonString()}")

        logger.info("[log] CREATING_REQUEST FINAL -----------------------")
        return requestMessage
    }

    /**
     * Create an ``CredentialProblemReportMessagev2`` as response to a received offer.
     *
     * @param options options for the problem report message.
     * @return credential problem report message.
     */
    suspend fun createOfferDeclinedProblemReport(options: AcceptOfferOptions): CredentialProblemReportMessageV2 {
        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion(CredentialsV2Constants.PROTOCOL_VERSION)
        credentialRecord.assertState(CredentialState.OfferReceived)

        updateState(credentialRecord, CredentialState.Declined)

        return CredentialProblemReportMessageV2()
    }

    private suspend fun getHolderDid(credentialRecord: CredentialExchangeRecord): String {
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        return connection.did
    }

    suspend fun updateState(credentialRecord: CredentialExchangeRecord, newState: CredentialState) {
        logger.info("Updating credential record ${credentialRecord.id} to state ${newState} (previous=${credentialRecord.state}")
        credentialRecord.state = newState
        logger.info("[log] ######credentialRecord: ${credentialRecord.state}")
        credentialExchangeRepository.update(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialRecord.copy())) // n ta emitindo
    }


//    private fun getFormatServicesFromMessage(messageFormats: List<Format>): List<CredentialFormatService> {
//        val formatServices = mutableSetOf<CredentialFormatService>()
//
//        for (msg in messageFormats) {
//            val service = getFormatServiceForFormat(msg.format)
//            if (service != null) {
//                formatServices.add(service)
//            }
//        }
//
//        return formatServices.toList()
//    }
//
//    private fun getFormatServiceForFormat(format: String): CredentialFormatService? {
//        return formats.find { it.supportsFormat(format) }
//    }
}