package org.hyperledger.ariesframework.anoncreds.formats.legacyindy

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

@Serializable
data class CreateIndyOfferOptions (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val attachmentId: String? = null,
    val attributes: List<CredentialPreviewAttribute>? = emptyList(),
    val credentialDefinitionId: String,
    val linkedAttachments: List<LinkedAttachment>? = emptyList()
)