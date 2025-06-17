package org.hyperledger.ariesframework.credentials.v2

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.credentials.CredentialsConstants
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatCoordinator
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOptions
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialProposalOptions
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AcceptProposalParams
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.models.CredentialRole
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.modelv2.AcceptCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.modelv2.AcceptOfferParams
import org.hyperledger.ariesframework.credentials.modelv2.AcceptRequestOptionsV2
import org.hyperledger.ariesframework.credentials.modelv2.AcceptRequestParams
import org.hyperledger.ariesframework.credentials.modelv2.CreateCredentialParams
import org.hyperledger.ariesframework.credentials.modelv2.CreateCredentialProblemReportOptions
import org.hyperledger.ariesframework.credentials.modelv2.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.credentials.modelv2.NegotiateCredentialOfferOptions
import org.hyperledger.ariesframework.credentials.modelv2.NegotiateCredentialProposalOptions
import org.hyperledger.ariesframework.credentials.modelv2.ProcessCredentialParams
import org.hyperledger.ariesframework.credentials.modelv2.ProcessOfferParams
import org.hyperledger.ariesframework.credentials.modelv2.ProcessRequestParams
import org.hyperledger.ariesframework.credentials.modelv2.RequestCredentialParams
import org.hyperledger.ariesframework.credentials.modelv2.problemreport.CredentialProblemReportReason
import org.hyperledger.ariesframework.credentials.operation.CreateProposalParams
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialAckMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialProblemReportMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.ProposeCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateProposalOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.problemreports.messages.DescriptionOptions
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.hyperledger.ariesframework.util.composeAutoAccept
import org.slf4j.LoggerFactory
import java.util.UUID


class CredentialServiceV2(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(CredentialServiceV2::class.java)

    private val credentialExchangeRepository = agent.credentialExchangeRepository
    private val didCommMessageRepository = agent.didCommMessageRepository
    private val ledgerService = agent.ledgerService
    private val credentialFormats = emptyList<CredentialFormatService<*>>()
    private val credentialFormatCoordinator = CredentialFormatCoordinator(agent,credentialFormats)

    init {
        Registers(agent).initialize()
    }

    /**
     * Create a ``ProposeCredentialMessageV2`` not bound to an existing credential record.
     *
     * @param options options for the proposal.
     * @return proposal message and associated credential record.
     */
     suspend fun createProposal(options: CreateProposalOptionsV2): Pair<ProposeCredentialMessageV2, CredentialExchangeRecord> {
        logger.debug("Get the Format Service and Create Proposal Message")

        val formatServices = this.getFormatServices(options.credentialFormats)
        if (formatServices.isEmpty()){
            throw CredoError("Unable to create proposal. No supported formats")
        }

        val credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = options.connection.id,
            threadId = BaseRecord.generateId(),
            state = CredentialState.ProposalSent,
            role = CredentialRole.Holder,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
        )

        val createProposalParams = CreateProposalParams(
            options.credentialFormats,
            formatServices,
            credentialExchangeRecord,
            options.comment,
            options.goalCode,
            options.goal
        )
        var proposalCredentialMessageV2 = this.credentialFormatCoordinator.createProposal(createProposalParams)

        logger.debug("Save record and emit state change event")
        credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))

        return Pair(proposalCredentialMessageV2, credentialExchangeRecord)
    }

    /**
     * Method called by {@link ProposeCredentialHandlerV2} on reception of a propose credential message
     * We do the necessary processing here to accept the proposal and do the state change, emit event etc.
     * @param messageContext the inbound propose credential message
     * @returns credential record appropriate for this incoming message (once accepted)
     */
     suspend fun processProposal(messageContext: InboundMessageContext): CredentialExchangeRecord {

        val connection = messageContext.connection
        val proposalMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ProposeCredentialMessageV2
        logger.debug("[2.0] Processing credential proposal with id ${proposalMessage.id}")

        val credentialRecord = agent.credentialExchangeRepository.getByThreadAndRole(proposalMessage.threadId, CredentialRole.Issuer)

        val formatServices = getFormatServicesFromMessage(proposalMessage.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process proposal. No supported formats")
        }

        if (credentialRecord != null) {
            val proposalCredentialMessage = agent.didCommMessageRepository.getTypedAgentMessage<ProposeCredentialMessageV2>(
                associatedRecordId = credentialRecord.id,
                messageType = ProposeCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver
            )

            val offerCredentialMessage = didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                    associatedRecordId = credentialRecord.id,
                    messageType = OfferCredentialMessageV2.type,
                    role = DidCommMessageRole.Sender
            )

            // assert
            credentialRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
            credentialRecord.assertState(CredentialState.OfferSent)

            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = proposalCredentialMessage,
                lastSentMessage = offerCredentialMessage,
                expectedConnectionId = credentialRecord.connectionId
            )

            // verification of authorization
            if (credentialRecord.connectionId == null) {
                agent.connectionService.matchIncomingMessageToRequestMessageInOutOfBandExchange(
                    messageContext= messageContext,
                    expectedConnectionId = credentialRecord.connectionId
                )
                credentialRecord.connectionId = connection?.id
            }

            credentialFormatCoordinator.processProposal(
                credentialExchangeRecord = credentialRecord,
                formatServices = formatServices,
                message = proposalMessage
            )

            updateState(credentialRecord, CredentialState.ProposalReceived)
            return credentialRecord
        }

        // assert
        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext
        )

        // none credential record finded with this thread id - create a new one
        logger.debug("No credential record found for offer, creating a new one")
        val credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = connection?.id,
            threadId = proposalMessage.threadId,
            parentThreadId = proposalMessage.thread?.parentThreadId,
            state = CredentialState.ProposalReceived,
            role = CredentialRole.Issuer,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2
        )

        // process the proposal with format services
        credentialFormatCoordinator.processProposal(
            credentialExchangeRecord = credentialExchangeRecord,
            formatServices = formatServices,
            message = proposalMessage
        )

        // save new registry and emit an event
        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))
        return credentialExchangeRecord

    }


    /**
     * Accept a received {@link ProposeCredentialMessageV2} and create a {@link OfferCredentialMessageV2}.
     *
     * This method handles a credential proposal by determining which credential format services are supported,
     * either based on the provided `credentialFormats` or by extracting them from the original proposal message.
     * If no compatible format service is found, an error is thrown.
     *
     * It then delegates to the {@link CredentialFormatCoordinator} to create an offer message,
     * updates the credential exchange record state to `OfferSent`,
     * and returns both the updated credential record and the generated offer message.
     *
     * @param agentContext The context containing agent configuration, services, and dependencies
     * @param options The {@link AcceptCredentialProposalOptions} object with proposal data, including formats, comment, and goal
     * @return A Pair of {@link OfferCredentialMessageV2, CredentialExchangeRecord} containing the updated credential record and the offer message
     *
     * @throws CredoError if no supported credential formats are provided or detected
     */
    suspend fun acceptProposal(
        options: AcceptCredentialProposalOptions
    ): Pair<OfferCredentialMessageV2, CredentialExchangeRecord> {

        val credentialExchangeRecord = options.credentialExchangeRecord
        val credentialFormats = options.credentialFormats
        val comment = options.comment
        val goal = options.goal
        val goalCode = options.goalCode
        val autoAcceptCredential = options.autoAcceptCredential

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.ProposalReceived)

        var formatServices = getFormatServices(credentialFormats ?: emptyMap())
        if (formatServices.isEmpty()) {
            val proposalMessage = didCommMessageRepository.getAgentMessage(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = ProposeCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver
            )
            val proposalMessageV2 = MessageSerializer.decodeFromString(proposalMessage) as ProposeCredentialMessageV2
            formatServices = getFormatServicesFromMessage(proposalMessageV2.formats)
        }

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to accept proposal. No supported formats provided as input or in proposal message")
        }

        val acceptParams = AcceptProposalParams(
            credentialRecord = credentialExchangeRecord,
            formatServices = formatServices,
            comment = comment,
            goal = goal,
            goalCode = goalCode,
            credentialFormats = credentialFormats
        )
        val offerMessage = credentialFormatCoordinator.acceptProposal(acceptParams)

        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.OfferSent)

        return Pair(offerMessage,credentialExchangeRecord)
    }

    /**
     * Negotiate a credential proposal as issuer (by sending a credential offer message) to the connection
     * associated with the credential record.
     *
     * @param options configuration for the offer see {@link NegotiateCredentialProposalOptions}
     * @returns Credential exchange record associated with the credential offer
     */
    suspend fun negotiateProposal(options: NegotiateCredentialProposalOptions): Pair<CredentialExchangeRecord, OfferCredentialMessageV2>{
        val credentialExchangeRecord = options.credentialExchangeRecord
        val credentialFormats = options.credentialFormats
        val comment = options.comment
        val goal = options.goal
        val goalCode = options.goalCode

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.ProposalReceived)

        if (credentialExchangeRecord.connectionId.isNullOrBlank()){
            throw CredoError("No connectionId found for credential record '${credentialExchangeRecord.id}'. Connection-less issuance does not support negotiation.")
        }

        var formatServices = getFormatServices(credentialFormats ?: emptyMap())
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create offer. No supported formats.")
        }

        val createCredential = CreateCredentialParams(
            credentialRecord = credentialExchangeRecord,
            formatServices = formatServices,
            comment = comment,
            goal = goal,
            goalCode = goalCode,
            credentialFormats = credentialFormats
        )
        val offerMessage = credentialFormatCoordinator.createOffer(createCredential)
        val autoAcceptCredential = options.autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential

        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.OfferSent)

        return Pair(credentialExchangeRecord, offerMessage)

    }

    /**
     * Create a {@link OfferCredentialMessageV2} as beginning of protocol process. If no connectionId is provided, the
     * exchange will be created without a connection for usage in oob and connection-less issuance.
     *
     * @param formatService {@link CredentialFormatService} the format service object containing format-specific logic
     * @param options attributes of the original offer
     * @returns Object containing offer message and associated credential record
     *
     */
    suspend fun createOffer(options: org.hyperledger.ariesframework.credentials.modelv2.CreateCredentialOfferOptionsV2): Pair<CredentialExchangeRecord, OfferCredentialMessageV2>{
        val connectionRecord = options.connectionRecord
        val credentialFormats = options.credentialFormat
        val autoAcceptCredential = options.autoAcceptCredential
        val comment = options.comment
        val goal = options.goal
        val goalCode = options.goalCode
        
        var formatServices = getFormatServices(credentialFormats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create offer. No supported formats.")
        }
        
        val credentialExchangeRecord = CredentialExchangeRecord(
            connectionId= connectionRecord?.id,
            threadId= BaseRecord.generateId(),
            state= CredentialState.OfferSent,
            role= CredentialRole.Issuer,
            autoAcceptCredential= autoAcceptCredential,
            protocolVersion= CredentialsConstants.PROTOCOL_VERSION_V2
        )

        val createCredential = CreateCredentialParams(
            credentialRecord = credentialExchangeRecord,
            formatServices = formatServices,
            comment = comment,
            goal = goal,
            goalCode = goalCode,
            credentialFormats = credentialFormats
        )
        val offerMessage = credentialFormatCoordinator.createOffer(createCredential)
        logger.debug("Saving record and emitting state changed for credential exchange record ${credentialExchangeRecord.id}")

        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))

        return Pair(credentialExchangeRecord, offerMessage)
    }

    /**
     * Method called by {@link OfferCredentialHandlerV2} on reception of a offer credential message
     * We do the necessary processing here to accept the offer and do the state change, emit event etc.
     * @param messageContext the inbound offer credential message
     * @returns credential record appropriate for this incoming message (once accepted)
     */
    suspend fun processOffer(messageContext: InboundMessageContext): CredentialExchangeRecord{

        val connection = messageContext.connection
        val message = messageContext.message
        val offerMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as OfferCredentialMessageV2

        logger.debug("Processing credential offer with id ${offerMessage.id}")

        var credentialExchangeRecord = agent.credentialExchangeRepository.findByThreadRoleAndConnectionId(
            threadId = offerMessage.threadId,
            role = CredentialRole.Holder,
            connectionId = connection?.id
        )

        val formatServices = getFormatServicesFromMessage(offerMessage.formats)
        if (formatServices.isEmpty()){
            throw CredoError("Unable to process offer. No supported formats")
        }

        if (credentialExchangeRecord!=null){
            val proposeMessage = agent.didCommMessageRepository.getTypedAgentMessage<ProposeCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = ProposeCredentialMessageV2.type,
                role = DidCommMessageRole.Sender
            )

            val offerCredentialMessage = agent.didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = OfferCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver
            )

            credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
            credentialExchangeRecord.assertState(CredentialState.ProposalSent)

            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = offerCredentialMessage,
                lastSentMessage = proposeMessage,
                expectedConnectionId = credentialExchangeRecord.connectionId
            )

            val processOfferParams = ProcessOfferParams(
                credentialExchangeRecord = credentialExchangeRecord,
                message = offerMessage,
                formatService = formatServices
            )
            credentialFormatCoordinator.processOffer(processOfferParams)

            updateState(credentialExchangeRecord, CredentialState.OfferReceived)
            return credentialExchangeRecord
        }

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext
        )

        logger.debug("No credential record found for offer, creating a new one")

        credentialExchangeRecord = CredentialExchangeRecord(
            connectionId= connection?.id,
            threadId= offerMessage.threadId,
            parentThreadId= offerMessage.thread?.parentThreadId,
            state= CredentialState.OfferReceived,
            role= CredentialRole.Holder,
            protocolVersion= CredentialsConstants.PROTOCOL_VERSION_V2,
        )

        val processOfferParams = ProcessOfferParams(
            credentialExchangeRecord = credentialExchangeRecord,
            message = offerMessage,
            formatService = formatServices
        )
        credentialFormatCoordinator.processOffer(processOfferParams)
        logger.debug("Saving credential record and emit offer-received event")
        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))
        return credentialExchangeRecord
    }


    suspend fun acceptOffer(options: AcceptCredentialOfferOptionsV2): Pair<CredentialExchangeRecord, RequestCredentialMessageV2>{
        val credentialExchangeRecord = options.credentialExchangeRecord
        val credentialFormats = options.credentialFormats

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.OfferReceived)

        var formatServices = getFormatServices(credentialFormats ?: emptyMap())
        if (formatServices.isEmpty()){
            val offerMessage = agent.didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = OfferCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver
            )

            formatServices = if (offerMessage != null) getFormatServicesFromMessage(offerMessage.formats) else emptyList()
        }

        if (formatServices.isEmpty()){
            throw CredoError("Unable to accept offer. No supported formats provided as input or in offer message")
        }

        val acceptOfferParams = AcceptOfferParams(
            credentialRecord = credentialExchangeRecord,
            formatServices = formatServices,
            comment = options.comment,
            goal = options.goal,
            goalCode = options.goalCode,
            credentialFormats = credentialFormats
        )

        val requestCredentialMessageV2 = credentialFormatCoordinator.acceptOffer(acceptOfferParams)
        credentialExchangeRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential

        updateState(credentialExchangeRecord, CredentialState.RequestSent)
        return Pair(credentialExchangeRecord, requestCredentialMessageV2)
    }


    /**
     * Create a {@link ProposePresentationMessage} as response to a received credential offer.
     * To create a proposal not bound to an existing credential exchange, use {@link createProposal}.
     *
     * @param options configuration to use for the proposal
     * @returns Object containing proposal message and associated credential record
     *
     */
    suspend fun negotiateOffer(options: NegotiateCredentialOfferOptions): Pair<CredentialExchangeRecord, ProposeCredentialMessageV2>{
        val credentialExchangeRecord = options.credentialExchangeRecord
        val autoAcceptCredential = options.autoAcceptCredential
        val credentialFormats = options.credentialFormat

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.OfferReceived)

        if (credentialExchangeRecord.connectionId == null){
            throw CredoError("No connectionId found for credential record '${credentialExchangeRecord.id}'. Connection-less issuance does not support negotiation.")
        }

        val formatServices = getFormatServices(credentialFormats)
        if (credentialFormats.isEmpty()){
            throw CredoError("Unable to create proposal. No supported formats")
        }

        val createProposalParams = CreateProposalParams(
            credentialFormats,
            formatServices,
            credentialExchangeRecord,
            options.comment,
            options.goalCode,
            options.goal
        )
        val proposeMessage = credentialFormatCoordinator.createProposal(createProposalParams)
        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.ProposalSent)

        return Pair(credentialExchangeRecord, proposeMessage)
    }


    /**
     * Create a {@link RequestCredentialMessageV2} as beginning of protocol process.
     * @returns Object containing offer message and associated credential record
     *
     */
    suspend fun createRequest(options: CreateCredentialRequestOptions): Pair<CredentialExchangeRecord, RequestCredentialMessageV2>{

        val autoAcceptCredential = options.autoAcceptCredential
        val connectionRecord = options.connectionRecord
        val credentialFormats = options.credentialFormats

        val formatServices = getFormatServices(credentialFormats)
        if (credentialFormats.isEmpty()){
            throw CredoError("Unable to create request. No supported formats")
        }

        val credentialExchangeRecord = CredentialExchangeRecord(
            connectionId= connectionRecord.id,
            threadId= UUID.randomUUID().toString(),
            state= CredentialState.RequestSent,
            role= CredentialRole.Holder,
            autoAcceptCredential = autoAcceptCredential,
            protocolVersion= CredentialsConstants.PROTOCOL_VERSION_V2
        )

        val requestParams = RequestCredentialParams(
            credentialFormats = credentialFormats,
            formatServices = formatServices,
            credentialRecord = credentialExchangeRecord,
            comment = options.comment,
            goalCode = options.goalCode,
            goal = options.goal
        )
        val requestMessage = credentialFormatCoordinator.createRequest(requestParams)

        logger.debug("Saving record and emitting state changed for credential exchange record ${credentialExchangeRecord.id}")
        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))

        return Pair(credentialExchangeRecord, requestMessage)
    }


    /**
     * Process a received {@link RequestCredentialMessage}. This will not accept the credential request
     * or send a credential. It will only update the existing credential record with
     * the information from the credential request message. Use {@link createCredential}
     * after calling this method to create a credential.
     *z
     * @param messageContext The message context containing a v2 credential request message
     * @returns credential record associated with the credential request message
     *
     */
    suspend fun processRequest(messageContext: InboundMessageContext): CredentialExchangeRecord{
        val connection = messageContext.connection
        val message = messageContext.message

        val requestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestCredentialMessageV2
        logger.debug("Processing credential request with id ${requestMessage.id}")

        var credentialExchangeRecord = agent.credentialExchangeRepository.findSingleByQuery(
            "{\"threadId\": \"${requestMessage.threadId}\", \"role\": \"${CredentialRole.Issuer}\"}"
        )

        val formatServices = getFormatServicesFromMessage(requestMessage.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process proposal. No supported formats")
        }

        if (credentialExchangeRecord != null){
            val proposalCredentialMessage = agent.didCommMessageRepository.getTypedAgentMessage<ProposeCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = ProposeCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver
            )

            val offerCredentialMessage = didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = OfferCredentialMessageV2.type,
                role = DidCommMessageRole.Sender
            )

            // assert
            credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
            credentialExchangeRecord.assertState(CredentialState.OfferSent)

            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = proposalCredentialMessage,
                lastSentMessage = offerCredentialMessage,
                expectedConnectionId = credentialExchangeRecord.connectionId
            )

            // verification of authorization
            if (credentialExchangeRecord.connectionId == null) {
                agent.connectionService.matchIncomingMessageToRequestMessageInOutOfBandExchange(
                    messageContext= messageContext,
                    expectedConnectionId = credentialExchangeRecord.connectionId
                )
                credentialExchangeRecord.connectionId = connection?.id
            }

            val processRequestParams = ProcessRequestParams(
                credentialExchangeRecord = credentialExchangeRecord,
                message = requestMessage,
                formatService = formatServices
            )
            credentialFormatCoordinator.processRequest(processRequestParams)

            updateState(credentialExchangeRecord, CredentialState.RequestReceived)

            return credentialExchangeRecord
        }


        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
            lastReceivedMessage = null,
            lastSentMessage = null,
            expectedConnectionId = null
        )

        logger.debug("No credential record found for offer, creating a new one")

        credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = connection?.id,
            threadId = requestMessage.threadId,
            parentThreadId = requestMessage.thread?.parentThreadId,
            state = CredentialState.RequestReceived,
            role = CredentialRole.Issuer,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2
        )

        val processRequestParams = ProcessRequestParams(
            credentialExchangeRecord = credentialExchangeRecord,
            message = requestMessage,
            formatService = formatServices
        )
        credentialFormatCoordinator.processRequest(processRequestParams)

        logger.debug("Saving credential record and emit request-received event")

        // save new registry and emit an event
        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))
        return credentialExchangeRecord
    }

    suspend fun acceptRequest(options: AcceptRequestOptionsV2): Pair<CredentialExchangeRecord, IssueCredentialMessageV2>{

        val credentialExchangeRecord = options.credentialExchangeRecord
        val credentialFormats = options.credentialFormats
        val autoAcceptCredential = options.autoAcceptCredential

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.OfferSent)

        var formatServices = getFormatServices(options.credentialFormats ?: emptyMap())
        if (formatServices.isEmpty()) {
            val requestMessage = agent.didCommMessageRepository.getTypedAgentMessage<RequestCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = RequestCredentialMessageV2.type,
                role = DidCommMessageRole.Sender
            )

            formatServices = if (requestMessage != null) getFormatServicesFromMessage(requestMessage.formats) else emptyList()
        }

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to accept request. No supported formats provided as input or in request message")
        }

        val acceptRequestParams = AcceptRequestParams(
            credentialExchangeRecord = credentialExchangeRecord,
            formatService = formatServices,
            comment = options.comment,
            goal = options.goal,
            goalCode = options.goalCode,
            credentialFormat = credentialFormats
        )
        val message = credentialFormatCoordinator.acceptRequest(acceptRequestParams)

        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.CredentialIssued)

        return Pair(credentialExchangeRecord, message)
    }

    /**
     * Process a received {@link IssueCredentialMessageV2}. This will not accept the credential
     * or send a credential acknowledgement. It will only update the existing credential record with
     * the information from the issue credential message. Use {@link createAck}
     * after calling this method to create a credential acknowledgement.
     *
     * @param messageContext The message context containing an issue credential message
     *
     * @returns credential record associated with the issue credential message
     *
     */
    suspend fun processCredential(messageContext: InboundMessageContext): CredentialExchangeRecord{
        val connection = messageContext.connection
        val message = messageContext.message

        val issueCredential = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as IssueCredentialMessageV2
        logger.debug("Processing credential with id ${issueCredential.id}")

        var credentialExchangeRecord = agent.credentialExchangeRepository.getSingleByQuery(
            "{\"threadId\": \"${issueCredential.threadId}\", \"role\": \"${CredentialRole.Issuer}\", \"connectionId\": \"${connection?.id}\"}"
        )

        val requestMessage = agent.didCommMessageRepository.getTypedAgentMessage<RequestCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = RequestCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        ) ?: throw CredoError("Request message not found")

        val offerMessage = didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Sender
        )

        // assert
        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.RequestSent)

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
            lastReceivedMessage = requestMessage,
            lastSentMessage = offerMessage,
            expectedConnectionId = credentialExchangeRecord.connectionId
        )

        val formatServices = getFormatServicesFromMessage(issueCredential.formats)
        if (formatServices.isEmpty()){
            throw CredoError("Unable to process credential. No supported formats")
        }

        val processCredentialParams = ProcessCredentialParams(
            credentialExchangeRecord = credentialExchangeRecord,
            formatService = formatServices,
            requestCredentialMessageV2 = requestMessage,
            message = issueCredential
        )
        credentialFormatCoordinator.processCredential(processCredentialParams)
        updateState(credentialExchangeRecord, CredentialState.CredentialReceived)
        return credentialExchangeRecord

    }

    /**
     * Create a {@link CredentialAckMessageV2} as response to a received credential.
     *
     * @param credentialRecord The credential record for which to create the credential acknowledgement
     * @returns Object containing credential acknowledgement message and associated credential record
     *
     */
    suspend fun acceptCredential(
        credentialExchangeRecord: CredentialExchangeRecord
    ): Pair<CredentialExchangeRecord, CredentialAckMessageV2> {
        // Ensure the protocol and state are valid before proceeding
        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.CredentialReceived)

        val ackMessage = CredentialAckMessageV2(
            status = AckStatus.OK,
            threadId = credentialExchangeRecord.threadId
        ).apply {
            setThread(
                threadId = credentialExchangeRecord.threadId,
                parentThreadId = credentialExchangeRecord.parentThreadId
            )
        }

        updateState(
            credentialRecord = credentialExchangeRecord,
            newState = CredentialState.Done
        )

        return credentialExchangeRecord to ackMessage
    }

    /**
     * Process a received {@link CredentialAckMessage}.
     *
     * @param messageContext The message context containing a credential acknowledgement message
     * @returns credential record associated with the credential acknowledgement message
     *
     */
    suspend fun processAck(messageContext: InboundMessageContext): CredentialExchangeRecord{
        val connection = messageContext.connection

        val ackMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as CredentialAckMessageV2
        logger.debug("Processing credential ack with id ${ackMessage.id}")

        var credentialExchangeRecord = agent.credentialExchangeRepository.getSingleByQuery(
            "{\"threadId\": \"${ackMessage.threadId}\", \"role\": \"${CredentialRole.Issuer}\", \"connectionId\": \"${connection?.id}\"}"
        )
        credentialExchangeRecord.connectionId = connection?.id

        val requestMessage = agent.didCommMessageRepository.getTypedAgentMessage<RequestCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = RequestCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        ) ?: throw CredoError("Request message not found")

        val issueCredentialMessage = didCommMessageRepository.getTypedAgentMessage<IssueCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = IssueCredentialMessageV2.type,
            role = DidCommMessageRole.Sender
        ) ?: throw CredoError("issue credential message not found")

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.CredentialIssued)

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
            lastReceivedMessage = requestMessage,
            lastSentMessage = issueCredentialMessage,
            expectedConnectionId = credentialExchangeRecord.connectionId
        )

        updateState(credentialExchangeRecord, CredentialState.Done)
        return credentialExchangeRecord

    }

    /**
     * Create a {@link CredentialProblemReportMessageV2} to be sent.
     *
     * @param message message to send
     * @returns a {@link CredentialProblemReportMessageV2}
     *
     */
    suspend fun processAck(options: CreateCredentialProblemReportOptions): Pair<CredentialExchangeRecord, CredentialProblemReportMessageV2>{
        val credentialExchangeRecord = options.credentialExchangeRecord
        val message = CredentialProblemReportMessageV2(
            description = DescriptionOptions(
                en = options.description,
                code = CredentialProblemReportReason.IssuanceAbandoned.name
            )
        )

        message.setThread(
            threadId = credentialExchangeRecord.threadId,
            parentThreadId = credentialExchangeRecord.parentThreadId
        )

        return Pair(credentialExchangeRecord, message)
    }


    /**
     * Get all the format service objects for a given credential format
     * @param credentialFormats Map of format keys to any payload
     * @return List of matching CredentialFormatService instances
     */
    private fun getFormatServices(
        credentialFormats: Map<String, JsonElement>
    ): List<CredentialFormatService<*>> {
        return credentialFormats.keys.mapNotNull { getFormatServiceForFormatKey(it) }
            .distinct()
    }

    private fun getFormatServiceForFormatKey(formatKey: String): CredentialFormatService<*>? {
        return credentialFormats.find {  formatService -> formatService.formatKey == formatKey }
    }

    private fun getFormatServiceForFormat(format: String): CredentialFormatService<*>? {
        return credentialFormats.find { it.supportsFormat(format) }
    }

    protected fun getFormatServiceForRecordType(credentialRecordType: String): CredentialFormatService<*> {
        return credentialFormats.find { it.credentialRecordType == credentialRecordType }
            ?: throw CredoError(
                "No format service found for credential record type $credentialRecordType in v2 credential protocol"
            )
    }

    /**
     * Get all the format service objects for a given credential format from an incoming message
     * @param messageFormats the format objects containing the format name (eg indy)
     * @return the credential format service objects in an array - derived from format object keys
     */
    private fun getFormatServicesFromMessage(messageFormats: List<Format>): List<CredentialFormatService<*>> {
        return messageFormats.mapNotNull { getFormatServiceForFormat(it.format) }.distinct()
    }


    /**
     * Determine whether the agent should automatically respond to a received proposal message.
     *
     * This function evaluates the `autoAcceptCredential` configuration and compares the proposal with the previously
     * sent offer. It verifies if all format services agree on auto-responding and whether the credential previews match.
     *
     * @param agentContext The context providing access to services and agent configuration
     * @param credentialRecord The record representing the current credential exchange
     * @param proposalMessage The received proposal message to evaluate
     * @return True if the agent should automatically respond to the proposal, false otherwise
     *
     * @throws CredoError If no offer message is found to compare with the proposal
     */
    suspend fun shouldAutoRespondToProposal(
        credentialRecord: CredentialExchangeRecord,
        messageContext: InboundMessageContext
    ): Boolean {

        val proposalMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ProposeCredentialMessageV2

        val autoAccept = composeAutoAccept(
            credentialRecord.autoAcceptCredential,
            agent.agentConfig.autoAcceptCredential
        )

        // Always or Never settings short-circuit
        if (autoAccept == AutoAcceptCredential.Always) return true
        if (autoAccept == AutoAcceptCredential.Never) return false

        val offerMessage = findOfferMessage(credentialRecord.id) ?: return false

        val formatServices = getFormatServicesFromMessage(offerMessage.formats)

        for (formatService in formatServices) {
            val offerAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                offerMessage.formats,
                offerMessage.offerAttachments
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments
            )

            val shouldAutoRespondToFormat = formatService.shouldAutoRespondToProposal(
                credentialRecord = credentialRecord,
                offerAttachment = offerAttachment,
                proposalAttachment = proposalAttachment
            )


            if (!shouldAutoRespondToFormat) return false
        }

        if (proposalMessage.credentialPreview != null || offerMessage.credentialPreview != null) {
            if (proposalMessage.credentialPreview == null || offerMessage.credentialPreview == null) return false

            return arePreviewAttributesEqual(
                proposalMessage.credentialPreview.attributes,
                offerMessage.credentialPreview.attributes
            )
        }

        return true
    }


    /**
     * Determine whether a credential offer should be automatically accepted.
     *
     * This method evaluates the auto-accept configuration (either from the credential record or
     * from the global agent configuration). If set to `Always`, it immediately returns `true`.
     * If set to `Never`, it immediately returns `false`. If the configuration is conditional,
     * it proceeds to compare the credential offer message and the previously stored proposal
     * message (if available).
     *
     * It retrieves the proposal and offer messages, extracts their corresponding attachments using
     * the appropriate format services, and checks—per format—if they match and are acceptable.
     * It also compares credential previews to ensure they are aligned.
     *
     * @param credentialRecord The credential exchange record associated with this protocol instance
     * @param messageContext The inbound message context containing the credential offer
     *
     * @return `true` if the offer should be automatically accepted, `false` otherwise
     */
    suspend fun shouldAutoRespondToOffer(
        credentialRecord: CredentialExchangeRecord,
        messageContext: InboundMessageContext
    ): Boolean {
        val offerMessage = messageContext.plaintextMessage
            .let { MessageSerializer.decodeFromString(it) as? OfferCredentialMessageV2 }
            ?: return false

        val autoAccept = composeAutoAccept(
            credentialRecord.autoAcceptCredential,
            agent.agentConfig.autoAcceptCredential
        )

        // Always or Never settings short-circuit
        when (autoAccept) {
            AutoAcceptCredential.Always -> return true
            AutoAcceptCredential.Never -> return false
            else -> {}
        }

        val proposalMessage = findProposalMessage(credentialRecord.id) ?: return false
        val formatServices = getFormatServicesFromMessage(proposalMessage.formats)

        for (formatService in formatServices) {
            val offerAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                offerMessage.formats,
                offerMessage.offerAttachments
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments
            )

            val shouldAutoRespond = formatService.shouldAutoRespondToOffer(
                credentialRecord,
                offerAttachment,
                proposalAttachment
            )

            if (!shouldAutoRespond) return false
        }

        val offerPreview = offerMessage.credentialPreview?.attributes
        val proposalPreview = proposalMessage.credentialPreview?.attributes

        return arePreviewAttributesEqual(
            proposalPreview ?: emptyList(),
            offerPreview ?: emptyList()
        )
    }

    /**
     * Determine whether a credential request should be automatically accepted.
     *
     * This method evaluates the auto-accept configuration (either from the credential record or
     * from the global agent configuration). If set to `Always`, it immediately returns `true`.
     * If set to `Never`, it immediately returns `false`. If the configuration is conditional,
     * it proceeds to compare the credential request message with the previously exchanged offer
     * and proposal messages.
     *
     * It retrieves the proposal and offer messages from storage, decodes them, and extracts
     * their corresponding attachments using the appropriate format services. Then, for each
     * credential format, it invokes the corresponding logic to determine whether the format
     * contents align across proposal, offer, and request.
     *
     * The method iterates over all involved format services to ensure that each one deems
     * the credential exchange chain acceptable. If any format-specific check fails, it returns `false`.
     *
     * @param credentialRecord The credential exchange record associated with this protocol instance
     * @param messageContext The inbound message context containing the credential request
     *
     * @return `true` if the request should be automatically accepted, `false` otherwise
     */
    suspend fun shouldAutoRespondToRequest(credentialRecord: CredentialExchangeRecord, messageContext: InboundMessageContext): Boolean {

        val requestCredentialMessageV2 =
            MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestCredentialMessageV2


        val autoAccept = composeAutoAccept(
                credentialRecord.autoAcceptCredential,
                agent.agentConfig.autoAcceptCredential
        )

        if (autoAccept === AutoAcceptCredential.Always) return true
        if (autoAccept === AutoAcceptCredential.Never) return false

        val proposalMessage = findProposalMessage(credentialRecord.id) ?: return false
        val offerMessage = findOfferMessage(credentialRecord.id) ?: return false

        val formatServices = getFormatServicesFromMessage(offerMessage.formats)

        for (formatService in formatServices) {
            val offerAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                offerMessage.formats,
                offerMessage.offerAttachments
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments
            )

            val requestAttachment = this.credentialFormatCoordinator.getAttachmentForService(
                formatService,
                requestCredentialMessageV2.formats,
                requestCredentialMessageV2.requestAttachments
            )

            val shouldAutoRespondToFormat = formatService.shouldAutoRespondToRequest(
                    credentialRecord,
                    offerAttachment,
                    requestAttachment,
                    proposalAttachment,
            )

            if (!shouldAutoRespondToFormat) return false
        }

        return true
    }

    suspend fun shouldAutoRespondToCredential(credentialRecord: CredentialExchangeRecord, messageContext: InboundMessageContext): Boolean {
        val issueMessage =
            MessageSerializer.decodeFromString(messageContext.plaintextMessage) as IssueCredentialMessageV2


        val autoAccept = composeAutoAccept(
            credentialRecord.autoAcceptCredential,
            agent.agentConfig.autoAcceptCredential
        )

        if (autoAccept === AutoAcceptCredential.Always) return true
        if (autoAccept === AutoAcceptCredential.Never) return false

        val proposalMessage = findProposalMessage(credentialRecord.id) ?: return false
        val offerMessage = findOfferMessage(credentialRecord.id) ?: return false
        val requestMessage = findRequestMessage(credentialRecord.id) ?: return false

        val formatServices = getFormatServicesFromMessage(offerMessage.formats)

        for (formatService in formatServices) {
            val offerAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                offerMessage.formats,
                offerMessage.offerAttachments
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments
            )

            val requestAttachment = this.credentialFormatCoordinator.getAttachmentForService(
                formatService,
                requestMessage.formats,
                requestMessage.requestAttachments
            )

            val issueAttachment = this.credentialFormatCoordinator.getAttachmentForService(
                formatService,
                issueMessage.formats,
                issueMessage.credentialAttachments
            )

            val shouldAutoRespondToFormat = formatService.shouldAutoRespondToCredential(
                credentialRecord,
                offerAttachment,
                issueAttachment,
                requestAttachment,
                proposalAttachment,
            )

            if (!shouldAutoRespondToFormat) return false
        }

        return true
    }

    suspend fun findProposalMessage(credentialExchangeId: String): ProposeCredentialMessageV2? =
        findMessage(credentialExchangeId, ProposeCredentialMessageV2.type)

    suspend fun findRequestMessage(credentialExchangeId: String): RequestCredentialMessageV2? =
        findMessage(credentialExchangeId, RequestCredentialMessageV2.type)

    suspend fun findOfferMessage(credentialExchangeId: String): OfferCredentialMessageV2? =
        findMessage(credentialExchangeId, OfferCredentialMessageV2.type)

    private suspend inline fun <reified T> findMessage(
        credentialExchangeId: String,
        messageType: String
    ): T? {
        val messageStr = agent.didCommMessageRepository.getAgentMessage(
            associatedRecordId = credentialExchangeId,
            messageType = messageType
        ) ?: return null

        return runCatching {
            MessageSerializer.decodeFromString(messageStr) as T
        }.getOrElse {
            logger.warn("Failed to deserialize ${T::class.simpleName} for record ID $credentialExchangeId: ${it.message}")
            null
        }
    }

    private fun arePreviewAttributesEqual(
        firstAttributes: List<CredentialPreviewAttribute>,
        secondAttributes: List<CredentialPreviewAttribute>
    ): Boolean {
        if (firstAttributes.size != secondAttributes.size) return false

        val secondAttributeMap = secondAttributes.associateBy { it.name }

        // Verifica se não existem chaves duplicadas
        if (firstAttributes.map { it.name }.toSet().size != firstAttributes.size) return false
        if (secondAttributes.map { it.name }.toSet().size != secondAttributes.size) return false

        for (firstAttribute in firstAttributes) {
            val secondAttribute = secondAttributeMap[firstAttribute.name] ?: return false

            if (firstAttribute.value != secondAttribute.value) return false
            if (firstAttribute.mimeType != secondAttribute.mimeType) return false
        }

        return true
    }

    suspend fun updateState(credentialRecord: CredentialExchangeRecord, newState: CredentialState) {
        logger.debug("[updateState] Updating credential record ${credentialRecord.id} to state $newState (previous=${credentialRecord.state}")
        credentialRecord.setToState(newState)
        credentialExchangeRepository.update(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialRecord.copy()))
    }



    /*** -------------------------- ***/
//    /**
//     * Create a ``OfferCredentialMessageV2`` not bound to an existing credential record.
//     *
//     * @param options options for the offer.
//     * @return offer message and associated credential record.
//     */
//    suspend fun createOfferCredentialMessage(options: CreateCredentialOfferOptionsV2): Pair<OfferCredentialMessageV2, CredentialExchangeRecord> {
//        logger.debug("[2.0] createOfferCredentialMessage init")
//
//        if (options.connection == null) {
//            logger.info("Creating credential offer without connection. This should be used for out-of-band request message with handshake.")
//        }
//
//        val credentialRecord = CredentialExchangeRecord(
//            connectionId = options.connection?.id ?: "connectionless-offer",
//            threadId = BaseRecord.generateId(),
//            state = CredentialState.OfferSent,
//            autoAcceptCredential = options.autoAcceptCredential,
//            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
//        )
//
//        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(options.credentialDefinitionId)
//        val offer = Issuer().createCredentialOffer(
//            credentialDefinitionRecord.schemaId,
//            credentialDefinitionRecord.credDefId,
//            CredentialKeyCorrectnessProof(credentialDefinitionRecord.keyCorrectnessProof),
//        )
//        val attachment = Attachment.fromData(offer.toJson().toByteArray(), OfferCredentialMessageV2.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
//        val credentialPreview = CredentialPreviewV2(options.attributes)
//
//        val message = OfferCredentialMessageV2(
//            formats = options.formats,
//            offerAttachments = listOf(attachment),
//            comment = options.comment,
//            credentialPreview = credentialPreview,
//            goal = options.goal,
//            goalCode = options.goalCode,
//            replacementId = options.replacementId,
//        )
//
//        message.id = credentialRecord.threadId
//
//        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, message, credentialRecord.id)
//
//        credentialRecord.credentialAttributes = options.attributes
//        credentialExchangeRepository.save(credentialRecord)
//        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))
//
//        return Pair(message, credentialRecord)
//    }
//
//    /**
//     * Create a ``RequestCredentialMessageV2`` as response to a received credential offer.
//     *
//     * @param options options for the request.
//     * @return request message.
//     */
//     suspend fun createRequestCredentialMessage(options: AcceptOfferOptions): RequestCredentialMessageV2 {
//        logger.debug("[2.0] createRequestCredentialMessage init")
//
//        val credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
//        credentialRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
//        credentialRecord.assertState(CredentialState.OfferReceived)
//
//        val offerMessageJson = didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessageV2.type)
//        val offerMessage = OfferCredentialMessageV2.decode(offerMessageJson)
//        offerMessage.validateIndyAttachId()
//
//        val holderDid = options.holderDid ?: getHolderDid(credentialRecord)
//        val formats = offerMessage.formats
//        val goal = offerMessage.goal
//        val goalCode = offerMessage.goalCode
//        val comment = offerMessage.comment
//        val credentialOfferJson = offerMessage.getCredentialOffer()
//        val credentialOffer = CredentialOffer(credentialOfferJson)
//        val credentialDefinition = ledgerService.getCredentialDefinition(credentialOffer.credDefId())
//        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
//
//        val credReqTuple = Prover().createCredentialRequest(
//            entropy = null,
//            proverDid = holderDid,
//            credDef = CredentialDefinition(credentialDefinition),
//            linkSecret = linkSecret,
//            linkSecretId = agent.wallet.linkSecretId!!,
//            credOffer = credentialOffer,
//        )
//
//        credentialRecord.indyRequestMetadata = credReqTuple.metadata.toJson()
//        credentialRecord.credentialDefinitionId = credentialOffer.credDefId()
//
//        val attachment = Attachment.fromData(
//            credReqTuple.request.toJson().toByteArray(),
//            RequestCredentialMessageV2.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID,
//        )
//
//        val requestMessage = RequestCredentialMessageV2(
//            formats = formats,
//            requestAttachments = listOf(attachment),
//            goalCode = goalCode,
//            goal = goal,
//            comment = comment,
//        )
//        requestMessage.thread = ThreadDecorator(credentialRecord.threadId)
//
//        credentialRecord.credentialAttributes = offerMessage.credentialPreview?.attributes
//        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential
//
//        didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, requestMessage, credentialRecord.id)
//        updateState(credentialRecord, CredentialState.RequestSent)
//
//        return requestMessage
//    }
//
//    override suspend fun processRequestCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
//        logger.debug("[2.0] processRequestCredentialMessage init")
//        val requestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestCredentialMessageV2
//
//        require(requestMessage.getRequestAttachmentById(RequestCredentialMessageV2.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID) != null) {
//            "Indy attachment with id ${RequestCredentialMessageV2.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID} not found in request message"
//        }
//
//        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(
//            requestMessage.threadId,
//            null,
//        )
//
//        val connection = messageContext.assertReadyConnection()
//        credentialRecord.connectionId = connection.id
//
//        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, requestMessage, credentialRecord.id)
//        updateState(credentialRecord, CredentialState.RequestReceived)
//
//        return credentialRecord
//    }
//
//    /**
//     * Method called by {@link OfferCredentialHandlerV2} on reception of a offer credential message (2.0)
//     * We do the necessary processing here to accept the offer and do the state change, emit event etc.
//     * @param messageContext the inbound offer credential message
//     * @returns credential record appropriate for this incoming message (once accepted)
//     */
//    override suspend fun processOfferCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
//        logger.debug("[2.0] processOfferCredentialMessage init")
//
//        val offerMessage = OfferCredentialMessageV2.decode(messageContext.plaintextMessage)
//        offerMessage.validateIndyAttachId()
//
//        var credentialExchangeRecord = credentialExchangeRepository.findByThreadAndConnectionId(offerMessage.threadId, messageContext.connection?.id)
//
//        if (credentialExchangeRecord != null) {
//            agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, offerMessage, credentialExchangeRecord.id)
//            updateState(credentialExchangeRecord, CredentialState.OfferReceived)
//        } else {
//            val connection = messageContext.assertReadyConnection()
//            credentialExchangeRecord = CredentialExchangeRecord(
//                connectionId = connection.id,
//                threadId = offerMessage.threadId,
//                parentThreadId = offerMessage.threadId, // todo
//                state = CredentialState.OfferReceived,
//                role = CredentialRole.Holder,
//                protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
//            )
//
//            agent.didCommMessageRepository.saveAgentMessage(
//                role = DidCommMessageRole.Receiver,
//                agentMessage = offerMessage,
//                associatedRecordId = credentialExchangeRecord.id,
//            )
//
//            credentialExchangeRepository.save(credentialExchangeRecord)
//
//            agent.historyRepository.save(
//                HistoryRecord(
//                    historyType = HistoryType.CredentialOfferReceived,
//                    connectionId = credentialExchangeRecord.connectionId,
//                    theirLabel = connection.theirLabel,
//                    associatedRecordId = credentialExchangeRecord.id,
//                ),
//            )
//
//            agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy())) // accept credential?
//        }
//
//        return credentialExchangeRecord
//    }
//
//    /**
//     * Process a received ``IssueCredentialMessageV2``.
//     * This will store the credential, but not accept it yet.
//     * Use ``createAck(options:)`` after calling this method to accept the credential and create an ack message.
//     *
//     * @param messageContext message context containing the credential message.
//     * @return credential record associated with the credential message.
//     */
//    override suspend fun processIssueCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
//        logger.debug("[2.0] processIssueCredentialMessage init")
//        val issueMessage = IssueCredentialMessageV2.decode(messageContext.plaintextMessage)
//        val issueAttachment = issueMessage.getCredentialAttachmentById(IssueCredentialMessageV2.INDY_CREDENTIAL_ATTACHMENT_ID)
//
//        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(issueMessage.threadId, messageContext.connection?.id)
//        val credential = Credential(issueAttachment.getDataAsString())
//        logger.debug("Storing credential: ${credential.values()}")
//
//        val (schemaJson, _) = ledgerService.getSchema(credential.schemaId())
//        val schema = Schema(schemaJson)
//
//        val credentialDefinition = CredentialDefinition(ledgerService.getCredentialDefinition(credential.credDefId()))
//        val revocationRegistryJson = credential.revRegId()?.let { ledgerService.getRevocationRegistryDefinition(it) }
//        val revocationRegistry = revocationRegistryJson?.let { RevocationRegistryDefinition(it) }
//        if (revocationRegistry != null) {
//            GlobalScope.launch {
//                agent.revocationService.downloadTails(revocationRegistry)
//            }
//        }
//
//        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
//        val processedCredential = Prover().processCredential(
//            cred = credential,
//            credReqMetadata = CredentialRequestMetadata(credentialRecord.indyRequestMetadata!!),
//            linkSecret = linkSecret,
//            credDef = credentialDefinition,
//            revRegDef = revocationRegistry,
//        )
//
//        val credentialId = UUID.randomUUID().toString()
//
//        agent.credentialRepository.save(
//            CredentialRecord(
//                credentialId = credentialId,
//                credentialRevocationId = processedCredential.revRegIndex()?.toString(),
//                revocationRegistryId = processedCredential.revRegId(),
//                linkSecretId = agent.wallet.linkSecretId!!,
//                credentialObject = processedCredential,
//                schemaId = processedCredential.schemaId(),
//                schemaName = schema.name(),
//                schemaVersion = schema.version(),
//                schemaIssuerId = schema.issuerId(),
//                issuerId = credentialDefinition.issuerId(),
//                credentialDefinitionId = processedCredential.credDefId(),
//                revocationNotification = null,
//            ),
//        )
//
//        credentialRecord.credentials.add(CredentialRecordBinding("indy", credentialId))
//        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, issueMessage, credentialRecord.id)
//        updateState(credentialRecord, CredentialState.CredentialReceived)
//
//        return credentialRecord
//    }
//
//    override suspend fun createCredentialAckMessage(options: AcceptCredentialOptions): CredentialAckMessageV2 {
//        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
//        credentialRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
//        credentialRecord.assertState(CredentialState.CredentialReceived)
//
//        updateState(credentialRecord, CredentialState.Done)
//
//        return CredentialAckMessageV2(credentialRecord.threadId, AckStatus.OK)
//    }
//
//    /**
//     * Create a ``IssueCredentialMessageV2`` as response to a received credential request.
//     *
//     * @param options options for the credential issueance.
//     * @return credential message.
//     */
//    override suspend fun createIssueCredentialMessage(options: AcceptRequestOptions): IssueCredentialMessageV2 {
//        logger.debug("[2.0] createIssueCredentialMessage init")
//
//        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
//        credentialRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
//        credentialRecord.assertState(CredentialState.RequestReceived)
//
//        val offerMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessageV2.type)
//        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessageV2
//        val requestMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, RequestCredentialMessageV2.type)
//        val requestMessage = MessageSerializer.decodeFromString(requestMessageJson) as RequestCredentialMessageV2
//
//        val offerAttachment = offerMessage.getOfferAttachmentById(OfferCredentialMessageV2.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
//        val requestAttachment = requestMessage.getRequestAttachmentById(RequestCredentialMessageV2.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID)
//        check(offerAttachment != null && requestAttachment != null) {
//            "Missing data payload in offer or request attachment in credential Record ${credentialRecord.id}"
//        }
//
//        val formats = offerMessage.formats
//        val goal = offerMessage.goal
//        val comment = options.comment
//        val offer = CredentialOffer(offerAttachment.getDataAsString())
//        val request = CredentialRequest(requestAttachment.getDataAsString())
//        val credDefId = offer.credDefId()
//        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(credDefId)
//
//        var revocationConfig: CredentialRevocationConfig? = null
//        val revocationRecord = agent.revocationRegistryRepository.findByCredDefId(credDefId)
//        if (revocationRecord != null) {
//            val registryIndex = agent.revocationRegistryRepository.incrementRegistryIndex(credDefId)
//            logger.debug("Revocation registry index: $registryIndex")
//            revocationConfig = CredentialRevocationConfig(
//                regDef = RevocationRegistryDefinition(revocationRecord.revocRegDef),
//                regDefPrivate = RevocationRegistryDefinitionPrivate(revocationRecord.revocRegPrivate),
//                statusList = RevocationStatusList(revocationRecord.revocStatusList),
//                registryIndex = registryIndex.toUInt(),
//            )
//        }
//
//        val credential = Issuer().createCredential(
//            CredentialDefinition(credentialDefinitionRecord.credDef),
//            CredentialDefinitionPrivate(credentialDefinitionRecord.credDefPriv),
//            offer,
//            request,
//            credentialRecord.getCredentialInfo()!!.claims,
//            null,
//            revocationConfig,
//        )
//
//        val attachment = Attachment.fromData(
//            credential.toJson().toByteArray(),
//            IssueCredentialMessageV2.INDY_CREDENTIAL_ATTACHMENT_ID,
//        )
//        val issueMessage = IssueCredentialMessageV2(
//            formats,
//            listOf(attachment),
//            goal,
//            comment,
//        )
//        issueMessage.thread = ThreadDecorator(credentialRecord.threadId)
//
//        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, issueMessage, credentialRecord.id)
//        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential
//        updateState(credentialRecord, CredentialState.CredentialIssued)
//
//        return issueMessage
//    }
//
//    /**
//     * Create an ``CredentialProblemReportMessagev2`` as response to a received offer.
//     *
//     * @param options options for the problem report message.
//     * @return credential problem report message.
//     */
//    override suspend fun createOfferDeclinedProblemReport(options: AcceptOfferOptions): CredentialProblemReportNotificationMessage {
//        logger.info("[2.0] createOfferDeclinedProblemReport init")
//        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
//        credentialRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
//        credentialRecord.assertState(CredentialState.OfferReceived)
//
//        updateState(credentialRecord, CredentialState.Declined)
//
//        return CredentialProblemReportNotificationMessage()
//    }
//
//    private suspend fun getHolderDid(credentialRecord: CredentialExchangeRecord): String {
//        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
//        return connection.did
//    }
//

//
//    suspend fun processAck(messageContext: InboundMessageContext): CredentialExchangeRecord {
//        val ackMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as CredentialAckMessageV2
//
//        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(ackMessage.threadId, messageContext.connection?.id)
//        updateState(credentialRecord, CredentialState.Done)
//
//        return credentialRecord
//    }

//    /**
//     * Create an ``CredentialProblemReportMessage`` as response to a received offer.
//     *
//     * @param options options for the problem report message.
//     * @return credential problem report message.
//     */
//    override suspend fun createOfferDeclinedProblemReport(options: AcceptOfferOptions): CredentialProblemReportMessage {
//        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
//        credentialRecord.setToProtocolVersionV2()
//        credentialRecord.assertState(CredentialState.OfferReceived)
//
//        updateState(credentialRecord, CredentialState.Declined)
//
//        return CredentialProblemReportMessage(credentialRecord.threadId)
//    }




}
