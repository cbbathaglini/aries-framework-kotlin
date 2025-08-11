package org.hyperledger.ariesframework.credentials.formats

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsCredentialFormatService
import org.hyperledger.ariesframework.anoncreds.formats.LegacyIndyCredentialFormatService
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MetadataKeys
import org.hyperledger.ariesframework.credentials.models.AcceptProposalParams
import org.hyperledger.ariesframework.credentials.models.AcceptOfferParams
import org.hyperledger.ariesframework.credentials.models.AcceptRequestParams
import org.hyperledger.ariesframework.credentials.models.CreateCredentialParams
import org.hyperledger.ariesframework.credentials.models.ProcessCredentialParams
import org.hyperledger.ariesframework.credentials.models.ProcessOfferParams
import org.hyperledger.ariesframework.credentials.models.ProcessRequestParams
import org.hyperledger.ariesframework.credentials.models.RequestCredentialParams
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
import org.hyperledger.ariesframework.util.PrintLongLine
import org.slf4j.LoggerFactory
import kotlin.math.log

class CredentialFormatCoordinator(
    val agent: Agent,
    val formatServices: List<CredentialFormatService<*>> = listOf<CredentialFormatService<*>>(AnoncredsCredentialFormatService(agent= agent), LegacyIndyCredentialFormatService(agent= agent))
) {
    private val logger = LoggerFactory.getLogger(CredentialFormatCoordinator::class.java)

    suspend fun createProposal(params: CreateProposalParams): ProposeCredentialMessageV2 {
        val (credentialFormats, formatServices, credentialRecord, comment, goalCode, goal) = params

        val formats = mutableListOf<Format>()
        val proposalAttachments = mutableListOf<Attachment>()
        var credentialPreview: CredentialPreviewV2? = null

        for (formatService in formatServices) {
            val credentialFormatCreateProposalReturn = formatService.createProposal(
                credentialFormats = credentialFormats,
                credentialExchangeRecord = credentialRecord
            )

            if (credentialFormatCreateProposalReturn.previewAttribute != null) {
                credentialPreview = CredentialPreviewV2(
                    attributes = credentialFormatCreateProposalReturn.previewAttribute
                )
            }

            proposalAttachments.add(credentialFormatCreateProposalReturn.attachment)
            formats.add(credentialFormatCreateProposalReturn.format)
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

        val proposalMessage = agent.didCommMessageRepository.getTypedAgentMessage<ProposeCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = ProposeCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        ) ?: throw CredoError("Proposal message not found")

        credentialExchangeRecord.credentialAttributes = proposalMessage.credentialPreview?.attributes

        for (formatService in formatServices) {
            val proposalAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = proposalMessage.formats,
                attachments = proposalMessage.proposalAttachments
            )

            val credentialFormatCreateOffer = formatService.acceptProposal(
                credentialFormats = acceptProposalParams.credentialFormats,
                credentialRecord = credentialExchangeRecord,
                proposalAttachments = proposalAttachment
            )

            if(credentialFormatCreateOffer.previewAttributes != null && credentialFormatCreateOffer.previewAttributes != null){
                credentialPreview = CredentialPreviewV2(
                    attributes = credentialFormatCreateOffer.previewAttributes
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
        logger.info("processing offer: ${processOfferParams.toString()}")
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
        logger.info("savedOrUpdatedAgentMessage")

    }

    suspend fun acceptOffer(params: AcceptOfferParams) : RequestCredentialMessageV2 {

        val credentialExchangeRecord = params.credentialRecord
        logger.info("credentialExchangeRecord: ${credentialExchangeRecord.toString()}")

        val formats = mutableListOf<Format>()
        val requestAttachment = mutableListOf<Attachment>()
        val requestAppendAttachments = mutableListOf<Attachment>()

        val offerMessage = agent.didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        ) ?: throw CredoError("Offer message not found")

        logger.info("formatServices: ${formatServices.toString()}")

        var service : CredentialFormatService<*>? = null
        for (format in offerMessage.formats) {
            service = findFormatService(format.attachId)
            if (service != null) {
                val attachment = getAttachmentForService(
                    service,
                    offerMessage.formats,
                    offerMessage.offerAttachments
                )

                val acceptedOffer = service.acceptOffer(
                    attachment = attachment,
                    credentialExchangeRecord = credentialExchangeRecord,
                    credentialFormats = params.credentialFormats,
                    offerCredentialMessageV2 = offerMessage
                )
                requestAttachment.add(acceptedOffer.attachment)
                formats.add(acceptedOffer.format)
                requestAppendAttachments.addAll(acceptedOffer.appendAttachment ?: emptyList())

                logger.info("credentialExchangeRecord ====>> ${credentialExchangeRecord.toString()}")
            }
        }

        credentialExchangeRecord.credentialAttributes = offerMessage.credentialPreview?.attributes

        val requestMessage = RequestCredentialMessageV2(
            formats = formats,
            attachments = requestAppendAttachments,
            requestAttachments = requestAttachment,
            goalCode = params.goalCode,
            goal = params.goal,
            comment = params.comment
        )
        logger.info("requestMessage created: $requestMessage")
        logger.info("credentialExchangeRecord.credentialAttributes: ${credentialExchangeRecord.credentialAttributes.toString()}")

        requestMessage.setThread(
            threadId = credentialExchangeRecord.threadId,
            parentThreadId = credentialExchangeRecord.parentThreadId
        )

        agent.didCommMessageRepository.saveOrUpdateAgentMessage(
            role = DidCommMessageRole.Sender,
            agentMessage = requestMessage,
            associatedRecordId = credentialExchangeRecord.id
        )
        logger.info("saveOrUpdateAgentMessage in acceptoffer credentialformatcoordinator")

        return requestMessage
    }

    private fun findFormatService(format: String): CredentialFormatService<*>? {
        return formatServices.find { it.formatKey == format }
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

        logger.info("-create request-")
        for (formatService in formatServices) {
            val credentialFormatCreateReturn = formatService.createRequest(
                credentialFormats = params.credentialFormats,
                credentialExchangeRecord = params.credentialRecord
            )
            logger.info("credentialFormatCreateReturn: ${credentialFormatCreateReturn.toString()}")
            requestAttachment.add(credentialFormatCreateReturn.attachment)
            formats.add(credentialFormatCreateReturn.format)
        }

        val requestCredentialMessageV2 = RequestCredentialMessageV2(
            formats = formats,
            requestAttachments = requestAttachment,
            goalCode = params.goalCode,
            goal = params.goal,
            comment = params.comment
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

        val requestMessage = agent.didCommMessageRepository.getTypedAgentMessage<RequestCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = RequestCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        ) ?: throw CredoError("Request message not found")

        val offerMessage = agent.didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Sender
        ) ?: throw CredoError("Offer message not found")

        val formats = mutableListOf<Format>()
        val credentialAttachments = mutableListOf<Attachment>()


        for (formatService in formatServices) {
            val requestAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = requestMessage.formats,
                attachments = requestMessage.requestAttachments
            )

            val offerAttachment =  getAttachmentForService(
                credentialFormatService = formatService,
                formats = offerMessage.formats,
                attachments = offerMessage.offerAttachments
            )

            val acceptedRequest = formatService.acceptRequest(
                requestAttachment = requestAttachment,
                offerAttachment = offerAttachment,
                credentialExchangeRecord = credentialExchangeRecord,
                credentialFormats = credentialFormats,
                requestAppendAttachments = requestMessage.attachments
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
        logger.info("issue message >> ${issueMessage.toString()}")
        logger.info("issue message >> ${issueMessage.formats.first().toString()}")
        logger.info("issue message >> ${issueMessage.credentialAttachments.first().toString()}")
        val requestMessage = params.requestCredentialMessageV2
        val credentialExchangeRecord = params.credentialExchangeRecord
        val formatServices = params.formatService

        logger.info("credentialExchange AnonCredsCredentialRequestMetadataKey => ${credentialExchangeRecord.metadata.get( MetadataKeys.AnonCredsCredentialRequestMetadataKey)}")

        val offerMessage = agent.didCommMessageRepository.getTypedAgentMessage<OfferCredentialMessageV2>(
            associatedRecordId = credentialExchangeRecord.id,
            messageType = OfferCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver
        ) ?: throw CredoError("Offer message not found")


        for (formatService in formatServices){
            val offerAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = offerMessage.formats,
                attachments = offerMessage.offerAttachments
            )
            logger.info("offerAttachment: ${offerAttachment.toString()}")

            val issueAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = issueMessage.formats,
                attachments = issueMessage.credentialAttachments
            )
            PrintLongLine.print("issueAttachment enco: ${issueAttachment.toString()}")

            val requestAttachment = getAttachmentForService(
                credentialFormatService = formatService,
                formats = requestMessage.formats,
                attachments = requestMessage.requestAttachments
            )
            logger.info("requestAttachment: ${requestAttachment.toString()}")

            formatService.processCredential(
                attachment = issueAttachment,
                offerAttachment = offerAttachment,
                requestAttachment = requestAttachment,
                credentialExchangeRecord = credentialExchangeRecord,
                requestAppendAttachments = requestMessage.attachments
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
        logger.info("attttt: ${attachment.data.toString()}")
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

        return format.attachId
    }

}