package org.hyperledger.ariesframework.anoncreds.model

import uniffi.indy_besu_vdr.RevocationRegistryDefinition
import uniffi.indy_besu_vdr.RevocationStatusList

data class RevocationRegistryBucket(
    val definition: RevocationRegistryDefinition,
    val tailsFilePath: String? = null,
    val tailsHash: String? = null,
    val revocationStatusLists: MutableMap<Long, RevocationStatusList>? = null,
)
