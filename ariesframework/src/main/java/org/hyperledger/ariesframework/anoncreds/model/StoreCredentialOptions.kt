package org.hyperledger.ariesframework.anoncreds.model

data class StoreCredentialOptions(
    val credential: Any, // Pode ser W3cJsonLdVerifiableCredential ou AnonCredsCredential
    val credentialRequestMetadata: AnonCredsCredentialRequestMetadata,
    val credentialDefinition: AnonCredsCredentialDefinition,
    val schema: AnonCredsSchema,
    val credentialDefinitionId: String,
    val credentialId: String? = null,
    val revocationRegistry: RevocationRegistryInfo? = null,
)

data class RevocationRegistryInfo(
    val id: String,
    val definition: AnonCredsRevocationRegistryDefinition,
)
