package org.hyperledger.ariesframework.credentials.v2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.v2.CredentialsV2Constants.Companion.REQUEST_CREDENTIAL
import org.hyperledger.ariesframework.credentials.v2.models.Format

@Serializable
class RequestCredentialMessageV2(
    val formats: List<Format>,

    @SerialName("requests~attach")
    val requestAttachments: List<Attachment>,

    @SerialName("goal_code")
    val goalCode: String? = null,

    val goal: String? = null,

    val comment: String? = null,

    ) : AgentMessage(generateId(), type) {

    companion object {
        const val INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID = "indy"
        const val FORMAT = "hlindy/cred-abstract@v2.0"
        val type = REQUEST_CREDENTIAL
    }

    fun getRequestAttachmentById(id: String): Attachment? {
        return requestAttachments.find { it.id == id }
    }

}