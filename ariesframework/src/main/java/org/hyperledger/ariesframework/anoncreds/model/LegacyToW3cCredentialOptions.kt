package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class LegacyToW3cCredentialOptions(
    val credential: AnonCredsCredential,
    val issuerId: String,
    val processOptions: ProcessOptions? = null,
)

@Serializable
data class ProcessOptions(
    val credentialDefinition: AnonCredsCredentialDefinition,
    val credentialRequestMetadata: AnonCredsCredentialRequestMetadata,
    val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition? = null,
)
