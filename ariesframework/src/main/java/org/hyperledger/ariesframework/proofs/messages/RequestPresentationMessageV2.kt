package org.hyperledger.ariesframework.proofs.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
class RequestPresentationMessageV2(
    val comment: String? = null,
    @SerialName("request_presentations~attach")
    val requestPresentationAttachmentsV2: List<Attachment>,
) : AgentMessage(generateId(), RequestPresentationMessageV2.type) {
    companion object {
        const val INDY_PROOF_REQUEST_ATTACHMENT_ID = "indy"
        const val type = "https://didcomm.org/present-proof/2.0/request-presentation"
    }

    fun getRequestPresentationAttachmentById(id: String): Attachment? {
        return requestPresentationAttachmentsV2.firstOrNull { it.id == id }
    }

    fun indyProofRequest(): String {
        val attachment = getRequestPresentationAttachmentById(RequestPresentationMessageV2.INDY_PROOF_REQUEST_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Request presentation attachment not found")
    }
}
