package org.hyperledger.ariesframework.proofs.messages.v2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ProofFormat

@Serializable
class PresentationMessageV2(
    val comment: String? = null,
    @SerialName("formats")
    val formsts: List<ProofFormat>,
    @SerialName("presentations~attach")
    val presentationAttachments: List<Attachment>,
) : AgentMessage(generateId(), type) {
    companion object {
        const val INDY_PROOF_ATTACHMENT_ID = "indy"
        const val type = "https://didcomm.org/present-proof/2.0/presentation"
    }

    fun getPresentationAttachmentById(id: String): Attachment? {
        return presentationAttachments.firstOrNull { it.id == id }
    }

    fun indyProof(): String {
        val attachment = getPresentationAttachmentById(INDY_PROOF_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Presentation attachment not found")
    }
}
