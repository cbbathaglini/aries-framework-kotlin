package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class FetchRevocationRegistryDefinitionResult (
    val revocationRegistryDefinition : AnonCredsRevocationRegistryDefinition? = null,
    val revocationRegistryDefinitionId : String,
    val indyNamespace : String?
)
