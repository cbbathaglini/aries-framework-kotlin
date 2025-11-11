package org.hyperledger.ariesframework.proofs.v2

import android.util.Log
import anoncreds_uniffi.PresentationRequest
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RequestedCredential
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsProofFormatService
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsCredentialsForProofRequest
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.GetCredentialsForProofRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.anoncreds.model.holder.CredentialForProofRequest
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.history.models.HistoryType
import org.hyperledger.ariesframework.history.repository.HistoryRecord
import org.hyperledger.ariesframework.problemreports.messages.PresentationProblemReportMessageV2
import org.hyperledger.ariesframework.proofs.models.AcceptProofProposalParams
import org.hyperledger.ariesframework.proofs.models.AcceptProofProposalServiceParams
import org.hyperledger.ariesframework.proofs.models.AcceptProofRequestOptions
import org.hyperledger.ariesframework.proofs.models.AcceptProofRequestParams
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.CreateProofProblemReportOptions
import org.hyperledger.ariesframework.proofs.models.CreateProofProposalParams
import org.hyperledger.ariesframework.proofs.models.CreateProofRequestOptions
import org.hyperledger.ariesframework.proofs.models.CreateProposalProofOptionsV2
import org.hyperledger.ariesframework.proofs.models.GetCredentialsForRequestOptions
import org.hyperledger.ariesframework.proofs.models.NegotiateProofProposalOptions
import org.hyperledger.ariesframework.proofs.models.NegotiateProofRequestParams
import org.hyperledger.ariesframework.proofs.models.ProofConstants
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.ProofRole
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.models.RequestProofRequestParams
import org.hyperledger.ariesframework.proofs.models.RequestedAttributeAnonCreds
import org.hyperledger.ariesframework.proofs.models.RequestedCredentialsAnoncreds
import org.hyperledger.ariesframework.proofs.models.RequestedPredicateAnonCreds
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentialsAnonCreds
import org.hyperledger.ariesframework.proofs.models.RevocationInterval
import org.hyperledger.ariesframework.proofs.models.SelectCredentialsForRequestOptions
import org.hyperledger.ariesframework.proofs.models.composeAutoAccept
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.PresentationVerifier
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesframework.proofs.utils.RecoverFromLedger
import org.hyperledger.ariesframework.proofs.utils.W3cUtils
import org.hyperledger.ariesframework.proofs.v2.formats.ProofFormatCoordinator
import org.hyperledger.ariesframework.proofs.v2.formats.ProofFormatService
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationAckMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationProblemReportErrorV2
import org.hyperledger.ariesframework.proofs.v2.messages.ProposePresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.hyperledger.ariesframework.util.concurrentForEach
import org.slf4j.LoggerFactory
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.log
import kotlin.math.max

class ProofServiceV2(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(ProofServiceV2::class.java)

    private val proofRepository = agent.proofRepository
    private val didCommMessageRepository = agent.didCommMessageRepository
    private val proofFormats = listOf<ProofFormatService<*>>(
        AnoncredsProofFormatService(agent = agent),
    )
    private val proofFormatCoordinator = ProofFormatCoordinator(agent, proofFormats)

    suspend fun createProposal(options: CreateProposalProofOptionsV2): Pair<ProposePresentationMessageV2, ProofExchangeRecord> {
        logger.debug("Get the Format Service and Create Proposal Message")

        val (connectionRecord, proofFormats, comment, autoAcceptProof, goalCode, goal, parentThreadId) = options

        val formatServices = this.getFormatServices(proofFormats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create proposal. No supported formats")
        }

        val proofRecord = ProofExchangeRecord(
            connectionId = connectionRecord.id,
            threadId = BaseRecord.generateId(),
            parentThreadId = parentThreadId,
            state = ProofState.ProposalSent,
            role = ProofRole.Prover,
            autoAcceptProof = autoAcceptProof,
            protocolVersion = ProofConstants.PROTOCOL_VERSION_V2,
        )

        val createProposalParams = CreateProofProposalParams(
            formatServices = formatServices,
            proofFormats = proofFormats,
            proofRecord = proofRecord,
            comment = comment,
            goal = goal,
            goalCode = goalCode,
        )

        val proposalMessage: ProposePresentationMessageV2 =
            this.proofFormatCoordinator.createProposal(createProposalParams)

        logger.debug("Save record and emit state change event")
        proofRepository.save(proofRecord)
        agent.eventBus.publish(AgentEvents.ProofEventV2(proofRecord.copy()))

        return Pair(proposalMessage, proofRecord)
    }

    suspend fun processProposal(messageContext: InboundMessageContext): ProofExchangeRecord {
        logger.info("PROCESS PROPOSAL -------------------------")
        val proofProposalMessage =
            MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ProposePresentationMessageV2
        val connection = messageContext.assertReadyConnection()

        logger.debug("Processing presentation proposal with id ${proofProposalMessage.id}")

        var proofRecord: ProofExchangeRecord? = proofRepository.findByThreadRoleAndConnection(
            threadId = proofProposalMessage.threadId,
            role = ProofRole.Verifier,
            connectionId = connection.id,
        )

        val formatServices = getFormatServicesFromMessage(proofProposalMessage.formats)
        if (formatServices.size == 0) {
            throw CredoError("Unable to process proposal. No supported formats")
        }

        if (proofRecord != null) {
            val lastReceivedMessage =
                didCommMessageRepository.getTypedAgentMessage<IssueCredentialMessageV2>(
                    associatedRecordId = proofRecord.id,
                    messageType = ProposePresentationMessageV2.type,
                    role = DidCommMessageRole.Receiver,
                ) ?: throw CredoError("propose presentation message not found")

            val lastSentMessage =
                didCommMessageRepository.getTypedAgentMessage<IssueCredentialMessageV2>(
                    associatedRecordId = proofRecord.id,
                    messageType = RequestPresentationMessageV2.type,
                    role = DidCommMessageRole.Sender,
                ) ?: throw CredoError("propose presentation message not found")

            // Assert
            proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
            proofRecord.assertState(ProofState.RequestSent)

            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = lastReceivedMessage,
                lastSentMessage = lastSentMessage,
                expectedConnectionId = proofRecord.connectionId,
            )

            proofFormatCoordinator.processProposal(
                proofRecord = proofRecord,
                formatServices = formatServices,
                message = proofProposalMessage,
            )

            updateState(proofRecord, ProofState.ProposalReceived)

            return proofRecord
        }
        // Assert
        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
        )

        // No proof record exists with thread id
        proofRecord = ProofExchangeRecord(
            connectionId = connection.id,
            threadId = proofProposalMessage.threadId,
            state = ProofState.ProposalReceived,
            role = ProofRole.Verifier,
            protocolVersion = ProofConstants.PROTOCOL_VERSION_V2,
            parentThreadId = proofProposalMessage.thread?.parentThreadId,
        )

        proofFormatCoordinator.processProposal(
            proofRecord = proofRecord,
            formatServices = formatServices,
            message = proofProposalMessage,
        )

        // Save record and emit event
        proofRepository.save(proofRecord)
        agent.eventBus.publish(AgentEvents.ProofEventV2(proofRecord.copy()))

        return proofRecord
    }

    suspend fun acceptProposal(params: AcceptProofProposalServiceParams): Pair<RequestPresentationMessageV2, ProofExchangeRecord> {
        logger.info("ACCEPT PROPOSAL -------------------------")
        val (proofRecord, proofFormats, comment, goalCode, goal, autoAcceptProof, willConfirm) = params

        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.ProposalReceived)

        var formatServices = getFormatServices(proofFormats ?: emptyMap())
        if (formatServices.isEmpty()) {
            val proposalMessage = didCommMessageRepository.getAgentMessage(
                associatedRecordId = proofRecord.id,
                messageType = ProposePresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            )
            val proposalMessageV2 =
                MessageSerializer.decodeFromString(proposalMessage) as ProposePresentationMessageV2
            formatServices = getFormatServicesFromMessage(proposalMessageV2.formats)
        }

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to accept proposal. No supported formats provided as input or in proposal message")
        }

        val acceptParams = AcceptProofProposalParams(
            proofRecord = proofRecord,
            formatServices = formatServices,
            comment = comment,
            goal = goal,
            goalCode = goalCode,
            proofFormats = proofFormats,
            presentMultiple = false,
            willConfirm = willConfirm,
        )

        val message: RequestPresentationMessageV2 =
            proofFormatCoordinator.acceptProposal(acceptParams)

        proofRecord.autoAcceptProof = autoAcceptProof ?: proofRecord.autoAcceptProof
        updateState(proofRecord, ProofState.RequestSent)

        return Pair(message, proofRecord)
    }

    suspend fun negotiateProposal(params: NegotiateProofProposalOptions): Pair<RequestPresentationMessageV2, ProofExchangeRecord> {
        val (proofRecord, proofFormats, autoAcceptProof, comment, goalCode, goal, willConfirm) = params

        // Assert
        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.ProposalReceived)

        if (proofRecord.connectionId.isNullOrBlank()) {
            throw CredoError("No connectionId found for proof record '${proofRecord.id}'. Connection-less verification does not support negotiation.")
        }

        var formatServices = getFormatServices(proofFormats ?: emptyMap())
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create request. No supported formats.")
        }

        val requestProofRequestParams = RequestProofRequestParams(
            proofRecord = proofRecord,
            proofFormats = proofFormats,
            formatServices = formatServices,
            comment = comment,
            goalCode = goalCode,
            goal = goal,
            presentMultiple = false, // Not supported at the moment
            willConfirm = willConfirm,
        )

        val requestMessage = proofFormatCoordinator.createRequest(requestProofRequestParams)

        proofRecord.autoAcceptProof = autoAcceptProof ?: proofRecord.autoAcceptProof
        updateState(proofRecord, ProofState.RequestSent)

        return Pair(requestMessage, proofRecord)
    }

    /**
     * Create and initialize a verifiable proof request using the DIDComm Present Proof protocol v2.
     *
     * @param params Options containing proof record, proof formats, connection info, and request metadata
     * @return Pair containing the generated RequestPresentationMessageV2 and the associated ProofExchangeRecord
     * @throws CredoError if no supported proof formats are found
     */
    suspend fun createRequest(params: CreateProofRequestOptions): Pair<RequestPresentationMessageV2, ProofExchangeRecord> {
        val (anoncredsProofRequest, formats, proofFormats, parentThreadId, connectionRecord, comment, goalCode, goal, autoAcceptProof: AutoAcceptProof, willConfirm) = params

        val formatServices = getFormatServicesByList(formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create request. No supported formats")
        }

        logger.info("connnnnnid: ${connectionRecord?.id ?: "connectionless-proof-request"}")
        val proofRecord = ProofExchangeRecord(
            connectionId = connectionRecord?.id ?: "connectionless-proof-request",
            threadId = BaseRecord.generateId(),
            state = ProofState.RequestSent,
            role = ProofRole.Verifier,
            autoAcceptProof = autoAcceptProof,
            protocolVersion = ProofConstants.PROTOCOL_VERSION_V2,
        )

        val requestParams = RequestProofRequestParams(
            proofRecord = proofRecord,
            proofFormats = proofFormats,
            formatServices = formatServices,
            comment = comment,
            goalCode = goalCode,
            goal = goal,
            willConfirm = willConfirm,
            attachmentId = formats.first().attachmentId!!,
        )

        val requestMessage: RequestPresentationMessageV2 =
            proofFormatCoordinator.createRequest(requestParams)

        logger.info("request message: ${requestMessage.requestPresentationAttachments.size}")
        logger.debug("Saving record and emitting state changed for proof exchange record ${proofRecord.id}")
        agent.proofRepository.save(proofRecord)
        agent.eventBus.publish(AgentEvents.ProofEventV2(proofRecord.copy()))

        return Pair(requestMessage, proofRecord)
    }

    suspend fun processRequest(messageContext: InboundMessageContext? = null, requestMessage: RequestPresentationMessageV2? = null): ProofExchangeRecord {
        logger.info("PROCESS REQUEST -------------------------")

        var connection: ConnectionRecord? = null

        var requestMessage = requestMessage
        if (messageContext != null) {
            requestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestPresentationMessageV2
            logger.debug("Processing proof request with id ${requestMessage.id}")
            connection = messageContext.connection
        }

        val proofRecord: ProofExchangeRecord? = agent.proofRepository.getByThreadAndConnectionIdAndRole(
            role = ProofRole.Prover.name,
            connectionId = connection?.id,
            threadId = requestMessage!!.threadId,
        )
        logger.info("proofRecord-proofRecord: ${proofRecord?.state}")

        val formatServices = getFormatServicesFromMessage(requestMessage.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process request. No supported formats")
        }
        logger.info("[TAG10]")
        if (proofRecord != null) {
            logger.info("[TAG11]")
            val lastSentMessage =
                agent.didCommMessageRepository.getTypedAgentMessage<ProposePresentationMessageV2>(
                    associatedRecordId = proofRecord.id,
                    messageType = ProposePresentationMessageV2.type,
                    role = DidCommMessageRole.Sender,
                )

            val lastReceivedMessage =
                didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                    associatedRecordId = proofRecord.id,
                    messageType = RequestPresentationMessageV2.type,
                    role = DidCommMessageRole.Receiver,
                )
            logger.info("[TAG20]")
            // assert
            proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
            proofRecord.assertState(ProofState.ProposalSent)
            logger.info("[TAG30]")
//            agent.connectionService.assertConnectionOrOutOfBandExchange(
//                messageContext = messageContext,
//                lastReceivedMessage = lastReceivedMessage,
//                lastSentMessage = lastSentMessage,
//                expectedConnectionId = proofRecord.connectionId,
//            )

            proofFormatCoordinator.processRequest(
                proofRecord = proofRecord,
                message = requestMessage,
                formatServices = formatServices,
            )
            logger.info("Requested processed")

            proofRepository.save(proofRecord)
            updateState(proofRecord, ProofState.RequestReceived)
            logger.info("Update record")
            return proofRecord
        }

//        agent.connectionService.assertConnectionOrOutOfBandExchange(
//            messageContext = messageContext,
//        )

        logger.info("[TAG100]")
        logger.info("[TAG100]message: ${requestMessage.threadId}")
        logger.info("[TAG100]con: ${connection?.id}")
        logger.debug("No proof record found for request, creating a new one")

        logger.info("connnnnnid: ${connection?.id ?: "connectionless"}")
        val record = ProofExchangeRecord(
            connectionId = connection?.id ?: "connectionless",
            threadId = requestMessage.threadId,
            parentThreadId = requestMessage.thread?.parentThreadId,
            state = ProofState.RequestReceived,
            role = ProofRole.Prover,
            protocolVersion = ProofConstants.PROTOCOL_VERSION_V2,
        )
        logger.info("[TAG110]")
        proofFormatCoordinator.processRequest(
            proofRecord = record,
            message = requestMessage,
            formatServices = formatServices,
        )
        logger.info("[TAG120]")
        logger.debug("Saving proof record and emit request-received event")

        // save new registry and emit an event
        agent.proofRepository.save(record)
        logger.info("[TAG130]")
        agent.historyRepository.save(
            HistoryRecord(
                historyType = HistoryType.ProofRequestReceived.name,
                connectionId = proofRecord?.connectionId ?: "",
                theirLabel = connection?.theirLabel,
                associatedRecordId = proofRecord?.id ?: "",
                content = requestMessage.toJsonString(),
            ),
        )

        logger.info("[TAG140]")
        agent.eventBus.publish(AgentEvents.ProofEventV2(record.copy()))
        logger.info("Saved new proof record successfully")
        return record
    }

    /**
     * Create a ``PresentationProblemReportV2`` as response to a received presentation request.
     *
     * @param proofRecord the proof record for which to create the presentation acknowledgement.
     * @return the presentation problem report message and an associated proof record.
     */
    suspend fun createPresentationDeclinedProblemReport(proofRecord: ProofExchangeRecord): Pair<PresentationProblemReportMessageV2, ProofExchangeRecord> {
        proofRecord.assertState(ProofState.RequestReceived)

        val probMessage = PresentationProblemReportMessageV2(proofRecord.threadId)
        updateState(proofRecord, ProofState.Declined)

        return Pair(probMessage, proofRecord)
    }

    suspend fun acceptRequest(params: AcceptProofRequestOptions): Pair<PresentationMessageV2, ProofExchangeRecord> {
        val (proofRecord, proofFormats, comment, goalCode, goal, autoAcceptProof, requestedCredentials) = params

        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.RequestReceived)

        var formatServices = getFormatServicesByList(proofFormats ?: emptyList<ProofFormatSpec>())
        if (formatServices.isEmpty()) {
            val requestMessage =
                agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                    associatedRecordId = proofRecord.id,
                    messageType = RequestPresentationMessageV2.type,
                    role = DidCommMessageRole.Receiver,
                )

            formatServices =
                if (requestMessage != null) getFormatServicesFromMessage(requestMessage.formats) else emptyList()
        }

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to accept request. No supported formats provided as input or in request message")
        }

        val acceptRequestParams = AcceptProofRequestParams(
            proofRecord = proofRecord,
            proofFormats = requestedCredentials,
            formatServices = formatServices,
            comment = comment,
            lastPresentation = true,
            goalCode = goalCode,
            goal = goal,
        )
        val message: PresentationMessageV2 = proofFormatCoordinator.acceptRequest(acceptRequestParams)

        proofRecord.chosenCredentialId = params.chosenCredentialId
        proofRecord.autoAcceptProof = autoAcceptProof ?: proofRecord.autoAcceptProof
        proofRecord.presentationMessage = message
        updateState(proofRecord, ProofState.PresentationSent)

        return Pair(message, proofRecord)
    }

    suspend fun negotiateRequest(params: NegotiateProofRequestParams): Pair<ProposePresentationMessageV2, ProofExchangeRecord> {
        val (proofRecord, proofFormats, comment, goalCode, goal, autoAcceptProof) = params

        // Assert
        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.RequestReceived)

        if (proofRecord.connectionId.isBlank()) {
            throw CredoError("No connectionId found for proof record '${proofRecord.id}'. Connection-less verification does not support negotiation.")
        }

        val formatServices = getFormatServices(proofFormats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create request. No supported formats.")
        }

        val createProofProposalParams = CreateProofProposalParams(
            formatServices = formatServices,
            proofFormats = proofFormats,
            proofRecord = proofRecord,
            comment = comment,
            goalCode = goalCode,
            goal = goal,
        )

        val proposalMessage: ProposePresentationMessageV2 =
            proofFormatCoordinator.createProposal(createProofProposalParams)

        proofRecord.autoAcceptProof = autoAcceptProof ?: proofRecord.autoAcceptProof
        updateState(proofRecord, ProofState.ProposalSent)

        return Pair(proposalMessage, proofRecord)
    }

    suspend fun getCredentialsForRequest(params: GetCredentialsForRequestOptions): AnonCredsCredentialsForProofRequest {
        val (proofRecord, proofFormats) = params

        // Assert
        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.RequestReceived)

        var formatServices = getFormatServices(proofFormats ?: emptyMap())

        if (formatServices.size == 0) {
            val requestMessage =
                agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                    associatedRecordId = proofRecord.id,
                    messageType = RequestPresentationMessageV2.type,
                    role = DidCommMessageRole.Receiver,
                )

            formatServices = this.getFormatServicesFromMessage(requestMessage?.formats!!)
        }

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to get proofs for request. No supported formats provided as input or in request message.")
        }

        // revisar
        val result: AnonCredsCredentialsForProofRequest =
            proofFormatCoordinator.getCredentialsForRequest(
                proofRecord = proofRecord,
                proofFormats = proofFormats,
                formatServices = formatServices,
            )
        return result
    }

    suspend fun selectCredentialsForRequest(params: SelectCredentialsForRequestOptions): AnonCredsSelectedCredentials {
        val (proofRecord, proofFormats) = params

        // Assert
        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.RequestReceived)

        var formatServices = getFormatServices(proofFormats ?: emptyMap())

        if (formatServices.size == 0) {
            val requestMessage =
                agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                    associatedRecordId = proofRecord.id,
                    messageType = RequestPresentationMessageV2.type,
                    role = DidCommMessageRole.Receiver,
                )

            formatServices = this.getFormatServicesFromMessage(requestMessage?.formats!!)
        }

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to get proofs for request. No supported formats provided as input or in request message.")
        }

        // revisar
        val result: AnonCredsSelectedCredentials =
            proofFormatCoordinator.selectCredentialsForRequest(
                proofRecord = proofRecord,
                proofFormats = proofFormats,
                formatServices = formatServices,
            )
        return result
    }

    suspend fun processPresentation(messageContext: InboundMessageContext): ProofExchangeRecord {
        logger.info("path = processPresentation")
        val connection = messageContext.connection

        val presentationMessage =
            MessageSerializer.decodeFromString(messageContext.plaintextMessage) as PresentationMessageV2

        logger.debug("Processing presentation with id ${presentationMessage.id}")

        val proofRecord = proofRepository.findByThreadRoleAndConnection(
            threadId = presentationMessage.threadId,
            role = ProofRole.Verifier,
        ) ?: throw CredoError("Proof record not found")

        val lastSentMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = RequestPresentationMessageV2.type,
                role = DidCommMessageRole.Sender,
            )

        val lastReceivedMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<ProposePresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = ProposePresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            )

        // Assert
        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.RequestSent)

        agent.connectionService.assertConnectionOrOutOfBandExchange(
            messageContext = messageContext,
            lastReceivedMessage = lastReceivedMessage,
            lastSentMessage = lastSentMessage,
            expectedConnectionId = proofRecord.connectionId,
        )

        if (proofRecord.connectionId == null) {
            agent.connectionService.matchIncomingMessageToRequestMessageInOutOfBandExchange(
                messageContext = messageContext,
                expectedConnectionId = proofRecord.connectionId,
            )
            proofRecord.connectionId = connection?.id!!
        }

        val formatServices = getFormatServicesFromMessage(presentationMessage.formats)
        if (formatServices.isEmpty()) {
            proofRecord.errorMessage = "Unable to process presentation. No supported formats"
            updateState(proofRecord, ProofState.Abandoned)
            throw PresentationProblemReportErrorV2(
                message = proofRecord.errorMessage!!,
                problemCode = "abandoned",
                threadId = proofRecord.threadId,
                proofRecord = proofRecord,
            )
        }

        val result = proofFormatCoordinator.processPresentation(
            proofRecord = proofRecord,
            presentationMessage = presentationMessage,
            message = lastSentMessage!!,
            formatServices = formatServices,
        )

        proofRecord.isVerified = result.isValid
        if (result.isValid) {
            updateState(proofRecord, ProofState.PresentationReceived)
        } else {
            proofRecord.errorMessage = result.message
            proofRecord.isVerified = false
            updateState(proofRecord, ProofState.Abandoned)

            throw PresentationProblemReportErrorV2(
                message = proofRecord.errorMessage!!,
                problemCode = "abandoned",
                threadId = proofRecord.threadId,
                proofRecord = proofRecord,
            )
        }

        return proofRecord
    }

    suspend fun processPresentationOffline(message: PresentationMessageV2): ProofExchangeRecord? {
        logger.info("[init] Processing presentation in ProcessPresentationProofProcessor")

        val presentationMessage = message
        val formatServices = getFormatServicesFromMessage(presentationMessage.formats)

        val threadId = presentationMessage.threadId
        val proofRecord = ProofExchangeRecord(
            connectionId = "connectionless-proof-presentation",
            threadId = threadId,
            state = ProofState.ProposalReceived,
            role = ProofRole.Verifier,
            protocolVersion = ProofConstants.PROTOCOL_VERSION_V2
        )

        val verifierRecord : VerifierRecord = agent.verifierRepository.getByGlobalThreadId(threadId)

        val lastSentMessage = verifierRecord.requestMessage
            ?: throw Exception("No RequestPresentationMessageV2 found in verifier record")

        val result = proofFormatCoordinator.processPresentation(
            proofRecord,
            presentationMessage,
            lastSentMessage,
            formatServices
        )
        logger.info("result: ${result.isValid}")

        val presentationVerifier = PresentationVerifier(
            presentationMessage = message,
            isVerified = proofRecord.isVerified,
            isOffline = true,
            proofRecordId = proofRecord.id
        )

        verifierRecord.addPresentation(presentationVerifier)
        agent.verifierRepository.update(verifierRecord)
        verifierRecord.printDetails()

        logger.info("[end] Finished processing presentation for proof ${proofRecord.id}")

        agent.proofRepository.save(proofRecord)
        return proofRecord
    }

    suspend fun acceptPresentation(proofRecord: ProofExchangeRecord): Pair<PresentationAckMessageV2, ProofExchangeRecord> {
        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.PresentationReceived)

        val presentation =
            agent.didCommMessageRepository.getTypedAgentMessage<PresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = PresentationMessageV2.type,
                role = DidCommMessageRole.Sender,
            )

        if (presentation?.lastPresentation != true) {
            throw CredoError(
                "Trying to send an ack message while presentation with id ${presentation?.id} " +
                    "indicates this is not the last presentation (presentation.last_presentation is set to false)",
            )
        }

        val message = PresentationAckMessageV2(
            threadId = proofRecord.threadId,
            status = AckStatus.OK,
        )

        message.setThread(
            threadId = proofRecord.threadId,
            parentThreadId = proofRecord.parentThreadId,
        )

        updateState(proofRecord, ProofState.Done)

        return Pair(message, proofRecord)
    }

    suspend fun createAck(proofRecord: ProofExchangeRecord): Pair<PresentationAckMessageV2, ProofExchangeRecord> {
        logger.info("path = createAck")
        proofRecord.assertState(ProofState.PresentationReceived)

        val ackMessage = PresentationAckMessageV2(proofRecord.threadId, AckStatus.OK)
        updateState(proofRecord, ProofState.Done)

        return Pair(ackMessage, proofRecord)
    }

    suspend fun processOfflineAck(proofRecord: ProofExchangeRecord): Pair<PresentationAckMessageV2, ProofExchangeRecord> {

        val ackMessage = PresentationAckMessageV2(proofRecord.threadId, AckStatus.OK)
        updateState(proofRecord, ProofState.Done)

        return Pair(ackMessage, proofRecord)
    }

    suspend fun processAck(messageContext: InboundMessageContext? = null, message: PresentationAckMessageV2? = null): ProofExchangeRecord {
        logger.info("path = processAck")

        var presentationAckMessage : PresentationAckMessageV2? = message
        var connection : ConnectionRecord? = null

        if (messageContext != null) {
            connection = messageContext.connection
            presentationAckMessage =
                MessageSerializer.decodeFromString(messageContext.plaintextMessage) as PresentationAckMessageV2
        }

        logger.info("Processing proof ack with id ${presentationAckMessage?.id}")

        val proofRecord = proofRepository.findByThreadRoleAndConnection(
            threadId = presentationAckMessage?.threadId,
            role = ProofRole.Prover,
            connectionId = connection?.id,
        )
        proofRecord?.connectionId = connection?.id!!

        val lastSentMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<PresentationMessageV2>(
                associatedRecordId = proofRecord?.id!!,
                messageType = PresentationMessageV2.type,
                role = DidCommMessageRole.Sender,
            )

        val lastReceivedMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = RequestPresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            )

        // Assert
        proofRecord.assertProtocolVersion(ProofConstants.PROTOCOL_VERSION_V2)
        proofRecord.assertState(ProofState.PresentationSent)

        if (messageContext != null) {
            agent.connectionService.assertConnectionOrOutOfBandExchange(
                messageContext = messageContext,
                lastReceivedMessage = lastReceivedMessage,
                lastSentMessage = lastSentMessage,
                expectedConnectionId = proofRecord.connectionId,
            )
        }

        updateState(proofRecord, ProofState.Done)

        return proofRecord
    }

    suspend fun createProblemReport(problemParam: CreateProofProblemReportOptions): Pair<PresentationProblemReportMessageV2, ProofExchangeRecord> {
        val (proofRecord, description) = problemParam
        val message = PresentationProblemReportMessageV2(proofRecord.threadId)
        updateState(proofRecord, ProofState.Declined)

        message.setThread(
            threadId = proofRecord.threadId,
            parentThreadId = proofRecord.parentThreadId,
        )

        return Pair(message, proofRecord)
    }

    suspend fun shouldAutoRespondToProposal(
        proofRecord: ProofExchangeRecord,
        proposalMessage: ProposePresentationMessageV2,
    ): Boolean {
        val autoAccept = composeAutoAccept(
            proofRecord.autoAcceptProof,
            agent.agentConfig.autoAcceptProof,
        )

        // Handle always / never cases
        if (autoAccept === AutoAcceptProof.Always) return true
        if (autoAccept === AutoAcceptProof.Never) return false

        val requestMessage = findRequestMessage(proofRecord.id) ?: return false

        val formatServices = this.getFormatServicesFromMessage(requestMessage.formats)

        for (formatService in formatServices) {
            val requestAttachment = this.proofFormatCoordinator.getAttachmentForService(
                formatService,
                requestMessage.formats,
                requestMessage.requestPresentationAttachments,
            )

            val proposalAttachment = proofFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments,
            )

            val shouldAutoRespondToFormat = formatService.shouldAutoRespondToProposal(
                proofRecord = proofRecord,
                requestAttachment = requestAttachment,
                proposalAttachment = proposalAttachment,
            )

            if (!shouldAutoRespondToFormat) return false
        }

        return true
    }

    suspend fun shouldAutoRespondToRequest(
        proofRecord: ProofExchangeRecord,
        requestMessage: RequestPresentationMessageV2,
    ): Boolean {
        val autoAccept = composeAutoAccept(
            proofRecord.autoAcceptProof,
            agent.agentConfig.autoAcceptProof,
        )

        // Handle always / never cases
        if (autoAccept === AutoAcceptProof.Always) return true
        if (autoAccept === AutoAcceptProof.Never) return false

        val proposalMessage = findProposalMessage(proofRecord.id) ?: return false

        val formatServices = this.getFormatServicesFromMessage(requestMessage.formats)

        for (formatService in formatServices) {
            val proposalAttachment = this.proofFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments,
            )

            val requestAttachment = proofFormatCoordinator.getAttachmentForService(
                formatService,
                requestMessage.formats,
                requestMessage.requestPresentationAttachments,
            )

            val shouldAutoRespondToFormat = formatService.shouldAutoRespondToRequest(
                proofRecord = proofRecord,
                requestAttachment = requestAttachment,
                proposalAttachment = proposalAttachment,
            )

            if (!shouldAutoRespondToFormat) return false
        }

        return true
    }

    suspend fun shouldAutoRespondToPresentation(
        proofRecord: ProofExchangeRecord,
        presentationMessage: PresentationMessageV2,
    ): Boolean {
        val autoAccept = composeAutoAccept(
            proofRecord.autoAcceptProof,
            agent.agentConfig.autoAcceptProof,
        )

        // Handle always / never cases
        if (autoAccept === AutoAcceptProof.Always) return true
        if (autoAccept === AutoAcceptProof.Never) return false

        val proposalMessage = findProposalMessage(proofRecord.id) ?: return false

        val requestMessage = findRequestMessage(proofRecord.id) ?: return false
        if (requestMessage.willConfirm != null && !requestMessage.willConfirm) return false

        val formatServices = this.getFormatServicesFromMessage(requestMessage.formats)

        for (formatService in formatServices) {
            val proposalAttachment = this.proofFormatCoordinator.getAttachmentForService(
                formatService,
                proposalMessage.formats,
                proposalMessage.proposalAttachments,
            )

            val requestAttachment = proofFormatCoordinator.getAttachmentForService(
                formatService,
                requestMessage.formats,
                requestMessage.requestPresentationAttachments,
            )

            val presenttionAttachment = proofFormatCoordinator.getAttachmentForService(
                formatService,
                presentationMessage.formats,
                presentationMessage.presentationAttachments,
            )

            val shouldAutoRespondToFormat = formatService.shouldAutoRespondToPresentation(
                proofRecord = proofRecord,
                requestAttachment = requestAttachment,
                proposalAttachment = proposalAttachment,
                presentationAttachment = presenttionAttachment,
            )

            if (!shouldAutoRespondToFormat) return false
        }

        return true
    }

    suspend fun findRequestMessage(proofRecordId: String): RequestPresentationMessageV2? =
        findMessage(proofRecordId, RequestPresentationMessageV2.type)

    suspend fun findProposalMessage(proofRecordId: String): ProposePresentationMessageV2? =
        findMessage(proofRecordId, ProposePresentationMessageV2.type)

    private suspend inline fun <reified T> findMessage(
        proofRecordId: String,
        messageType: String,
    ): T? {
        val messageStr = agent.didCommMessageRepository.getAgentMessage(
            associatedRecordId = proofRecordId,
            messageType = messageType,
        ) ?: return null

        return runCatching {
            MessageSerializer.decodeFromString(messageStr) as T
        }.getOrElse {
            logger.warn("Failed to deserialize ${T::class.simpleName} for record ID $proofRecordId: ${it.message}")
            null
        }
    }

    private fun getFormatServicesByList(
        formats: List<ProofFormatSpec>,
    ): List<ProofFormatService<*>> {
        return formats.mapNotNull { getFormatServiceForFormatKey(it.attachmentId!!) }
            .distinct()
    }

    private fun getFormatServiceForFormatKey(formatKey: String): ProofFormatService<*>? {
        logger.info("format key: $formatKey")
        val finded = proofFormats.find { formatService -> formatService.formatKey == formatKey }
        return finded
    }

    private fun getFormatServiceForFormat(format: String): ProofFormatService<*>? {
        return proofFormats.find { it.supportsFormat(format) }
    }

    private fun getFormatServices(
        formats: Map<String, JsonElement>,
    ): List<ProofFormatService<*>> {
        return formats.keys.mapNotNull { getFormatServiceForFormatKey(it) }
            .distinct()
    }

    private fun getFormatServicesFromMessage(messageFormats: List<ProofFormatSpec>): List<ProofFormatService<*>> {
        return messageFormats.mapNotNull { getFormatServiceForFormat(it.format) }.distinct()
    }

    suspend fun updateState(proofRecord: ProofExchangeRecord, newState: ProofState) {
        proofRecord.state = newState
        val a = agent.proofRepository.findById(proofRecord.id)
        logger.info("agent 00000: ${a?.state} ${a?.threadId}")
        agent.proofRepository.update(proofRecord)
        agent.eventBus.publish(AgentEvents.ProofEventV2(proofRecord.copy()))
    }

    /**
     * Takes a ``RetrievedCredentials`` object and auto selects credentials in a ``RequestedCredentials`` object.
     *
     * Use the return value of this method as input to ``createPresentation(proofRecord:requestedCredentials:comment:)`` to
     * automatically select credentials for presentation.
     *
     * @param retrievedCredentials the retrieved credentials to auto select from.
     * @return a ``RequestedCredentials`` object.
     */
    suspend fun autoSelectCredentialsForProofRequest(retrievedCredentials: RetrievedCredentialsAnonCreds): RequestedCredentialsAnoncreds {
        val requestedCredentials = RequestedCredentialsAnoncreds()
        retrievedCredentials.requestedAttributes.keys.forEach { attributeName ->
            val attributeArray = retrievedCredentials.requestedAttributes[attributeName]!!

            if (attributeArray.isEmpty()) {
                throw Exception("Cannot find credentials for attribute '$attributeName'.")
            }
            val nonRevokedAttributes = attributeArray.filter { attr ->
                attr.revoked != true
            }
            if (nonRevokedAttributes.isEmpty()) {
                throw Exception("Cannot find non-revoked credentials for attribute '$attributeName'.")
            }
            requestedCredentials.requestedAttributes[attributeName] = attributeArray[0]
        }

        retrievedCredentials.requestedPredicates.keys.forEach { predicateName ->
            val predicateArray = retrievedCredentials.requestedPredicates[predicateName]!!

            if (predicateArray.isEmpty()) {
                throw Exception("Cannot find credentials for predicate '$predicateName'.")
            }
            val nonRevokedPredicates = predicateArray.filter { pred ->
                pred.revoked != true
            }
            if (nonRevokedPredicates.isEmpty()) {
                throw Exception("Cannot find non-revoked credentials for predicate '$predicateName'.")
            }
            requestedCredentials.requestedPredicates[predicateName] = nonRevokedPredicates[0]
        }

        return requestedCredentials
    }

    /**
     * Create a ``RetrievedCredentials`` object. Given input proof request,
     * use credentials in the wallet to build indy requested credentials object for proof creation.
     *
     * @param proofRequest the proof request to build the requested credentials object from.
     * @return ``RetrievedCredentials`` object.
     */
    suspend fun getRequestedCredentialsForProofRequest(
        anoncredsProofRequest: AnonCredsProofRequest,
        credentialW3cId: String?
    ): RetrievedCredentialsAnonCreds = coroutineScope {

        val attrDeferred = anoncredsProofRequest.requestedAttributes.map { (referent, requestedAttribute) ->
            async {
                val credentials = agent.anonCredsHolderService.getCredentialsForProofRequest(
                    options = GetCredentialsForProofRequestOptions(
                        proofRequest = anoncredsProofRequest,
                        attributeReferent = referent,
                        credentialW3cId = credentialW3cId
                    ),
                )

                val attributes = credentials.credentials.map { credentialInfo ->
                    val (revoked, deltaTimestamp) = getRevocationStatusForRequestedItemAnoncreds(
                        anoncredsProofRequest,
                        requestedAttribute.nonRevoked,
                        credentialInfo,
                    )
                    RequestedAttributeAnonCreds(
                        credentialId = credentialInfo.credentialInfo.credentialId,
                        timestamp = deltaTimestamp,
                        revealed = true,
                        credentialInfo = credentialInfo.credentialInfo,
                        revoked = revoked,
                    )
                }
                referent to attributes
            }
        }

        val predDeferred = anoncredsProofRequest.requestedPredicates.map { (referent, requestedPredicate) ->
            async {
                val credentials = agent.anonCredsHolderService.getCredentialsForProofRequest(
                    options = GetCredentialsForProofRequestOptions(
                        proofRequest = anoncredsProofRequest,
                        attributeReferent = referent,
                        credentialW3cId = credentialW3cId
                    ),
                )

                val predicates = credentials.credentials.map { credentialInfo ->
                    val (revoked, deltaTimestamp) = getRevocationStatusForRequestedItemAnoncreds(
                        anoncredsProofRequest,
                        requestedPredicate.nonRevoked,
                        credentialInfo,
                    )
                    RequestedPredicateAnonCreds(
                        credentialId = credentialInfo.credentialInfo.credentialId,
                        timestamp = deltaTimestamp,
                        credentialInfo = credentialInfo.credentialInfo,
                        revoked = revoked,
                    )
                }
                referent to predicates
            }
        }

        // 2) Consolida resultados sem concorrência (sem lock)
        val retrieved = RetrievedCredentialsAnonCreds()
        retrieved.requestedAttributes.putAll(attrDeferred.awaitAll().toMap())
        retrieved.requestedPredicates.putAll(predDeferred.awaitAll().toMap())
        retrieved
    }

    suspend fun getRevocationStatusForRequestedItem(
        proofRequest: ProofRequest,
        nonRevoked: RevocationInterval?,
        credential: CredentialForProofRequest,
    ): Pair<Boolean?, Int?> {
        val requestNonRevoked = nonRevoked ?: proofRequest.nonRevoked
        val credentialRevocationId = credential.credentialInfo.credentialRevocationId
        val revocationRegistryId = credential.credentialInfo.revocationRegistryId
        if (requestNonRevoked == null || credentialRevocationId == null || revocationRegistryId == null) {
            return Pair(null, null)
        }

        if (agent.agentConfig.ignoreRevocationCheck) {
            return Pair(false, requestNonRevoked.to)
        }

        return agent.revocationService.getRevocationStatus(
            credentialRevocationId,
            revocationRegistryId,
            requestNonRevoked,
        )
    }

    suspend fun getRevocationStatusForRequestedItemAnoncreds(
        proofRequest: AnonCredsProofRequest,
        nonRevoked: AnonCredsNonRevokedInterval?,
        credential: CredentialForProofRequest,
    ): Pair<Boolean?, Int?> {
        val requestNonRevoked = nonRevoked ?: proofRequest.nonRevoked
        val credentialRevocationId = credential.credentialInfo.credentialRevocationId
        val revocationRegistryId = credential.credentialInfo.revocationRegistryId
        if (requestNonRevoked == null || credentialRevocationId == null || revocationRegistryId == null) {
            return Pair(null, null)
        }

        if (agent.agentConfig.ignoreRevocationCheck) {
            return Pair(false, requestNonRevoked.to?.toInt())
        }

        return agent.revocationService.getRevocationStatusAnonCreds(
            credentialRevocationId,
            revocationRegistryId,
            requestNonRevoked,
        )
    }

    suspend fun createProofFlutter(
        proofRequest: String,
        requestedCredentials: RequestedCredentialsAnoncreds,
    ): ByteArray {
        logger.debug("Validating predicates of credentials: ${requestedCredentials.toJsonString()}")
        val anoncredsCreds = mutableListOf<RequestedCredential>()
        val credentialIds = requestedCredentials.getCredentialIdentifiers()
        val schemaIds = mutableSetOf<String>()
        val credentialDefinitionIds = mutableSetOf<String>()

        credentialIds.concurrentForEach { credId ->
            val credentialRecord = agent.w3cCredentialRepository.getById(credId)
            val credential = W3cUtils.getCredentialUniffiByW3cCredentialRecord(credentialRecord)
            schemaIds.add(credential.schemaId())
            credentialDefinitionIds.add(credential.credDefId())

            val requestedPredicates = mutableListOf<String>()
            var timestamp: Int? = null

            requestedCredentials.requestedPredicates.forEach { (referent, pred) ->
                if (pred.credentialId == credId) {
                    requestedPredicates.add(referent)
                    if (pred.timestamp != null) {
                        timestamp = max(pred.timestamp, timestamp ?: 0)
                    }
                }
            }

            val requestedCredential = RequestedCredential(
                credential,
                timestamp?.toULong(),
                revState = null,
                mutableMapOf<String, Boolean>(),
                requestedPredicates,
            )
            anoncredsCreds.add(requestedCredential)
        }

        val schemas = RecoverFromLedger.getSchemas(schemaIds, agent)
        val credentialDefinitions = RecoverFromLedger.getCredentialDefinitions(credentialDefinitionIds, agent)
        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)

        try {
            val presentation = Prover().createPresentation(
                PresentationRequest(proofRequest),
                anoncredsCreds,
                emptyMap(),
                linkSecret,
                schemas,
                credentialDefinitions,
            )
            Log.d("validating for flutter", presentation.toJson().toString())
            return presentation.toJson().toByteArray()
        } catch (e: Exception) {
            throw Exception("Cannot create a proof using the provided credentials. $e")
        }
    }
}
