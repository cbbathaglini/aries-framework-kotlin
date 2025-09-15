package org.hyperledger.ariesframework.anoncreds.formats.legacyindy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

@Serializable
data class LegacyIndyProposeCredentialFormat(
    @SerialName("schemaId")
    val schemaId: String? = null,

    @SerialName("schemaName")
    val schemaName: String? = null,

    @SerialName("schemaVersion")
    val schemaVersion: String? = null,

    @SerialName("credentialDefinitionId")
    val credentialDefinitionId: String? = null,

    @SerialName("attributes")
    val attributes: List<CredentialPreviewAttribute>? = null,

    @SerialName("linkedAttachments")
    val linkedAttachments: List<LinkedAttachment>? = null,
)
