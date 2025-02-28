package org.hyperledger.ariesframework.credentials.v2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.v2.CredentialsV2Constants.Companion.ISSUE_CREDENTIAL
import org.hyperledger.ariesframework.credentials.v2.models.Format

@Serializable
class IssueCredentialMessageV2(
    val formats: List<Format>,

    @SerialName("credentials~attach")
    val credentialAttachments: List<Attachment>,

    @SerialName("goal_code") val goalCode: String? = null,
    val goal: String? = null,

    val comment: String? = null
) : AgentMessage(generateId(), type) {

    companion object {
        const val INDY_CREDENTIAL_ATTACHMENT_ID = "indy"
        val type = ISSUE_CREDENTIAL
    }

    fun getCredentialAttachmentById(id: String): Attachment? {
        return credentialAttachments.find { it.id == id }
    }
}