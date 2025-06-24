package org.hyperledger.ariesframework.anoncreds

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsResolutionMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition

@Serializable
data class GetRevocationRegistryDefinitionReturn(
    val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition? = null,
    val revocationRegistryDefinitionId: String,
    val resolutionMetadata: AnonCredsResolutionMetadata,
    val revocationRegistryDefinitionMetadata: Map<String, Any> //exensible
)