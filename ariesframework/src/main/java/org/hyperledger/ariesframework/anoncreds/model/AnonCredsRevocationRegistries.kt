package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsRevocationRegistryEntry(
    val tailsFilePath: String,
    val tailsHash: String? = null,
    val definition: AnonCredsRevocationRegistryDefinition,
    val revocationStatusLists: MutableMap<Long, AnonCredsRevocationStatusList>? = null
)

typealias AnonCredsRevocationRegistries = MutableMap<String, AnonCredsRevocationRegistryEntry>