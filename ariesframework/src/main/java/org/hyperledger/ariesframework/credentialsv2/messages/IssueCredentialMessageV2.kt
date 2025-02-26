package org.hyperledger.ariesframework.credentialsv2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentialsv2.models.Format

@Serializable
class IssueCredentialMessageV2(
    val formats: List<Format>,

    @SerialName("credentials~attach")
    val credentialAttachments: List<Attachment>,

    @SerialName("goal_code") val goalCode: String? = null,
    val goal: String? = null,

    val comment: String? = null
) : AgentMessage(generateId(), IssueCredentialMessageV2.type) {

    companion object {
        val type = "https://didcomm.org/issue-credential/2.0/issue-credential"
    }

    fun getCredentialAttachmentById(id: String): Attachment? {
        return credentialAttachments.find { it.id == id }
    }
}