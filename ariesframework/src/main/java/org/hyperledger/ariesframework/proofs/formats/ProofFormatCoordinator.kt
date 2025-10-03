package org.hyperledger.ariesframework.proofs.formats

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsProofFormatService
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsCredentialsForProofRequest
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.messages.v2.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.messages.v2.ProposePresentationMessageV2
import org.hyperledger.ariesframework.proofs.messages.v2.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.AcceptProofProposalParams
import org.hyperledger.ariesframework.proofs.models.AcceptProofRequestParams
import org.hyperledger.ariesframework.proofs.models.CreateProofProposalParams
import org.hyperledger.ariesframework.proofs.models.ProcessPresentationReturn
import org.hyperledger.ariesframework.proofs.models.ProofFormatCreateReturn
import org.hyperledger.ariesframework.proofs.models.ProofFormatProcessOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.models.RequestProofRequestParams
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.slf4j.LoggerFactory

class ProofFormatCoordinator(
    val agent: Agent,
    val formatServices: List<ProofFormatService<*>> = listOf<ProofFormatService<*>>(
        AnoncredsProofFormatService(agent = agent),
    ),
) {
    private val logger = LoggerFactory.getLogger(ProofFormatCoordinator::class.java)

    suspend fun createProposal(params: CreateProofProposalParams): ProposePresentationMessageV2 {
        val (formatServices, proofFormats, proofRecord, comment, goalCode, goal) = params

        val formats = mutableListOf<ProofFormatSpec>()
        val proposalAttachments = mutableListOf<Attachment>()

        for (formatService in formatServices) {
            val proofFormatCreateProposalReturn = formatService.createProposal(
                proofFormats = proofFormats,
                profRecord = proofRecord,
            )

            proposalAttachments.add(proofFormatCreateProposalReturn.attachment)
            formats.add(proofFormatCreateProposalReturn.format)
        }

        val message = ProposePresentationMessageV2(
            formats = formats,
            proposalAttachments = proposalAttachments,
            comment = comment,
            goalCode = goalCode,
            goal = goal,
        )
        message.id = proofRecord.threadId
        message.setThread(
            threadId = proofRecord.threadId,
            parentThreadId = proofRecord.parentThreadId,
        )

        agent.didCommMessageRepository.saveAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = message,
            associatedRecordId = proofRecord.id,
        )

        return message
    }

    suspend fun processProposal(
        proofRecord: ProofExchangeRecord,
        message: ProposePresentationMessageV2,
        formatServices: List<ProofFormatService<*>>,
    ) {
        for (formatService in formatServices) {
            val attachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = message.formats,
                attachments = message.proposalAttachments,
            )

            formatService.processProposal(
                attachment = attachment,
                proofRecord = proofRecord,
            )
        }

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            agentMessage = message,
            role = DidCommMessageRole.Receiver,
            associatedRecordId = proofRecord.id,
        )
    }

    suspend fun acceptProposal(params: AcceptProofProposalParams): RequestPresentationMessageV2 {
        val (proofRecord, proofFormats, formatServices, comment, goalCode, goal, presentMultiple, willConfirm) = params

        val formats = mutableListOf<ProofFormatSpec>()
        val requestAttachments = mutableListOf<Attachment>()

        val proposalMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<ProposePresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = ProposePresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            ) ?: throw CredoError("Proposal message not found")

        for (formatService in formatServices) {
            val proposalAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = proposalMessage.formats,
                attachments = proposalMessage.proposalAttachments,
            )

            val proofAccepted = formatService.acceptProposal(
                proofFormats = proofFormats,
                proofRecord = proofRecord,
                proposalAttachment = proposalAttachment,
            )

            requestAttachments.add(proofAccepted.attachment)
            formats.add(proofAccepted.format)
        }

        val message = RequestPresentationMessageV2(
            formats = formats,
            requestAttachment = requestAttachments,
            comment = comment,
            goalCode = goalCode,
            goal = goal,
            presentMultiple = presentMultiple,
            willConfirm = willConfirm,
        )

        message.setThread(
            threadId = proofRecord.threadId,
            parentThreadId = proofRecord.parentThreadId,
        )

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            agentMessage = message,
            role = DidCommMessageRole.Sender,
            associatedRecordId = proofRecord.id,
        )

        return message
    }

    suspend fun createRequest(params: RequestProofRequestParams): RequestPresentationMessageV2 {
        logger.info("createRequest in coordinator")
        val (proofRecord, proofFormats, formatServices, comment, goalCode, goal, presentMultiple, willConfirm, attachmentId) = params

        logger.info("format service: ${formatServices.first().formatKey}")
        val formats = mutableListOf<ProofFormatSpec>()
        val requestAttachments = mutableListOf<Attachment>()

        logger.info("format service: ${formatServices.first().formatKey}")
        for (formatService in formatServices) {
            val proofFormatCreateProposalReturn = formatService.createRequest(
                proofFormats = proofFormats,
                attachmentId = attachmentId,
                proofRecord = proofRecord,
            )
            logger.info("proofFormatCreateProposalReturn: ${proofFormatCreateProposalReturn.attachment.getDataAsJson()}")
            requestAttachments.add(proofFormatCreateProposalReturn.attachment)
            formats.add(proofFormatCreateProposalReturn.format)
        }

        logger.info("createRequest after formtatservice")
        val message = RequestPresentationMessageV2(
            formats = formats,
            requestAttachment = requestAttachments,
            comment = comment,
            goalCode = goalCode,
            goal = goal,
            presentMultiple = presentMultiple,
            willConfirm = willConfirm,
        )
        message.setThread(
            threadId = proofRecord.threadId,
            parentThreadId = proofRecord.parentThreadId,
        )
        logger.info("message: ${message.toJsonString()}")
        agent.didCommMessageRepository.saveAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = message,
            associatedRecordId = proofRecord.id,
        )
        logger.info("save didcomm")
        return message
    }

    suspend fun processRequest(
        proofRecord: ProofExchangeRecord,
        message: RequestPresentationMessageV2,
        formatServices: List<ProofFormatService<*>>,
    ) {
        for (formatService in formatServices) {
            val attachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = message.formats,
                attachments = message.requestAttachment,
            )

            formatService.processRequest(
                options = ProofFormatProcessOptions(
                    attachment = attachment,
                    proofRecord = proofRecord,
                ),
            )
        }

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            agentMessage = message,
            role = DidCommMessageRole.Receiver,
            associatedRecordId = proofRecord.id,
        )
    }

    suspend fun acceptRequest(params: AcceptProofRequestParams): PresentationMessageV2 {
        val (proofRecord, proofFormats, formatServices, comment, lastPresentation, goalCode, goal) = params

        val requestMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = RequestPresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            ) ?: throw CredoError("Request message not found")

        val proposal =
            agent.didCommMessageRepository.findAgentMessage(
                associatedRecordId = proofRecord.id,
                messageType = ProposePresentationMessageV2.type,
            )

        val proposalMessage = if (proposal != null) {
            MessageSerializer.decodeFromString(proposal) as ProposePresentationMessageV2
        } else {
            null
        }

        val formats = mutableListOf<ProofFormatSpec>()
        val presentationAttachments = mutableListOf<Attachment>()

        for (formatService in formatServices) {
            val requestAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = requestMessage.formats,
                attachments = requestMessage.requestAttachment,
            )

            val proposalAttachment = if (proposalMessage != null) {
                getAttachmentForService(
                    proofFormatService = formatService,
                    formats = proposalMessage.formats,
                    attachments = proposalMessage.proposalAttachments,
                )
            } else {
                null
            }

            val proofAccepted: ProofFormatCreateReturn = formatService.acceptRequest(
                requestMessage = requestMessage,
                proofFormats = proofFormats,
                proofRecord = proofRecord,
                proposalAttachment = proposalAttachment,
                requestAttachment = requestAttachment,
                attachmentId = formatService.formatKey,
            )

            presentationAttachments.add(proofAccepted.attachment)
            formats.add(proofAccepted.format)
        }

        val message = PresentationMessageV2(
            formats = formats,
            presentationAttachments = presentationAttachments,
            comment = comment,
            goalCode = goalCode,
            goal = goal,
            lastPresentation = lastPresentation,
        )

        message.setThread(
            threadId = proofRecord.threadId,
            parentThreadId = proofRecord.parentThreadId,
        )
        message.setPleaseAck()

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            agentMessage = message,
            role = DidCommMessageRole.Sender,
            associatedRecordId = proofRecord.id,
        )

        return message
    }

    suspend fun getCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement> = emptyMap(),
        formatServices: List<ProofFormatService<*>>,
    ): AnonCredsCredentialsForProofRequest {
        val requestMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = RequestPresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            ) ?: throw CredoError("Request message not found")

        val proposalMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<ProposePresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = ProposePresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            ) ?: throw CredoError("Proposal message not found")

        val credentialsForRequest: MutableMap<String, Any?> = mutableMapOf()

        for (formatService in formatServices) {
            val requestAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = requestMessage.formats,
                attachments = requestMessage.requestAttachment,
            )

            val proposalAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = proposalMessage.formats,
                attachments = proposalMessage.proposalAttachments,
            )

            val proofFormat: AnonCredsCredentialsForProofRequest =
                formatService.getCredentialsForRequest(
                    requestAttachment = requestAttachment,
                    proposalAttachment = proposalAttachment,
                    proofRecord = proofRecord,
                    proofFormats = proofFormats,
                )

            credentialsForRequest[formatService.formatKey] = proofFormat
        }

        // TODO
        return AnonCredsCredentialsForProofRequest(
            attributes = emptyMap(),
            predicates = emptyMap(),
        )
    }

    suspend fun selectCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement> = emptyMap(),
        formatServices: List<ProofFormatService<*>>,
    ): AnonCredsSelectedCredentials {
        val requestMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<RequestPresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = RequestPresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            ) ?: throw CredoError("Request message not found")

        val proposalMessage =
            agent.didCommMessageRepository.getTypedAgentMessage<ProposePresentationMessageV2>(
                associatedRecordId = proofRecord.id,
                messageType = ProposePresentationMessageV2.type,
                role = DidCommMessageRole.Receiver,
            ) ?: throw CredoError("Proposal message not found")

        val credentialsForRequest: MutableMap<String, Any?> = mutableMapOf()

        for (formatService in formatServices) {
            val requestAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = requestMessage.formats,
                attachments = requestMessage.requestAttachment,
            )

            val proposalAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = proposalMessage.formats,
                attachments = proposalMessage.proposalAttachments,
            )

            val proofFormat: AnonCredsSelectedCredentials =
                formatService.selectCredentialsForRequest(
                    requestAttachment = requestAttachment,
                    proposalAttachment = proposalAttachment,
                    proofRecord = proofRecord,
                    proofFormats = proofFormats,
                )

            credentialsForRequest[formatService.formatKey] = proofFormat
        }

        // TODO
        // 'selectCredentialsForRequest',
        //      'output'
        return AnonCredsSelectedCredentials(
            attributes = emptyMap(),
            predicates = emptyMap(),
            selfAttestedAttributes = emptyMap(),
        )
    }

    suspend fun processPresentation(
        proofRecord: ProofExchangeRecord,
        presentationMessage: PresentationMessageV2,
        message: RequestPresentationMessageV2,
        formatServices: List<ProofFormatService<*>>,
    ): ProcessPresentationReturn {
        val formatVerificationResults: MutableList<Boolean> = mutableListOf()

        for (formatService in formatServices) {
            val requestAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = message.formats,
                attachments = message.requestAttachment,
            )

            val presentationAttachment = getAttachmentForService(
                proofFormatService = formatService,
                formats = presentationMessage.formats,
                attachments = presentationMessage.presentationAttachments,
            )

            try {
                logger.info("presentationAttachment: ${presentationAttachment.getDataAsJson()})")
                logger.info("requestAttachment: ${presentationAttachment.getDataAsJson()})")
                val isValid = formatService.processPresentation(
                    presentationAttachment = presentationAttachment,
                    requestAttachment = requestAttachment,
                    proofRecord = proofRecord,
                    presentationMessage = presentationMessage,
                    requestMessage = message,
                )

                formatVerificationResults.add(isValid)
                logger.info("isvalid: ${isValid}")
            } catch (error: Exception) {
                logger.error("message error: ${error.message}")
                return ProcessPresentationReturn(
                    isValid = false,
                    message = error.message.toString(),
                )
            }
        }

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            agentMessage = message,
            role = DidCommMessageRole.Receiver,
            associatedRecordId = proofRecord.id,
        )

        val isValid = formatVerificationResults.all { it == true }

        if (isValid) {
            return ProcessPresentationReturn(
                isValid = isValid,
            )
        }

        return ProcessPresentationReturn(
            isValid = isValid,
            message = "Not all presentations are valid",
        )
    }

    /*
     * retrieves the attachment associated with a given ProofFormatService
     * from a list of attachments, based on the format identifiers
     * */
    fun getAttachmentForService(
        proofFormatService: ProofFormatService<*>,
        formats: List<ProofFormatSpec>,
        attachments: List<Attachment>,
    ): Attachment {
        val attachmentId = getAttachmentIdForService(proofFormatService, formats)
        val attachment = attachments.find { it.id == attachmentId }
            ?: throw CredoError("Attachment with id $attachmentId not found in attachments.")
        return attachment
    }

    /*
     * searches for a Format within the provided list of formats that is supported by the given
     * ProofFormatService. If found, it returns the associated attachmentId
     * */
    fun getAttachmentIdForService(
        proofFormatService: ProofFormatService<*>,
        formats: List<ProofFormatSpec>,
    ): String {
        val format = formats.find { proofFormatService.supportsFormat(it.format) }
            ?: throw CredoError("No attachment found for service ${proofFormatService.formatKey}")

        return format.attachmentId!!
    }
}
