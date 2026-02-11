package org.hyperledger.ariesframework.credentials.v2

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsCredentialFormatService
import org.hyperledger.ariesframework.anoncreds.formats.LegacyIndyCredentialFormatService
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.CredentialsConstants
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatCoordinator
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialProposalOptions
import org.hyperledger.ariesframework.credentials.models.AcceptOfferParams
import org.hyperledger.ariesframework.credentials.models.AcceptProposalParams
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptionsV2
import org.hyperledger.ariesframework.credentials.models.AcceptRequestParams
import org.hyperledger.ariesframework.credentials.models.CreateCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.models.CreateCredentialParams
import org.hyperledger.ariesframework.credentials.models.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.models.CredentialRole
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.models.NegotiateCredentialOfferOptions
import org.hyperledger.ariesframework.credentials.models.NegotiateCredentialProposalOptions
import org.hyperledger.ariesframework.credentials.models.ProcessCredentialParams
import org.hyperledger.ariesframework.credentials.models.ProcessOfferParams
import org.hyperledger.ariesframework.credentials.models.ProcessRequestParams
import org.hyperledger.ariesframework.credentials.models.RequestCredentialParams
import org.hyperledger.ariesframework.credentials.models.problemreport.CredentialProblemReportReason
import org.hyperledger.ariesframework.credentials.operation.CreateProposalParams
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.v2.handlers.OfferCredentialHandlerV2
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialAckMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialProblemReportMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.ProposeCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateProposalOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.DeclineCredentialOfferOptions
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.credentials.v2.models.problemreport.CreateCredentialProblemReportOptions
import org.hyperledger.ariesframework.credentials.v2.models.problemreport.SendCredentialProblemReportOptions
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.problemreports.messages.DescriptionOptions
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.hyperledger.ariesframework.util.LogUtil
import org.hyperledger.ariesframework.util.PrintLongLine
import org.hyperledger.ariesframework.util.composeAutoAccept
import org.slf4j.LoggerFactory
import java.util.UUID

class CredentialServiceV2(val agent: Agent) {
    //private val logger = LoggerFactory.getLogger(CredentialServiceV2::class.java)
    private val credentialExchangeRepository = agent.credentialExchangeRepository
    private val didCommMessageRepository = agent.didCommMessageRepository
    private val credentialFormats = listOf<CredentialFormatService<*>>(AnoncredsCredentialFormatService(agent = agent), LegacyIndyCredentialFormatService(agent = agent))
    private val credentialFormatCoordinator = CredentialFormatCoordinator(agent, credentialFormats)

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
        LogUtil.info(this) { "Get the Format Service and Create Proposal Message" }

        val formatServices = this.getFormatServices(options.credentialFormats)
        if (formatServices.isEmpty()) {
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
            options.goal,
        )

        val proposalCredentialMessageV2 = this.credentialFormatCoordinator.createProposal(createProposalParams)

        LogUtil.info(this) { "Save record and emit state change event: ${proposalCredentialMessageV2}" }
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
        LogUtil.info(this) { "Processing credential proposal"}
        val connection = messageContext.connection
        val proposalMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ProposeCredentialMessageV2
        LogUtil.info(this) { "credential proposal id = ${proposalMessage.id}"}

        val credentialRecord = agent.credentialExchangeRepository.getByThreadAndRole(proposalMessage.threadId, CredentialRole.Issuer)

        val formatServices = getFormatServicesFromMessage(proposalMessage.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process proposal. No supported formats")
        }

        if (credentialRecord != null) {
            LogUtil.info(this) { "Credential record already exists " +
                    "[id=${credentialRecord.id} | w3cid=${credentialRecord.w3cCredentialId} | revReg=${credentialRecord.revRegId}]" }
            val proposalCredentialMessage = agent.didCommMessageRepository.getTypedAgentMessage<ProposeCredentialMessageV2>(
                associatedRecordId = credentialRecord.id,
                messageType = ProposeCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver,
            )

            val offerCredentialMessage = didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                associatedRecordId = credentialRecord.id,
                messageType = OfferCredentialMessageV2.type,
                role = DidCommMessageRole.Sender,
            )

            // assert
            credentialRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
            credentialRecord.assertState(CredentialState.OfferSent)

            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = proposalCredentialMessage,
                lastSentMessage = offerCredentialMessage,
                expectedConnectionId = credentialRecord.connectionId,
            )

            // verification of authorization
            if (credentialRecord.connectionId == null) {
                agent.connectionService.matchIncomingMessageToRequestMessageInOutOfBandExchange(
                    messageContext = messageContext,
                    expectedConnectionId = credentialRecord.connectionId,
                )
                credentialRecord.connectionId = connection?.id
            }

            credentialFormatCoordinator.processProposal(
                credentialExchangeRecord = credentialRecord,
                formatServices = formatServices,
                message = proposalMessage,
            )

            credentialExchangeRepository.save(credentialRecord)
            updateState(credentialRecord, CredentialState.ProposalReceived)
            return credentialRecord
        }

        LogUtil.info(this) { "No credential record found for offer, creating a new one" }

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
        )

        val credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = connection?.id,
            threadId = proposalMessage.threadId,
            parentThreadId = proposalMessage.thread?.parentThreadId,
            state = CredentialState.ProposalReceived,
            role = CredentialRole.Issuer,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
        )

        LogUtil.info(this) { "Credential record created = ${credentialExchangeRecord.logSummary()}" }

        credentialFormatCoordinator.processProposal(
            credentialExchangeRecord = credentialExchangeRecord,
            formatServices = formatServices,
            message = proposalMessage,
        )

        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))
        LogUtil.info(this) { "Save credential and emit" }
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
        options: AcceptCredentialProposalOptions,
    ): Pair<OfferCredentialMessageV2, CredentialExchangeRecord> {
        LogUtil.info(this) { "Accepting proposal" }
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
                role = DidCommMessageRole.Receiver,
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
            credentialFormats = credentialFormats,
        )
        val offerMessage = credentialFormatCoordinator.acceptProposal(acceptParams)

        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.OfferSent)
        LogUtil.info(this) { "Proposal accept" }
        return Pair(offerMessage, credentialExchangeRecord)
    }

    /**
     * Negotiate a credential proposal as issuer (by sending a credential offer message) to the connection
     * associated with the credential record.
     *
     * @param options configuration for the offer see {@link NegotiateCredentialProposalOptions}
     * @returns Credential exchange record associated with the credential offer
     */
    suspend fun negotiateProposal(options: NegotiateCredentialProposalOptions): Pair<CredentialExchangeRecord, OfferCredentialMessageV2> {
        LogUtil.info(this) { "Init of negotiate proposal" }
        val credentialExchangeRecord = options.credentialExchangeRecord
        val credentialFormats = options.credentialFormats
        val comment = options.comment
        val goal = options.goal
        val goalCode = options.goalCode

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.ProposalReceived)

        if (credentialExchangeRecord.connectionId.isNullOrBlank()) {
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
            credentialFormats = credentialFormats,
        )
        val offerMessage = credentialFormatCoordinator.createOffer(createCredential)
        val autoAcceptCredential = options.autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential

        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.OfferSent)

        LogUtil.info(this) { "proposal negotiated" }
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
    suspend fun createOffer(options: CreateCredentialOfferOptionsV2): Pair<CredentialExchangeRecord, OfferCredentialMessageV2> {
        LogUtil.info(this) { "create offer" }
        val connectionRecord = options.connectionRecord
        val credentialFormats = options.credentialFormat
        val autoAcceptCredential = options.autoAcceptCredential
        val comment = options.comment
        val goal = options.goal
        val goalCode = options.goalCode

        val formatServices = getFormatServices(credentialFormats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create offer. No supported formats.")
        }

        val credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = connectionRecord?.id,
            threadId = BaseRecord.generateId(),
            state = CredentialState.OfferSent,
            role = CredentialRole.Issuer,
            autoAcceptCredential = autoAcceptCredential,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
        )
        LogUtil.info(this) { "credential created ${credentialExchangeRecord.logSummary()}" }

        val createCredential = CreateCredentialParams(
            credentialRecord = credentialExchangeRecord,
            formatServices = formatServices,
            comment = comment,
            goal = goal,
            goalCode = goalCode,
            credentialFormats = credentialFormats,
        )
        val offerMessage = credentialFormatCoordinator.createOffer(createCredential)


        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))

        LogUtil.info(this) { "Saving record and emitting state changed for credential exchange record ${credentialExchangeRecord.id}" }
        return Pair(credentialExchangeRecord, offerMessage)
    }

    /**
     * Method called by {@link OfferCredentialHandlerV2} on reception of a offer credential message
     * We do the necessary processing here to accept the offer and do the state change, emit event etc.
     * @param messageContext the inbound offer credential message
     * @returns credential record appropriate for this incoming message (once accepted)
     */
    suspend fun processOffer(messageContext: InboundMessageContext): CredentialExchangeRecord {
        LogUtil.info(this) { "processing offer" }
        val connection = messageContext.connection
        val offerMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as OfferCredentialMessageV2
        //PrintLongLine.print("offer message long: $offerMessage")

        LogUtil.info(this) { "Processing credential offer with id ${offerMessage.id}"}

        var credentialExchangeRecord = agent.credentialExchangeRepository.findByThreadRoleAndConnectionId(
            threadId = offerMessage.threadId,
            role = CredentialRole.Holder,
            connectionId = connection?.id,
        )

        LogUtil.info(this) { "Processing credential ${credentialExchangeRecord?.logSummary()}"}

        val formatServices = getFormatServicesFromMessage(offerMessage.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process offer. No supported formats")
        }

        if (credentialExchangeRecord != null) {
            val proposeMessage = agent.didCommMessageRepository.getTypedAgentMessage<ProposeCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = ProposeCredentialMessageV2.type,
                role = DidCommMessageRole.Sender,
            )

            val offerCredentialMessage = agent.didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = OfferCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver,
            )

            credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
            credentialExchangeRecord.assertState(CredentialState.ProposalSent)
            credentialExchangeRecord.updateComment(offerMessage.comment)

            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = offerCredentialMessage,
                lastSentMessage = proposeMessage,
                expectedConnectionId = credentialExchangeRecord.connectionId,
            )

            val processOfferParams = ProcessOfferParams(
                credentialExchangeRecord = credentialExchangeRecord,
                message = offerMessage,
                formatService = formatServices,
            )
            credentialFormatCoordinator.processOffer(processOfferParams)

            credentialExchangeRepository.save(credentialExchangeRecord)
            updateState(credentialExchangeRecord, CredentialState.OfferReceived)
            LogUtil.info(this) { "Processed credential ${credentialExchangeRecord?.logSummary()}"}
            return credentialExchangeRecord
        }

        LogUtil.info(this) { "No credential record found for offer, creating a new one" }
        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
        )

        credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = connection?.id,
            threadId = offerMessage.threadId,
            parentThreadId = offerMessage.thread?.parentThreadId,
            state = CredentialState.OfferReceived,
            role = CredentialRole.Holder,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
            formats = offerMessage.formats,
            comment = offerMessage.comment,
        )
        LogUtil.info(this) { "Credential created = ${credentialExchangeRecord.logSummary()}"}

        val processOfferParams = ProcessOfferParams(
            credentialExchangeRecord = credentialExchangeRecord,
            message = offerMessage,
            formatService = formatServices,
        )
        credentialFormatCoordinator.processOffer(processOfferParams)

        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))

        LogUtil.info(this) { "Saving credential record and emit offer-received event" }
        return credentialExchangeRecord
    }

    suspend fun acceptOffer(options: AcceptCredentialOfferOptionsV2): Pair<CredentialExchangeRecord, RequestCredentialMessageV2> {
        LogUtil.info(this) { "Accept offer" }
        val credentialExchangeRecord = options.credentialExchangeRecord
        val credentialFormats = options.credentialFormats

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.OfferReceived)
        LogUtil.info(this) { "Credential record = ${credentialExchangeRecord.logSummary()}" }

        var formatServices = getFormatServicesByList(credentialFormats!!)
        if (formatServices.isEmpty()) {
            val offerMessage: OfferCredentialMessageV2? = agent.didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = OfferCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver,
            )

            formatServices = if (offerMessage != null) getFormatServicesFromMessage(offerMessage.formats) else emptyList()
        }

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to accept offer. No supported formats provided as input or in offer message")
        }

        val acceptOfferParams = AcceptOfferParams(
            credentialRecord = credentialExchangeRecord,
            formatServices = formatServices,
            comment = options.comment,
            goal = options.goal,
            goalCode = options.goalCode,
            credentialFormats = credentialFormats,
        )

        val requestCredentialMessageV2 = credentialFormatCoordinator.acceptOffer(acceptOfferParams)
        credentialExchangeRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential

        updateState(credentialExchangeRecord, CredentialState.RequestSent)
        LogUtil.info(this) { "Saving credential record and emit offer-received event" }
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
    suspend fun negotiateOffer(options: NegotiateCredentialOfferOptions): Pair<CredentialExchangeRecord, ProposeCredentialMessageV2> {
        LogUtil.info(this) { "negotiate offer" }
        val credentialExchangeRecord = options.credentialExchangeRecord
        val autoAcceptCredential = options.autoAcceptCredential
        val credentialFormats = options.credentialFormat

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.OfferReceived)

        if (credentialExchangeRecord.connectionId == null) {
            throw CredoError("No connectionId found for credential record '${credentialExchangeRecord.id}'. Connection-less issuance does not support negotiation.")
        }

        val formatServices = getFormatServices(credentialFormats)
        if (credentialFormats.isEmpty()) {
            throw CredoError("Unable to create proposal. No supported formats")
        }

        val createProposalParams = CreateProposalParams(
            credentialFormats,
            formatServices,
            credentialExchangeRecord,
            options.comment,
            options.goalCode,
            options.goal,
        )
        val proposeMessage = credentialFormatCoordinator.createProposal(createProposalParams)
        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.ProposalSent)

        LogUtil.info(this) { "offer negotiated" }
        return Pair(credentialExchangeRecord, proposeMessage)
    }

    /**
     * Create a {@link RequestCredentialMessageV2} as beginning of protocol process.
     * @returns Object containing offer message and associated credential record
     *
     */
    suspend fun createRequest(options: CreateCredentialRequestOptions): Pair<CredentialExchangeRecord, RequestCredentialMessageV2> {
        LogUtil.info(this) { "create request" }
        val autoAcceptCredential = options.autoAcceptCredential
        val connectionRecord = options.connectionRecord
        val credentialFormats = options.credentialFormats

        val formatServices = getFormatServicesFromMessage(credentialFormats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create request. No supported formats")
        }

        val credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = connectionRecord.id,
            threadId = UUID.randomUUID().toString(),
            state = CredentialState.RequestSent,
            role = CredentialRole.Holder,
            autoAcceptCredential = autoAcceptCredential,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
        )
        LogUtil.info(this) { "credential = ${credentialExchangeRecord.logSummary()}" }

        val requestParams = RequestCredentialParams(
            credentialFormats = credentialFormats, // map
            formatServices = formatServices,
            credentialRecord = credentialExchangeRecord,
            comment = options.comment,
            goalCode = options.goalCode,
            goal = options.goal,
        )
        val requestMessage = credentialFormatCoordinator.createRequest(requestParams)

        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))

        LogUtil.info(this) { "Saving record and emitting state changed for credential exchange record ${credentialExchangeRecord.id}" }
        return Pair(credentialExchangeRecord, requestMessage)
    }

    /**
     * Process a received {@link RequestCredentialMessage}. This will not accept the credential request
     * or send a credential. It will only update the existing credential record with
     * the information from the credential request message. Use {@link createCredential}
     * after calling this method to create a credential.
     *
     * @param messageContext The message context containing a v2 credential request message
     * @returns credential record associated with the credential request message
     *
     */
    suspend fun processRequest(messageContext: InboundMessageContext): CredentialExchangeRecord {
        LogUtil.info(this) { "processing request" }
        val connection = messageContext.connection
        val message = messageContext.message

        val requestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestCredentialMessageV2
        LogUtil.info(this) { "Processing credential request with id ${requestMessage.id}" }

        var credentialExchangeRecord = agent.credentialExchangeRepository.findSingleByQuery(
            "{\"threadId\": \"${requestMessage.threadId}\", \"role\": \"${CredentialRole.Issuer}\"}",
        )

        val formatServices = getFormatServicesFromMessage(requestMessage.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process proposal. No supported formats")
        }

        if (credentialExchangeRecord != null) {
            val proposalCredentialMessage = agent.didCommMessageRepository.getTypedAgentMessage<ProposeCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = ProposeCredentialMessageV2.type,
                role = DidCommMessageRole.Receiver,
            )

            val offerCredentialMessage = didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
                associatedRecordId = credentialExchangeRecord.id,
                messageType = OfferCredentialMessageV2.type,
                role = DidCommMessageRole.Sender,
            )

            // assert
            credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
            credentialExchangeRecord.assertState(CredentialState.OfferSent)

            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = proposalCredentialMessage,
                lastSentMessage = offerCredentialMessage,
                expectedConnectionId = credentialExchangeRecord.connectionId,
            )

            // verification of authorization
            if (credentialExchangeRecord.connectionId == null) {
                agent.connectionService.matchIncomingMessageToRequestMessageInOutOfBandExchange(
                    messageContext = messageContext,
                    expectedConnectionId = credentialExchangeRecord.connectionId,
                )
                credentialExchangeRecord.connectionId = connection?.id
            }

            val processRequestParams = ProcessRequestParams(
                credentialExchangeRecord = credentialExchangeRecord,
                message = requestMessage,
                formatService = formatServices,
            )
            credentialFormatCoordinator.processRequest(processRequestParams)

            credentialExchangeRepository.save(credentialExchangeRecord)
            updateState(credentialExchangeRecord, CredentialState.RequestReceived)

            return credentialExchangeRecord
        }

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
        )

        LogUtil.info(this) { "No credential record found for offer, creating a new one" }

        credentialExchangeRecord = CredentialExchangeRecord(
            connectionId = connection?.id,
            threadId = requestMessage.threadId,
            parentThreadId = requestMessage.thread?.parentThreadId,
            state = CredentialState.RequestReceived,
            role = CredentialRole.Issuer,
            protocolVersion = CredentialsConstants.PROTOCOL_VERSION_V2,
        )
        LogUtil.info(this) { "credential created = ${credentialExchangeRecord.logSummary()}" }

        val processRequestParams = ProcessRequestParams(
            credentialExchangeRecord = credentialExchangeRecord,
            message = requestMessage,
            formatService = formatServices,
        )
        credentialFormatCoordinator.processRequest(processRequestParams)

        agent.credentialExchangeRepository.save(credentialExchangeRecord)
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy()))
        LogUtil.info(this) { "Saving credential record and emit request-received event" }
        return credentialExchangeRecord
    }

    suspend fun acceptRequest(options: AcceptRequestOptionsV2): Pair<CredentialExchangeRecord, IssueCredentialMessageV2> {
        LogUtil.info(this) { "accepting request" }
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
                role = DidCommMessageRole.Sender,
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
            credentialFormat = credentialFormats,
        )
        val message = credentialFormatCoordinator.acceptRequest(acceptRequestParams)

        credentialExchangeRecord.autoAcceptCredential = autoAcceptCredential ?: credentialExchangeRecord.autoAcceptCredential
        updateState(credentialExchangeRecord, CredentialState.CredentialIssued)
        LogUtil.info(this) { "request accepted" }
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
    suspend fun processCredential(messageContext: InboundMessageContext): CredentialExchangeRecord {
        LogUtil.info(this) { "processing request" }
        val connection = messageContext.connection
        val message = messageContext.plaintextMessage

        val issueCredential = MessageSerializer.decodeFromString(message) as IssueCredentialMessageV2
        LogUtil.info(this) { "Processing credential with id ${issueCredential.id}" }


        val credentialExchangeRecord = agent.credentialExchangeRepository.getByThreadAndRoleAndConnectionId(
            threadId = issueCredential.threadId,
            connectionId = connection?.id,
            role = CredentialRole.Holder.name,
        )

        val requestMessage = agent.didCommMessageRepository.getTypedAgentMessage<RequestCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = RequestCredentialMessageV2.type,
            role = DidCommMessageRole.Sender,
        ) ?: throw CredoError("Request message not found")

        val offerMessage = didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver,
        )
        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.RequestSent)

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
            lastReceivedMessage = requestMessage,
            lastSentMessage = offerMessage,
            expectedConnectionId = credentialExchangeRecord.connectionId,
        )

        val formatServices = getFormatServicesFromMessage(issueCredential.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process credential. No supported formats")
        }

        val processCredentialParams = ProcessCredentialParams(
            credentialExchangeRecord = credentialExchangeRecord,
            formatService = formatServices,
            requestCredentialMessageV2 = requestMessage,
            message = issueCredential,
        )
        //PrintLongLine.print("processCredentialParams ==> $processCredentialParams")
        //PrintLongLine.print("processCredentialParams message==> ${processCredentialParams.message.credentialAttachments}")

        credentialFormatCoordinator.processCredential(processCredentialParams)
        updateState(credentialExchangeRecord, CredentialState.CredentialReceived)

        LogUtil.info(this) { "request accepted" }
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
        credentialExchangeRecord: CredentialExchangeRecord,
    ): Pair<CredentialExchangeRecord, CredentialAckMessageV2> {
        LogUtil.info(this) { "accepting credential" }
        // Ensure the protocol and state are valid before proceeding
        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.CredentialReceived)

        val ackMessage = CredentialAckMessageV2(
            status = AckStatus.OK,
            threadId = credentialExchangeRecord.threadId,
        ).apply {
            setThread(
                threadId = credentialExchangeRecord.threadId,
                parentThreadId = credentialExchangeRecord.parentThreadId,
            )
        }
        LogUtil.info(this) { "ack message generated = ${ackMessage}" }

        updateState(
            credentialRecord = credentialExchangeRecord,
            newState = CredentialState.Done,
        )


        LogUtil.info(this) { "credential accepted = Done" }
        return credentialExchangeRecord to ackMessage
    }

    /**
     * Process a received {@link CredentialAckMessage}.
     *
     * @param messageContext The message context containing a credential acknowledgement message
     * @returns credential record associated with the credential acknowledgement message
     *
     */
    suspend fun processAck(messageContext: InboundMessageContext): CredentialExchangeRecord {
        LogUtil.info(this) { "processing ack" }
        val connection = messageContext.connection

        val ackMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as CredentialAckMessageV2
        LogUtil.info(this) { "Processing credential ack with id ${ackMessage.id}"}

        var credentialExchangeRecord = agent.credentialExchangeRepository.getSingleByQuery(
            "{\"threadId\": \"${ackMessage.threadId}\", \"role\": \"${CredentialRole.Issuer}\", \"connectionId\": \"${connection?.id}\"}",
        )
        credentialExchangeRecord.connectionId = connection?.id

        val requestMessage = agent.didCommMessageRepository.getTypedAgentMessage<RequestCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = RequestCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver,
        ) ?: throw CredoError("Request message not found")

        val issueCredentialMessage = didCommMessageRepository.getTypedAgentMessage<IssueCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = IssueCredentialMessageV2.type,
            role = DidCommMessageRole.Sender,
        ) ?: throw CredoError("issue credential message not found")

        credentialExchangeRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialExchangeRecord.assertState(CredentialState.CredentialIssued)

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
            lastReceivedMessage = requestMessage,
            lastSentMessage = issueCredentialMessage,
            expectedConnectionId = credentialExchangeRecord.connectionId,
        )

        updateState(credentialExchangeRecord, CredentialState.Done)
        LogUtil.info(this) { "ack processed" }
        return credentialExchangeRecord
    }

    /**
     * Create a {@link CredentialProblemReportMessageV2} to be sent.
     *
     * @param message message to send
     * @returns a {@link CredentialProblemReportMessageV2}
     *
     */
    suspend fun createProblemReport(options: CreateCredentialProblemReportOptions): Pair<CredentialExchangeRecord, CredentialProblemReportMessageV2> {
        LogUtil.info(this) { "creating problem report" }
        val credentialExchangeRecord = options.credentialExchangeRecord
        val message = CredentialProblemReportMessageV2(
            description = DescriptionOptions(
                en = options.description,
                code = CredentialProblemReportReason.IssuanceAbandoned.name,
            ),
        )

        message.setThread(
            threadId = credentialExchangeRecord.threadId,
            parentThreadId = credentialExchangeRecord.parentThreadId,
        )

        LogUtil.info(this) { "problem report created" }
        return Pair(credentialExchangeRecord, message)
    }

    /**
     * Get all the format service objects for a given credential format
     * @param credentialFormats Map of format keys to any payload
     * @return List of matching CredentialFormatService instances
     */
    private fun getFormatServices(
        credentialFormats: Map<String, JsonElement>,
    ): List<CredentialFormatService<*>> {
        return credentialFormats.keys.mapNotNull { getFormatServiceForFormatKey(it) }
            .distinct()
    }

    private fun getFormatServicesByList(
        credentialFormats: List<Format>,
    ): List<CredentialFormatService<*>> {
        return credentialFormats.mapNotNull { getFormatServiceForFormat(it.attachId) }
            .distinct()
    }

    private fun getFormatServiceForFormatKey(formatKey: String): CredentialFormatService<*>? {
        return credentialFormats.find { formatService -> formatService.formatKey == formatKey }
    }

    private fun getFormatServiceForFormat(format: String): CredentialFormatService<*>? {
        return credentialFormats.find { it.supportsFormat(format) }
    }

    protected fun getFormatServiceForRecordType(credentialRecordType: String): CredentialFormatService<*> {
        return credentialFormats.find { it.credentialRecordType == credentialRecordType }
            ?: throw CredoError(
                "No format service found for credential record type $credentialRecordType in v2 credential protocol",
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
     * Create an ``CredentialProblemReportMessagev2`` as response to a received offer.
     *
     * @param options options for the problem report message.
     * @return credential problem report message.
     */
    suspend fun declineOffer(credentialRecord: CredentialExchangeRecord, options: DeclineCredentialOfferOptions): CredentialExchangeRecord {
        LogUtil.info(this) { "declining offer" }
        credentialRecord.assertProtocolVersion(CredentialsConstants.PROTOCOL_VERSION_V2)
        credentialRecord.assertState(CredentialState.OfferReceived)

        if (options.sendProblemReport != null && options.sendProblemReport) {
            val sendCredentialProblemReportOptions = SendCredentialProblemReportOptions(
                credentialRecordId = credentialRecord.id,
                description = options.problemReportDescription ?: "offer declined",
            )
            sendProblemReport(sendCredentialProblemReportOptions)
        }

        updateState(credentialRecord, CredentialState.Declined)
        LogUtil.info(this) { "offer declined = Declined" }
        return credentialRecord
    }

    /**
     * Send problem report message for a credential record
     * @param credentialRecordId The id of the credential record for which to send problem report
     * @returns credential record associated with the credential problem report message
     */
    suspend fun sendProblemReport(options: SendCredentialProblemReportOptions): CredentialExchangeRecord {
        LogUtil.info(this) { "sending problem report" }
        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
        val offerMessage = agent.credentialServiceV2.findOfferMessage(credentialRecord.id)

        val createCredentialProblemReportOptions = CreateCredentialProblemReportOptions(
            credentialExchangeRecord = credentialRecord,
            description = options.description,
        )
        val (credentialExchangeRecord, credentialProblemReportMessageV2) = createProblemReport(createCredentialProblemReportOptions)

        var connectionRecord: ConnectionRecord? = null
        if (credentialRecord.connectionId != null) {
            connectionRecord = agent.connectionService.getById(credentialRecord.connectionId!!)
        }
        connectionRecord?.assertReady()

        // If there's no connection (so connection-less, we require the state to be offer received)
        if (connectionRecord == null) {
            credentialRecord.assertState(CredentialState.OfferReceived)

            if (offerMessage == null) {
                throw CredoError("No offer message found for credential record with id '${credentialRecord.id}'")
            }
        }

        // [TODO]como faz para colocar o connection se for connectiion-less???? mudei no Outboundmessage
        agent.messageSender.send(OutboundMessage(credentialProblemReportMessageV2, connectionRecord))

        LogUtil.info(this) { "problem report sended" }
        return credentialRecord
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
        messageContext: InboundMessageContext,
    ): Boolean {
        val proposalMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ProposeCredentialMessageV2

        val autoAccept = composeAutoAccept(
            credentialRecord.autoAcceptCredential,
            agent.agentConfig.autoAcceptCredential,
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
                offerMessage.offerAttachments,
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments,
            )

            val shouldAutoRespondToFormat = formatService.shouldAutoRespondToProposal(
                credentialRecord = credentialRecord,
                offerAttachment = offerAttachment,
                proposalAttachment = proposalAttachment,
            )

            if (!shouldAutoRespondToFormat) return false
        }

        if (proposalMessage.credentialPreview != null || offerMessage.credentialPreview != null) {
            if (proposalMessage.credentialPreview == null || offerMessage.credentialPreview == null) return false

            return arePreviewAttributesEqual(
                proposalMessage.credentialPreview.attributes,
                offerMessage.credentialPreview.attributes,
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
        messageContext: InboundMessageContext,
    ): Boolean {
        val offerMessage = messageContext.plaintextMessage
            .let { MessageSerializer.decodeFromString(it) as? OfferCredentialMessageV2 }
            ?: return false

        val autoAccept = composeAutoAccept(
            credentialRecord.autoAcceptCredential,
            agent.agentConfig.autoAcceptCredential,
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
                offerMessage.offerAttachments,
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments,
            )

            val shouldAutoRespond = formatService.shouldAutoRespondToOffer(
                credentialRecord,
                offerAttachment,
                proposalAttachment,
            )

            if (!shouldAutoRespond) return false
        }

        val offerPreview = offerMessage.credentialPreview?.attributes
        val proposalPreview = proposalMessage.credentialPreview?.attributes

        return arePreviewAttributesEqual(
            proposalPreview ?: emptyList(),
            offerPreview ?: emptyList(),
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
            agent.agentConfig.autoAcceptCredential,
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
                offerMessage.offerAttachments,
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments,
            )

            val requestAttachment = this.credentialFormatCoordinator.getAttachmentForService(
                formatService,
                requestCredentialMessageV2.formats,
                requestCredentialMessageV2.requestAttachments,
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
            agent.agentConfig.autoAcceptCredential,
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
                offerMessage.offerAttachments,
            )

            val proposalAttachment = credentialFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments,
            )

            val requestAttachment = this.credentialFormatCoordinator.getAttachmentForService(
                formatService,
                requestMessage.formats,
                requestMessage.requestAttachments,
            )

            val issueAttachment = this.credentialFormatCoordinator.getAttachmentForService(
                formatService,
                issueMessage.formats,
                issueMessage.credentialAttachments,
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
        messageType: String,
    ): T? {
        val messageStr = agent.didCommMessageRepository.getAgentMessage(
            associatedRecordId = credentialExchangeId,
            messageType = messageType,
        ) ?: return null
        return runCatching {
            MessageSerializer.decodeFromString(messageStr) as T
        }.getOrElse {
            LogUtil.warn(this) { "Failed to deserialize ${T::class.simpleName} for record ID $credentialExchangeId: ${it.message}"}
            null
        }
    }

    private fun arePreviewAttributesEqual(
        firstAttributes: List<CredentialPreviewAttribute>,
        secondAttributes: List<CredentialPreviewAttribute>,
    ): Boolean {
        if (firstAttributes.size != secondAttributes.size) return false

        val secondAttributeMap = secondAttributes.associateBy { it.name }

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
        credentialRecord.setToState(newState)
        credentialExchangeRepository.update(credentialRecord)
        LogUtil.info(this) { "Update credential record ${credentialRecord.id} to state $newState (previous=${credentialRecord.state})" }
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialRecord.copy()))
    }
}
