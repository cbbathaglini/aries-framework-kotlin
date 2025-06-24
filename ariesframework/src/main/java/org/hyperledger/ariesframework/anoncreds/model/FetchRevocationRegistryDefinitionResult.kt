package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class FetchRevocationRegistryDefinitionResult (
    val revocationRegistryDefinition = result.revocationRegistryDefinition,
    val revocationRegistryDefinitionId = revocationRegistryDefinitionId,
    val indyNamespace : String?
)
