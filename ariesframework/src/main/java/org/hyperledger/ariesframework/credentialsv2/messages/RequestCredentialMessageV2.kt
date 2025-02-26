package org.hyperledger.ariesframework.credentialsv2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentialsv2.models.Format
import java.text.Normalizer.Form

@Serializable
class RequestCredentialMessageV2(
    val formats: List<Format>,

    @SerialName("requests~attach")
    val requestAttachments: List<Attachment>,

    @SerialName("goal_code")
    val goalCode: String? = null,

    val goal: String? = null,

    val comment: String? = null,

) : AgentMessage(generateId(), RequestCredentialMessageV2.type) {

    companion object {
        val type = "https://didcomm.org/issue-credential/2.0/request-credential"
    }

    fun getRequestAttachmentById(id: String): Attachment? {
        return requestAttachments.find { it.id == id }
    }
}