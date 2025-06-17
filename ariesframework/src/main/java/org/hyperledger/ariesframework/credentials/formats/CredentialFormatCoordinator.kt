package org.hyperledger.ariesframework.credentials.formats

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.models.AcceptProposalParams
import org.hyperledger.ariesframework.credentials.modelv2.AcceptOfferParams
import org.hyperledger.ariesframework.credentials.modelv2.AcceptRequestParams
import org.hyperledger.ariesframework.credentials.modelv2.CreateCredentialParams
import org.hyperledger.ariesframework.credentials.modelv2.ProcessCredentialParams
import org.hyperledger.ariesframework.credentials.modelv2.ProcessOfferParams
import org.hyperledger.ariesframework.credentials.modelv2.ProcessRequestParams
import org.hyperledger.ariesframework.credentials.modelv2.RequestCredentialParams
import org.hyperledger.ariesframework.credentials.operation.CreateProposalParams
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.ProposeCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.storage.DidCommMessageRole

class CredentialFormatCoordinator(
    val agent: Agent,
    val formatServices: List<CredentialFormatService<*>>
) {

    suspend fun createProposal(params: CreateProposalParams): ProposeCredentialMessageV2 {
        val (credentialFormats, formatServices, credentialRecord, comment, goalCode, goal) = params

        val formats = mutableListOf<Format>()
        val proposalAttachments = mutableListOf<Attachment>()
        var credentialPreview: CredentialPreviewV2? = null

        for (formatService in formatServices) {
            val result = formatService.createProposal(
                agent,
                FormatCreateProposalOptions(
                    credentialFormats = credentialFormats,
                    credentialRecord = credentialRecord
                )
            )

            if (result.previewAttributes != null) {
                credentialPreview = CredentialPreviewV2(
                    attributes = result.previewAttributes
                )
            }

            proposalAttachments.add(result.attachment)
            formats.add(result.format)
        }

        credentialRecord.credentialAttributes = credentialPreview?.attributes

        val message = ProposeCredentialMessageV2(
            formats = formats,
            proposalAttachments = proposalAttachments,
            comment = comment,
            credentialPreview = credentialPreview,
            goalCode = goalCode,
            goal = goal
        )

        message.id = credentialRecord.threadId

        message.setThread(
            threadId = credentialRecord.threadId,
            parentThreadId = credentialRecord.parentThreadId
        )

        agent.didCommMessageRepository.saveAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = message,
            associatedRecordId = credentialRecord.id)
        return message

    }

    suspend fun processProposal(credentialExchangeRecord: CredentialExchangeRecord, message: ProposeCredentialMessageV2, formatServices: List<CredentialFormatService<*>>) {

        for (formatService in formatServices) {
            val attachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = message.formats,
                attachments = message.proposalAttachments
            )

            formatService.processProposal(
                attachment = attachment,
                credentialRecord = credentialExchangeRecord
            )
        }

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            agentMessage = message,
            role = DidCommMessageRole.Receiver,
            associatedRecordId = credentialExchangeRecord.id
        )
    }

    suspend fun acceptProposal(acceptProposalParams: AcceptProposalParams): OfferCredentialMessageV2 {

        val credentialExchangeRecord = acceptProposalParams.credentialRecord

        val formats = mutableListOf<Format>()
        val offerAttachments = mutableListOf<Attachment>()
        var credentialPreview: CredentialPreviewV2? = null

        val proposalMessage = agent.didCommMessageRepository.getAgentMessage(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = ProposeCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        )

        val proposalMessageV2 = MessageSerializer.decodeFromString(proposalMessage) as ProposeCredentialMessageV2
        credentialExchangeRecord.credentialAttributes = proposalMessageV2.credentialPreview?.attributes

        for (formatService in formatServices) {
            val proposalAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = proposalMessageV2.formats,
                attachments = proposalMessageV2.proposalAttachments
            )

            val credentialFormatCreateOffer = formatService.acceptProposal(
                credentialFormats = acceptProposalParams.credentialFormats,
                credentialRecord = credentialExchangeRecord,
                proposalAttachments = proposalAttachment
            )

            if(credentialFormatCreateOffer.previewAttributes != null && credentialFormatCreateOffer.previewAttributes.attributes != null){
                credentialPreview = CredentialPreviewV2(
                    attributes = credentialFormatCreateOffer.previewAttributes.attributes
                )
            }

            offerAttachments.add(credentialFormatCreateOffer.attachment)
            formats.add(credentialFormatCreateOffer.format)
        }

        credentialExchangeRecord.credentialAttributes = credentialPreview?.attributes

        if (credentialPreview==null){
            credentialPreview = CredentialPreviewV2(attributes = emptyList())
        }

        val message =  OfferCredentialMessageV2(
                formats = formats,
                offerAttachments = offerAttachments,
                credentialPreview = credentialPreview,
                comment = acceptProposalParams.comment,
                goalCode = acceptProposalParams.goalCode,
                goal = acceptProposalParams.goal,
        )

        message.setThread(
            threadId = credentialExchangeRecord.threadId,
            parentThreadId = credentialExchangeRecord.parentThreadId
        )

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = message,
            associatedRecordId= credentialExchangeRecord.id
        )

        return message
    }


    /**
     * Create a {@link OfferCredentialMessageV2}.
     *
     * @param negotiateParams
     * @returns The created {@link OfferCredentialMessageV2}
     *
     */
    suspend fun createOffer(params: CreateCredentialParams): OfferCredentialMessageV2 {
        val formats = mutableListOf<Format>()
        val offerAttachments = mutableListOf<Attachment>()
        var credentialPreview: CredentialPreviewV2? = null


        val credentialExchangeRecord = params.credentialRecord
        val credentialFormat = params.credentialFormats

        for (formatService in formatServices) {
            val offerCreated = formatService.createOffer(credentialFormat, credentialExchangeRecord)

            if (offerCreated.previewAttributes == null){
                credentialPreview = CredentialPreviewV2(
                    attributes = offerCreated.previewAttributes
                )
            }

            offerAttachments.add(offerCreated.attachment)
            formats.add(offerCreated.format)
        }

        credentialExchangeRecord.credentialAttributes = credentialPreview?.attributes

        if (credentialPreview == null){
            credentialPreview = CredentialPreviewV2(
                attributes = emptyList()
            )
        }

        val message =  OfferCredentialMessageV2(
            formats = formats,
            offerAttachments = offerAttachments,
            credentialPreview = credentialPreview,
            comment = params.comment,
            goalCode = params.goalCode,
            goal = params.goal,
        )

        message.setThread(
            threadId = credentialExchangeRecord.threadId,
            parentThreadId = credentialExchangeRecord.parentThreadId
        )

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = message,
            associatedRecordId= credentialExchangeRecord.id
        )

        return message
    }

    suspend fun processOffer(processOfferParams: ProcessOfferParams) {
        val credentialExchangeRecord = processOfferParams.credentialExchangeRecord
        val formatServices = processOfferParams.formatService
        val message = processOfferParams.message

        for (formatService in formatServices) {
            val attachment = getAttachmentForService(formatService, message.formats, message.offerAttachments)
            formatService.processOffer(attachment, credentialExchangeRecord)
        }

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Receiver,
            agentMessage = message,
            associatedRecordId= credentialExchangeRecord.id
        )
    }

    suspend fun acceptOffer(params: AcceptOfferParams) : RequestCredentialMessageV2 {

        val credentialExchangeRecord = params.credentialRecord

        val formats = mutableListOf<Format>()
        val requestAttachment = mutableListOf<Attachment>()
        val requestAppendAttachments = mutableListOf<Attachment>()


        val offerMessageDid = agent.didCommMessageRepository.findAgentMessage(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        )
        val offerMessage = MessageSerializer.decodeFromString(offerMessageDid) as OfferCredentialMessageV2


        for (formatService in formatServices) {
            val attachment = getAttachmentForService(formatService, offerMessage.formats, offerMessage.offerAttachments)

            val acceptedOffer = formatService.acceptOffer(
                attachment = attachment,
                credentialExchangeRecord= credentialExchangeRecord,
                credentialFormats = params.credentialFormats
            )

            requestAttachment.add(acceptedOffer.attachment)
            formats.add(acceptedOffer.format)
            requestAppendAttachments.addAll(acceptedOffer.appendAttachment)
        }

        credentialExchangeRecord.credentialAttributes = offerMessage.credentialPreview?.attributes
        val requestMessage = RequestCredentialMessageV2(
            formats = formats,
            requestAttachments = requestAppendAttachments,
            goalCode = params.goalCode,
            goal = params.goal,
            comment = params.comment,
            appendAttachments = null
        )

        requestMessage.setThread(
            threadId = credentialExchangeRecord.id,
            parentThreadId = credentialExchangeRecord.parentThreadId
        )

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = requestMessage,
            associatedRecordId = credentialExchangeRecord.id
        )

        return requestMessage
    }

    /**
     * Create a {@link RequestCredentialMessageV2}.
     *
     * @param options
     * @returns The created {@link RequestCredentialMessageV2}
     *
     */
    suspend fun createRequest(params: RequestCredentialParams) : RequestCredentialMessageV2 {

        val credentialExchangeRecord = params.credentialRecord
        val formats = mutableListOf<Format>()
        val requestAttachment = mutableListOf<Attachment>()

        for (formatService in formatServices) {
            val credentialFormatCreateReturn = formatService.createRequest(
                credentialFormats = params.credentialFormats,
                credentialExchangeRecord = params.credentialRecord
            )

            requestAttachment.add(credentialFormatCreateReturn.attachment)
            formats.add(credentialFormatCreateReturn.format)
        }

        val requestCredentialMessageV2 = RequestCredentialMessageV2(
            formats = formats,
            requestAttachments = requestAttachment,
            goalCode = params.goalCode,
            goal = params.goal,
            comment = params.comment,
            appendAttachments = null
        )

        requestCredentialMessageV2.setThread(
            threadId = credentialExchangeRecord.threadId,
            parentThreadId = credentialExchangeRecord.parentThreadId
        )

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = requestCredentialMessageV2,
            associatedRecordId = credentialExchangeRecord.id
        )

        return requestCredentialMessageV2
    }


    suspend fun processRequest(params: ProcessRequestParams) {

        val credentialExchangeRecord = params.credentialExchangeRecord
        val formatServices = params.formatService
        val requestMessage = params.message

        for (formatService in formatServices) {
            val attachment = getAttachmentForService(formatService, requestMessage.formats, requestMessage.requestAttachments)
            formatService.processRequest(
                attachment = attachment,
                credentialExchangeRecord = credentialExchangeRecord
            )
        }

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Receiver,
            agentMessage = requestMessage,
            associatedRecordId = credentialExchangeRecord.id
        )
    }

    suspend fun acceptRequest(params: AcceptRequestParams) : IssueCredentialMessageV2 {

        val credentialExchangeRecord = params.credentialExchangeRecord
        val credentialFormats = params.credentialFormat

        val requestMessage = agent.didCommMessageRepository.findAgentMessage(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = RequestCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        )
        val requestMessageV2 = MessageSerializer.decodeFromString(requestMessage) as RequestCredentialMessageV2

        val offerCredentialMessage = agent.didCommMessageRepository.findAgentMessage(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Sender
        )
        val offerCredentialMessageV2 = MessageSerializer.decodeFromString(offerCredentialMessage) as OfferCredentialMessageV2

        val formats = mutableListOf<Format>()
        val credentialAttachments = mutableListOf<Attachment>()


        for (formatService in formatServices) {
            val requestAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = requestMessageV2.formats,
                attachments = requestMessageV2.requestAttachments
            )

            val offerAttachment =  getAttachmentForService(
                credentialFormatService = formatService,
                formats = offerCredentialMessageV2.formats,
                attachments = offerCredentialMessageV2.offerAttachments
            )

            val acceptedRequest = formatService.acceptRequest(
                requestAttachment = requestAttachment,
                offerAttachment = offerAttachment,
                credentialExchangeRecord = credentialExchangeRecord,
                credentialFormats = credentialFormats,
                requestAppendAttachments = requestMessageV2.appendAttachments
            )

            credentialAttachments.add(acceptedRequest.attachment)
            formats.add(acceptedRequest.format)

        }

        val issueMessage = IssueCredentialMessageV2(
            formats = formats,
            credentialAttachments = credentialAttachments,
            goalCode = params.goalCode,
            goal = params.goal,
            comment = params.comment
        )

        issueMessage.setThread(
            threadId = credentialExchangeRecord.threadId,
            parentThreadId = credentialExchangeRecord.parentThreadId
        )

        issueMessage.setPleaseAck()

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = issueMessage,
            associatedRecordId = credentialExchangeRecord.id
        )

        return issueMessage

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
    suspend fun processCredential(params: ProcessCredentialParams) {
        val issueMessage = params.message
        val requestMessage = params.requestCredentialMessageV2
        val credentialExchangeRecord = params.credentialExchangeRecord
        val formatServices = params.formatService

        val offerCredentialMessage = agent.didCommMessageRepository.findAgentMessage(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        )

        val offerMessage = MessageSerializer.decodeFromString(offerCredentialMessage) as OfferCredentialMessageV2

        for (formatService in formatServices){
            val offerAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = offerMessage.formats,
                attachments = offerMessage.offerAttachments
            )

            val issueAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = issueMessage.formats,
                attachments = issueMessage.credentialAttachments
            )

            val requestAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = requestMessage.formats,
                attachments = requestMessage.requestAttachments
            )

            formatService.processCredential(
                attachment = issueAttachment,
                offerAttachment = offerAttachment,
                requestAttachment = requestAttachment,
                credentialExchangeRecord = credentialExchangeRecord,
                requestAppendAttachments = requestMessage.appendAttachments
            )
        }

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Receiver,
            agentMessage = issueMessage,
            associatedRecordId = credentialExchangeRecord.id
        )
    }

    /*
    * retrieves the attachment associated with a given CredentialFormatService
    * from a list of attachments, based on the format identifiers
    * */
    fun getAttachmentForService(
        credentialFormatService: CredentialFormatService<*>,
        formats: List<Format>,
        attachments: List<Attachment>
    ): Attachment {
        val attachmentId = getAttachmentIdForService(credentialFormatService, formats)

        val attachment = attachments.find { it.id == attachmentId }
            ?: throw CredoError("Attachment with id $attachmentId not found in attachments.")

        return attachment
    }


    /*
    * searches for a Format within the provided list of formats that is supported by the given
    * CredentialFormatService. If found, it returns the associated attachmentId
    * */
    fun getAttachmentIdForService(
        credentialFormatService: CredentialFormatService<*>,
        formats: List<Format>
    ): String {
        val format = formats.find { credentialFormatService.supportsFormat(it.format) }
            ?: throw CredoError("No attachment found for service ${credentialFormatService.formatKey}")

        return format.attachId!!
    }

}