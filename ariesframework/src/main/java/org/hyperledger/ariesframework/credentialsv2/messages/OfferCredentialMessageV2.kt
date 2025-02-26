package org.hyperledger.ariesframework.credentialsv2.messages

import org.hyperledger.ariesframework.credentialsv2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentialsv2.models.Format
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
class OfferCredentialMessageV2(
    val formats: List<Format>,

    @SerialName("offers~attach")
    val offerAttachments: List<Attachment>,

    @SerialName("goal_code")
    val goalCode: String? = null,

    val goal: String? = null,

    val comment: String? = null,

    @SerialName("credential_preview")
    val credentialPreview: CredentialPreviewV2? = null,

    @SerialName("replacement_id") val replacementId: String? = null,

) : AgentMessage(generateId(), OfferCredentialMessageV2.type) {

    companion object {
        val type = "https://didcomm.org/issue-credential/2.0/offer-credential"
    }

    fun getOfferAttachmentById(id: String): Attachment? {
        return offerAttachments.find { it.id == id }
    }
}