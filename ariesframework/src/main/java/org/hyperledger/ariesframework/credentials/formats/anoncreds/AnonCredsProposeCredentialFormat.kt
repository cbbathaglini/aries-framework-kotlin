package org.hyperledger.ariesframework.credentials.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.v2.models.CredentialPreviewAttributeOptions


@Serializable
data class AnonCredsProposeCredentialFormat(
    @SerialName("schemaIssuerId") val schemaIssuerId: String? = null,
    @SerialName("schemaId") val schemaId: String? = null,
    @SerialName("schemaName") val schemaName: String? = null,
    @SerialName("schemaVersion") val schemaVersion: String? = null,

    @SerialName("credentialDefinitionId") val credentialDefinitionId: String? = null,
    @SerialName("issuerId") val issuerId: String? = null,

    @SerialName("attributes") val attributes: List<CredentialPreviewAttributeOptions>? = null,
    @SerialName("linkedAttachments") val linkedAttachments: List<LinkedAttachment>? = null,

    // Compatibilidade com versões anteriores
    @SerialName("schemaIssuerDid") val schemaIssuerDid: String? = null,
    @SerialName("issuerDid") val issuerDid: String? = null
)