package org.hyperledger.ariesframework.proofs.messages.v2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec

@Serializable
class ProposePresentationMessageV2(
    val comment: String? = null,
    @SerialName("goal_code")
    val goalCode: String? = null,
    val goal: String? = null,
    @SerialName("proposals~attach")
    val proposalAttachments: MutableList<Attachment> = mutableListOf(),
    val formats : MutableList<ProofFormatSpec> = mutableListOf()
) : AgentMessage(generateId(), type) {

    companion object {
        const val type = "https://didcomm.org/present-proof/2.0/propose-presentation"
    }

    fun getProposalAttachmentById(id: String): Attachment? {
        return proposalAttachments.find { it.id == id }
    }

}