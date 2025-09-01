package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsRevocationRegistryEntry(
    val tailsFilePath: String,
    val definition: AnonCredsRevocationRegistryDefinition,
    val revocationStatusLists: Map<Long, AnonCredsRevocationStatusList>? = null
)

typealias AnonCredsRevocationRegistries = MutableMap<String, AnonCredsRevocationRegistryEntry>