package org.hyperledger.ariesframework.credentials.formats.anoncreds

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

@Serializable
data class CreateAnoncredsOffer(
    val credentialExchangeRecord: CredentialExchangeRecord,
    val attachmentId: String?,
    val attributes: List<CredentialPreviewAttribute>,
    val credentialDefinitionId: String,
    val revocationRegistryDefinitionId: String?,
    val revocationRegistryIndex: Long?,
    val linkedAttachments: List<LinkedAttachment>?,
)
