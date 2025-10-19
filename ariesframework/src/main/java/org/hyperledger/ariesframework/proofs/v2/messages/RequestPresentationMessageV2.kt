package org.hyperledger.ariesframework.proofs.v2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec

@Serializable
class RequestPresentationMessageV2(
    val comment: String? = null,

    val goal: String? = null,

    @SerialName("goal_code")
    val goalCode: String? = null,

    @SerialName("will_confirm")
    val willConfirm: Boolean? = true,

    @SerialName("present_multiple")
    val presentMultiple: Boolean? = false,

    @SerialName("formats")
    val formats: List<ProofFormatSpec> = emptyList(),

    @SerialName("request_presentations~attach")
    val requestAttachment: List<Attachment>,
) : AgentMessage(generateId(), type) {

    companion object {
        const val INDY_PROOF_REQUEST_ATTACHMENT_ID = "indy"
        const val ANONCREDS_PROOF_REQUEST_ATTACHMENT_ID = "anoncreds"
        const val type = "https://didcomm.org/present-proof/2.0/request-presentation"
    }

    fun getRequestPresentationAttachmentById(id: String): Attachment? {
        return requestAttachment.firstOrNull { it.id == id }
    }

    fun indyProofRequest(): String {
        val attachment = getRequestPresentationAttachmentById(INDY_PROOF_REQUEST_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Request presentation attachment not found")
    }

    fun anoncredsProofRequest(): String {
        val attachment = getRequestPresentationAttachmentById(ANONCREDS_PROOF_REQUEST_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Request presentation attachment not found")
    }
}
