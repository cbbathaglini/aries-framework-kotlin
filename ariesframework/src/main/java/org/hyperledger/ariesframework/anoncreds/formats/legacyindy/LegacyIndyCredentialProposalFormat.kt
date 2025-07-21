package org.hyperledger.ariesframework.anoncreds.formats.legacyindy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

@Serializable
data class LegacyIndyCredentialProposalFormat(
    @SerialName("schema_id")
    val schemaId: String? = null,

    @SerialName("schema_name")
    val schemaName: String? = null,

    @SerialName("schema_version")
    val schemaVersion: String? = null,

    @SerialName("credential_definition_id")
    val credentialDefinitionId: String? = null,

    @SerialName("attributes")
    val attributes: List<CredentialPreviewAttribute>? = null,

    @SerialName("linked_attachments")
    val linkedAttachments: List<LinkedAttachment>? = null
)