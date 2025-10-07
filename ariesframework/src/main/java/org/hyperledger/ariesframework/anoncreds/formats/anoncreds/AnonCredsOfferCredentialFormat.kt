package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

@Serializable
data class AnonCredsOfferCredentialFormat(
    @SerialName("credentialDefinitionId")
    val credentialDefinitionId: String,

    @SerialName("revocationRegistryDefinitionId")
    val revocationRegistryDefinitionId: String? = null,

    @SerialName("revocationRegistryIndex")
    val revocationRegistryIndex: Int? = null,

    @SerialName("attributes")
    val attributes: List<CredentialPreviewAttribute>,

    @SerialName("linkedAttachments")
    val linkedAttachments: List<LinkedAttachment>? = null,
)
