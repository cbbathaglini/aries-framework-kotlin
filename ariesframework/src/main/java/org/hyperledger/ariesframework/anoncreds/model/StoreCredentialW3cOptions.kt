package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential

@Serializable
data class StoreCredentialW3cOptions(
    val credential: @Contextual W3cJsonLdVerifiableCredential,
    val credentialDefinitionId: String,
    val schema: AnonCredsSchema,
    val credentialDefinition: AnonCredsCredentialDefinition,
    val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition? = null,
    val revocationRegistryId: String? = null,
    val credentialRequestMetadata: AnonCredsCredentialRequestMetadata,
)
