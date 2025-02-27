package org.hyperledger.ariesframework.credentials.v2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.v2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentials.v2.CredentialsV2Constants
import org.hyperledger.ariesframework.credentials.v2.models.Format

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

    @SerialName("replacement_id")
    val replacementId: String? = null,

    ) : AgentMessage(generateId(), type) {

    companion object {
        const val INDY_CREDENTIAL_OFFER_ATTACHMENT_ID = "indy"
        const val type = CredentialsV2Constants.OFFER_CREDENTIAL
    }


    fun findIndyFormatByAttachId(): Format? {
        return formats.find { it.attachId == INDY_CREDENTIAL_OFFER_ATTACHMENT_ID }
    }


    fun getOfferAttachmentById(id: String): Attachment? {
        return offerAttachments.find { it.id == id }
    }

    fun getCredentialOffer(): String {
        val attachment = getOfferAttachmentById(INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Credential offer attachment not found")
    }

}