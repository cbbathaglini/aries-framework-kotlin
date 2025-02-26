package org.hyperledger.ariesframework.credentialsv2

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentialsv2.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentialsv2.models.Format
import org.hyperledger.ariesframework.error.CredoError


class CredentialFormatCoordinator<CFs : List<CredentialFormatService>> {

//    suspend fun createProposal(
//        agentContext: AgentContext,
//        credentialFormats: CredentialFormatPayload<ExtractCredentialFormats<CFs>, "createProposal">,
//    formatServices: List<CredentialFormatService>,
//    credentialRecord: CredentialExchangeRecord,
//    comment: String? = null,
//    goalCode: String? = null,
//    goal: String? = null
//    ): V2ProposeCredentialMessage {
//        val didCommMessageRepository = agentContext.dependencyManager.resolve(DidCommMessageRepository::class.java)
//
//        val formats = mutableListOf<Format>()
//        val proposalAttachments = mutableListOf<Attachment>()
//        var credentialPreview: V2CredentialPreview? = null
//
//        for (formatService in formatServices) {
//            val (format, attachment, previewAttributes) = formatService.createProposal(agentContext, credentialFormats, credentialRecord)
//
//            if (previewAttributes != null) {
//                credentialPreview = V2CredentialPreview(previewAttributes)
//            }
//
//            proposalAttachments.add(attachment)
//            formats.add(format)
//        }
//
//        credentialRecord.credentialAttributes = credentialPreview?.attributes
//
//        val message = V2ProposeCredentialMessage(
//            id = credentialRecord.threadId,
//            formats = formats,
//            proposalAttachments = proposalAttachments,
//            comment = comment,
//            credentialPreview = credentialPreview,
//            goalCode = goalCode,
//            goal = goal
//        )
//
//        message.setThread(credentialRecord.threadId, credentialRecord.parentThreadId)
//
//        didCommMessageRepository.saveOrUpdateAgentMessage(agentContext, message, DidCommMessageRole.Sender, credentialRecord.id)
//
//        return message
//    }
//
//    suspend fun processProposal(
//        agentContext: AgentContext,
//        credentialRecord: CredentialExchangeRecord,
//        message: V2ProposeCredentialMessage,
//        formatServices: List<CredentialFormatService>
//    ) {
//        val didCommMessageRepository = agentContext.dependencyManager.resolve(DidCommMessageRepository::class.java)
//
//        for (formatService in formatServices) {
//            val attachment = getAttachmentForService(formatService, message.formats, message.proposalAttachments)
//            formatService.processProposal(agentContext, attachment, credentialRecord)
//        }
//
//        didCommMessageRepository.saveOrUpdateAgentMessage(agentContext, message, DidCommMessageRole.Receiver, credentialRecord.id)
//    }
//
//    suspend fun acceptProposal(
//        agentContext: AgentContext,
//        credentialRecord: CredentialExchangeRecord,
//        credentialFormats: CredentialFormatPayload<ExtractCredentialFormats<CFs>, "acceptProposal">? = null,
//    formatServices: List<CredentialFormatService>,
//    comment: String? = null,
//    goalCode: String? = null,
//    goal: String? = null
//    ): V2OfferCredentialMessage {
//        val didCommMessageRepository = agentContext.dependencyManager.resolve(DidCommMessageRepository::class.java)
//
//        val proposalMessage = didCommMessageRepository.getAgentMessage(agentContext, credentialRecord.id, V2ProposeCredentialMessage::class.java, DidCommMessageRole.Receiver)
//
//        credentialRecord.credentialAttributes = proposalMessage.credentialPreview?.attributes
//
//        val formats = mutableListOf<Format>()
//        val offerAttachments = mutableListOf<Attachment>()
//        var credentialPreview: V2CredentialPreview? = null
//
//        for (formatService in formatServices) {
//            val proposalAttachment = getAttachmentForService(formatService, proposalMessage.formats, proposalMessage.proposalAttachments)
//
//            val (attachment, format, previewAttributes) = formatService.acceptProposal(agentContext, credentialRecord, credentialFormats, proposalAttachment)
//
//            if (previewAttributes != null) {
//                credentialPreview = V2CredentialPreview(previewAttributes)
//            }
//
//            offerAttachments.add(attachment)
//            formats.add(format)
//        }
//
//        credentialRecord.credentialAttributes = credentialPreview?.attributes ?: emptyList()
//
//        val message = V2OfferCredentialMessage(
//            formats = formats,
//            credentialPreview = credentialPreview ?: V2CredentialPreview(emptyList()),
//            offerAttachments = offerAttachments,
//            comment = comment,
//            goalCode = goalCode,
//            goal = goal
//        )
//
//        message.setThread(credentialRecord.threadId, credentialRecord.parentThreadId)
//
//        didCommMessageRepository.saveOrUpdateAgentMessage(agentContext, message, DidCommMessageRole.Sender, credentialRecord.id)
//
//        return message
//    }
//
//    private fun getAttachmentForService(
//        credentialFormatService: CredentialFormatService,
//        formats: List<Format>,
//        attachments: List<Attachment>
//    ): Attachment {
//        val attachmentId = getAttachmentIdForService(credentialFormatService, formats)
//        return attachments.find { it.id == attachmentId }
//            ?: throw CredoError("Attachment with id $attachmentId not found in attachments.")
//    }
//
//    private fun getAttachmentIdForService(credentialFormatService: CredentialFormatService, formats: List<Format>): String {
//        val format = formats.find { credentialFormatService.supportsFormat(it.format) }
//            ?: throw CredoError("No attachment found for service ${credentialFormatService.formatKey}")
//        return format.attachmentId
//    }
}