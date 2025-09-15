package org.hyperledger.ariesframework.vc.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsCredentialTags(
    @SerialName("anonCredsLinkSecretId")
    val linkSecretId: String,

    @SerialName("anonCredsCredentialRevocationId")
    val credentialRevocationId: String? = null,

    @SerialName("anonCredsMethodName")
    val methodName: String,

    @SerialName("anonCredsSchemaName")
    val schemaName: String,

    @SerialName("anonCredsSchemaVersion")
    val schemaVersion: String,

    @SerialName("anonCredsSchemaId")
    val schemaId: String,

    @SerialName("anonCredsSchemaIssuerId")
    val schemaIssuerId: String,

    @SerialName("anonCredsCredentialDefinitionId")
    val credentialDefinitionId: String,

    @SerialName("anonCredsRevocationRegistryId")
    val revocationRegistryId: String? = null,

    @SerialName("anonCredsUnqualifiedIssuerId")
    val unqualifiedIssuerId: String? = null,

    @SerialName("anonCredsUnqualifiedSchemaId")
    val unqualifiedSchemaId: String? = null,

    @SerialName("anonCredsUnqualifiedSchemaIssuerId")
    val unqualifiedSchemaIssuerId: String? = null,

    @SerialName("anonCredsUnqualifiedCredentialDefinitionId")
    val unqualifiedCredentialDefinitionId: String? = null,

    @SerialName("anonCredsUnqualifiedRevocationRegistryId")
    val unqualifiedRevocationRegistryId: String? = null,

    val dynamicAttributes: Map<String, String?> = emptyMap(),
)
