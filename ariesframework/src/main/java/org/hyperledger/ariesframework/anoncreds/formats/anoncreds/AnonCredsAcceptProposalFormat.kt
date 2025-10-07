package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

@Serializable
data class AnonCredsAcceptProposalFormat(
    @SerialName("credentialDefinitionId")
    val credentialDefinitionId: String? = null,

    @SerialName("revocationRegistryDefinitionId")
    val revocationRegistryDefinitionId: String? = null,

    @SerialName("revocationRegistryIndex")
    val revocationRegistryIndex: Int? = null,

    @SerialName("attributes")
    val attributes: List<CredentialPreviewAttribute>? = null,

    @SerialName("linkedAttachments")
    val linkedAttachments: List<LinkedAttachment>? = null,
)
