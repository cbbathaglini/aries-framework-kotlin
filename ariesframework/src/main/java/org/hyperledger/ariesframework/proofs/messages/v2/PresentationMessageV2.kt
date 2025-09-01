package org.hyperledger.ariesframework.proofs.messages.v2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.decorators.AckDecorator
import org.hyperledger.ariesframework.decorators.AckValues
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec

@Serializable
class PresentationMessageV2(
    val comment: String? = null,

    @SerialName("goal_code")
    val goalCode: String? = null,

    val goal: String? = null,

    @SerialName("last_presentation")
    val lastPresentation: Boolean? = true,

    @SerialName("formats")
    val formats: List<ProofFormatSpec>,

    @SerialName("presentations~attach")
    val presentationAttachments: List<Attachment>,

    var pleaseAck: AckDecorator? = null,
) : AgentMessage(generateId(), type) {

    companion object {
        const val INDY_PROOF_ATTACHMENT_ID = "indy"
        const val ANONCREDS_PROOF_ATTACHMENT_ID = "indy"
        const val type = "https://didcomm.org/present-proof/2.0/presentation"
    }

    fun getPresentationAttachmentById(id: String): Attachment? {
        return presentationAttachments.firstOrNull { it.id == id }
    }

    fun indyProof(): String {
        val attachment = getPresentationAttachmentById(INDY_PROOF_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Presentation attachment not found")
    }

    fun anoncredsProof(): String {
        val attachment = getPresentationAttachmentById(ANONCREDS_PROOF_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Presentation attachment not found")
    }

    fun setPleaseAck(on: List<AckValues> = listOf(AckValues.Receipt)) {
        this.pleaseAck = AckDecorator(on)
    }
}
