package org.hyperledger.ariesframework.credentials.v2

import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialDefinitionPrivate
import anoncreds_uniffi.CredentialKeyCorrectnessProof
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.CredentialRequest
import anoncreds_uniffi.CredentialRequestMetadata
import anoncreds_uniffi.CredentialRevocationConfig
import anoncreds_uniffi.Issuer
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.RevocationRegistryDefinitionPrivate
import anoncreds_uniffi.RevocationStatusList
import anoncreds_uniffi.Schema
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.credentials.v1.AcceptCredentialOptions
import org.hyperledger.ariesframework.credentials.v1.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.v1.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.v1.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.v1.messages.IssueCredentialMessage
import org.hyperledger.ariesframework.credentials.v1.messages.OfferCredentialMessage
import org.hyperledger.ariesframework.credentials.v1.messages.RequestCredentialMessage
import org.hyperledger.ariesframework.credentials.v1.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.ProposeCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.credentials.v2.handlers.CredentialAckHandlerV2
import org.hyperledger.ariesframework.credentials.v2.handlers.IssueCredentialHandlerV2
import org.hyperledger.ariesframework.credentials.v2.handlers.OfferCredentialHandlerV2
import org.hyperledger.ariesframework.credentials.v2.handlers.RequestCredentialHandlerV2
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialAckMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateCredentialOfferOptionsV2
import org.hyperledger.ariesframework.problemreports.messages.CredentialProblemReportNotificationMessage
import org.hyperledger.ariesframework.credentials.v2.models.CreateProposalOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentials.v2.models.CredentialRole
import org.hyperledger.ariesframework.didcomm.models.Protocol
import org.hyperledger.ariesframework.revocationnotification.message.RevocationNotificationMessageV1
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.slf4j.LoggerFactory
import java.util.Date
import java.util.UUID


class CredentialServiceV2(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(CredentialServiceV2::class.java)

    private val credentialExchangeRepository = agent.credentialExchangeRepository
    private val credentialRepository = agent.credentialRepository
    private val didCommMessageRepository = agent.didCommMessageRepository
    private val connectionService = agent.connectionService
    private val ledgerService = agent.ledgerService



    init {
        registerHandlers(agent.dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(CredentialAckHandlerV2(agent))
        dispatcher.registerHandler(IssueCredentialHandlerV2(agent))
        dispatcher.registerHandler(OfferCredentialHandlerV2(agent))
        dispatcher.registerHandler(RequestCredentialHandlerV2(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(CredentialAckMessageV2.type, CredentialAckMessageV2::class)
        MessageSerializer.registerMessage(IssueCredentialMessageV2.type, IssueCredentialMessageV2::class)
        MessageSerializer.registerMessage(OfferCredentialMessageV2.type, OfferCredentialMessageV2::class)
        MessageSerializer.registerMessage(ProposeCredentialMessageV2.type, ProposeCredentialMessageV2::class)
        MessageSerializer.registerMessage(RequestCredentialMessageV2.type, RequestCredentialMessageV2::class)
    }


    /*
        propose-credential --> offer-credential --> request-credential --> issue-credential
        ProposeCredentialMessageV2
        OfferCredentialMessageV2
        RequestCredentialMessageV2
        IssueCredentialMessageV2
     */

    /**
     * Create a ``ProposeCredentialMessageV2`` not bound to an existing credential record.
     *
     * @param options options for the proposal.
     * @return proposal message and associated credential record.
     */
    suspend fun createProposeCredentialMessageV2(options: CreateProposalOptionsV2): Pair<ProposeCredentialMessageV2, CredentialExchangeRecord> {
        logger.debug("[IDD] initializing createProposeCredentialMessageV2")

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

    /**
     * Create a ``OfferCredentialMessageV2`` not bound to an existing credential record.
     *
     * @param options options for the offer.
     * @return offer message and associated credential record.
     */
    suspend fun createOfferCredentialMessageV2(options: CreateCredentialOfferOptionsV2): Pair<OfferCredentialMessageV2, CredentialExchangeRecord> {

        if (options.connection == null) {
            logger.info("Creating credential offer without connection. This should be used for out-of-band request message with handshake.")
        }

        val credentialRecord = CredentialExchangeRecord(
            connectionId = options.connection?.id ?: "connectionless-offer",
            threadId = BaseRecord.generateId(),
            state = CredentialState.OfferSent,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = CredentialsV2Constants.PROTOCOL_VERSION,
        )

        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(options.credentialDefinitionId)
        val offer = Issuer().createCredentialOffer(
            credentialDefinitionRecord.schemaId,
            credentialDefinitionRecord.credDefId,
            CredentialKeyCorrectnessProof(credentialDefinitionRecord.keyCorrectnessProof),
        )
        val attachment = Attachment.fromData(offer.toJson().toByteArray(), OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        val credentialPreview = CredentialPreviewV2(options.attributes)

        val message = OfferCredentialMessageV2(
            formats = options.formats,
            offerAttachments = listOf(attachment),
            comment = options.comment,
            credentialPreview = credentialPreview,
            goal = options.goal,
            goalCode = options.goalCode,
            replacementId = options.replacementId
        )
        message.id = credentialRecord.threadId

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, message, credentialRecord.id)

        credentialRecord.credentialAttributes = options.attributes
        credentialExchangeRepository.save(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))

        return Pair(message, credentialRecord)
    }

    /**
     * Create a ``RequestCredentialMessageV2`` as response to a received credential offer.
     *
     * @param options options for the request.
     * @return request message.
     */
    suspend fun createRequestCredentialMessage(options: AcceptOfferOptions): RequestCredentialMessageV2 {
        logger.debug("[IDD] initializing createRequestCredentialMessage")
        logger.info("[IDD] credentialRecordid: ${options.credentialRecordId}")

        val credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion(CredentialsV2Constants.PROTOCOL_VERSION)
        credentialRecord.assertState(CredentialState.OfferReceived)
        logger.info("[IDD] credentialRecord: ${credentialRecord.toString()}")

        val offerMessageJson = didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessageV2.type)
        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessageV2


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

        logger.info("[IDD] credentialDefinition: ${credentialDefinition.toString()}")
        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
        logger.info("[IDD] linkSecret: ${linkSecret.toString()}")
        val credReqTuple = Prover().createCredentialRequest(
            null,
            holderDid,
            CredentialDefinition(credentialDefinition),
            linkSecret,
            agent.wallet.linkSecretId!!,
            credentialOffer,
        )

        credentialRecord.indyRequestMetadata = credReqTuple.metadata.toJson()
        credentialRecord.credentialDefinitionId = credentialOffer.credDefId()

        logger.info("[IDD] credentialRecord: ${credentialRecord.toString()}")

        val attachment = Attachment.fromData(
            credReqTuple.request.toJson().toByteArray(),
            RequestCredentialMessageV2.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID,
        )

        val requestMessage = RequestCredentialMessageV2(
            formats,
            listOf(attachment),
            goalCode,
            goal,
            comment
        )
        requestMessage.thread = ThreadDecorator(credentialRecord.threadId)
        logger.info("[IDD][2.0][10] requestMessage: ${requestMessage}")

        credentialRecord.credentialAttributes = offerMessage.credentialPreview?.attributes
        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential

        logger.info("[IDD] requestMessage BEFORE: ${requestMessage.toString()}")
        logger.info("[IDD] offerMessage: ${offerMessage.toJsonString()}")

        didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, requestMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.RequestSent)

        logger.info("[IDD] credentialRecord: ${credentialRecord.toString()}")
        logger.info("[IDD][2.0] requestMessage AFTER UPDATE: ${requestMessage.toString()}")

        return requestMessage
    }

    suspend fun processRequestCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
        logger.debug("[IDD] initializing processRequestCredentialMessage")
        val requestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestCredentialMessage

        require(requestMessage.getRequestAttachmentById(RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID) != null) {
            "Indy attachment with id ${RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID} not found in request message"
        }

        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(
            requestMessage.threadId,
            null,
        )

        // The credential offer may have been a connectionless-offer.
        val connection = messageContext.assertReadyConnection()
        credentialRecord.connectionId = connection.id

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, requestMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.RequestReceived)

        return credentialRecord
    }

    /**
     * Method called by {@link OfferCredentialHandlerV2} on reception of a offer credential message
     * We do the necessary processing here to accept the offer and do the state change, emit event etc.
     * @param messageContext the inbound offer credential message
     * @returns credential record appropriate for this incoming message (once accepted)
     */
    suspend fun processOfferCredentialMessageV2(messageContext: InboundMessageContext): CredentialExchangeRecord {
        logger.info("[IDD] initializing processOfferCredentialMessageV2")

        val offerMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as OfferCredentialMessageV2
        logger.info("[IDD] Processing credential offer with id ${offerMessage.id}")

        checkNotNull(offerMessage.findIndyFormatByAttachId()) {
            "Indy attachment with id ${OfferCredentialMessageV2.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID} not found in offer message"
        }

//        var allcredentialrecords = credentialExchangeRepository.getAll();
//        allcredentialrecords.forEach { record ->
//            logger.info("--> [IDD]Credential Record: $record")
//        }

        var credentialExchangeRecord = credentialExchangeRepository.findByThreadAndConnectionId(offerMessage.threadId, messageContext.connection?.id)
        if (credentialExchangeRecord != null) {
            logger.info("[IDD] credential record already exists")
            agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, offerMessage, credentialExchangeRecord.id)
            updateState(credentialExchangeRecord, CredentialState.OfferReceived)
        } else {
            logger.info("[IDD] credential record doesnt exists")
            val connection = messageContext.assertReadyConnection()
            credentialExchangeRecord = CredentialExchangeRecord(
                connectionId = connection.id,
                threadId = offerMessage.threadId,
                parentThreadId = offerMessage.threadId, //todo
                state = CredentialState.OfferReceived,
                role = CredentialRole.Holder,
                protocolVersion = CredentialsV2Constants.PROTOCOL_VERSION,
            )

            logger.info("[IDD] Saving credential record")
            agent.didCommMessageRepository.saveAgentMessage(
                DidCommMessageRole.Receiver,
                offerMessage,
                credentialExchangeRecord.id
            )

            checkNotNull(credentialExchangeRecord){
                throw IllegalArgumentException("Credential record cannot be null")
            }

            credentialExchangeRepository.save(credentialExchangeRecord)

            logger.info("[IDD] emit offer-received event")
            agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialExchangeRecord.copy())) // accept credential?

        }

        logger.info("[log] return of cred: ${credentialExchangeRecord.toString()}")
        return credentialExchangeRecord
    }



    /**
     * Process a received ``IssueCredentialMessage``. This will store the credential, but not accept it yet.
     * Use ``createAck(options:)`` after calling this method to accept the credential and create an ack message.
     *
     * @param messageContext message context containing the credential message.
     * @return credential record associated with the credential message.
     */
    suspend fun processIssueCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
        logger.debug("[IDD] initializing processIssueCredentialMessageV2")
        val issueMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as IssueCredentialMessageV2

        logger.info("[IDD] issueMessage ${issueMessage.toJsonString()}")
        val issueAttachment = issueMessage.getCredentialAttachmentById(IssueCredentialMessageV2.INDY_CREDENTIAL_ATTACHMENT_ID)
        check(issueAttachment != null) {
            "Indy attachment with id ${IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID} not found in issue message"
        }
        logger.info("[IDD] issueAttachment ${issueAttachment.toString()}")
        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(issueMessage.threadId, messageContext.connection?.id)
        logger.info("[IDD] credentialRecord ${credentialRecord.toString()}")

        val credential = Credential(issueAttachment.getDataAsString())
        logger.info("[IDD] credential ${credential}")

        logger.debug("Storing credential: ${credential.values()}")
        val (schemaJson, _) = ledgerService.getSchema(credential.schemaId())
        val schema = Schema(schemaJson)
        val credentialDefinition = CredentialDefinition(ledgerService.getCredentialDefinition(credential.credDefId()))
        val revocationRegistryJson = credential.revRegId()?.let { ledgerService.getRevocationRegistryDefinition(it) }
        val revocationRegistry = revocationRegistryJson?.let { RevocationRegistryDefinition(it) }
        if (revocationRegistry != null) {
            GlobalScope.launch {
                agent.revocationService.downloadTails(revocationRegistry)
            }
        }
        logger.info("[IDD] credentialDefinition ${credentialDefinition.toJson().toString()}")
        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
        val processedCredential = Prover().processCredential(
            credential,
            CredentialRequestMetadata(credentialRecord.indyRequestMetadata!!),
            linkSecret,
            credentialDefinition,
            revocationRegistry,
        )

        val credentialId = UUID.randomUUID().toString()

        val revocationMessage = messageContext.plaintextMessage?.let {
            MessageSerializer.decodeFromString(it) as? RevocationNotificationMessageV2
        }
        logger.info("[IDD] revocationMessage ${revocationMessage?.toJsonString()}")

        val revocationNotification = revocationMessage?.let {
            RevocationNotification(
                comment = it.comment,
                revocationDate = Date()
            )
        } ?: RevocationNotification()

        logger.info("[IDD] revocationNotification ${revocationNotification.toString()}")
        try {
            //credentialRecord =
            agent.credentialRepository.save(
                CredentialRecord(
                    credentialId = credentialId,
                    credentialRevocationId = processedCredential.revRegIndex()?.toString(),
                    revocationRegistryId = processedCredential.revRegId(),
                    linkSecretId = agent.wallet.linkSecretId!!,
                    credentialObject = processedCredential,
                    schemaId = processedCredential.schemaId(),
                    schemaName = schema.name(),
                    schemaVersion = schema.version(),
                    schemaIssuerId = schema.issuerId(),
                    issuerId = credentialDefinition.issuerId(),
                    credentialDefinitionId = processedCredential.credDefId(),
                    revocationNotification = revocationNotification
                ),
            )
        }catch (e:Exception){
            logger.error("[IDD] message error: ${e.message} || ${e.cause} || ${e.stackTrace}")
        }

        logger.info("[IDD] credentialRecord ${credentialRecord.toString()}")
        credentialRecord.credentials.add(CredentialRecordBinding("indy", credentialId))
        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, issueMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.CredentialReceived)

        logger.info("[IDD] after updating status ${credentialRecord}")
        return credentialRecord
    }

    suspend fun createCredentialAckMessageV2(options: AcceptCredentialOptions): CredentialAckMessageV2 {
        logger.debug("[IDD] initializing createCredentialAckMessageV2")
        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        logger.debug("[IDD] createCredentialAckMessageV2-credentialRecord ${credentialRecord.toString()}")
        credentialRecord.assertProtocolVersion(CredentialsV2Constants.PROTOCOL_VERSION)
        credentialRecord.assertState(CredentialState.CredentialReceived)

        logger.debug("[IDD] createCredentialAckMessageV2 ${credentialRecord.toString()}")

        updateState(credentialRecord, CredentialState.Done)

        return CredentialAckMessageV2(credentialRecord.threadId, AckStatus.OK)
    }


    /**
     * Create a ``IssueCredentialMessage`` as response to a received credential request.
     *
     * @param options options for the credential issueance.
     * @return credential message.
     */
    suspend fun createIssueCredentialMessageV2(options: AcceptRequestOptions): IssueCredentialMessageV2 {
        logger.debug("[IDD] initializing createIssueCredentialMessageV2")
        logger.debug("[IDD] ioptions: ${options}")

        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion(CredentialsV2Constants.PROTOCOL_VERSION)
        credentialRecord.assertState(CredentialState.RequestReceived)

        val offerMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessageV2.type)
        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessageV2
        val requestMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, RequestCredentialMessageV2.type)
        val requestMessage = MessageSerializer.decodeFromString(requestMessageJson) as RequestCredentialMessageV2

        val offerAttachment = offerMessage.getOfferAttachmentById(OfferCredentialMessageV2.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        val requestAttachment = requestMessage.getRequestAttachmentById(RequestCredentialMessageV2.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID)
        check(offerAttachment != null && requestAttachment != null) {
            "Missing data payload in offer or request attachment in credential Record ${credentialRecord.id}"
        }

        val formats = offerMessage.formats
        val goal = offerMessage.goal
        val comment = offerMessage.comment
        val offer = CredentialOffer(offerAttachment.getDataAsString())
        val request = CredentialRequest(requestAttachment.getDataAsString())
        val credDefId = offer.credDefId()
        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(credDefId)

        var revocationConfig: CredentialRevocationConfig? = null
        val revocationRecord = agent.revocationRegistryRepository.findByCredDefId(credDefId)
        if (revocationRecord != null) {
            val registryIndex = agent.revocationRegistryRepository.incrementRegistryIndex(credDefId)
            logger.debug("Revocation registry index: $registryIndex")
            revocationConfig = CredentialRevocationConfig(
                regDef = RevocationRegistryDefinition(revocationRecord.revocRegDef),
                regDefPrivate = RevocationRegistryDefinitionPrivate(revocationRecord.revocRegPrivate),
                statusList = RevocationStatusList(revocationRecord.revocStatusList),
                registryIndex = registryIndex.toUInt(),
            )
        }

        val credential = Issuer().createCredential(
            CredentialDefinition(credentialDefinitionRecord.credDef),
            CredentialDefinitionPrivate(credentialDefinitionRecord.credDefPriv),
            offer,
            request,
            credentialRecord.getCredentialInfo()!!.claims,
            null,
            revocationConfig,
        )

        val attachment = Attachment.fromData(
            credential.toJson().toByteArray(),
            IssueCredentialMessageV2.INDY_CREDENTIAL_ATTACHMENT_ID,
        )
        val issueMessage = IssueCredentialMessageV2(
            formats,
            listOf(attachment),
            goal,
            options.comment,
        )
        issueMessage.thread = ThreadDecorator(credentialRecord.threadId)

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, issueMessage, credentialRecord.id)
        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential
        updateState(credentialRecord, CredentialState.CredentialIssued)

        return issueMessage
    }

    /**
     * Create an ``CredentialProblemReportMessagev2`` as response to a received offer.
     *
     * @param options options for the problem report message.
     * @return credential problem report message.
     */
    suspend fun createOfferDeclinedProblemReport(options: AcceptOfferOptions): CredentialProblemReportNotificationMessage {
        logger.info("[IDD] PROBLEM REPORT")
        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion(CredentialsV2Constants.PROTOCOL_VERSION)
        credentialRecord.assertState(CredentialState.OfferReceived)

        updateState(credentialRecord, CredentialState.Declined)

        return CredentialProblemReportNotificationMessage()
    }

    private suspend fun getHolderDid(credentialRecord: CredentialExchangeRecord): String {
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        return connection.did
    }

    suspend fun updateState(credentialRecord: CredentialExchangeRecord, newState: CredentialState) {
        logger.info("[IDD][UPDATE STATE] Updating credential record ${credentialRecord.id} to state ${newState} (previous=${credentialRecord.state}")
        credentialRecord.setToState(newState)
        credentialExchangeRepository.update(credentialRecord)

        logger.info("[IDD] publish event of ${credentialRecord.toString()}")
        agent.eventBus.publish(AgentEvents.CredentialEventV2(credentialRecord.copy()))
    }

}