package org.hyperledger.ariesframework.credentials.v1.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
class RequestCredentialMessage(
    val comment: String? = null,
    @SerialName("requests~attach")
    val requestAttachments: List<Attachment>,
) : AgentMessage(generateId(), type) {
    companion object {
        const val INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID = "libindy-cred-request-0"
        const val type = "https://didcomm.org/issue-credential/1.0/request-credential"
    }

    fun getRequestAttachmentById(id: String): Attachment? {
        return requestAttachments.firstOrNull { it.id == id }
    }
}
