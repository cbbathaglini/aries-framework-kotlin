package org.hyperledger.ariesframework.credentialsv2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentialsv2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentialsv2.models.Format

@Serializable
class ProposeCredentialMessageV2(
    val formats: List<Format>,

    @SerialName("filters~attach")
    val proposalAttachments: List<Attachment>,

    @SerialName("credential_preview")
    val credentialPreview: CredentialPreviewV2? = null,

    @SerialName("goal_code")
    val goalCode: String? = null,

    val goal: String? = null,

    val comment: String? = null,

) : AgentMessage(generateId(), ProposeCredentialMessageV2.type) {

    companion object {
        val type = "https://didcomm.org/issue-credential/2.0/propose-credential"
    }

    fun getProposalAttachmentById(id: String): Attachment? {
        return proposalAttachments.find { it.id == id }
    }
}