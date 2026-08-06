package org.hyperledger.ariesframework.anoncreds.model

import uniffi.indy_besu_vdr.RevocationRegistryDefinition
import uniffi.indy_besu_vdr.RevocationStatusList

data class RevocationRegistryBucket(
    val definition: RevocationRegistryDefinition? = null,
    val tailsFilePath: String? = null,
    val tailsHash: String? = null,
    val revocationStatusLists: MutableMap<ULong, RevocationStatusList>? = null,
    val webvhEntry: AnonCredsRevocationRegistryEntry? = null,
)
